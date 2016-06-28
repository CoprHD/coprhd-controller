/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.emc.sa.machinetags.vmware.DatastoreMachineTag;
import com.emc.sa.machinetags.vmware.VMwareDatastoreTagger;
import com.emc.sa.service.linux.file.MountInfo;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
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

    public static String generateMountTag(URI hostId, String destinationPath, String subDirectory, String securityType) {
        return "mountNfs;" + hostId.toString() + ";" + destinationPath + ";" + subDirectory + ";" + securityType;
    }

    public static void setFileSystemTag(ViPRCoreClient client, URI fsId, String tag) {
        Set<String> tags = new HashSet<String>();
        tags.add(tag);
        client.fileSystems().addTags(fsId, tags);
    }

    public static void removeFileSystemTag(ViPRCoreClient client, URI fsId, String tag) {
        Set<String> removeTags = new HashSet<String>();
        removeTags.add(tag);
        client.fileSystems().removeTags(fsId, removeTags);
    }

    public static Set<String> getFileSystemTags(ViPRCoreClient client, URI fsId) {
        return client.fileSystems().getTags(fsId);
    }

    public static List<MountInfo> convertNFSTagsToMounts(List<String> mountTags) {
        List<MountInfo> mountList = new ArrayList<MountInfo>();
        for (String tag : mountTags) {
            mountList.add(convertNFSTag(tag));
        }
        return mountList;
    }

    public static Map<String, MountInfo> getNFSMountInfoFromTags(ViPRCoreClient client) {
        List<URI> fsIds = client.fileSystems().listBulkIds();
        Map<String, MountInfo> results = new HashMap<String, MountInfo>();
        for (URI fsId : fsIds) {
            List<String> mountTags = new ArrayList<String>();
            mountTags.addAll(client.fileSystems().getTags(fsId));
            for (String tag : mountTags) {
                MountInfo mountInfo = convertNFSTag(tag);
                mountInfo.setFsId(fsId);
                results.put(tag, mountInfo);
            }
        }
        return results;
    }

    public static MountInfo convertNFSTag(String tag) {
        MountInfo mountInfo = new MountInfo();
        if (tag.startsWith("mountNfs")) {
            String[] pieces = StringUtils.trim(tag).split(";");
            if (pieces.length > 1) {
                mountInfo.setHostId(ViPRExecutionUtils.uri(pieces[1]));
            }
            if (pieces.length > 2) {
                mountInfo.setMountPoint(pieces[2]);
            }
            if (pieces.length > 3) {
                mountInfo.setSubDirectory(pieces[3]);
            }
            if (pieces.length > 4) {
                mountInfo.setSecurityType(pieces[4]);
            }
            if (pieces.length > 5) {
                mountInfo.setFsId(ViPRExecutionUtils.uri(pieces[5]));
            }
            mountInfo.setTag(tag);
        }
        return mountInfo;
    }

}
