/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

/**
 * Completer for migration commit operation.
 */
public class MigrationCommitTaskCompleter extends MigrationOperationTaskCompleter {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MigrationCommitTaskCompleter.class);
    private static String ISA_NAMESPACE = "vipr";
    private static String VMFS_DATASTORE = fqnName(ISA_NAMESPACE, "vmfsDatastore");
    private static String MOUNTPOINT = fqnName(ISA_NAMESPACE, "mountPoint");
    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");
    private URI sourceSystemURI;
    private List<String> deviceIds;

    public MigrationCommitTaskCompleter(URI cgURI, URI migrationURI, URI sourceSystemURI, String opId) {
        super(cgURI, migrationURI, opId);
        this.sourceSystemURI = sourceSystemURI;
    }

    public void setVolumeIds(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    private static String fqnName(String namespace, String name) {
        return namespace + ":" + name;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            // Remove the vmware datastore names and mountpoints tags for the volumes involved in migration
            logger.info("Processing the tag names from volumes involved in migration. Volumes: {}", deviceIds);
            if (deviceIds != null) {
                StringSet dataStoresAndMountPointsAffected = new StringSet();
                Migration migration = dbClient.queryObject(Migration.class, migrationURI);
                StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
                for (String deviceId : deviceIds) {
                    String volumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                            sourceSystem.getNativeGuid(), deviceId);
                    Volume volume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, volumeNativeGuid);
                    if (volume != null && volume.getTag() != null) {
                        Iterator<ScopedLabel> tagIter = volume.getTag().iterator();
                        ScopedLabelSet newTags = new ScopedLabelSet(volume.getTag());
                        boolean isAffected = false;
                        while (tagIter.hasNext()) {
                            ScopedLabel sl = tagIter.next();
                            if (sl.getLabel().startsWith(VMFS_DATASTORE) || sl.getLabel().startsWith(MOUNTPOINT)) {
                                logger.info("tag affected: {}", sl.getLabel());
                                Matcher matcher = MACHINE_TAG_REGEX.matcher(sl.getLabel());
                                if (matcher.matches()) {
                                    String tagValue = matcher.group(2);
                                    String name = String.format("%s (%s)", tagValue, volume.getNativeId());
                                    dataStoresAndMountPointsAffected.add(name);
                                }
                                newTags.remove(sl);
                                isAffected = true;
                            }
                        }
                        if (isAffected) {
                            volume.setTag(newTags);
                            dbClient.updateObject(volume);
                        }
                    }
                }
                // Update the datastores and mountpoints affected
                if (!dataStoresAndMountPointsAffected.isEmpty()) {
                    logger.info("Data stores and mount points affected: {}", dataStoresAndMountPointsAffected);
                    migration.setDataStoresAffected(dataStoresAndMountPointsAffected);
                    dbClient.updateObject(migration);
                }
            }
        } catch (Exception ex) {
            logger.warn("Problem while updating tags for volumes.", ex);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}
