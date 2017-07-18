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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.google.common.collect.Maps;

/**
 * Utility class to retrieve tags from data objects.
 */
public class TagUtils {
    private TagUtils(){};

    private static final Logger _log = LoggerFactory.getLogger(TagUtils.class);
    private static String ISA_NAMESPACE = "vipr";

    private static String MOUNTPOINT = fqnName(ISA_NAMESPACE, "mountPoint");
    private static String VMFS_DATASTORE = fqnName(ISA_NAMESPACE, "vmfsDatastore");
    private static String BOOT_VOLUME = fqnName(ISA_NAMESPACE, "bootVolume");
    private static String ORDER_ID = fqnName(ISA_NAMESPACE, "orderId");
    private static String ORDER_NUMBER = fqnName(ISA_NAMESPACE, "orderNumber");

    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    public static String SITE = "site";
    public static String SEPARATOR = "=";

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

    /**
     * Returns the site that a DataObject should be a part of.
     * This is currently stored as a tag of form Site=siteName
     * 
     * @param dataObject -- DataObject to check
     * @return - site name String, or null if not found
     */
    public static String getSiteName(DataObject dataObject) {
        ScopedLabelSet tags = dataObject.getTag();
        if (tags != null) {
            String labelPrefix = SITE + SEPARATOR;
            for (ScopedLabel tag : tags) {
                String label = tag.getLabel();
                if (NullColumnValueGetter.isNotNullValue(label) && label.startsWith(labelPrefix)) {
                    String site = label.replaceAll(labelPrefix, "");
                    return site;
                }
            }
        }
        return null;
    }
    
    public static void setSiteName(DataObject dataObject, String siteName) {
        _log.info("****** dataObject is " + dataObject);
        _log.info("****** siteName is " + siteName);

        boolean siteNameIsNull = NullColumnValueGetter.isNullValue(siteName);
        _log.info("****** siteNameIsNull is " + siteNameIsNull);

        ScopedLabelSet tags = dataObject.getTag();
        if (tags == null && siteNameIsNull) {
            // no need to update anything if siteName is null and no existing tags set
            return;
        }

        // find the existing Site tag, if set
        String labelPrefix = SITE + SEPARATOR;
        ScopedLabel siteTag = null;
        if (tags != null) {
            for (ScopedLabel tag : tags) {
                String label = tag.getLabel();
                if (NullColumnValueGetter.isNotNullValue(label) && label.startsWith(labelPrefix)) {
                    siteTag = tag;
                }
                String oldSiteName = label.replaceAll(labelPrefix, "");
                if (!siteNameIsNull && siteName.equals(oldSiteName)) {
                    // no need to update anything because the new name is the same as the old
                    return;
                }
            }
        }

        // if any existing site tag was found, remove it because we're going to set a new one.
        if (siteTag != null) {
            tags.remove(siteTag); // if the siteTag was found, tags cannot be null
            _log.info("****** removed existing site tag " + siteTag);
            dataObject.markChangedValue("tags");
            if (siteNameIsNull) {
                // if the siteName is null, then the user is intending to clear out the site tag, altogether 
                return;
            }
        }

        // if the siteName is not null, then create a new siteTag and set it in the tags.
        if (!siteNameIsNull) {
            if (tags == null) {
                tags = new ScopedLabelSet();
                dataObject.setTag(tags);
            }
            _log.info("****** create new siteTag {} " + siteTag, siteName);
            ScopedLabel newSiteTag = new ScopedLabel();
            newSiteTag.setLabel(labelPrefix + siteName);
            tags.add(newSiteTag);
            dataObject.markChangedValue("tags");
            _log.info("****** newSiteTag is " + newSiteTag);
        }
    }
}
