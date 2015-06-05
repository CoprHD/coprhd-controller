/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.sf.oval.configuration.annotation.Constraint;

/**
 * This field must be a valid host name or IP address.
 * Message key: validation.hostNameOrIpAddress
 * $1: field name
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Constraint(checkWith = HostNameOrIpAddressCheck.class)
public @interface HostNameOrIpAddress {
    String message() default HostNameOrIpAddressCheck.MESSAGE_KEY;
}
