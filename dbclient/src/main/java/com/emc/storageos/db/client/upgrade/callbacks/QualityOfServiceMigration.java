/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * DB Migration callback to populate Quality of Service objects for
 * pre-existing Virtual Pools.
 *
 */
public class QualityOfServiceMigration extends BaseCustomMigrationCallback{

    private static final Logger _log = LoggerFactory.getLogger(QualityOfServiceMigration.class);
    private static final String SYSTEM_TYPE = "system_type";
    private static final String RAID_LEVEL = "raid_level";
    private static final Integer UNLIMITED_SNAPSHOTS = -1;
    private static final Integer DISABLED_SNAPSHOTS = 0;
    private static final String QOS_CONSUMER = "back-end";
    private static final String QOS_NAME = "specs-"; // with appended Virtual Pool label
    // QoS spec labels
    private static final String SPEC_PROVISIONING_TYPE = "Provisioning Type";
    private static final String SPEC_PROTOCOL = "Protocol";
    private static final String SPEC_DRIVE_TYPE = "Drive Type";
    private static final String SPEC_SYSTEM_TYPE = "System Type";
    private static final String SPEC_MULTI_VOL_CONSISTENCY = "Multi-Volume Consistency";
    private static final String SPEC_RAID_LEVEL = "RAID Level";
    private static final String SPEC_EXPENDABLE = "Expendable";
    private static final String SPEC_MAX_SAN_PATHS = "Maximum SAN paths";
    private static final String SPEC_MIN_SAN_PATHS = "Minimum SAN paths";
    private static final String SPEC_MAX_BLOCK_MIRRORS = "Maximum block mirrors";
    private static final String SPEC_PATHS_PER_INITIATOR = "Paths per Initiator";
    private static final String SPEC_HIGH_AVAILABILITY = "High Availability";
    private static final String SPEC_MAX_SNAPSHOTS = "Maximum Snapshots";

    private static final String LABEL_DISABLED_SNAPSHOTS = "disabled";
    private static final String LABEL_UNLIMITED_SNAPSHOTS = "unlimited";

    @Override
    public void process() throws MigrationCallbackException {
        _log.debug("START - QualityOfServiceMigration callback");

        DbClient _dbClient = getDbClient();

        List<QosSpecification> qosSpecifications = new ArrayList<>();
        List<URI> virtualPoolURIs = _dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> vpIter = _dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolURIs);
        while (vpIter.hasNext()) {
            VirtualPool virtualPool = vpIter.next();
            if(virtualPool != null){
                _log.info("Virtual Pool found, id: {}", virtualPool.getId());
                QosSpecification qosSpecification = getDataFromVirtualPool(virtualPool);
                qosSpecifications.add(qosSpecification);
            }
        }

        if(!qosSpecifications.isEmpty()){
            _dbClient.createObject(qosSpecifications);
        }

        _log.debug("END - QualityOfServiceMigration callback");
    }

    /**
     * Retrieves information from given Virtual Pool
     *
     * @param virtualPool Virtual Pool
     * @return QosSpecification filled with information from Virtual Pool
     */
    private QosSpecification getDataFromVirtualPool(VirtualPool virtualPool) throws MigrationCallbackException {
        _log.debug("Fetching data from Virtual Pool, id: {}", virtualPool.getId());

        QosSpecification qos = null;
        try {
            qos = new QosSpecification();
            StringMap specs = new StringMap();
            qos.setName(QOS_NAME + virtualPool.getLabel());
            qos.setConsumer(QOS_CONSUMER);
            qos.setLabel(virtualPool.getLabel());
            qos.setId(URIUtil.createId(QosSpecification.class));
            qos.setVirtualPoolId(virtualPool.getId());
            String protocols = null;
            if (virtualPool.getProtocols() != null) {
                protocols = virtualPool.getProtocols().toString();
            }
            if (protocols != null) {
                specs.put(SPEC_PROTOCOL, protocols.substring(1, protocols.length() - 1));
            }
            if (virtualPool.getSupportedProvisioningType() != null) {
                specs.put(SPEC_PROVISIONING_TYPE, virtualPool.getSupportedProvisioningType());
            }
            if (virtualPool.getDriveType() != null) {
                specs.put(SPEC_DRIVE_TYPE, virtualPool.getDriveType());
            }
            String systemType = getSystemType(virtualPool);
            if (systemType != null) {
                specs.put(SPEC_SYSTEM_TYPE, systemType);
            }
            if (virtualPool.getMultivolumeConsistency() != null) {
                specs.put(SPEC_MULTI_VOL_CONSISTENCY, Boolean.toString(virtualPool.getMultivolumeConsistency()));
            }
            if (virtualPool.getArrayInfo() != null && virtualPool.getArrayInfo().get(RAID_LEVEL) != null) {
                specs.put(SPEC_RAID_LEVEL, virtualPool.getArrayInfo().get(RAID_LEVEL).toString());
            }
            if (virtualPool.getExpandable() != null) {
                specs.put(SPEC_EXPENDABLE, Boolean.toString(virtualPool.getExpandable()));
            }
            if (virtualPool.getNumPaths() != null) {
                specs.put(SPEC_MAX_SAN_PATHS, Integer.toString(virtualPool.getNumPaths()));
            }
            if (virtualPool.getMinPaths() != null) {
                specs.put(SPEC_MIN_SAN_PATHS, Integer.toString(virtualPool.getMinPaths()));
            }
            if (virtualPool.getMaxNativeContinuousCopies() != null) {
                specs.put(SPEC_MAX_BLOCK_MIRRORS, Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
            }
            if (virtualPool.getPathsPerInitiator() != null) {
                specs.put(SPEC_PATHS_PER_INITIATOR, Integer.toString(virtualPool.getPathsPerInitiator()));
            }
            if (virtualPool.getHighAvailability() != null) {
                specs.put(SPEC_HIGH_AVAILABILITY, virtualPool.getHighAvailability());
            }
            if (virtualPool.getMaxNativeSnapshots() != null) {
                if (virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
                    specs.put(SPEC_MAX_SNAPSHOTS, LABEL_UNLIMITED_SNAPSHOTS);
                }else if(virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)){
                    specs.put(SPEC_MAX_SNAPSHOTS, LABEL_DISABLED_SNAPSHOTS);
                }else{
                    specs.put(SPEC_MAX_SNAPSHOTS, Integer.toString(virtualPool.getMaxNativeSnapshots()));
                }
            }

            qos.setSpecs(specs);
        } catch (Exception e) {
            String errorMsg = String.format("%s encounter unexpected error %s", getName(), e.getMessage());
            _log.error(errorMsg);
            throw new MigrationCallbackException(errorMsg, e);
        }

        return qos;
    }

    /**
     * Gives systemType of the given vPool
     *
     * @param virtualPool
     * @return {@link String} vpool's systemType
     */
    private String getSystemType(VirtualPool virtualPool) {
        String systemType = null;

        if (virtualPool != null && virtualPool.getArrayInfo() != null && virtualPool.getArrayInfo().containsKey(SYSTEM_TYPE)) {
            for (String sysType : virtualPool.getArrayInfo().get(SYSTEM_TYPE)) {
                systemType = sysType;
                break;
            }
        }

        return systemType;
    }
}