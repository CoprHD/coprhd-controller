/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.command.SSHCommandExecutor;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.storagedriver.model.StorageVolume;

public class IBMSVCCLI {

    // Logger
    private static final Logger _log = LoggerFactory.getLogger(SSHConnection.class);

    private static void executeCommand(SSHConnection connection, IBMSVCCLICommand command) {
        SSHCommandExecutor executor = new SSHCommandExecutor(connection);
        command.setCommandExecutor(executor);
        command.execute();
    }

    public static IBMSVCQueryStorageSystemResult queryStorageSystem(SSHConnection connection) {
        _log.info("Starting queryStorageSystem() for Querying IBM-SVC StorageSystem information...");
        IBMSVCQueryStorageSystemCommand command = new IBMSVCQueryStorageSystemCommand();
        executeCommand(connection, command);
        _log.info("Ending queryStorageSystem() for Querying IBM-SVC StorageSystem information.");
        return command.getResults();
    }

    public static IBMSVCQueryAllStoragePoolResult queryAllStoragePool(SSHConnection connection) {
        _log.info("Starting queryAllStoragePool() for Querying IBM-SVC All Storage Pool information...");
        IBMSVCQueryAllStoragePoolCommand command = new IBMSVCQueryAllStoragePoolCommand();
        executeCommand(connection, command);
        _log.info("Ending queryAllStoragePool() for Querying IBM-SVC All Storage Pool information.");
        return command.getResults();
    }

    public static IBMSVCQueryStoragePoolResult queryStoragePool(SSHConnection connection, String poolName) {
        _log.info("Starting queryStoragePool() for Querying IBM-SVC " + poolName + " Storage Pool information...");
        IBMSVCQueryStoragePoolCommand command = new IBMSVCQueryStoragePoolCommand(poolName);
        executeCommand(connection, command);
        _log.info("Ending queryStoragePool() for Querying IBM-SVC " + poolName + " Storage Pool information.");
        return command.getResults();
    }

    public static IBMSVCQueryAllClusterNodeResult queryAllClusterNodes(SSHConnection connection) {
        _log.info("Starting queryAllClusterNodes() for Querying IBM-SVC All Storage Port information...");
        IBMSVCQueryAllClusterNodeCommand command = new IBMSVCQueryAllClusterNodeCommand();
        executeCommand(connection, command);
        _log.info("Ending queryAllClusterNodes() for Querying IBM-SVC All Storage Port information.");
        return command.getResults();
    }

    public static IBMSVCQueryStoragePortResult queryStoragePort(SSHConnection connection, String portName) {
        _log.info("Starting queryStoragePort() for Querying IBM-SVC Storage Port information...");
        IBMSVCQueryStoragePortCommand command = new IBMSVCQueryStoragePortCommand(portName);
        executeCommand(connection, command);
        _log.info("Ending queryStoragePort() for Querying IBM-SVC Storage Port information.");
        return command.getResults();
    }

    public static IBMSVCQueryAllStorageVolumeResult queryAllStorageVolumes(SSHConnection connection) {
        _log.info("Starting queryAllStorageVolumes() for Querying IBM-SVC All Storage Volume information...");
        IBMSVCQueryAllStorageVolumeCommand command = new IBMSVCQueryAllStorageVolumeCommand();
        executeCommand(connection, command);
        _log.info("Ending queryAllStorageVolumes() for Querying IBM-SVC All Storage Volume information.");
        return command.getResults();
    }

    public static IBMSVCQueryAllHostResult queryAllHosts(SSHConnection connection) {
        _log.info("Starting queryAllHost() for Querying IBM-SVC All Host information...");
        IBMSVCQueryAllHostCommand command = new IBMSVCQueryAllHostCommand();
        executeCommand(connection, command);
        _log.info("Ending queryAllHost() for Querying IBM-SVC All Host information.");
        return command.getResults();
    }

    public static IBMSVCQueryHostInitiatorResult queryHostInitiator(SSHConnection connection, String hostId) {
        _log.info("Starting queryHostInitiator() for Querying IBM-SVC All Host information...");
        IBMSVCQueryHostInitiatorCommand command = new IBMSVCQueryHostInitiatorCommand(hostId);
        executeCommand(connection, command);
        _log.info("Ending queryHostInitiator() for Querying IBM-SVC All Host information.");
        return command.getResults();
    }

    public static IBMSVCCreateVolumeResult createStorageVolumes(SSHConnection connection, StorageVolume volume,
                                                                boolean formatBeforeUse, boolean createMirrorCopy) {
        _log.info("Starting createStorageVolumes() for Creating IBM-SVC Storage Volume...");
        String volumeName = volume.getDisplayName();
        String poolName = volume.getStoragePoolId();
        String volumeSize = volume.getRequestedCapacity().toString();
        boolean thinlyProvisioned = volume.getThinlyProvisioned();

        _log.info(String.format("createStorageVolumes() Command Parameter - VolName : %s VolSize : %s PoolName : %s ",
                volumeName, volumeSize, poolName));
        IBMSVCCreateVolumeCommand command = new IBMSVCCreateVolumeCommand(volumeName, volumeSize,
                    poolName, formatBeforeUse, createMirrorCopy, thinlyProvisioned);
        executeCommand(connection, command);
        _log.info("Ending createStorageVolumes() for Creating IBM-SVC Storage Volume.");
        return command.getResults();
    }

    public static IBMSVCExpandVolumeResult expandStorageVolumes(SSHConnection connection, String volumeId,
                                                                String newVolumeCapacity) {
        _log.info("Starting expandStorageVolumes() for Expanding IBM-SVC Storage Volume...");
        boolean formatBeforeUse = false;
        IBMSVCExpandVolumeCommand command = new IBMSVCExpandVolumeCommand(volumeId, newVolumeCapacity, formatBeforeUse);
        executeCommand(connection, command);
        _log.info("Ending expandStorageVolumes() for Expanding IBM-SVC Storage Volume.");
        return command.getResults();
    }

    public static IBMSVCDeleteVolumeResult deleteStorageVolumes(SSHConnection connection, String volumeId) {
        _log.info("Starting deleteStorageVolumes() for Deleting IBM-SVC Storage Volume...");
        IBMSVCDeleteVolumeCommand command = new IBMSVCDeleteVolumeCommand(volumeId);
        executeCommand(connection, command);
        _log.info("Ending deleteStorageVolumes() for Deleting IBM-SVC Storage Volume.");
        return command.getResults();
    }

    public static IBMSVCExportVolumeResult exportStorageVolumes(SSHConnection connection, String volumeId,
                                                                String volumeName, String hostName) {
        _log.info("Starting exportStorageVolumes() for Exporting IBM-SVC Storage Volume...");
        IBMSVCExportVolumeCommand command = new IBMSVCExportVolumeCommand(volumeId, volumeName, hostName);
        executeCommand(connection, command);
        _log.info("Ending exportStorageVolumes() for Exporting IBM-SVC Storage Volume.");
        return command.getResults();
    }

    public static IBMSVCUnExportVolumeResult unexportStorageVolumes(SSHConnection connection, String volumeId,
                                                                String volumeName, String hostName) {
        _log.info("Starting unexportStorageVolumes() for UnExporting IBM-SVC Storage Volume...");
        IBMSVCUnExportVolumeCommand command = new IBMSVCUnExportVolumeCommand(volumeId, volumeName, hostName);
        executeCommand(connection, command);
        _log.info("Ending unexportStorageVolumes() for UnExporting IBM-SVC Storage Volume.");
        return command.getResults();
    }

    public static IBMSVCGetVolumeResult queryStorageVolume(SSHConnection connection, String volumeId) {
        _log.info("Starting queryStorageVolume() for Getting IBM-SVC Storage Volume...");
        IBMSVCGetVolumeCommand command = new IBMSVCGetVolumeCommand(volumeId);
        executeCommand(connection, command);
        _log.info("Ending queryStorageVolume() for Getting IBM-SVC Storage Volume.");
        return command.getResults();
    }

    public static IBMSVCCreateFCMappingResult createFCMapping(SSHConnection connection, String srcVolumeName,
                                                        String tgtVolumeName, String consistGrpName, boolean fullCopy) {
        _log.info("Starting createFCMapping() for Creating IBM-SVC FlashCopy Mapping...");
        IBMSVCCreateFCMappingCommand command = new IBMSVCCreateFCMappingCommand(srcVolumeName, tgtVolumeName,
                consistGrpName, fullCopy);
        executeCommand(connection, command);
        _log.info("Ending createFCMapping() for Creating IBM-SVC FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCPreStartFCMappingResult preStartFCMapping(SSHConnection connection, String fc_map_Id) {
        _log.info("Starting preStartFCMapping() for Preparing to start IBM-SVC FlashCopy Mapping...");
        IBMSVCPreStartFCMappingCommand command = new IBMSVCPreStartFCMappingCommand(fc_map_Id);
        executeCommand(connection, command);
        _log.info("Ending preStartFCMapping() for Preparing to start IBM-SVC FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCQueryFCMappingResult queryFCMapping(SSHConnection connection, String fc_map_Id,
                                                            boolean isFilter, String srcVolName, String tgtVolName) {
        _log.info("Starting queryFCMapping() for Querying IBM-SVC FlashCopy Mapping...");
        IBMSVCQueryFCMappingCommand command = new IBMSVCQueryFCMappingCommand(fc_map_Id, isFilter, srcVolName, tgtVolName);
        executeCommand(connection, command);
        _log.info("Ending queryFCMapping() for Querying IBM-SVC FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCStartFCMappingResult startFCMapping(SSHConnection connection, String fc_map_Id) {
        _log.info("Starting startFCMapping() for Starting IBM-SVC FlashCopy Mapping...");
        IBMSVCStartFCMappingCommand command = new IBMSVCStartFCMappingCommand(fc_map_Id, false);
        executeCommand(connection, command);
        _log.info("Ending startFCMapping() for Starting IBM-SVC FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCStopFCMappingResult stopFCMapping(SSHConnection connection, String fc_map_Id) {
        _log.info("Starting stopFCMapping() for Stopping IBM-SVC FlashCopy Mapping...");
        IBMSVCStopFCMappingCommand command = new IBMSVCStopFCMappingCommand(fc_map_Id);
        executeCommand(connection, command);
        _log.info("Ending stopFCMapping() for Stopping IBM-SVC FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCDeleteFCMappingResult deleteFCMapping(SSHConnection connection, String fc_map_Id) {
        _log.info("Starting deleteFCMapping() for Deleting IBM-SVC FlashCopy Mapping...");
        IBMSVCDeleteFCMappingCommand command = new IBMSVCDeleteFCMappingCommand(fc_map_Id);
        executeCommand(connection, command);
        _log.info("Ending deleteFCMapping() for Deleting IBM-SVC FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCQueryVolumeFCMappingResult queryVolumeFCMapping(SSHConnection connection, String volumeId) {
        _log.info("Starting queryVolumeFCMapping() for Querying IBM-SVC Volume FlashCopy Mapping...");
        IBMSVCQueryVolumeFCMappingCommand command = new IBMSVCQueryVolumeFCMappingCommand(volumeId);
        executeCommand(connection, command);
        _log.info("Ending queryVolumeFCMapping() for Querying IBM-SVC Volume FlashCopy Mapping.");
        return command.getResults();
    }


    public static IBMSVCChangeFCMappingResult changeFCMapping(SSHConnection connection, String fcMappingId,
                                String copyRate, String autoDelete, String consistencyGrpId, String cleanRate) {
        _log.info("Starting queryVolumeFCMapping() for Querying IBM-SVC Volume FlashCopy Mapping...");
        IBMSVCChangeFCMappingCommand command = new IBMSVCChangeFCMappingCommand(fcMappingId, copyRate, autoDelete,
                consistencyGrpId, cleanRate);
        executeCommand(connection, command);
        _log.info("Ending queryVolumeFCMapping() for Querying IBM-SVC Volume FlashCopy Mapping.");
        return command.getResults();
    }

    public static IBMSVCCreateFCConsistGrpResult createFCConsistGrp(SSHConnection connection, String consistGrpName) {
        _log.info("Starting createFCConsistGrp() for Creating IBM-SVC FlashCopy Consistency Group...");
        IBMSVCCreateFCConsistGrpCommand command = new IBMSVCCreateFCConsistGrpCommand(consistGrpName);
        executeCommand(connection, command);
        _log.info("Ending createFCConsistGrp() for Creating IBM-SVC FlashCopy Consistency Group.");
        return command.getResults();
    }

    public static IBMSVCDeleteFCConsistGrpResult deleteFCConsistGrp(SSHConnection connection,
                                                                    String consistGrpId, String consistGrpName) {
        _log.info("Starting deleteFCConsistGrp() for Deleting IBM-SVC FlashCopy Consistency Group...");
        IBMSVCDeleteFCConsistGrpCommand command = new IBMSVCDeleteFCConsistGrpCommand(consistGrpId, consistGrpName);
        executeCommand(connection, command);
        _log.info("Ending deleteFCConsistGrp() for Deleting IBM-SVC FlashCopy Consistency Group.");
        return command.getResults();
    }

    public static IBMSVCQueryFCConsistGrpResult queryFCConsistGrp(SSHConnection connection,
                                                                    String consistGrpId, String consistGrpName) {
        _log.info("Starting deleteFCConsistGrp() for Querying IBM-SVC FlashCopy Consistency Group...");
        IBMSVCQueryFCConsistGrpCommand command = new IBMSVCQueryFCConsistGrpCommand(consistGrpId, consistGrpName);
        executeCommand(connection, command);
        _log.info("Ending deleteFCConsistGrp() for Querying IBM-SVC FlashCopy Consistency Group.");
        return command.getResults();
    }

    public static IBMSVCPreStartFCConsistGrpResult preStartFCConsistGrp(SSHConnection connection, String consistGrpId,
                                                                        String consistGrpName) {
        _log.info("Starting preStartFCConsistGrp() for Preparing to start IBM-SVC FlashCopy Consistency Group...");
        IBMSVCPreStartFCConsistGrpCommand command = new IBMSVCPreStartFCConsistGrpCommand(consistGrpId, consistGrpName);
        executeCommand(connection, command);
        _log.info("Ending preStartFCConsistGrp() for Preparing to start IBM-SVC FlashCopy Consistency Group.");
        return command.getResults();
    }

    public static IBMSVCStartFCConsistGrpResult startFCConsistGrp(SSHConnection connection, String consistGrpId,
                                                                  String consistGrpName) {
        _log.info("Starting startFCConsistGrp() for Starting IBM-SVC FlashCopy Consistency Group...");
        IBMSVCStartFCConsistGrpCommand command = new IBMSVCStartFCConsistGrpCommand(consistGrpId, consistGrpName, false);
        executeCommand(connection, command);
        _log.info("Ending startFCConsistGrp() for Starting IBM-SVC FlashCopy Consistency Group.");
        return command.getResults();
    }

    public static IBMSVCStopFCConsistGrpResult stopFCConsistGrp(SSHConnection connection, String consistGrpId,
                                                                  String consistGrpName) {
        _log.info("Starting stopFCConsistGrp() for Stopping IBM-SVC FlashCopy Consistency Group...");
        IBMSVCStopFCConsistGrpCommand command = new IBMSVCStopFCConsistGrpCommand(consistGrpId, consistGrpName);
        executeCommand(connection, command);
        _log.info("Ending stopFCConsistGrp() for Stopping IBM-SVC FlashCopy Consistency Group.");
        return command.getResults();
    }

    public static IBMSVCDetachCloneMirrorResult detachVolumeClones(SSHConnection connection, String volumeId,
                                                                String cloneVolumeName) {
        _log.info("Starting detachVolumeClones() for Detaching IBM-SVC Storage Volume Clones...");
        IBMSVCDetachCloneMirrorCommand command = new IBMSVCDetachCloneMirrorCommand(volumeId, cloneVolumeName);
        executeCommand(connection, command);
        _log.info("Ending detachVolumeClones() for Detached IBM-SVC Storage Volume Clones.");
        return command.getResults();
    }

    public static IBMSVCCreateMirrorVolumeResult createMirrorVolume(SSHConnection connection,
                                                                    String sourceVolumeId, String targetPoolId) {
        _log.info("Starting createMirrorVolume() for Creating IBM-SVC Mirror Volume...");
        IBMSVCCreateMirrorVolumeCommand command = new IBMSVCCreateMirrorVolumeCommand(sourceVolumeId, targetPoolId);
        executeCommand(connection, command);
        _log.info("Ending createMirrorVolume() for Creating IBM-SVC Mirror Volume.");
        return command.getResults();
    }

    public static IBMSVCQueryMirrorSyncProgressResult queryMirrorSyncProgress(SSHConnection connection,
                                                                    String sourceVolumeId) {
        _log.info("Starting queryMirrorSyncProgress() for Querying IBM-SVC Mirror Volume Sync Progress...");
        IBMSVCQueryMirrorSyncProgressCommand command = new IBMSVCQueryMirrorSyncProgressCommand(sourceVolumeId);
        executeCommand(connection, command);
        _log.info("Ending queryMirrorSyncProgress() for Querying IBM-SVC Mirror Volume Sync Progress.");
        return command.getResults();
    }

    public static IBMSVCDeleteMirrorVolumeResult deleteMirrorVolume(SSHConnection connection,
                                                                    String sourceVolumeId) {
        _log.info("Starting deleteMirrorVolume() for Deleting IBM-SVC Mirror Volume...");
        IBMSVCDeleteMirrorVolumeCommand command = new IBMSVCDeleteMirrorVolumeCommand(sourceVolumeId);
        executeCommand(connection, command);
        _log.info("Ending deleteMirrorVolume() for Deleting IBM-SVC Mirror Volume.");
        return command.getResults();
    }

}
