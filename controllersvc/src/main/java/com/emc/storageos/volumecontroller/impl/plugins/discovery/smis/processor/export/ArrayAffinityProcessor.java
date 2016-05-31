/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
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
     * Update preferred systems of a host
     *
     * @param systemId URI of storage system
     * @param profile AccessProfile
     * @param cimClient WBEMClient
     * @return true if there is mapped volume
     */
    public void updatePreferredPoolIds(URI systemId, AccessProfile profile, WBEMClient cimClient, DbClient dbClient) {
        _logger.info("Calling updatePreferredPoolIds");

        try {
            if (profile != null && profile.getProps() != null) {
                String hostIdStr = profile.getProps().get(Constants.HOST);
                if (StringUtils.isNotEmpty(hostIdStr)) {
                    Host host = dbClient.queryObject(Host.class, URI.create(hostIdStr));
                    if (host != null && !host.getInactive()) {
                        Set<URI> preferredPoolURIs = getPreferredPoolIds(host.getId(), profile, cimClient, dbClient);
                        if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, systemId, dbClient, preferredPoolURIs)) {
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
     * Get preferred pool Ids for a host
     *
     * @param hostId Id of Host instance
     * @param profile AccessProfile
     * @param cimClient WBEMClient
     * @param dbClient DbClient
     * @return set of URIs of preferred pools
     */
    private Set<URI> getPreferredPoolIds(URI hostId, AccessProfile profile, WBEMClient cimClient, DbClient dbClient) {
        Set<URI> preferredPools = new HashSet<URI>();

        List<Initiator> allInitiators = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient,
                        Initiator.class, ContainmentConstraint.Factory
                                .getContainedObjectsConstraint(
                                        hostId,
                                        Initiator.class, Constants.HOST));
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
                List<CIMObjectPath> maskPaths = DiscoveryUtils.getAssociatorNames(cimClient, hardwareIds.get(0).getObjectPath(), null,
                        SmisConstants.CIM_PROTOCOL_CONTROLLER, null, null);
                for (CIMObjectPath path : maskPaths) {
                    if (profile.getserialID().equals(path.getKeyValue(SmisConstants.CP_SYSTEM_NAME).toString())) {
                        List<CIMObjectPath> volumePaths = DiscoveryUtils.getAssociatorNames(cimClient, path, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                        for (CIMObjectPath volumePath : volumePaths) {
                            URI poolURI = ArrayAffinityDiscoveryUtils.getStoragePool(volumePath, cimClient, dbClient);
                            if (!NullColumnValueGetter.isNullURI(poolURI)) {
                                preferredPools.add(poolURI);
                            }
                        }
                    }
                }
            }
        }

        return preferredPools;
    }
}