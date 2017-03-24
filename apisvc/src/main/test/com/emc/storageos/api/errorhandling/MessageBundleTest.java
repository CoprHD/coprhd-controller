/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.errorhandling;

import static java.text.MessageFormat.format;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.utils.AbstractBundleTest;
import com.emc.storageos.svcs.errorhandling.utils.MessageUtils;

@RunWith(Parameterized.class)
public class MessageBundleTest extends AbstractBundleTest {
    public MessageBundleTest(final Class<?> baseClass) {
        super(baseClass);
    }

    @Test
    public void validPatterns() {
        // this will need to be modified so that it finds all MessageBundles
        final Map<String, String> failures = new HashMap<String, String>();
        final ResourceBundle bundle = MessageUtils.bundleForClass(baseClass);
        for (final String key : bundle.keySet()) {
            final String pattern = bundle.getString(key);
            try {
                MessageFormat format = new MessageFormat(pattern);
                assertNotNull(format);
            } catch (final IllegalArgumentException e) {
                failures.put(key, pattern);
            }
        }
        assertTrue(
                format("The following messages in the Bundle {0} are not valid: {1}",
                        baseClass.getName(), failures), failures.isEmpty());
    }

    @Test
    public void messagesWithoutMethods() {
        final Set<String> failures = new TreeSet<String>();

        System.out.println("Message bundle: " + baseClass);

        final ResourceBundle bundle = MessageUtils.bundleForClass(baseClass);
        final Method[] methods = baseClass.getDeclaredMethods();
        for (final String key : bundle.keySet()) {
            boolean found = false;
            for (final Method method : methods) {
                if (method.getName().equals(key)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add(key);
            }
        }

        assertTrue(
                format("The following keys in the Bundle {0} do not map to methods: {1}",
                        baseClass.getName(), failures), failures.isEmpty());
    }

    @Test
    public void methodsWithoutMessages() {
        final Set<String> failures = new TreeSet<String>();

        final ResourceBundle bundle = MessageUtils.bundleForClass(baseClass);

        final Method[] methods = baseClass.getDeclaredMethods();
        for (final Method method : methods) {
            final String name = method.getName();
            if (!bundle.keySet().contains(name)) {
                failures.add(name);
            }
        }

        assertTrue(
                format("The following methods do not have messages in the Bundle {0}: {1}",
                        baseClass.getName(), failures), failures.isEmpty());
    }

    @Test
    public void methodsWithoutCodes() {
        final Set<String> failures = new TreeSet<String>();

        final Method[] methods = baseClass.getDeclaredMethods();
        for (final Method method : methods) {
            final String name = method.getName();
            if (method.getAnnotation(DeclareServiceCode.class) == null) {
                failures.add(name);
            }
        }

        assertTrue(
                format("The following methods do not have service codes for interface {0}: {1}",
                        baseClass.getName(), failures), failures.isEmpty());
    }

    @Test
    public void methodsWithInvalidReturnType() {
        final Set<String> failures = new TreeSet<String>();

        final Method[] methods = baseClass.getDeclaredMethods();
        for (final Method method : methods) {
            final String name = method.getName();
            final Class<?> type = method.getReturnType();
            if (Modifier.isAbstract(type.getModifiers())
                    || !ServiceCoded.class.isAssignableFrom(type)
                    || !(Exception.class.isAssignableFrom(type) || ServiceError.class
                            .isAssignableFrom(type))) {
                failures.add(name);
            }
        }

        assertTrue(
                format("The following methods do not have valid return types for interface {0}: {1}",
                        baseClass.getName(), failures), failures.isEmpty());
    }
}
