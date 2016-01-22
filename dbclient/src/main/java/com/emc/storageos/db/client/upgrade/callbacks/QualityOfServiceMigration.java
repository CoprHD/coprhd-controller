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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * DB Migration callback to populate Quality of Service objects for
 * pre-existing Virtual Pools.
 *
 */
public class QualityOfServiceMigration extends BaseCustomMigrationCallback{

    private static final Logger _log = LoggerFactory.getLogger(QualityOfServiceMigration.class);
    private static final String SYSTEM_TYPE = "system_type";
    private static final Integer UNLIMITED_SNAPSHOTS = -1;
    private static final Integer DISABLED_SNAPSHOTS = 0;

    @Override
    public void process() {
        _log.info("START - QualityOfServiceMigration callback");

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

        _log.info("END - QualityOfServiceMigration callback");
    }

    /**
     * Retrieves information from given Virtual Pool
     *
     * @param virtualPool Virtual Pool
     * @return QosSpecification filled with information from Virtual Pool
     */
    public static QosSpecification getDataFromVirtualPool(VirtualPool virtualPool) {
        _log.debug("Fetching data from Virtual Pool, id: {}", virtualPool.getId());
        QosSpecification qos = new QosSpecification();
        StringMap specs = new StringMap();
        String systems = virtualPool.getProtocols().toString();
        qos.setName("specs-" + virtualPool.getLabel());
        qos.setConsumer("back-end");
        qos.setLabel(virtualPool.getLabel());
        qos.setId(URIUtil.createId(QosSpecification.class));
        qos.setVirtualPoolId(virtualPool.getId());
        specs.put("Provisioning Type", virtualPool.getSupportedProvisioningType());
        specs.put("Protocol", systems.substring(1, systems.length() - 1));
        specs.put("Drive Type", virtualPool.getDriveType());
        specs.put("System Type", getSystemType(virtualPool));
        specs.put("Multi-Volume Consistency", Boolean.toString(virtualPool.getMultivolumeConsistency()));
        if (virtualPool.getArrayInfo().get("raid_level") != null) {
            specs.put("RAID LEVEL", virtualPool.getArrayInfo().get("raid_level").toString());
        }
        specs.put("Expendable", Boolean.toString(virtualPool.getExpandable()));
        specs.put("Maximum SAN paths", Integer.toString(virtualPool.getNumPaths()));
        specs.put("Minimum SAN paths", Integer.toString(virtualPool.getMinPaths()));
        specs.put("Maximum block mirrors", Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
        specs.put("Paths per Initiator", Integer.toString(virtualPool.getPathsPerInitiator()));
        if (virtualPool.getHighAvailability() != null) {
            specs.put("High Availability", virtualPool.getHighAvailability());
        }
        if (virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
            specs.put("Maximum Snapshots", "unlimited");
        }else if(virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)){
            specs.put("Maximum Snapshots", "disabled");
        }else{
            specs.put("Maximum Snapshots", Integer.toString(virtualPool.getMaxNativeSnapshots()));
        }

        qos.setSpecs(specs);
        return qos;
    }

    /**
     * Gives systemType of the given vPool
     *
     * @param virtualPool
     * @return {@link String} vpool's systemType
     */
    public static String getSystemType(VirtualPool virtualPool) {
        String systemType = null;

        if (virtualPool != null && virtualPool.getArrayInfo().containsKey(SYSTEM_TYPE)) {
            for (String sysType : virtualPool.getArrayInfo().get(SYSTEM_TYPE)) {
                systemType = sysType;
                break;
            }
        }

        return systemType;
    }
}