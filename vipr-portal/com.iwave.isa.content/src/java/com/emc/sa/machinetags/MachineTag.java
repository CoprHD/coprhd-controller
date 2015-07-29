/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * representation of a machine tag
 * 
 * A machine tag has one of the following forms:
 * 
 * namespace:key=value
 * namespace:key.index=value
 */
public class MachineTag {

    private static final Pattern MACHINE_TAG_REGEX = Pattern.compile("([\\w\\.]*):(\\w*)(?:\\.(\\d))?=(.*)");
    // ([\w\.]*):(\w*)(?:\.(\d))?=(\w*)

    public final String namespace;
    public final String key;
    public final Integer index;
    public final String value;

    public MachineTag(String namespace, String key, Integer index, String value) {
        this.namespace = namespace;
        this.key = key;
        this.index = index;
        this.value = value;
    }

    public MachineTag(String namespace, String key, String value) {
        this(namespace, key, null, value);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getUniqueKey());
        sb.append("=").append(value);
        return sb.toString();
    }

    /** returns the full key for this tag including namespace, key, and index if applicable. */
    public String getUniqueKey() {
        final StringBuilder sb = new StringBuilder();
        sb.append(namespace).append(":").append(key);
        if (index != null) {
            sb.append(".").append(index);
        }
        return sb.toString();
    }

    public static MachineTag parse(String tag) {
        final Matcher matcher = MACHINE_TAG_REGEX.matcher(tag);
        if (matcher.matches()) {
            final String namespace = matcher.group(1);
            final String key = matcher.group(2);
            Integer index = null;
            if (matcher.group(3) != null) {
                index = Integer.valueOf(matcher.group(3));
            }
            final String value = matcher.group(4);
            return new MachineTag(namespace, key, index, value);
        }
        return null;
    }

    public boolean isTag(String namespace, String key, Integer index) {
        final boolean namespaces = this.namespace.equals(namespace);
        final boolean keys = this.key.equals(key);
        final boolean indexesBothNull = this.index == null && index == null;
        final boolean indexesNotNullButMatch = this.index != null && index != null && this.index.equals(index);
        final boolean indexes = indexesBothNull || indexesNotNullButMatch;
        return namespaces && keys && indexes;
    }

    public boolean isTag(String namespace, String key) {
        return isTag(namespace, key, null);
    }

}
