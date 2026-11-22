package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

/** Un lote no puede incluir dos estados para la misma posicion (misma pieza y misma cara, o ambas sin cara). */
@Documented
@Constraint(validatedBy = DistinctToothPositions.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistinctToothPositions {

    String message() default "tooth positions must be distinct";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DistinctToothPositions, OdontogramRequest> {

        @Override
        public boolean isValid(OdontogramRequest request, ConstraintValidatorContext context) {
            if (request == null || request.entries() == null) {
                return true;
            }
            Set<String> seen = new HashSet<>();
            for (OdontogramEntryRequest entry : request.entries()) {
                if (entry != null && entry.toothCode() != null
                        && !seen.add(entry.toothCode() + "/" + entry.surface())) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                            .addPropertyNode("entries")
                            .addConstraintViolation();
                    return false;
                }
            }
            return true;
        }
    }
}
