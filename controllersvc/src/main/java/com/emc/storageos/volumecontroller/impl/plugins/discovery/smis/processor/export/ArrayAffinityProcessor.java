/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

/*
 * Refresh preferredPoolIds for a host
 */
public class ArrayAffinityProcessor {
    private static final Logger _logger = LoggerFactory.getLogger(ArrayAffinityProcessor.class);
    private static final String CQL = "CQL";

    /**
     * Update preferred pools of a host
     *
     * @param profile AccessProfile
     * @param cimClient WBEMClient
     * @return true if there is mapped volume
     */
    public void updatePreferredPoolIds(AccessProfile profile, WBEMClient cimClient, DbClient dbClient) {
        _logger.info("Calling updatePreferredPoolIds");

        try {
            if (profile != null && profile.getProps() != null) {
                String hostIdStr = profile.getProps().get(Constants.HOST);
                String systemIdsStr = profile.getProps().get(Constants.SYSTEM_IDS);
                String[] systemIds = systemIdsStr.split(Constants.ID_DELIMITER);
                Set<String> systemIdSet = new HashSet<String>(Arrays.asList(systemIds));

                if (StringUtils.isNotEmpty(hostIdStr)) {
                    Host host = dbClient.queryObject(Host.class, URI.create(hostIdStr));
                    if (host != null && !host.getInactive()) {
                        Map<String, String> preferredPoolURIs = getPreferredPoolMap(host.getId(), profile, cimClient, dbClient);
                        if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, systemIdSet, dbClient, preferredPoolURIs)) {
                            dbClient.updateObject(host);
                        }
                    }
                }
            }
        } catch (Exception e) {
            _logger.warn("Exception on updatePreferredSystems {}", e.getMessage());
        }
    }

    /**
     * Get preferred pool to export type map for a host
     *
     * @param hostId Id of Host instance
     * @param profile AccessProfile
     * @param cimClient WBEMClient
     * @param dbClient DbClient
     * @return preferred pool to export type map
     */
    private Map<String, String> getPreferredPoolMap(URI hostId, AccessProfile profile, WBEMClient cimClient, DbClient dbClient) {
        Map<String, String> preferredPoolMap = new HashMap<String, String>();

        List<Initiator> allInitiators = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient,
                        Initiator.class, ContainmentConstraint.Factory
                                .getContainedObjectsConstraint(
                                        hostId,
                                        Initiator.class, Constants.HOST));
        Set<CIMObjectPath> hardwareIdPaths = new HashSet<CIMObjectPath>();
        for (Initiator initiator : allInitiators) {
            String normalizedPortName = Initiator.normalizePort(initiator
                    .getInitiatorPort());
            String query = String
                    .format("SELECT %s.%s FROM %s where %s.%s ='%s'",
                            SmisConstants.CIM_STORAGE_HARDWARE_ID, SmisConstants.CP_INSTANCE_ID, SmisConstants.CIM_STORAGE_HARDWARE_ID,
                            SmisConstants.CIM_STORAGE_HARDWARE_ID, SmisConstants.CP_ELEMENT_NAME, normalizedPortName);
            CIMObjectPath hardwareIdPath = CimObjectPathCreator.createInstance(
                    SmisConstants.CIM_STORAGE_HARDWARE_ID, Constants.EMC_NAMESPACE, null);
            List<CIMInstance> hardwareIds = DiscoveryUtils.executeQuery(cimClient, hardwareIdPath, query, CQL);
            if (!hardwareIds.isEmpty()) {
                hardwareIdPaths.add(hardwareIds.get(0).getObjectPath());
            }
        }

        Set<CIMObjectPath> maskPaths = new HashSet<CIMObjectPath>();
        for (CIMObjectPath hardwareIdPath : hardwareIdPaths) {
            maskPaths.addAll(DiscoveryUtils.getAssociatorNames(cimClient, hardwareIdPath, null,
                    SmisConstants.CIM_PROTOCOL_CONTROLLER, null, null));
        }

        for (CIMObjectPath path : maskPaths) {
            if (StringUtils.contains(path.getKeyValue(SmisConstants.CP_SYSTEM_NAME).toString(), profile.getserialID())) {
                List<CIMObjectPath> hardwareIdsInMask = DiscoveryUtils.getAssociatorNames(cimClient, path, null, SmisConstants.CIM_STORAGE_HARDWARE_ID, null, null);
                // check if the mask is shared or exclusive
                String maskType = hardwareIdPaths.containsAll(hardwareIdsInMask) ? ExportGroup.ExportGroupType.Host.name() :
                    ExportGroup.ExportGroupType.Cluster.name();

                List<CIMObjectPath> volumePaths = DiscoveryUtils.getAssociatorNames(cimClient, path, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                for (CIMObjectPath volumePath : volumePaths) {
                    URI poolURI = ArrayAffinityDiscoveryUtils.getStoragePool(volumePath, cimClient, dbClient);
                    if (!NullColumnValueGetter.isNullURI(poolURI)) {
                        ArrayAffinityDiscoveryUtils.addPoolToPreferredPoolMap(preferredPoolMap, poolURI.toString(), maskType);
                    }
                }
            }
        }

        return preferredPoolMap;
    }
}
