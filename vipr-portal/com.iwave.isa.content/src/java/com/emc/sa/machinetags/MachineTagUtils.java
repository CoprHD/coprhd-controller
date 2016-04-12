/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.emc.sa.machinetags.vmware.DatastoreMachineTag;
import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.ProjectResources;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class MachineTagUtils {

    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger(MachineTagUtils.class);

    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    public static String getTaskTag(DataObjectRestRep dataObject, String tagName) {
        if (dataObject.getTags() == null) {
            return null;
        }

        Map<String, String> currentMachineTags = parseMachineTags(dataObject.getTags());
        return currentMachineTags.get(tagName);
    }

    public static String getTaskTag(ViPRCoreClient client, URI taskId, String tagName) {
        Set<String> currentTags = getTaskTags(client, taskId);
        Map<String, String> currentMachineTags = parseMachineTags(currentTags);
        return currentMachineTags.get(tagName);
    }

    public static void setTaskTag(ViPRCoreClient client, URI taskId, String tagName, String tagValue) {
        removeTaskTag(client, taskId, tagName);
        client.tasks().addTags(taskId, Sets.newHashSet(machineTag(tagName, tagValue)));
    }

    public static void setTaskOrderIdTag(ViPRCoreClient client, URI taskId, String tagValue) {
        setTaskTag(client, taskId, KnownMachineTags.getOrderIdTagName(), tagValue);
    }

    public static void setTaskOrderNumberTag(ViPRCoreClient client, URI taskId, String tagValue) {
        setTaskTag(client, taskId, KnownMachineTags.getOrderNumberTagName(), tagValue);
    }

    public static void removeTaskTag(ViPRCoreClient client, URI taskId, String tagName) {
        Set<String> currentTags = getTaskTags(client, taskId);
        Map<String, String> currentMachineTags = parseMachineTags(currentTags);

        if (currentMachineTags.containsKey(tagName)) {
            String currentTagValue = currentMachineTags.get(tagName);
            client.tasks().removeTags(taskId, Sets.newHashSet(machineTag(tagName, currentTagValue)));
        }
    }

    protected static Set<String> getTaskTags(ViPRCoreClient client, URI taskId) {
        return client.tasks().getTags(taskId);
    }

    public static String getBlockVolumeTag(BlockObjectRestRep blockObject, String tagName) {
        if (blockObject.getTags() == null) {
            return null;
        }

        Map<String, String> currentMachineTags = parseMachineTags(blockObject.getTags());

        return currentMachineTags.get(tagName);

    }

    /**
     * @return The current value of the Block Volume machine tag, or NULL if the
     *         tag doesn't exist
     */
    public static String getBlockVolumeTag(ViPRCoreClient client, URI volumeId, String tagName) {
        Set<String> currentTags = getBlockResourceTags(client, volumeId);
        Map<String, String> currentMachineTags = parseMachineTags(currentTags);

        return currentMachineTags.get(tagName);
    }

    /**
     * Sets the value of a Machine Tag on the Volume. If the machine tag already
     * exists, its value is replaced with @{link #tagValue}
     */
    public static void setBlockVolumeTag(ViPRCoreClient client, URI volumeId, String tagName, String tagValue) {
        removeBlockVolumeTag(client, volumeId, tagName);
        getClientResource(client, volumeId).addTags(volumeId, Sets.newHashSet(machineTag(tagName, tagValue)));
    }

    protected static ProjectResources<? extends BlockObjectRestRep> getClientResource(ViPRCoreClient client,
            URI volumeId) {
        ProjectResources<? extends BlockObjectRestRep> resource = null;
        if (ResourceType.fromResourceId(volumeId.toString()).equals(ResourceType.VOLUME)) {
            resource = client.blockVolumes();
        } else if (ResourceType.fromResourceId(volumeId.toString()).equals(ResourceType.BLOCK_SNAPSHOT)) {
            resource = client.blockSnapshots();
        } else {
            throw new IllegalStateException("Unable to find block resource with id " + volumeId);
        }
        return resource;
    }

    /** Removes the Machine Tag with the specified name from the block volume */
    public static void removeBlockVolumeTag(ViPRCoreClient client, URI volumeId, String tagName) {
        Set<String> currentTags = getBlockResourceTags(client, volumeId);
        Map<String, String> currentMachineTags = parseMachineTags(currentTags);

        if (currentMachineTags.containsKey(tagName)) {
            String currentTagValue = currentMachineTags.get(tagName);
            getClientResource(client, volumeId).removeTags(volumeId,
                    Sets.newHashSet(machineTag(tagName, currentTagValue)));
        }
    }

    protected static Set<String> getBlockResourceTags(ViPRCoreClient client, URI resourceId) {
        return getClientResource(client, resourceId).getTags(resourceId);
    }

    public static Map<String, String> getDatastoresOnFilesystem(FileShareRestRep fileSystem) {
        return getDatastoresOnFilesystemsOld(Collections.singleton(fileSystem));
    }

    public static Map<String, String> getDatastoresOnFilesystemsOld(Collection<FileShareRestRep> filesystems) {
        Map<String, String> datastoreTags = Maps.newHashMap();
        for (FileShareRestRep filesystem : filesystems) {
            MachineTagsCollection tags = VMwareDatastoreTagger.getFileSystemTags(filesystem);
            for (MachineTag tag : tags.findAll(DatastoreMachineTag.NAMESPACE, DatastoreMachineTag.DATASTORE)) {
                datastoreTags.put(tag.getUniqueKey(), tag.value);
            }
        }
        return datastoreTags;
    }

    public static boolean hasDatastores(FileShareRestRep filesystem) {
        MachineTagsCollection tags = VMwareDatastoreTagger.getFileSystemTags(filesystem);
        MachineTagsCollection datastoreTags = tags
                .findAll(DatastoreMachineTag.NAMESPACE, DatastoreMachineTag.DATASTORE);
        return datastoreTags.size() > 0;
    }

    public static List<String> getDatastores(ViPRCoreClient client, URI projectId, URI vcenterId, URI datacenterId) {
        final VMwareDatastoreTagger tagger = new VMwareDatastoreTagger(client);
        return tagger.getDatastoreNames(projectId, vcenterId, datacenterId);
    }

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

    private static String machineTag(String name, String value) {
        return name + "=" + value;
    }

}
