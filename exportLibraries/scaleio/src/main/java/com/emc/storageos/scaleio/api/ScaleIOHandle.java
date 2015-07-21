/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.Map;

import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;

public interface ScaleIOHandle {

    ScaleIOQueryAllResult queryAll() throws Exception;

    ScaleIOQueryAllSDCResult queryAllSDC() throws Exception;

    ScaleIOQueryAllSDSResult queryAllSDS() throws Exception;

    ScaleIOQueryClusterResult queryClusterCommand() throws Exception;

    ScaleIOQueryStoragePoolResult queryStoragePool(String protectionDomainName, String storagePoolName) throws Exception;

    ScaleIOAddVolumeResult addVolume(String protectionDomainName, String storagePoolName,
                                     String volumeName, String volumeSize) throws Exception;

    ScaleIOAddVolumeResult addVolume(String protectionDomainName, String storagePoolName,
                                     String volumeName, String volumeSize, boolean thinProvisioned) throws Exception;

    ScaleIORemoveVolumeResult removeVolume(String volumeId) throws Exception;

    ScaleIOModifyVolumeCapacityResult modifyVolumeCapacity(String volumeId, String newSizeGB) throws Exception;

    ScaleIOSnapshotVolumeResult snapshotVolume(String id, String snapshot, String systemId) throws Exception;

    ScaleIOSnapshotMultiVolumeResult snapshotMultiVolume(Map<String, String> id2snapshot, String systemId) throws Exception;

    ScaleIOQueryAllVolumesResult queryAllVolumes() throws Exception;

    ScaleIOMapVolumeToSDCResult mapVolumeToSDC(String volumeId, String sdcId) throws Exception;

    ScaleIOUnMapVolumeToSDCResult unMapVolumeToSDC(String volumeId, String sdcId) throws Exception;

    ScaleIORemoveConsistencyGroupSnapshotsResult removeConsistencyGroupSnapshot(String consistencyGroupId) throws Exception;

    ScaleIOQueryAllSCSIInitiatorsResult queryAllSCSIInitiators() throws Exception;

    ScaleIOMapVolumeToSCSIInitiatorResult mapVolumeToSCSIInitiator(String volumeId, String initiatorId) throws Exception;

    ScaleIOUnMapVolumeFromSCSIInitiatorResult unMapVolumeFromSCSIInitiator(String volumeId, String initiatorId) throws Exception;

    String getVersion();
    
    String getSystemId() throws Exception;
    
    ScaleIOVolume queryVolume(String volId) throws Exception;
}
