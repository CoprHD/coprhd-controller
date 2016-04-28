/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.geo;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.common.VdcUtil;

public class GeoDependencyChecker {
    final private Logger log = LoggerFactory.getLogger(GeoDependencyChecker.class);

    private final DbClient dbClient;
    private final GeoClientCacheManager geoClientManager;
    private final DependencyChecker localDependencyChecker;

    public GeoDependencyChecker(DbClient dbClient, CoordinatorClient coordinator, DependencyChecker localDependencyChecker) {
        this.dbClient = dbClient;

        geoClientManager = new GeoClientCacheManager();
        geoClientManager.setDbClient(dbClient);
        geoClientManager.setCoordinatorClient(coordinator);

        this.localDependencyChecker = localDependencyChecker;
    }

    /**
     * checks to see if any references exist for this uri
     * uses dependency list created from relational indices
     * 
     * @param uri id of the DataObject
     * @param type DataObject class name
     * @param onlyActive if true, checks for active references only (expensive)
     * @return null if no references exist on this uri, return the type of the dependency if exist
     */
    public String checkDependencies(URI uri, Class<? extends DataObject> type, boolean onlyActive) {
        return checkDependencies(uri, type, onlyActive, null);
    }

    /**
     * checks to see if any references exist for this uri
     * uses dependency list created from relational indices
     * 
     * @param uri id of the DataObject
     * @param type DataObject class name
     * @param onlyActive if true, checks for active references only (expensive)
     * @param excludeTypes optional list of classes that can be excluded as dependency
     * @return null if no references exist on this uri, return the type of the dependency if exist
     */
    public String checkDependencies(URI uri, Class<? extends DataObject> type, boolean onlyActive,
            List<Class<? extends DataObject>> excludeTypes) {
        String depMsg = localDependencyChecker.checkDependencies(uri, type, true, excludeTypes);
        if (depMsg != null || KeyspaceUtil.isLocal(type)) {
            return depMsg;
        }

        // If there is any vdc under disconnect status, do not check dependency, return ""
        if (hasDisconnectedVdc()) {
            return "";
        }

        List<URI> vDCIds = dbClient.queryByType(VirtualDataCenter.class, true);

        VirtualDataCenter vDC = null;
        for (URI vDCId : vDCIds) {
            if (vDCId.equals(VdcUtil.getLocalVdc().getId()))
            {
                continue; // skip local vDC
            }

            vDC = dbClient.queryObject(VirtualDataCenter.class, vDCId);

            GeoServiceClient client = geoClientManager.getGeoClient(vDC.getShortId());

            log.debug("Query Geo server={}", client.getServiceURI());
            try {
                String dependency = client.checkDependencies(type, uri, true);
                if (!dependency.isEmpty()) {
                    log.info("Can't GC {} because depends on {} on {}", new Object[] { uri, dependency, vDCId });
                    return dependency;
                }
            } catch (Exception e) {
                log.error("Failed to query depenedency for {} on {} e=", new Object[] { uri, vDC.getShortId(), e });
                log.error("so assume it has dependency");
                return "";
            }
        }

        log.debug("Geo object {} can be GC", uri);
        return null;
    }

    private boolean hasDisconnectedVdc() {
        boolean hasDiconnVdc = false;

        List<URI> vDCIds = dbClient.queryByType(VirtualDataCenter.class, true);
        VirtualDataCenter vDC = null;
        for (URI vDCId : vDCIds) {
            if (vDCId.equals(VdcUtil.getLocalVdc().getId()))
            {
                continue; // skip local vDC
            }

            vDC = dbClient.queryObject(VirtualDataCenter.class, vDCId);

            if (vDC.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.DISCONNECTED ||
                    vDC.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.DISCONNECTING) {
                log.info("Geo dependency check, vdc {} is under disconnect status", vDC.getShortId());
                hasDiconnVdc = true;
            }
        }

        return hasDiconnVdc;
    }
}
