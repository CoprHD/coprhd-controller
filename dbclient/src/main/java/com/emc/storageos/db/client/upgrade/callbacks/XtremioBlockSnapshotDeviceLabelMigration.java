/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to set the deviceLabel of XtremIO snapshots same as their labels.
 *
 */

public class XtremioBlockSnapshotDeviceLabelMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(XtremioBlockSnapshotDeviceLabelMigration.class);

    /**
     * Get all the BlockSnapshots associated with XtremIO storage systems and if the deviceLabel is not set, set it to the label of the
     * snapshots.
     *
     */
    @Override
    public void process() throws MigrationCallbackException {
        log.info("Started execution of XtremioBlockSnapshotDeviceLabelMigration migration callback.");
        try {
            DbClient dbClient = getDbClient();
            List<URI> storageSystemURIList = dbClient.queryByType(StorageSystem.class, true);
            Iterator<StorageSystem> storageSystems = dbClient.queryIterativeObjects(StorageSystem.class, storageSystemURIList);
            while (storageSystems.hasNext()) {
                StorageSystem storageSystem = storageSystems.next();
                if (DiscoveredDataObject.Type.xtremio.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                    URIQueryResultList snapshotURIs = new URIQueryResultList();
                    dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceSnapshotConstraint(storageSystem.getId()),
                            snapshotURIs);
                    Iterator<BlockSnapshot> xioSnapshots = dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
                    List<BlockSnapshot> updatedSnaps = new ArrayList<BlockSnapshot>();
                    while (xioSnapshots.hasNext()) {
                        BlockSnapshot xioSnapshot = xioSnapshots.next();
                        if (!xioSnapshot.getInactive() && NullColumnValueGetter.isNullValue(xioSnapshot.getDeviceLabel())) {
                            String label = xioSnapshot.getLabel();
                            log.info(String.format("Setting deviceLabel of snapshot %s : %s to %s", label,
                                    xioSnapshot.getNativeGuid(), label));
                            xioSnapshot.setDeviceLabel(label);
                            updatedSnaps.add(xioSnapshot);
                        }
                    }
                    dbClient.updateObject(updatedSnaps);
                }
            }
            log.info("Completed executing XtremioBlockSnapshotDeviceLabelMigration migration callback.");
        } catch (Exception e) {
            String errorMsg = String.format("%s encounter unexpected error %s", this.getName(), e.getMessage());
            throw new MigrationCallbackException(errorMsg, e);
        }
    }

}