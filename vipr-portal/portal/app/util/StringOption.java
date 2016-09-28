/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.collect.Lists;

/**
 * Option support for strings.
 * 
 * @author jonnymiller
 */
public class StringOption implements Comparable<StringOption> {
    public static final StringOption NONE_OPTION;

    static {
        NONE_OPTION = new StringOption("NONE", "none");
    }

    public String id;
    public String name;

    public StringOption(String value) {
        this(value, value);
    }

    public StringOption(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public int compareTo(StringOption o) {
        return name.compareToIgnoreCase(o.name);
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(id);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StringOption)) {
            return false;
        }
        StringOption other = (StringOption) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(id, other.id);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        return id + "=" + name;
    }

    public static List<StringOption> options(Collection<?> values) {
        return options(values, true);
    }

    public static List<StringOption> options(Collection<?> values, boolean sorted) {
        List<StringOption> options = Lists.newArrayList();
        for (Object value : values) {
            options.add(new StringOption(value.toString()));
        }
        if (sorted) {
            Collections.sort(options);
        }
        return options;
    }

    public static StringOption[] options(String[] values) {
        return options(values, true);
    }

    public static StringOption[] options(String[] values, boolean sorted) {
        return options(values, "", sorted);
    }

    public static StringOption[] options(String[] values, String prefix) {
        return options(values, prefix, true);
    }

    public static StringOption[] options(String[] values, String prefix, boolean sorted) {
        StringOption[] options = new StringOption[values.length];
        for (int i = 0; i < values.length; i++) {
            options[i] = new StringOption(values[i], getDisplayValue(values[i], prefix));
        }
        if (sorted) {
            Arrays.sort(options);
        }
        return options;
    }

    /**
     * Get given enum's i18n display-able string.
     * 
     * @param value
     *            the enumeration value
     * @param namePrefix
     *            the prefix of the message key.
     * @return the display value.
     */
    public static String getDisplayValue(String value, String namePrefix) {
        String key;
        if (StringUtils.isNotBlank(namePrefix)) {
            key = namePrefix + "." + value;
        }
        else {
            key = value;
        }
        String message = MessagesUtils.get(key);
        if (key.equals(message)) {
            message = value;
        }
        return message;
    }
}
