/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public enum ResourceType {
    VOLUME("Volume"),
    EXPORT_GROUP("ExportGroup"),
    FILE_SHARE("FileShare"),
    FILE_SNAPSHOT("Snapshot"),
    QUOTA_DIRECTORY("QuotaDirectory"),
    BLOCK_SNAPSHOT("BlockSnapshot"),
    BLOCK_SNAPSHOT_SESSION("BlockSnapshotSession"),
    BLOCK_CONTINUOUS_COPY("BlockMirror"),
    VPLEX_CONTINUOUS_COPY("VplexMirror"),
    PROJECT("Project"),

    HOST("Host"),
    CLUSTER("Cluster"),
    VCENTER("Vcenter"),
    VCENTER_DATA_CENTER("VcenterDataCenter"),

    VIRTUAL_ARRAY("VirtualArray"),
    VIRTUAL_POOL("VirtualPool"),
    CONSISTENCY_GROUP("BlockConsistencyGroup"),
    SMIS_PROVIDER("SMISProvider"),
    STORAGE_PROVIDER("StorageProvider"),
    STORAGE_POOL("StoragePool"),

    STORAGE_SYSTEM("StorageSystem"),
    NETWORK_SYSTEM("NetworkSystem"),
    PROTECTION_SYSTEM("ProtectionSystem"),
    COMPUTE_SYSTEM("ComputeSystem"),

    UNMANAGED_VOLUME("UnManagedVolume"),
    UNMANAGED_FILESYSTEM("UnManagedFileSystem"),
    UNMANAGED_EXPORTMASK("UnManagedExportMask"),

    BUCKET("Bucket"),
    
    UNKNOWN("Unknown");

    private static final Pattern RESOURCE_ID = Pattern.compile("urn\\:storageos\\:([^\\:]+)");
    private String label;

    ResourceType(String label) {
        this.label = label;
    }

    public static boolean isResourceId(String resourceId) {
        Matcher m = RESOURCE_ID.matcher(resourceId);
        return m.find();
    }

    public static ResourceType fromResourceId(URI resourceId) {
        if (resourceId != null) {
            return fromResourceId(resourceId.toString());
        }
        return UNKNOWN;
    }

    public static ResourceType fromResourceId(String resourceId) {
        Matcher m = RESOURCE_ID.matcher(resourceId);
        String label = m.find() ? m.group(1) : null;

        if (StringUtils.isBlank(label)) {
            return UNKNOWN;
        }

        for (ResourceType resourceType : values()) {
            if (resourceType.label.equals(label)) {
                return resourceType;
            }
        }

        return UNKNOWN;
    }

    public static boolean isType(ResourceType resourceType, String resourceId) {
        if (resourceType != null && StringUtils.isNotBlank(resourceId)) {
            return resourceType.equals(ResourceType.fromResourceId(resourceId));
        }
        return false;
    }

    public static boolean isType(ResourceType resourceType, URI resourceId) {
        if (resourceId != null) {
            return isType(resourceType, resourceId.toString());
        }
        return false;
    }
}