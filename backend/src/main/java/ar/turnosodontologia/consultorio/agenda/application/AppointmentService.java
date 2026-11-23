package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.Appointment;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentEventsPublisher;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentOrigin;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import ar.turnosodontologia.consultorio.agenda.domain.CancelledBy;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import ar.turnosodontologia.consultorio.agenda.domain.TimeInterval;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursFit;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursRepository;
import ar.turnosodontologia.consultorio.pacientes.api.PatientLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientStatus;
import ar.turnosodontologia.consultorio.shared.application.ConstraintViolations;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.InvalidRangeException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.PracticeLock;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico camino de escritura sobre {@code appointment} (design D16, D18). Orden de validaciones del alta:
 * 1) inicio futuro, 2) paciente, 3) prestacion, 4) lock por consultorio, 5) horario de atencion, 6) bloqueos,
 * 7) turnos, 8) saveAndFlush, 9) evento. La reprogramacion sigue el mismo orden (sin 2 y 3).
 */
@Service
public class AppointmentService {

    /** Amplitud maxima del listado por rango (design D17, ajuste post-verify W1). */
    public static final Duration MAX_LIST_RANGE = Duration.ofDays(93);

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentService.class);
    private static final String NO_OVERLAP_CONSTRAINT = "appointment_no_overlap";
    private static final String NOT_FOUND = "APPOINTMENT_NOT_FOUND";

    private final AppointmentRepository appointments;
    private final PracticeServiceRepository services;
    private final WorkingHoursRepository workingHours;
    private final ScheduleBlockRepository blocks;
    private final PatientLookup patients;
    private final PracticeLock lock;
    private final AppointmentEventsPublisher events;
    private final InstallationPractice practice;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, PracticeServiceRepository services,
            WorkingHoursRepository workingHours, ScheduleBlockRepository blocks, PatientLookup patients,
            PracticeLock lock, AppointmentEventsPublisher events, InstallationPractice practice, Clock clock) {
        this.appointments = appointments;
        this.services = services;
        this.workingHours = workingHours;
        this.blocks = blocks;
        this.patients = patients;
        this.lock = lock;
        this.events = events;
        this.practice = practice;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResult book(BookAppointmentCommand command) {
        UUID practiceId = practice.id();
        requireFuture(command.startsAt());
        requireWritablePatient(command.patientId());
        PracticeService catalogService = requireActiveService(command.serviceId(), practiceId);
        lock.lock(practiceId);
        AppointmentOrigin origin = command.origin() != null ? command.origin() : AppointmentOrigin.INTERNAL;
        Appointment appointment = Appointment.create(practiceId, command.patientId(), command.serviceId(),
                command.startsAt(), catalogService.durationMin(), origin, command.overbooking(),
                command.notes(), clock);
        ensureAvailable(practiceId, appointment.startsAt(), appointment.endsAt(), command.overbooking(), null);
        Appointment saved = saveAndTranslate(appointment);
        events.confirmed(saved);
        // Solo identificadores: nunca notas ni datos del paciente.
        LOG.info("Turno confirmado appointmentId={} patientId={} serviceId={} practiceId={}", saved.id(),
                saved.patientId(), saved.serviceId(), practiceId);
        return AppointmentResult.of(saved);
    }

    @Transactional
    public AppointmentResult cancel(UUID id) {
        return cancel(id, CancelledBy.DENTIST);
    }

    /** Cancelación expuesta a otros módulos (C-15): el consumidor decide {@code by}, normalmente {@code PATIENT}. */
    @Transactional
    public AppointmentResult cancel(UUID id, CancelledBy by) {
        if (by == null) {
            throw new ValidationException("cancelledBy", "REQUIRED");
        }
        Appointment appointment = lockAndLoad(id);
        appointment.cancel(by, clock);
        appointments.save(appointment);
        events.cancelled(appointment);
        LOG.info("Turno cancelado appointmentId={} practiceId={}", appointment.id(), appointment.practiceId());
        return AppointmentResult.of(appointment);
    }

    @Transactional
    public AppointmentResult complete(UUID id) {
        Appointment appointment = lockAndLoad(id);
        appointment.complete(clock);
        appointments.save(appointment);
        return AppointmentResult.of(appointment);
    }

    @Transactional
    public AppointmentResult noShow(UUID id) {
        Appointment appointment = lockAndLoad(id);
        appointment.markNoShow(clock);
        appointments.save(appointment);
        return AppointmentResult.of(appointment);
    }

    /**
     * Reprogramacion atomica in-place (design D14, D15): conserva id, paciente, prestacion y duracion snapshot; si
     * el nuevo rango no esta disponible la transaccion se revierte y el turno original queda intacto.
     */
    @Transactional
    public AppointmentResult reschedule(UUID id, Instant newStartsAt, boolean overbooking) {
        if (id == null) {
            throw new ValidationException("appointmentId", "REQUIRED");
        }
        if (newStartsAt == null) {
            throw new ValidationException("newStartsAt", "REQUIRED");
        }
        UUID practiceId = practice.id();
        // Sin datos concurrentes: puede fallar antes del lock. El turno se carga recien bajo el lock (D3, D18).
        requireFuture(newStartsAt);
        Appointment appointment = lockAndLoad(id);
        appointment.validateReschedulable(newStartsAt, clock);
        Instant newEndsAt = appointment.endsAtIfStartedAt(newStartsAt);
        ensureAvailable(practiceId, newStartsAt, newEndsAt, overbooking, appointment.id());
        Instant previousStartsAt = appointment.startsAt();
        Instant previousEndsAt = appointment.endsAt();
        appointment.reschedule(newStartsAt, overbooking, clock);
        Appointment saved = saveAndTranslate(appointment);
        events.rescheduled(saved, previousStartsAt, previousEndsAt);
        LOG.info("Turno reprogramado appointmentId={} practiceId={}", saved.id(), practiceId);
        return AppointmentResult.of(saved);
    }

    @Transactional(readOnly = true)
    public AppointmentResult get(UUID id) {
        return AppointmentResult.of(requireOwned(id));
    }

    /**
     * Turnos de cualquier estado cuyo rango intersecta {@code [from, to)}, ordenados por inicio. Ambos extremos son
     * obligatorios, {@code to} debe ser posterior a {@code from} y la amplitud no puede superar
     * {@link #MAX_LIST_RANGE}; si no, {@link InvalidRangeException} (400 INVALID_RANGE).
     */
    @Transactional(readOnly = true)
    public List<AppointmentResult> list(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)
                || Duration.between(from, to).compareTo(MAX_LIST_RANGE) > 0) {
            throw new InvalidRangeException();
        }
        return appointments.findInRange(practice.id(), from, to).stream().map(AppointmentResult::of).toList();
    }

    /**
     * Toma el lock del consultorio ANTES de leer el turno (desvio aprobado D3): serializa toda mutacion de turnos y
     * evita que una operacion pise con una copia vieja el estado que otra acaba de confirmar (lost update).
     * Orden de locks unico: consultorio y luego fila del turno.
     */
    private Appointment lockAndLoad(UUID id) {
        lock.lock(practice.id());
        return requireOwned(id);
    }

    private Appointment requireOwned(UUID id) {
        UUID practiceId = practice.id();
        return appointments.findById(id)
                .filter(appointment -> practiceId.equals(appointment.practiceId()))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** Pasos 5 a 7 de D18; siempre se ejecutan bajo el lock por consultorio. */
    private void ensureAvailable(UUID practiceId, Instant startsAt, Instant endsAt, boolean overbooking,
            UUID excludedId) {
        if (!overbooking && !WorkingHoursFit.fits(startsAt, endsAt, practice.zone(),
                workingHours.findByPracticeId(practiceId))) {
            throw new BusinessRuleException("OUTSIDE_WORKING_HOURS");
        }
        TimeInterval candidate = new TimeInterval(startsAt, endsAt);
        boolean blocked = blocks.findOverlapping(practiceId, startsAt, endsAt).stream()
                .anyMatch(block -> candidate.intersects(new TimeInterval(block.startsAt(), block.endsAt())));
        if (blocked) {
            throw new ConflictException("APPOINTMENT_SLOT_BLOCKED");
        }
        boolean taken = appointments.findOverlappingActive(practiceId, startsAt, endsAt, excludedId).stream()
                .anyMatch(other -> candidate.intersects(new TimeInterval(other.startsAt(), other.endsAt())));
        if (taken) {
            throw new ConflictException("APPOINTMENT_SLOT_TAKEN");
        }
    }

    /** Camino de carrera residual (design D10): la exclusion de la base se traduce al mismo codigo que la pre-validacion. */
    private Appointment saveAndTranslate(Appointment appointment) {
        try {
            return appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException error) {
            if (ConstraintViolations.isExclusionViolation(error, NO_OVERLAP_CONSTRAINT)) {
                throw new ConflictException("APPOINTMENT_SLOT_TAKEN");
            }
            throw error;
        }
    }

    private void requireFuture(Instant startsAt) {
        if (!startsAt.isAfter(clock.instant())) {
            throw new BusinessRuleException("APPOINTMENT_IN_PAST");
        }
    }

    private void requireWritablePatient(UUID patientId) {
        PatientStatus status = patients.findStatus(patientId)
                .orElseThrow(() -> new NotFoundException("PATIENT_NOT_FOUND"));
        if (!status.active()) {
            throw new ConflictException("PATIENT_INACTIVE");
        }
    }

    private PracticeService requireActiveService(UUID serviceId, UUID practiceId) {
        PracticeService found = services.findById(serviceId)
                .filter(candidate -> practiceId.equals(candidate.practiceId()))
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND"));
        if (!found.isActive()) {
            throw new BusinessRuleException("SERVICE_INACTIVE");
        }
        return found;
    }
}
