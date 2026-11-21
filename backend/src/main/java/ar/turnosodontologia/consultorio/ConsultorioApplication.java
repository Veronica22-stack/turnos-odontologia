package ar.turnosodontologia.consultorio;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsultorioApplication {

    private static final String UTC = "UTC";

    /** Fija UTC como zona por defecto de la JVM: pgJDBC la usa para la zona de la sesion de base de datos. */
    public static void configureTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(UTC));
    }

    public static void main(String[] args) {
        configureTimeZone();
        SpringApplication.run(ConsultorioApplication.class, args);
    }
}
