package com.emc.storageos.volumecontroller.impl.vnx;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.vnx.xmlapi.VNXFileSshApi;
import com.emc.storageos.vnx.xmlapi.XMLApiResult;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.AbstractFileMirrorOperations;

public class VnxFileMirrorOperations extends AbstractFileMirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(VnxFileMirrorOperations.class);

    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    private String VDM_MIRROR_SESSION = "vdm_session";

    private final VNXFileSshApi _sshApi = new VNXFileSshApi();

    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        String cmdResult = VNXFileConstants.CMD_SUCCESS;
        XMLApiResult result = null;

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        String policyName = targetFileShare.getLabel();

        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        // rpo value
        String schedule = null;
        if (virtualPool != null) {
            if (virtualPool.getFrRpoValue() == 0) {
                // Zero RPO value means policy has to be started manually-NO Schedule
                schedule = "";
            } else {
                schedule = createSchedule(virtualPool.getFrRpoValue().toString(), virtualPool.getFrRpoType());
            }
        }

        String interConnectName = null;
        String rpRpoType = virtualPool.getRpRpoType();
        if (rpRpoType.equals(FileReplicationType.REMOTE.toString())) {

            // interconnect
            String interConnSourceName = getValideInterConnectionRemoteMirror(sourceStorageSystem, targetStorageSystem.getIpAddress());
            if (interConnSourceName != null) {
                String interConnDestName = getValideInterConnectionRemoteMirror(targetStorageSystem, sourceStorageSystem.getIpAddress());
                if (interConnSourceName != null && !interConnSourceName.isEmpty()) {
                    interConnectName = interConnSourceName;
                }
            }
        } else {
            String sourceDmName = null;
            String sourceVDMName = null;

            String targetDmName = null;
            String targetVDMName = null;

            if (sourceFileShare.getVirtualNAS() == null) {
                StorageHADomain sourceDM = getDataMover(sourceFileShare);
                sourceDmName = sourceDM.getName();
            } else {
                VirtualNAS vNasSource = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
                PhysicalNAS physicalDm = _dbClient.queryObject(PhysicalNAS.class, sourceFileShare.getVirtualNAS());
                sourceDmName = vNasSource.getNasName();
                sourceVDMName = physicalDm.getNasName();
            }

            if (targetFileShare.getVirtualNAS() == null) {
                StorageHADomain sourceDM = getDataMover(targetFileShare);
                targetDmName = sourceDM.getName();
            } else {
                VirtualNAS vNasTarget = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
                PhysicalNAS physicalDmTarget = _dbClient.queryObject(PhysicalNAS.class, sourceFileShare.getVirtualNAS());
                targetDmName = vNasTarget.getNasName();
                targetVDMName = physicalDmTarget.getNasName();
            }

            if (sourceDmName != null && targetDmName != null) {
                // replication between two DM of saem vnx box
                if (!sourceDmName.equals(targetDmName)) {
                    // get the interconnection if exists we will do it

                } else {
                    // get interconnection for loop back
                }
            }

        }

        // only for vdm replication session
        String createFSCmd = "";
        if (sourceFileShare.getVirtualNAS() != null && targetFileShare.getVirtualNAS() != null) {
            policyName = policyName + VDM_MIRROR_SESSION;
            VirtualNAS vNasSource = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
            VirtualNAS vNasDest = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
            createFSCmd = _sshApi.formatCreateVDMReplicateSession(policyName, vNasSource.getLabel(), vNasDest.getLabel(), null,
                    interConnectName,
                    schedule);
            _sshApi.setConnParams(sourceStorageSystem.getIpAddress(), sourceStorageSystem.getUsername(), sourceStorageSystem.getPassword());
            result = _sshApi.executeSshRetry(VNXFileSshApi.NAS_REPLICATE_CMD, createFSCmd);
            if (!result.isCommandSuccess()) {
                cmdResult = VNXFileConstants.CMD_FAILURE;
            }
        }

        // file replication session
        if (result.isCommandSuccess()) {
            // create replication command format
            _sshApi.formatCreateReplicateSession(policyName, sourceFileShare.getLabel(), targetFileShare.getLabel(), null, null,
                    interConnectName,
                    schedule);

            _sshApi.setConnParams(sourceStorageSystem.getIpAddress(), sourceStorageSystem.getUsername(), sourceStorageSystem.getPassword());

            result = _sshApi.executeSshRetry(VNXFileSshApi.NAS_REPLICATE_CMD, createFSCmd);
            if (!result.isCommandSuccess()) {
                cmdResult = VNXFileConstants.CMD_FAILURE;
            } else {
                completer.ready(_dbClient);
            }
        }

    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {

    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {

    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {

    }

    private String createSchedule(String fsRpoValue, String fsRpoType) {
        StringBuilder builder = new StringBuilder();
        // vnx specific rpo format
        return builder.toString();
    }

    /**
     * if source and target contain
     * 
     * @param sourceFileShare
     * @param targetFileShare
     * @param interConnect
     * @param maxSynctime
     * @return
     */
    private String getFormatCmdForCreateReplicator(FileShare sourceFileShare, FileShare targetFileShare, String interConnect,
            String maxSynctime) {
        String formatCmd = null;
        String policyName = targetFileShare.getLabel();

        if (sourceFileShare.getVirtualNAS() != null && targetFileShare.getVirtualNAS() != null) {
            VirtualNAS vNasSource = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
            VirtualNAS vNasDest = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
            formatCmd = _sshApi.formatCreateVDMReplicateSession(policyName, vNasSource.getLabel(), vNasDest.getLabel(), null, interConnect,
                    maxSynctime);

        } else if (sourceFileShare.getVirtualNAS() != null && targetFileShare.getVirtualNAS() == null) {
            VirtualNAS vNasSource = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
            StoragePool destPool = _dbClient.queryObject(StoragePool.class, targetFileShare.getPool());
            formatCmd = _sshApi.formatCreateVDMReplicateSession(policyName, vNasSource.getLabel(), null, destPool.getLabel(), interConnect,
                    maxSynctime);

        } else if (sourceFileShare.getVirtualNAS() == null && targetFileShare.getVirtualNAS() != null) {
            VirtualNAS vNasDest = _dbClient.queryObject(VirtualNAS.class, sourceFileShare.getVirtualNAS());
            StoragePool destPool = _dbClient.queryObject(StoragePool.class, targetFileShare.getPool());
            formatCmd = _sshApi.formatCreateVDMReplicateSession(policyName, sourceFileShare.getLabel(), null, destPool.getLabel(),
                    interConnect, maxSynctime);
        } else {

        }

        return formatCmd;
    }

	// give list of interConnects ids or controller station queryObject ControlStationQueryParams
    private String getValideInterConnectionRemoteMirror(StorageSystem sourceStorageSystem, String ipDest) {
        // first get the interconnect info for source system
		
		//-interconnect
        Map<String, Map<String, String>> interConnects = null;
        Map<String, String> interConnectInfos = null;
        _sshApi.setConnParams(sourceStorageSystem.getIpAddress(), sourceStorageSystem.getUsername(), sourceStorageSystem.getPassword());

        // get the interConnections
        interConnects = _sshApi.getReplicatorInterconnects();

        // get validate interConnects
        List<String> validConnection = new ArrayList<String>();
        if (interConnects != null) {
            for (Entry<String, Map<String, String>> entry : interConnects.entrySet()) {
                String interConnectId = entry.getKey();
				//isActiveInterConnect 
                if (_sshApi.isInterConnectValid(interConnectId) == true) {
                    validConnection.add(interConnectId);
                }
            }
        }

        if (!validConnection.isEmpty()) {
            String destSystemName = _sshApi.getReplicationConfig(ipDest);
            // check remote connection is add to source System or exists with source system
            for (String interConnectId : validConnection) {
                interConnectInfos = interConnects.get(interConnectId);
                if (interConnectInfos != null && !interConnectInfos.isEmpty()) {
                    if (interConnectInfos.get("destination_server").equals(destSystemName)) {
                        return interConnectId;
                    }
                }
            }

        } else {
            // throw exception for no validate inter connection exists
        }

        return null;
    }

    private StorageHADomain getDataMover(FileShare fileShare) {
        StoragePort port = _dbClient.queryObject(StoragePort.class, fileShare.getStoragePort());
        StorageHADomain dm = null;
        if (port != null) {
            dm = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
        }
        return dm;
    }

}