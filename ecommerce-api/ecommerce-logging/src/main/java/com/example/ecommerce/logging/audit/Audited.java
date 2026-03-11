package com.example.ecommerce.logging.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be audited.
 * Use with AOP to automatically log method calls.
 *
 * Example:
 * @Audited(action = "CREATE_ORDER", resourceType = "Order")
 * public Order createOrder(...) { }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * The action being performed.
     */
    String action();

    /**
     * The type of resource being acted upon.
     */
    String resourceType() default "";

    /**
     * SpEL expression to extract resource ID from method parameters.
     * Example: "#id" or "#order.id"
     */
    String resourceId() default "";
}
