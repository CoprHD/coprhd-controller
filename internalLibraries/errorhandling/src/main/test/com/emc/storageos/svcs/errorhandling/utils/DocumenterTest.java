/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.utils;

import static java.text.MessageFormat.format;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.Response.StatusType;

import org.junit.Test;

import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.utils.Documenter.DocumenterEntry;

public class DocumenterTest {

    @Test
    public void serviceCodeToStatus() {
        final Collection<DocumenterEntry> entries = Documenter.createEntries();

        final Map<ServiceCode, Set<StatusType>> serviceToStatus = new HashMap<ServiceCode, Set<StatusType>>();

        for (final DocumenterEntry entry : entries) {
            if (!serviceToStatus.containsKey(entry.getCode())) {
                serviceToStatus.put(entry.getCode(), new HashSet<StatusType>());
            }
            serviceToStatus.get(entry.getCode()).add(entry.getStatus());
        }

        final Map<ServiceCode, Set<StatusType>> failures = new HashMap<ServiceCode, Set<StatusType>>();
        for (final Entry<ServiceCode, Set<StatusType>> entry : serviceToStatus.entrySet()) {
            if (entry.getValue().size() > 1) {
                failures.put(entry.getKey(), entry.getValue());
            }
        }

        assertTrue("Some service codes map to more than one HTTP status code: " + failures,
                failures.isEmpty());
    }

    /**
     * This test ensures that the exception utility interfaces hold only methods
     * that return an exception that is of type (or a sub-type) of the exception
     * suggested by the name of the utility interface without the 's'. This is
     * to avoid confusion among developers. i.e: if we are using methods from
     * BadRequestExceptions, we would expect all methods to return a type or a
     * sub-type of BadRequestException.
     */
    @Test
    public void checkExceptionUtilityInterfacesConsistency() throws Exception {
        final List<Class<?>> list = Documenter.getMessageBundleClasses();

        // We are assuming that the return type must be of type (or a
        // sub-type) of the exception suggested by the name of the utility
        // interface without the 's':
        for (final Class<?> interfaze : list) {
            final String canonicalName = interfaze.getCanonicalName();
            final String expectedExceptionReturnedName = canonicalName.substring(0,
                    interfaze.getCanonicalName().length() - 1);
            if (expectedExceptionReturnedName.endsWith("Error")) {
                continue;
            }
            final Class<?> expectedExceptionReturned = Class.forName(expectedExceptionReturnedName);

            final Method[] methods = interfaze.getDeclaredMethods();
            for (final Method method : methods) {
                final Class<?> returnType = method.getReturnType();
                if (!expectedExceptionReturned.isAssignableFrom(returnType)) {
                    fail(format(
                            "Method {0} in interface {1} is returning a type that is not a {2}  or one of its subtypes.",
                            method.getName(), canonicalName, expectedExceptionReturnedName));
                }
            }
        }
    }

    /**
     * This test ensures that the message utility interfaces hold only methods
     * that return a {@link ServiceErrorRestRep}
     */
    @Test
    public void checkErrorsUtilityInterfacesConsistency() throws Exception {
        final List<Class<?>> list = Documenter.getMessageBundleClasses();

        for (final Class<?> interfaze : list) {
            if (!interfaze.getSimpleName().endsWith("Errors")) {
                continue;
            }
            final Method[] methods = interfaze.getDeclaredMethods();
            for (final Method method : methods) {
                final Class<?> returnType = method.getReturnType();
                if (!ServiceError.class.isAssignableFrom(returnType)) {
                    fail(format(
                            "Method {0} in interface {1} is returning a type that is not a ServiceError.",
                            method.getName(), interfaze.getCanonicalName()));
                }
            }
        }
    }
}
