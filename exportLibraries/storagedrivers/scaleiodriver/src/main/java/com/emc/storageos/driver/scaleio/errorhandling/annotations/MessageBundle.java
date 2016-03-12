/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.annotations;

import com.emc.storageos.driver.scaleio.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.driver.scaleio.errorhandling.resources.BadRequestException;
import com.emc.storageos.driver.scaleio.errorhandling.resources.BadRequestExceptions;
import com.emc.storageos.driver.scaleio.errorhandling.utils.Documenter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This is a marker Annotation for interfaces that have methods that need to be generated via the {@link ExceptionMessagesProxy}.
 * All the methods in the interface must be annotated with {@link DeclareServiceCode}.<br/>
 * You must also have a {@code interfaceName.properties} file which contains a key for each method and the message to use for each method.<br/>
 * If the methods of the interface return Exceptions then the interface name should suggest the type of exception returned,
 * i.e. all the methods in the {@link BadRequestExceptions} interface return {@link BadRequestException}s.
 * The return type can also be a sub-class of the one suggested.<br/>
 * You should update {@link Documenter#PACKAGES} when you add this annotation to an interface in a new package.
 * This will allow the methods in the interface to be documented at build time and for the unit test to validate them.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 * 
 * @author fountt1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MessageBundle {
    String value() default "";
}
