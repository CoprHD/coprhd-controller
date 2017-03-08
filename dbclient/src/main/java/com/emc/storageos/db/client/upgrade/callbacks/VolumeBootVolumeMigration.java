/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.google.common.collect.Maps;

/**
 * Migration handler to tag compute boot volumes from previous releases.
 * 
 */
public class VolumeBootVolumeMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VolumeBootVolumeMigration.class);

    private static String ISA_NAMESPACE = "vipr";
    private static String BOOT_VOLUME = fqnName(ISA_NAMESPACE, "bootVolume");
    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    private static String fqnName(String namespace, String name) {
        return namespace + ":" + name;
    }
    
    public static String getBootVolumeTagName() {
        return BOOT_VOLUME;
    }
    
    /**
     * Return true of false if a given volume is a boot volume for an OS.
     *
     * @param blockObject to validate
     * @return true or false if the volume is a boot volume
     */
    private boolean isVolumeBootVolume(Volume volume) {
        if (volume != null) {
            Map<String, String> parsedTags = parseMachineTags(volume.getTag());

            for (String tag : parsedTags.keySet()) {
                if (tag != null && tag.startsWith(getBootVolumeTagName())) {
                    return true;
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
    private Map<String, String> parseMachineTags(ScopedLabelSet tagSet) {
        Map<String, String> machineTags = Maps.newHashMap();
        if (tagSet != null) {
            for (ScopedLabel tag : tagSet) {
                Matcher matcher = MACHINE_TAG_REGEX.matcher(tag.getScope());
                if (matcher.matches()) {
                    machineTags.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return machineTags;
    }

    @Override
    public void process() throws MigrationCallbackException {
        tagBootVolumes();
    }

    /**
     * For all volumes, fill in the boot volume tag, if certain loose criteria is met
     */
    private void tagBootVolumes() {
        log.info("Updating volumes to contain boot tag.");
        DbClient dbClient = this.getDbClient();
        List<URI> hostURIs = dbClient.queryByType(Host.class, false);

        Iterator<Host> hosts = 
                dbClient.queryIterativeObjects(Host.class, hostURIs);
        
        while (hosts.hasNext()) {
            Host host = hosts.next();

            log.info("Examining Host (id={}) for boot volume upgrade", host.getId().toString());

            // If there's no boot volume, there's nothing to upgrade.
            if (NullColumnValueGetter.isNullURI(host.getBootVolumeId())) {
                continue;
            }
            
            Volume volume = dbClient.queryObject(Volume.class, host.getBootVolumeId());
            
            // if it's not in the DB, set it back to "null" on the host
            if (volume == null) {
                host.setBootVolumeId(NullColumnValueGetter.getNullURI());
                dbClient.updateObject(host);
                continue;
            }
            
            // If it's already a boot volume, move on
            if (isVolumeBootVolume(volume)) {
                continue;
            }
            
            // Add the tag.
            ScopedLabelSet tagSet = volume.getTag();
            if (tagSet == null) {
                tagSet = new ScopedLabelSet();
                volume.setTag(tagSet);
            }

            // Drop the new tag in the tag list
            ScopedLabel tagLabel = new ScopedLabel(volume.getTenant().getURI().toString(), getBootVolumeTagName() + "=" + host.getId());
            tagSet.add(tagLabel);
            
            // Update the volume object
            dbClient.updateObject(volume);
        }
    }
}
