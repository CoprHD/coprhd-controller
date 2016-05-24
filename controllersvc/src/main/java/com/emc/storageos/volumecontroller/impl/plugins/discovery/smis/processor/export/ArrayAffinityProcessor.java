/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/*
 * Refresh preferredSystemIds for a host
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
    public void updatePreferredSystems(URI systemId, AccessProfile profile, WBEMClient cimClient, DbClient dbClient) {
        _logger.info("Calling updatePreferredSystems");

        try {
            if (profile != null && profile.getProps() != null) {
                String hostIdStr = profile.getProps().get(Constants.HOST);
                if (StringUtils.isNotEmpty(hostIdStr)) {
                    Host host = dbClient.queryObject(Host.class, URI.create(hostIdStr));
                    if (host != null && !host.getInactive()) {
                        boolean isPreferredSystem = hasMappedVolumes(host.getId(), profile, cimClient, dbClient);
                        StringSet existingPreferredSystems = host.getPreferredSystemIds();
                        String systemIdStr = systemId.toString();
                        if (isPreferredSystem) {
                            if (!existingPreferredSystems.contains(systemIdStr)) {
                                existingPreferredSystems.add(systemIdStr);
                                dbClient.updateObject(host);
                            }
                        } else {
                            if (existingPreferredSystems.contains(systemIdStr)) {
                                existingPreferredSystems.remove(systemIdStr);
                                dbClient.updateObject(host);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            _logger.warn("Exception on updatePreferredSystems {}", e.getMessage());
        }
    }

    /**
     * Check if there is mapped volume for the host.
     *
     * @param hostId Id of Host instance
     * @param profile AccessProfile
     * @param cimClient WBEMClient
     * @param dbClient DbClient
     * @return true if there is mapped volume
     */
    private boolean hasMappedVolumes(URI hostId, AccessProfile profile, WBEMClient cimClient, DbClient dbClient) {
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
            List<CIMInstance> hardwareIds = executeQuery(cimClient, hardwareIdPath, query, CQL);
            if (!hardwareIds.isEmpty()) {
                List<CIMObjectPath> maskPaths = getAssociatorNames(cimClient, hardwareIds.get(0).getObjectPath(), null,
                        SmisConstants.CIM_PROTOCOL_CONTROLLER, null, null);
                for (CIMObjectPath path : maskPaths) {
                    if (profile.getserialID().equals(path.getKeyValue(SmisConstants.CP_SYSTEM_NAME).toString())) {
                        List<CIMObjectPath> volumePaths = getAssociatorNames(cimClient, path, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                        if (!volumePaths.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Executes query
     *
     * @param storageSystem
     * @param query
     * @param queryLanguage
     * @return list of matched instances
     */
    private List<CIMInstance> executeQuery(WBEMClient cimClient,
            CIMObjectPath objectPath, String query, String queryLanguage) {
        _logger.info(String.format(
                "Executing query: %s, objectPath: %s, query language: %s",
                query, objectPath, queryLanguage));

        CloseableIterator<CIMInstance> iterator = null;
        List<CIMInstance> instanceList = new ArrayList<CIMInstance>();
        try {
            iterator = cimClient.execQuery(objectPath, query, queryLanguage);
            while (iterator.hasNext()) {
                instanceList.add(iterator.next());
            }

        } catch (WBEMException we) {
            _logger.error(
                    "Caught an error while attempting to execute query and process query result. Query: "
                            + query,
                    we);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }

        return instanceList;
    }

    private List<CIMObjectPath> getAssociatorNames(WBEMClient cimClient,
            CIMObjectPath objectPath, String assocClass, String resultClass, String role, String resultRole) {
        CloseableIterator<CIMObjectPath> iterator = null;
        List<CIMObjectPath> objectPaths = new ArrayList<CIMObjectPath>();
        try {
            iterator = cimClient.associatorNames(objectPath, assocClass, resultClass, role, resultRole);
            while (iterator.hasNext()) {
                objectPaths.add(iterator.next());
            }

        } catch (WBEMException we) {
            _logger.error("Caught an error while attempting to execute associatorNames");
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }

        return objectPaths;
    }
}