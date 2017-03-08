/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.collect.Maps;

/**
 * Filters volumes to return all volumes that are boot volumes.
 */

/**
 * Boot volume filter for block volumes.
 * Much of the content comes from BlockProvider, BlockStorageUtils.java.
 */
public class BlockVolumeBootVolumeFilter extends DefaultResourceFilter<VolumeRestRep> {
    private static String ISA_NAMESPACE = "vipr";
    private static String BOOT_VOLUME = fqnName(ISA_NAMESPACE, "bootVolume");
    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    private static String fqnName(String namespace, String name) {
        return namespace + ":" + name;
    }
    
    public static String getBootVolumeTagName() {
        return BOOT_VOLUME;
    }
    
    @Override
    public boolean accept(VolumeRestRep volume) {
        return isVolumeBootVolume(volume);
    }

    /**
     * Return true of false if a given volume is a boot volume for an OS.
     *
     * @param blockObject to validate
     * @return true or false if the volume is a boot volume
     */
    public static boolean isVolumeBootVolume(BlockObjectRestRep blockObject) {
        if (blockObject != null) {
            Set<String> volumeTags = blockObject.getTags();
            if (volumeTags != null) {
                Map<String, String> parsedTags = parseMachineTags(volumeTags);

                for (String tag : parsedTags.keySet()) {
                    if (tag != null && tag.startsWith(getBootVolumeTagName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Parse machine tags
     * 
     * @param tags tags to decipher
     * @return a map of tags to values
     */
    public static Map<String, String> parseMachineTags(Collection<String> tags) {
        Map<String, String> machineTags = Maps.newHashMap();
        for (String tag : tags) {
            Matcher matcher = MACHINE_TAG_REGEX.matcher(tag);
            if (matcher.matches()) {
                machineTags.put(matcher.group(1), matcher.group(2));
            }
        }

        return machineTags;
    }
}