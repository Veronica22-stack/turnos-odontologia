package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.ReplaceWorkingHoursRequest;
import ar.turnosodontologia.consultorio.agenda.api.WorkingHoursDto;
import ar.turnosodontologia.consultorio.agenda.application.WorkingHoursService;
import ar.turnosodontologia.consultorio.agenda.application.WorkingHoursSlot;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/working-hours")
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;

    public WorkingHoursController(WorkingHoursService workingHoursService) {
        this.workingHoursService = workingHoursService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<List<WorkingHoursDto>> getWorkingHours() {
        List<WorkingHoursSlot> slots = workingHoursService.getWorkingHours();
        return ResponseEntity.ok(slots.stream()
                .map(s -> new WorkingHoursDto(s.weekday(), s.startTime(), s.endTime()))
                .toList());
    }

    @PutMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<List<WorkingHoursDto>> replaceWorkingHours(
            @Valid @RequestBody ReplaceWorkingHoursRequest request) {
        List<WorkingHoursSlot> inputSlots = request.workingHours() != null
                ? request.workingHours().stream()
                        .map(dto -> new WorkingHoursSlot(dto.weekday(), dto.startTime(), dto.endTime()))
                        .toList()
                : List.of();

        List<WorkingHoursSlot> savedSlots = workingHoursService.replaceWorkingHours(inputSlots);
        return ResponseEntity.ok(savedSlots.stream()
                .map(s -> new WorkingHoursDto(s.weekday(), s.startTime(), s.endTime()))
                .toList());
    }
}
