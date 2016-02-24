package com.emc.storageos.volumecontroller.impl.ceph;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.ceph.CephClient;
import com.emc.storageos.ceph.CephClientFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.DefaultSnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class CephSnapshotOperations extends DefaultSnapshotOperations {

    private static Logger _log = LoggerFactory.getLogger(CephSnapshotOperations.class);

    private DbClient _dbClient;
    private CephClientFactory _cephClientFactory;

    private CephClient getClient(StorageSystem storage) {
        return CephUtils.connectToCeph(_cephClientFactory, storage);
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCephClientFactory(CephClientFactory cephClientFactory) {
        _cephClientFactory = cephClientFactory;
    }

    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            CephClient cephClient = getClient(storage);
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume volume = _dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
            StoragePool pool = _dbClient.queryObject(StoragePool.class, volume.getPool());

            String id = CephUtils.createNativeId(blockSnapshot);
            cephClient.createSnap(pool.getPoolName(), volume.getNativeId(), id);

            blockSnapshot.setNativeId(id);
            blockSnapshot.setDeviceLabel(blockSnapshot.getLabel());
            blockSnapshot.setIsSyncActive(true);
            _dbClient.updateObject(blockSnapshot);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Snapshot creation failed", e);
            ServiceError error = DeviceControllerErrors.ceph.operationFailed("createSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            CephClient cephClient = getClient(storage);
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            Volume volume = _dbClient.queryObject(Volume.class, blockSnapshot.getParent().getURI());
            StoragePool pool = _dbClient.queryObject(StoragePool.class, volume.getPool());

            cephClient.deleteSnap(pool.getPoolName(), volume.getNativeId(), blockSnapshot.getNativeId());

            blockSnapshot.setInactive(true);
            _dbClient.updateObject(blockSnapshot);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Snapshot deletion failed", e);
            ServiceError error = DeviceControllerErrors.ceph.operationFailed("deleteSingleVolumeSnapshot", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

}
