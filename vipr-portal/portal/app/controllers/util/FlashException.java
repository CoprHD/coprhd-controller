/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Automatically flashException and redirect if an exception is thrown by the decorated
 * Play controller action.
 *
 * Provide an action name (either in Controller.actionName or actionName) format to force
 * a redirect to a specific action. Otherwise, the current referrer will be used.
 *
 * keep = true to call Validation.keep() and params.flash()
 * 
 * referrer = A String array containing a valid redirect action or multiple actions.
 * This is overwritten if a value() is defined.  Set
 * verify = false to disable static verification, which allows the use of a regexp
 * pattern here (ie. "blockvirtualpools.*vdc\\d+")
 * 
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashException {
    String value() default "";
    boolean keep() default false;
    String[] referrer() default {};
    boolean verify() default true;
}
