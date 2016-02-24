package com.emc.storageos.volumecontroller.impl.ceph;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.ceph.CephClient;
import com.emc.storageos.ceph.CephClientFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.iwave.ext.linux.LinuxSystemCLI;


public class CephStorageDevice extends DefaultBlockStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(CephStorageDevice.class);

    private DbClient _dbClient;
    private CephClientFactory _cephClientFactory;
    private SnapshotOperations _snapshotOperations;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCephClientFactory(CephClientFactory cephClientFactory) {
        _cephClientFactory = cephClientFactory;
    }

    public void setSnapshotOperations(SnapshotOperations snapshotOperations) {
        this._snapshotOperations = snapshotOperations;
    }

    @Override
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber) {
        return false;
    }

    @Override
    public void doConnect(StorageSystem storage) {
        _log.info("doConnect {} (nothing to do for ceph)", storage.getId().toString());
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        _log.info("doDisconnect {} (nothing to do for ceph)", storage.getId().toString());
    }

    @Override
    public String doAddStorageSystem(StorageSystem storage) throws DeviceControllerException {
        _log.info("doAddStorageSystem {} (nothing to do for ceph, just return null)", storage.getId().toString());
        return null;
    }

    @Override
    public void doRemoveStorageSystem(StorageSystem storage) throws DeviceControllerException {
        _log.info("doRemoveStorageSystem {} (nothing to do for ceph)", storage.getId().toString());
    }

    @Override
    public void doCreateVolumes(StorageSystem storage, StoragePool storagePool, String opId, List<Volume> volumes,
            VirtualPoolCapabilityValuesWrapper capabilities, TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            CephClient cephClient = getClient(storage);
            for (Volume volume : volumes) {
                String id = CephUtils.createNativeId(volume);
                cephClient.createImage(storagePool.getPoolName(), id, volume.getCapacity());

                volume.setNativeId(id);
                volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, volume));
                volume.setDeviceLabel(volume.getLabel());
                volume.setProvisionedCapacity(volume.getCapacity());
                volume.setAllocatedCapacity(volume.getCapacity());
                volume.setInactive(false);
            }
            _dbClient.updateObject(volumes);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Error while creating volumes", e);
            _dbClient.updateObject(volumes);
            ServiceError error = DeviceControllerErrors.ceph.operationFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    @Override
    public void doDeleteVolumes(StorageSystem storage, String opId, List<Volume> volumes, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        HashMap<URI, String> pools = new HashMap<URI, String>();
        try {
            CephClient cephClient = getClient(storage);
            for (Volume volume : volumes) {
            	if (volume.getNativeId() != null && !volume.getNativeId().isEmpty()) {
            	    URI poolUri = volume.getPool();
            	    String poolName = pools.get(poolUri);
            	    if (poolName == null) {
            	        StoragePool pool = _dbClient.queryObject(StoragePool.class, poolUri);
            	        poolName = pool.getPoolName();
            	        pools.put(poolUri, poolName);
            	    }
                	cephClient.deleteImage(poolName, volume.getNativeId());
            	} else {
                    _log.info("Volume {} was not created completely, so skip real deletion and just delete it from DB", volume.getLabel());            		
            	}
                volume.setInactive(true);
                _dbClient.updateObject(volume);
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Error while deleting volumes", e);
            ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("deleteVolume", e.getMessage());
            taskCompleter.error(_dbClient, code);
        }
    }

    @Override
    public void doExpandVolume(StorageSystem storage, StoragePool pool, Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        try {
            CephClient cephClient = getClient(storage);
            cephClient.resizeImage(pool.getPoolName(), volume.getNativeId(), size);
            volume.setProvisionedCapacity(size);
            volume.setAllocatedCapacity(size);
            volume.setCapacity(size);
            _dbClient.updateObject(volume);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Error while expanding volumes", e);
            ServiceCoded code = DeviceControllerErrors.ceph.operationFailed("expandVolume", e.getMessage());
            taskCompleter.error(_dbClient, code);
        }
    }

    @Override
    public void doCreateSnapshot(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _snapshotOperations.createSingleVolumeSnapshot(storage, snapshotList.get(0), createInactive,
                readOnly, taskCompleter);
    }

    @Override
    public void doDeleteSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        _snapshotOperations.deleteSingleVolumeSnapshot(storage, snapshot, taskCompleter);
    }

    @Override
    public void doCreateConsistencyGroup(StorageSystem storage, URI consistencyGroup, String replicationGroupName, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.error("Consistency groups are not supported for Ceph cluster");
        completeTaskAsUnsupported(taskCompleter);
    }

    @Override
    public void doDeleteConsistencyGroup(StorageSystem storage, URI consistencyGroup, String replicationGroupName, Boolean keepRGName, Boolean markInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.debug("doDeleteConsistencyGroup: do nothing for Ceph, because of doCreateConsistencyGroup is unsupported");
        taskCompleter.ready(_dbClient);
    }

    /**
     * Method calls the completer with error message indicating that the caller's method is unsupported
     *
     * @param completer [in] - TaskCompleter
     */
    private void completeTaskAsUnsupported(TaskCompleter completer) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String methodName = stackTrace[2].getMethodName();
        ServiceCoded code = DeviceControllerErrors.ceph.operationIsUnsupported(methodName);
        completer.error(_dbClient, code);
    }

    private CephClient getClient(StorageSystem storage) {
        return CephUtils.connectToCeph(_cephClientFactory, storage);
    }

}