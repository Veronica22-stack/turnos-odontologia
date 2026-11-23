package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.WorkingHoursLookup;
import ar.turnosodontologia.consultorio.agenda.api.WeeklyInterval;
import ar.turnosodontologia.consultorio.agenda.application.WorkingHoursService;
import ar.turnosodontologia.consultorio.agenda.application.WorkingHoursSlot;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkingHoursLookupContractTest {

    @Mock
    private WorkingHoursService workingHoursService;

    private final UUID practiceId = UUID.randomUUID();

    @Test
    void elPuertoExisteYNoExportaApplicationNiJpa() {
        assertThat(WorkingHoursLookup.class.isInterface()).isTrue();
        for (Method method : WorkingHoursLookup.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getPackageName()).doesNotContain("application", "jakarta.persistence");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertThat(parameter.getPackageName()).doesNotContain("application", "jakarta.persistence");
            }
        }
        assertThat(WeeklyInterval.class.getDeclaredFields()).extracting("name")
                .containsExactly("weekday", "startTime", "endTime");
    }

    @Test
    void traduceLosTramosDelServicioAListadoOrdenado() {
        when(workingHoursService.getWorkingHours()).thenReturn(List.of(
                new WorkingHoursSlot(1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new WorkingHoursSlot(1, LocalTime.of(14, 0), LocalTime.of(18, 0))));

        List<WeeklyInterval> intervalos = new WorkingHoursLookupAdapter(workingHoursService)
                .findWeeklyIntervals(practiceId);

        assertThat(intervalos).containsExactly(
                new WeeklyInterval(1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new WeeklyInterval(1, LocalTime.of(14, 0), LocalTime.of(18, 0)));
    }

    @Test
    void sinHorariosDevuelveListaVacia() {
        when(workingHoursService.getWorkingHours()).thenReturn(List.of());

        assertThat(new WorkingHoursLookupAdapter(workingHoursService).findWeeklyIntervals(practiceId)).isEmpty();
    }
}
