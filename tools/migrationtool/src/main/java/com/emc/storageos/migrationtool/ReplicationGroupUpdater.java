/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationtool;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;

public class ReplicationGroupUpdater extends Executor {

    private static final Logger log = LoggerFactory.getLogger(ReplicationGroupUpdater.class);

    @Override
    public boolean execute(String providerID) {
        WBEMClient client = null;
        try {
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStorageProviderByProviderIDConstraint(providerID), result);
            StorageProvider provider = null;
            if (result.iterator().hasNext()) {
                provider = _dbClient.queryObject(StorageProvider.class, result.iterator().next());
                if (null == provider || StorageProvider.ConnectionStatus.NOTCONNECTED.toString().equalsIgnoreCase(
                        provider.getConnectionStatus()) || provider.getInactive()
                        || !provider.getVersionString().split("\\.")[0].equals("8")) {
                    log.error("Not a valid provider {} to migrate. check the provider details:", providerID);
                    return false;
                }
            } else {
                log.error("No valid provider {} found in vipr db.", providerID);
                return false;
            }

            client = getCimClient(provider.getIPAddress(), String.valueOf(provider.getPortNumber()), provider.getUserName(),
                    provider.getPassword(), provider.getUseSSL());
            if (null == client) {
                log.error("No CIMClient found for provider: {}", providerID);
                return false;
            }
            CIMObjectPath emcSysCOP = new CIMObjectPath(null, null, null, "/root/emc", "EMC_StorageSystem", null);
            CloseableIterator<?> itr = client.enumerateInstanceNames(emcSysCOP);
            while (itr.hasNext()) {
                // Iterate through each system and get replication groups
                CIMObjectPath path = (CIMObjectPath) itr.next();
                CIMProperty<?> prop = path.getKey(Constants._Name);
                String serialID = ((String) prop.getValue()).split("\\+")[1];
                boolean sysResult = processEachSystem(client, path, serialID);
                if (sysResult) {
                    log.info("Processed system {} replication groups successfully.", serialID);
                }
            }

        } catch (Exception ex) {
            log.error("Exception occurred while updateing replication groups.", ex);
            return false;
        } finally {
            // close the connection
            client.close();
        }
        return true;
    }

    /**
     * Processes each system.
     * 1. Get all ReplicationGroups.
     * 2. Get the volumes for each ReplicationGroup.
     * 3. Find the volume/snapshot/clone/mirror in ViPR DB.
     * 4. Update the ReplicationName.
     * 
     * @param client
     * @param path
     * @return
     */
    private boolean processEachSystem(WBEMClient client, CIMObjectPath systemCOP, String serialID) {
        try {
            String deviceType = getSystemType(systemCOP);
            CloseableIterator<?> replicationGroupItr = client.associatorNames(systemCOP, null, "SE_ReplicationGroup", null, null);
            while (replicationGroupItr.hasNext()) {
                CIMObjectPath rgPath = (CIMObjectPath) replicationGroupItr.next();
                CloseableIterator<?> rgVolumesItr = client.associatorNames(rgPath, null, "CIM_StorageVolume", null, null);
                while (rgVolumesItr.hasNext()) {
                    CIMObjectPath rgVolumePath = (CIMObjectPath) rgVolumesItr.next();
                    CIMProperty<?> deviceIDProp = rgVolumePath.getKey("DeviceID");
                    String volumeNativeId = (String) deviceIDProp.getValue();
                    String volumeNativeGuid = generateVolumeNativeGuid(deviceType, serialID, volumeNativeId);
                    URIQueryResultList volumeInDbResult = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(volumeNativeGuid),
                            volumeInDbResult);
                    if (volumeInDbResult.iterator().hasNext()) {
                        Volume volumeInDB = _dbClient.queryObject(Volume.class, volumeInDbResult.iterator().next());
                        updateReplicationGroupName(rgPath, volumeInDB);
                    } else {
                        log.info("volume nativeguid not found in Volume column family: {}", volumeNativeGuid);
                        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotsByNativeGuid(volumeNativeGuid),
                                volumeInDbResult);
                        if (volumeInDbResult.iterator().hasNext()) {
                            BlockSnapshot volumeInDB = _dbClient.queryObject(BlockSnapshot.class, volumeInDbResult.iterator().next());
                            updateReplicationGroupName(rgPath, volumeInDB);
                        } else {
                            log.info("volume nativeguid not found in BlockSnapshot column family: {}", volumeNativeGuid);
                            _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getMirrorByNativeGuid(volumeNativeGuid),
                                    volumeInDbResult);
                            if (volumeInDbResult.iterator().hasNext()) {
                                BlockMirror volumeInDB = _dbClient.queryObject(BlockMirror.class, volumeInDbResult.iterator().next());
                                updateReplicationGroupName(rgPath, volumeInDB);
                            }
                        }
                    }

                }
            }
        } catch (WBEMException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    private void updateReplicationGroupName(CIMObjectPath rgPath, Volume volume) {
        String newRGName = getRGNameFromCOP(rgPath);
        String oldRGName = volume.getReplicationGroupInstance();
        log.info("newRGName: {}, oldRGName: {}", newRGName, oldRGName);

    }

    private void updateReplicationGroupName(CIMObjectPath rgPath, BlockSnapshot snapshot) {
        String newRGName = getRGNameFromCOP(rgPath);
        String oldRGName = snapshot.getReplicationGroupInstance();
        log.info("newRGName: {}, oldRGName: {}", newRGName, oldRGName);
    }

    private void updateReplicationGroupName(CIMObjectPath rgPath, BlockMirror mirror) {
        String newRGName = getRGNameFromCOP(rgPath);
        String oldRGName = mirror.getReplicationGroupInstance();
        log.info("newRGName: {}, oldRGName: {}", newRGName, oldRGName);
    }

    private String getRGNameFromCOP(CIMObjectPath rgPath) {
        CIMProperty<?> instanceKey = rgPath.getKey("InstanceID");
        return (String) instanceKey.getValue();
    }

    private String getSystemType(CIMObjectPath systemCOP) {
        CIMProperty<?> prop = systemCOP.getKey(Constants._Name);
        String sysName = (String) prop.getValue();
        if (sysName.contains("SYMMETRIX")) {
            return "SYMMETRIX";
        } else if (sysName.contains("CLARiiON")) {
            return "CLARIION";
        }
        return null;
    }

    private String generateVolumeNativeGuid(String serialID, String volumeId, String deviceType) {
        StringBuffer volumeNativeGuid = new StringBuffer(deviceType).append("+");
        volumeNativeGuid.append(serialID).append("+");
        volumeNativeGuid.append("VOLUME").append("+");
        volumeNativeGuid.append(volumeId);
        return volumeNativeGuid.toString();
    }
}
