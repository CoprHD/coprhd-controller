/*
/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.Task;
import com.google.common.collect.Maps;

/**
 * Utility class to retrieve tags from data objects.
 */
public class TagUtils {
    private TagUtils(){};
    
    private static String ISA_NAMESPACE = "vipr";

    private static String MOUNTPOINT = fqnName(ISA_NAMESPACE, "mountPoint");
    private static String VMFS_DATASTORE = fqnName(ISA_NAMESPACE, "vmfsDatastore");
    private static String BOOT_VOLUME = fqnName(ISA_NAMESPACE, "bootVolume");
    private static String ORDER_ID = fqnName(ISA_NAMESPACE, "orderId");
    private static String ORDER_NUMBER = fqnName(ISA_NAMESPACE, "orderNumber");

    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    private static String fqnName(String namespace, String name) {
        return namespace + ":" + name;
    }
    
    public static String getHostMountPointTagName(URI hostId) {
        return MOUNTPOINT + "-" + hostId;
    }

    public static String getVMFSDatastoreTagName(URI hostId) {
        return VMFS_DATASTORE + "-" + hostId;
    }

    public static String getBootVolumeTagName() {
        return BOOT_VOLUME;
    }

    public static String getOrderIdTagName() {
        return ORDER_ID;
    }

    public static String getOrderNumberTagName() {
        return ORDER_NUMBER;
    }

    /**
     * Quick method to see if a tag contains a label that denotes the tag as a mountpoint.
     * 
     * @param sl scoped label
     * @return true if this tag is a mountpoint/datastore/boot
     */
    public static boolean isMountContentTag(ScopedLabel sl) {
        if (sl.getLabel().startsWith(MOUNTPOINT) ||
                sl.getLabel().startsWith(VMFS_DATASTORE) ||
                sl.getLabel().startsWith(BOOT_VOLUME)) {
            return true;
        }
        return false;
    }
    
    private static Map<String, String> parseTags(Collection<String> tags) {
        Map<String, String> machineTags = Maps.newHashMap();
        for (String tag : tags) {
            Matcher matcher = MACHINE_TAG_REGEX.matcher(tag);
            if (matcher.matches()) {
                machineTags.put(matcher.group(1), matcher.group(2));
            }
        }
        return machineTags;
    }

    private static String getTagValue(DataObject dataObject, String tagName) {
        if (dataObject == null || (dataObject.getTag() == null)) {
            return null;
        }

        List<String> tags = new ArrayList<>();
        for (ScopedLabel sl : dataObject.getTag()) {
            tags.add("" + sl.getLabel());
        }
        Map<String, String> currentMachineTags = parseTags(tags);
        return currentMachineTags.get(tagName);
    }

    public static String getBlockVolumeMountPoint(BlockObject blockObject, URI hostId) {
        return getTagValue(blockObject, getHostMountPointTagName(hostId));
    }

    public static String getBlockVolumeVMFSDatastore(BlockObject blockObject, URI hostId) {
        return getTagValue(blockObject, getVMFSDatastoreTagName(hostId));
    }

    public static String getBlockVolumeBootVolume(BlockObject blockObject) {
        return getTagValue(blockObject, getBootVolumeTagName());
    }

    public static String getTaskOrderId(Task task) {
        return getTagValue(task, getOrderIdTagName());
    }

    public static String getTaskOrderNumber(Task task) {
        return getTagValue(task, getOrderNumberTagName());
    }

    public static String getOrderIdTagValue(DataObject dataObject) {
        return getTagValue(dataObject, getOrderIdTagName());
    }

    public static String getOrderNumberTagValue(DataObject dataObject) {
        return getTagValue(dataObject, getOrderNumberTagName());
    }
}
