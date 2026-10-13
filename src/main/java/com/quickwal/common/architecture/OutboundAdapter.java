package com.quickwal.common.architecture;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import java.lang.annotation.*;

/**
 * Marks a Spring component that adapts an application port to an external
 * system.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface OutboundAdapter {

    /**
     * Supplies the component name registered with Spring.
     *
     * @return the component name, or an empty name for the default
     */
    @AliasFor(annotation = Component.class)
    String value() default "";
}
