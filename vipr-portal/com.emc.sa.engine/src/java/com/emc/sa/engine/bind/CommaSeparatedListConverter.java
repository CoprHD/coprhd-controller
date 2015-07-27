/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.bind;

import java.util.List;

import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.util.TextUtils;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Converter that convers a command separate list of strings into a
 * List<String>.
 *
 * String "a, b, c" -> List [a, b, c]
 *
 * @author Chris Dail
 */
public class CommaSeparatedListConverter implements Converter {

    @SuppressWarnings({ "rawtypes" })
    @Override
    public Object convert(Class type, Object value) {
        final String string = value.toString();
        final List<String> elements = TextUtils.parseCSV(string);
        return Lists.transform(elements, new StringTrimmerFunction());
    }

    static class StringTrimmerFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return StringUtils.trimToEmpty(input);
        }
    }
}
