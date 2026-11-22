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

/** La cara solo se admite para estados que se refieren a una cara (caries y obturacion). */
@Documented
@Constraint(validatedBy = SurfaceAllowedForCondition.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SurfaceAllowedForCondition {

    String message() default "surface not allowed for condition";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Si la cara o el estado son nulos o invalidos, los reportan sus propias restricciones. */
    class Validator implements ConstraintValidator<SurfaceAllowedForCondition, OdontogramEntryRequest> {

        @Override
        public boolean isValid(OdontogramEntryRequest request, ConstraintValidatorContext context) {
            if (request == null || request.surface() == null || request.condition() == null) {
                return true;
            }
            boolean allowed = "CARIES".equals(request.condition()) || "FILLED".equals(request.condition());
            boolean knownCondition = switch (request.condition()) {
                case "HEALTHY", "CARIES", "FILLED", "MISSING", "CROWN", "ROOT_CANAL" -> true;
                default -> false;
            };
            if (allowed || !knownCondition) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("surface")
                    .addConstraintViolation();
            return false;
        }
    }
}
