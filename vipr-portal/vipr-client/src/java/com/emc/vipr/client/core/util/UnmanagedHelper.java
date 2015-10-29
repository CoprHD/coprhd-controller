/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.util;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.adapters.StringSetMapAdapter.Entry;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;

/**
 * Unmanaged volumes API is pretty poor. Need utilities to process key/value pairs as there is no concrete
 * fields. Hope they don't change the field names.
 */
public class UnmanagedHelper {
    public static final String IS_INGESTABLE = "IS_INGESTABLE";
    public static final String SUPPORTED_VPOOL_LIST = "SUPPORTED_VPOOL_LIST";
    public static final String NATIVE_ID = "NATIVE_ID";
    public static final String PROVISIONED_CAPACITY = "PROVISIONED_CAPACITY";
    public static final String IS_SNAP_SHOT = "IS_SNAP_SHOT";
    public static final String DEVICE_LABEL = "DEVICE_LABEL";
    public static final String IS_FULL_COPY = "IS_FULL_COPY";
    public static final String IS_LOCAL_MIRROR = "IS_LOCAL_MIRROR";
    public static final String IS_VOLUME_EXPORTED = "IS_VOLUME_EXPORTED";

    public static Set<URI> getVpoolsForUnmanaged(List<StringHashMapEntry> characteristicsEntries,
            List<String> supportedVPoolUris) {
        Set<URI> results = new HashSet<URI>();

        // Only return vpools which this can import if this is supported for ingestion
        if (!isSupportedForIngest(characteristicsEntries)) {
            return results;
        }

        if (null != supportedVPoolUris) {
            for (String vpoolUriStr : supportedVPoolUris) {
                results.add(URI.create(vpoolUriStr));
            }
        }
        return results;
    }

    public static boolean isSupportedForIngest(List<StringHashMapEntry> entries) {
        boolean isIngestable = getValue(entries, IS_INGESTABLE, true);
        return isIngestable;
    }

    public static boolean isVolumeExported(List<StringHashMapEntry> characteristicsEntries) {
        return getValue(characteristicsEntries, IS_VOLUME_EXPORTED, true);
    }

    public static boolean isMirror(List<StringHashMapEntry> characteristicsEntries) {
        return getValue(characteristicsEntries, IS_LOCAL_MIRROR, true);
    }

    public static boolean isSnapShot(List<StringHashMapEntry> characteristicsEntries) {
        return getValue(characteristicsEntries, IS_SNAP_SHOT, true);
    }

    public static boolean isClone(List<StringHashMapEntry> characteristicsEntries) {
        return getValue(characteristicsEntries, IS_FULL_COPY, true);
    }

    public static String getLabel(UnManagedVolumeRestRep volume) {
        String label = getInfoField(volume, DEVICE_LABEL);
        if (label == null || "".equals(label)) {
            label = volume.getName();
        }
        return label;
    }

    private static String getValue(List<StringHashMapEntry> entries, String key) {
        for (StringHashMapEntry entry : entries) {
            if ((key != null && key.length() > 0) && key.equals(entry.getName())) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static boolean getValue(List<StringHashMapEntry> entries, String key, boolean defaultValue) {
        String value = getValue(entries, key);
        try {
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            // nothing to do here. We'll just return 0;
        }
        return defaultValue;
    }

    public static String getInfoField(UnManagedVolumeRestRep volume, String key) {
        if (key == null || key.equals("")) {
            return "";
        }

        for (Entry entry : volume.getVolumeInformation()) {
            if (key.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "";
    }

    public static long getInfoField(UnManagedVolumeRestRep volume, String key, long defaultValue) {
        String value = getInfoField(volume, key);
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            // nothing to do here. We'll just return the default
        }
        return defaultValue;
    }
}
