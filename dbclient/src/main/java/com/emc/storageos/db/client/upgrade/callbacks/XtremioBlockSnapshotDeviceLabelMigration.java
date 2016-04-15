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

/**
 * Migration handler to set the deviceLabel of XtremIO snapshots same as their labels.
 *
 */

public class XtremioBlockSnapshotDeviceLabelMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(XtremioBlockSnapshotDeviceLabelMigration.class);

    /**
     * Get all the BlockSnapshots associated with XtremIO storage systems and set the deviceLabel to be same as the label of the snapshots.
     *
     */
    @Override
    public void process() {
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
    }

}
