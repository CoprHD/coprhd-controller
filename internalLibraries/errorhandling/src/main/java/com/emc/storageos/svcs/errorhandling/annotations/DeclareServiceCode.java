/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This annotation is used to indicate which {@link ServiceCode} to use when generating the {@link Exception} returned by the method that is annotated with this.
 * Only methods in an interface annotated with {@link MessageBundle} should use this annotation.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 * @author fountt1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DeclareServiceCode {
    ServiceCode value();
}
