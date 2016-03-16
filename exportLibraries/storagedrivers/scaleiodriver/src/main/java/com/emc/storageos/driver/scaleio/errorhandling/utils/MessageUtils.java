/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.utils;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;

import javax.lang.model.element.Element;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.apache.commons.lang.StringUtils.isBlank;

public class MessageUtils {
    private MessageUtils() {
    }

    public static ResourceBundle bundleForClass(final Class<?> clazz) throws IllegalStateException {
        return bundleForClass(clazz, Locale.ENGLISH);
    }

    public static ResourceBundle bundleForClass(final Class<?> clazz, final Locale locale)
            throws IllegalStateException, MissingResourceException {
        final String detailBase = bundleNameForClass(clazz);

        return bundleForName(detailBase, locale);
    }

    public static ResourceBundle bundleForName(final String name) throws MissingResourceException {
        return bundleForName(name, Locale.ENGLISH);
    }

    public static ResourceBundle bundleForName(final String name, final Locale locale)
            throws MissingResourceException {
        return ResourceBundle.getBundle(name, locale);
    }

    public static String bundleNameForClass(final Class<?> clazz) throws IllegalStateException {
        final MessageBundle bundle = clazz.getAnnotation(MessageBundle.class);

        if (bundle == null) {
            throw new IllegalStateException("The class must be annotated with @MessageBundle");
        }

        final String detailBase = isBlank(bundle.value()) ? clazz.getCanonicalName() : bundle
                .value();
        return detailBase;
    }

    public static String bundleNameForElement(final Element element) throws IllegalStateException {
        final MessageBundle bundle = element.getAnnotation(MessageBundle.class);

        if (bundle == null) {
            throw new IllegalStateException("The class must be annotated with @MessageBundle");
        }
        final String detailBase = isBlank(bundle.value()) ? element.asType().toString() : bundle
                .value();
        return detailBase;
    }

    public static ResourceBundle bundleForElement(final Element element)
            throws IllegalStateException {
        return bundleForElement(element, Locale.ENGLISH);
    }

    public static ResourceBundle bundleForElement(final Element element, final Locale locale)
            throws IllegalStateException, MissingResourceException {
        final String detailBase = bundleNameForElement(element);
        return bundleForName(detailBase, locale);
    }
}
