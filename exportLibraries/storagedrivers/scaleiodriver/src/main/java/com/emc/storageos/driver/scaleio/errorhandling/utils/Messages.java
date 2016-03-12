/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.utils;

import com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.text.MessageFormat;
import java.util.*;

import static com.emc.storageos.driver.scaleio.errorhandling.utils.MessageUtils.bundleForClass;
import static com.emc.storageos.driver.scaleio.errorhandling.utils.MessageUtils.bundleForName;
import static java.text.MessageFormat.format;

public class Messages {
    private static final Logger _logger = LoggerFactory.getLogger(Messages.class);

    private Messages() {
        // nothing to do
    }

    public static String localize(final Locale locale, final ServiceCode code) {
        try {
            final ResourceBundle bundle = bundleForClass(ServiceCode.class, locale);
            return bundle.getString(code.name());
        } catch (MissingResourceException e) {
            _logger.error("Unable to find resource for ServiceCode " + code.name(), e);
            return WordUtils.capitalize(code.name().replaceAll("_", " ").toLowerCase());
        }
    }

    public static String localize(final String bundle, final Locale locale, final String key,
            final Object... parameters) {
        final String pattern;
        if (bundle == null) {
            pattern = key;
        } else {
            pattern = getPattern(locale, key, bundle);
        }
        if (pattern == null) {
            return format("Untranslated message (locale={0}, bundle={1}, key={2}, arguments={3})",
                    locale, bundle, key, Arrays.toString(parameters));
        }

        final Object[] arguments = parametersToArguments(parameters);

        try {
            final MessageFormat format = new MessageFormat(pattern, locale);
            return format.format(arguments);
        } catch (final Exception e) {
            _logger.warn("Invalid patten defined in {} for {}", bundle, key);
            return format("{0} (locale={1},bundle={2},key={3},arguments={4})", pattern, locale,
                    bundle, key, Arrays.toString(parameters));
        }
    }

    private static Object[] parametersToArguments(final Object[] parameters) {
        if (parameters == null) {
            return null;
        }
        final Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = get(parameters[i]);
        }
        return arguments;
    }

    public static Object get(final Object param) {
        if (param == null) {
            // parameter is null so return null
            return null;
        }
        if (!isArray(param)) {
            // parameter is not an array so does not need to be converted
            return param;
        }

        // convert the array to a list
        final int length = Array.getLength(param);
        final List<Object> list = new ArrayList<Object>(length);
        for (int i = 0; i < length; i++) {
            final Object item = Array.get(param, i);
            // recursive call!
            list.add(get(item));
        }

        return list;
    }

    private static boolean isArray(final Object object) {
        return object.getClass().isArray();
    }

    public static String getPattern(final Locale locale, final String key, final String bundle) {
        if (key != null) {
            try {
                return bundleForName(bundle, locale).getString(key);
            } catch (final Exception e) {
                _logger.debug("Unable to find a message for {} in {}", key, bundle);
            }
        }
        return null;
    }
}
