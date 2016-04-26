package com.emc.storageos.migrationcontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;

public class HostExportManager {

    private DbClient _dbClient;
    private MigrationControllerImp _migrationControllerImp;
    private BlockDeviceController _blockDeviceController;
    private BlockStorageScheduler _blockStorageScheduler;
    private NetworkDeviceController _networkDeviceController;
    private URI _projectURI, _tenantURI;

    public HostExportManager() {

    }

    public HostExportManager(DbClient _dbClient, MigrationControllerImp migrationControllerImp,
            BlockDeviceController blockDeviceController, BlockStorageScheduler blockStorageScheduler,
            NetworkDeviceController networkDeviceController, URI projectURI, URI tenantURI) {
        // TODO Auto-generated constructor stub
        this._dbClient = _dbClient;
        this._migrationControllerImp = migrationControllerImp;
        this._blockDeviceController = blockDeviceController;
        this._blockStorageScheduler = blockStorageScheduler;
        this._networkDeviceController = networkDeviceController;
        this._projectURI = projectURI;
        this._tenantURI = tenantURI;
    }

    public ExportMaskPlacementDescriptor chooseBackendExportMask(StorageSystem storageSystem, StorageSystem tgtarray, URI varrayURI,
            Map<URI, Volume> volumeMap, String stepId) {

    }
    List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, _hostURI);
    Map<URI, List<StoragePort>> srcArrayTargetMap = ConnectivityUtil.getStoragePortsOfType(_dbClient,
            storageURI, StoragePort.PortType.frontend);
}
