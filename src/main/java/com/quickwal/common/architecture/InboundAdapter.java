package com.quickwal.common.architecture;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import java.lang.annotation.*;

/**
 * Marks a Spring component that adapts an external request into an application
 * use case.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface InboundAdapter {

    /**
     * Supplies the component name registered with Spring.
     *
     * @return the component name, or an empty name for the default
     */
    @AliasFor(annotation = Component.class)
    String value() default "";
}
