/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags.vmware;

import static com.emc.sa.machinetags.vmware.DatastoreMachineTag.DATACENTER;
import static com.emc.sa.machinetags.vmware.DatastoreMachineTag.DATASTORE;
import static com.emc.sa.machinetags.vmware.DatastoreMachineTag.MOUNT_POINT;
import static com.emc.sa.machinetags.vmware.DatastoreMachineTag.NAMESPACE;
import static com.emc.sa.machinetags.vmware.DatastoreMachineTag.VCENTER;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.machinetags.MachineTag;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.machinetags.MachineTagsCollection;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class VMwareDatastoreTagger {

    private ViPRCoreClient client;

    public VMwareDatastoreTagger(ViPRCoreClient client) {
        this.client = client;
    }

    public List<DatastoreMachineTag> getDatastoreTags(URI projectId) {
        final List<DatastoreMachineTag> datastoreTags = Lists.newArrayList();
        for (FileShareRestRep filesystem : client.fileSystems().findByProject(projectId)) {
            datastoreTags.addAll(this.getDatastoreTagsOnFilesystem(filesystem));
        }
        return datastoreTags;
    }

    private List<DatastoreMachineTag> getDatastoreTagsOnFilesystem(FileShareRestRep filesystem) {
        final List<DatastoreMachineTag> returnTags = Lists.newArrayList();
        final MachineTagsCollection tags = getFileSystemTags(filesystem);
        final MachineTagsCollection datastoreTags = tags.findAll(NAMESPACE, DATASTORE);
        for (MachineTag datastore : datastoreTags) {
            final Integer index = datastore.index;
            final MachineTag mountPoint = tags.find(NAMESPACE, MOUNT_POINT, index);
            final MachineTag vcenter = tags.find(NAMESPACE, VCENTER, index);
            final MachineTag datacenter = tags.find(NAMESPACE, DATACENTER, index);
            final DatastoreMachineTag datastoreTag = new DatastoreMachineTag(datastore, vcenter, datacenter, mountPoint);
            returnTags.add(datastoreTag);
        }
        return returnTags;
    }

    public List<String> getDatastoreNames(URI projectId, URI datacenterId) {
        final List<String> datastoreNames = Lists.newArrayList();
        for (FileShareRestRep filesystem : client.fileSystems().findByProject(projectId)) {
            for (DatastoreMachineTag tag : getDatastoreTagsOnFilesystem(filesystem)) {
                if (StringUtils.equals(datacenterId.toString(), tag.getDatacenter())) {
                    datastoreNames.add(tag.getDatastore());
                }
            }
        }
        return datastoreNames;
    }

    public List<String> getDatastoreNames(URI projectId, URI vcenterId, URI datacenterId) {
        return getDatastoreNames(projectId, datacenterId);
    }

    public static String getDatastoreMountPoint(FileShareRestRep filesystem, URI vcenterId, URI datacenterId, String datastoreName) {
        final MachineTagsCollection tags = getFileSystemTags(filesystem);
        final MachineTag datastoreTag = getDatastoreTag(tags, vcenterId, datacenterId, datastoreName);
        final MachineTag mountPointTag = tags.find(NAMESPACE, MOUNT_POINT, datastoreTag.index);
        return mountPointTag.value;
    }

    public Integer getDatastoreIndex(URI filesystemId, URI vcenterId, URI datacenterId, String datastoreName) {
        final FileShareRestRep filesystem = client.fileSystems().get(filesystemId);
        return getDatastoreIndex(filesystem, vcenterId, datacenterId, datastoreName);
    }

    private static Integer getDatastoreIndex(FileShareRestRep filesystem, URI vcenterId, URI datacenterId, String datastoreName) {
        final MachineTag datastoreTag = getDatastoreTag(getFileSystemTags(filesystem), vcenterId, datacenterId, datastoreName);
        return datastoreTag != null ? datastoreTag.index : 0;
    }

    private static MachineTag getDatastoreTag(MachineTagsCollection tags, URI vcenterId, URI datacenterId, String datastoreName) {
        final MachineTagsCollection datastoreTags = tags.findAll(NAMESPACE, DATASTORE);
        for (MachineTag datastoreTag : datastoreTags) {
            final Integer index = datastoreTag.index;
            final MachineTag vcenterTag = tags.find(NAMESPACE, VCENTER, index);
            final MachineTag datacenterTag = tags.find(NAMESPACE, DATACENTER, index);
            final boolean isVcenter = StringUtils.equals(vcenterTag.value, vcenterId.toString());
            final boolean isDatacenter = StringUtils.equals(datacenterTag.value, datacenterId.toString());
            final boolean isDatastore = StringUtils.equals(datastoreTag.value, datastoreName);
            if (isVcenter && isDatacenter && isDatastore) {
                return datastoreTag;
            }
        }
        return null;
    }

    public Integer removeDatastoreTagsFromFilesystem(URI filesystemId, URI vcenterId, URI datacenterId, String datastoreName) {
        final Integer index = getDatastoreIndex(filesystemId, vcenterId, datacenterId, datastoreName);
        removeDatastoreTagsFromFilesystem(filesystemId, index);
        return index;
    }

    private void removeDatastoreTagsFromFilesystem(URI filesystemId, Integer index) {
        final FileShareRestRep filesystem = client.fileSystems().get(filesystemId);
        final MachineTagsCollection tags = getFileSystemTags(filesystem);
        final MachineTagsCollection removeTags = tags.findTags(NAMESPACE, index, DATASTORE, MOUNT_POINT, VCENTER, DATACENTER);
        client.fileSystems().removeTags(filesystemId, removeTags.generateRawTags());
    }

    public Integer addDatastoreTagsToFilesystem(URI filesystemId, URI vcenterId, URI datacenterId, String datastoreName,
            String nfsMountPoint, List<String> endpoints) {
        final FileShareRestRep filesystem = client.fileSystems().get(filesystemId);
        final MachineTagsCollection tags = getFileSystemTags(filesystem);
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            MachineTag datastoreTag = tags.find(NAMESPACE, DATASTORE, i);
            if (datastoreTag == null) {
                final Integer index = Integer.valueOf(i);
                final DatastoreMachineTag tag = new DatastoreMachineTag(index, vcenterId.toString(),
                        datacenterId.toString(), datastoreName, nfsMountPoint, endpoints);
                addDatastoreTagsToFilesystem(filesystemId, tag);
                return Integer.valueOf(i);
            }
        }
        throw new IllegalStateException(
                "Attempt to find a valid index to use for this datastore tag failed. All values from 1 up to Integer.MAX_VALUE are being used.");
    }

    public static Map<String, String> getDatastoreTags(BlockObjectRestRep blockObject) {
        Map<String, String> datastoreTags = Maps.newHashMap();
        Set<String> volumeTags = blockObject.getTags();
        Map<String, String> parsedTags = MachineTagUtils.parseMachineTags(volumeTags);

        for (String tag : parsedTags.keySet()) {
            String tagValue = parsedTags.get(tag);
            if (tag != null
                    && tag.startsWith(KnownMachineTags.getVmfsDatastoreTagName())) {
                datastoreTags.put(tag, tagValue);
            }
        }
        return datastoreTags;
    }

    public static Set<String> getDatastoreNames(BlockObjectRestRep blockObject) {
        return Sets.newHashSet(getDatastoreTags(blockObject).values());
    }

    private void addDatastoreTagsToFilesystem(URI filesystemId, DatastoreMachineTag tag) {
        client.fileSystems().addTags(filesystemId, tag.getTags().generateRawTags());
    }

    public static MachineTagsCollection getFileSystemTags(FileShareRestRep fileSystem) {
        MachineTagsCollection tags = new MachineTagsCollection();
        if (fileSystem != null && fileSystem.getTags() != null) {
            for (String tag : fileSystem.getTags()) {
                MachineTag machineTag = MachineTag.parse(tag);
                if (machineTag != null) {
                    tags.add(machineTag);
                }
            }
        }
        return tags;
    }
}
