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

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCephClientFactory(CephClientFactory cephClientFactory) {
        _cephClientFactory = cephClientFactory;
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