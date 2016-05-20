/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Option support for enumerations.
 * 
 * @author jonnymiller
 */
public class EnumOption implements Comparable<EnumOption> {
    public String id;
    public String name;

    public EnumOption(Enum value) {
        this(value, null);
    }

    /**
     * Decorate an enum to provide i18n display string. By default, the resource key is the enum class name if
     * namePrefix is not provided.
     * 
     * @param value
     * @param namePrefix
     */
    public EnumOption(Enum value, String namePrefix) {
        this.id = value.name();
        name = getDisplayValue(id, StringUtils.defaultString(namePrefix, value.getClass().getSimpleName()));
    }

//    public EnumOption(String value, String namePrefix) {
//        id = value;
//        name = getDisplayValue(value, StringUtils.defaultString(namePrefix, value.getClass().getSimpleName()));
//    }

    public EnumOption(String value, String port) {
        id = value;
        name = port;
    }
    
    @Override
    public int compareTo(EnumOption o) {
        return name.compareTo(o.name);
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
        if (!(obj instanceof EnumOption)) {
            return false;
        }
        EnumOption other = (EnumOption) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(id, other.id);
        return builder.isEquals();
    }

    public static EnumOption[] options(Enum[] values) {
        return options(values, null);
    }

    public static EnumOption[] options(Enum[] values, boolean sorted) {
        return options(values, null, sorted);
    }

    public static EnumOption[] options(Enum[] values, String namePrefix) {
        EnumOption[] options = new EnumOption[values.length];
        for (int i = 0; i < values.length; i++) {
            options[i] = new EnumOption(values[i], namePrefix);
        }

        Collections.sort(Arrays.asList(options));
        return options;
    }

    public static EnumOption[] options(Enum[] values, String namePrefix, boolean sorted) {
        EnumOption[] options = new EnumOption[values.length];
        for (int i = 0; i < values.length; i++) {
            options[i] = new EnumOption(values[i], namePrefix);
        }
        if (sorted) {
            Collections.sort(Arrays.asList(options));
        }
        return options;
    }

    public static EnumOption[] options(String[] values, String namePrefix, boolean sorted) {
        EnumOption[] options = new EnumOption[values.length];
        for (int i = 0; i < values.length; i++) {
            options[i] = new EnumOption(values[i], namePrefix);
        }
        if (sorted) {
            Collections.sort(Arrays.asList(options));
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
    private static String getDisplayValue(String value, String namePrefix) {
        String key = namePrefix + "." + value;
        String message = MessagesUtils.get(key);
        if (key.equals(message)) {
            message = value;
        }
        return message;
    }
}
