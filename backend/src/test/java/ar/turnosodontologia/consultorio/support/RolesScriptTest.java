package ar.turnosodontologia.consultorio.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.RolesScript.Connect;
import ar.turnosodontologia.consultorio.support.RolesScript.Gexec;
import ar.turnosodontologia.consultorio.support.RolesScript.Statement;
import ar.turnosodontologia.consultorio.support.RolesScript.Step;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Intérprete del dialecto acotado de psql usado por infra/postgres/init/01-roles.sql (design.md D6/D7). */
class RolesScriptTest {

    @Test
    void sustituyeUnLiteralEscapandoLasComillasSimples() {
        List<Step> steps = RolesScript.parse("SELECT :'clave';", Map.of("clave", "o'brien"));

        assertThat(steps).containsExactly(new Statement("SELECT 'o''brien'", "SELECT :'clave'"));
    }

    @Test
    void sustituyeUnIdentificadorEscapandoLasComillasDobles() {
        List<Step> steps = RolesScript.parse("GRANT USAGE ON SCHEMA public TO :\"rol\";", Map.of("rol", "mi\"rol"));

        assertThat(steps).hasSize(1);
        assertThat(((Statement) steps.get(0)).sql()).isEqualTo("GRANT USAGE ON SCHEMA public TO \"mi\"\"rol\"");
    }

    @Test
    void fallaNombrandoLaVariableFaltante() {
        assertThatThrownBy(() -> RolesScript.parse("SELECT :'db_name', :'app_password';", Map.of("db_name", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app_password")
                .hasMessageNotContaining("db_name");
    }

    @Test
    void separaSentenciasPorPuntoYComaIgnorandoComentariosYCadenas() {
        String script = """
                -- comentario; con punto y coma
                SELECT 'a;b';
                SELECT 2; -- otro; comentario
                """;

        List<Step> steps = RolesScript.parse(script, Map.of());

        assertThat(steps).extracting(step -> ((Statement) step).sql()).containsExactly("SELECT 'a;b'", "SELECT 2");
    }

    @Test
    void noConfundeUnCastConUnaVariable() {
        List<Step> steps = RolesScript.parse("SELECT 1::int, :'x';", Map.of("x", "y"));

        assertThat(((Statement) steps.get(0)).sql()).isEqualTo("SELECT 1::int, 'y'");
    }

    @Test
    void reconoceGexecYConnect() {
        String script = """
                SELECT format('CREATE ROLE %I', :'rol')
                WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'rol')
                \\gexec
                \\connect :"base"
                SELECT 1;
                """;

        List<Step> steps = RolesScript.parse(script, Map.of("rol", "app", "base", "consultorio"));

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0)).isInstanceOf(Gexec.class);
        assertThat(((Gexec) steps.get(0)).sql())
                .contains("format('CREATE ROLE %I', 'app')")
                .contains("rolname = 'app'");
        assertThat(steps.get(1)).isEqualTo(new Connect("consultorio"));
        assertThat(steps.get(2)).isInstanceOf(Statement.class);
    }

    @Test
    void rechazaCualquierOtroMetacomando() {
        assertThatThrownBy(() -> RolesScript.parse("\\set x 1\nSELECT 1;", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\\set");
    }

    @Test
    void rechazaUnaSentenciaSinTerminar() {
        assertThatThrownBy(() -> RolesScript.parse("SELECT 1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sin terminar");
    }

    @Test
    void rechazaGexecSinSentenciaPrevia() {
        assertThatThrownBy(() -> RolesScript.parse("\\gexec", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gexec");
    }
}
