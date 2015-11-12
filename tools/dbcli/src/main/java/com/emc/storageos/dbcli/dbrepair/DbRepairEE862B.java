/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import static com.emc.storageos.dbcli.dbrepair.DbRepairUtils.displayDiffs;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.impl.DbModelClientImpl;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.StringSetUtil;

/**
 * Applicable Jira: https://asdjira.isus.emc.com:8443/browse/EE-862
 * <p/>
 * We will look at the specified ExportGroups and determine if there are any initiators
 * that are missing based on their cluster associations. This situation would arise if
 * the user added hosts outside of ViPR.
 */
public class DbRepairEE862B implements DbRepairStub {

    public static final String EXPORT_GROUPS = "ExportGroups";
    public static final String INITIATORS = "Initiators";

    @Override
    public Map<String, String> getParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EXPORT_GROUPS, "Comma delimited set of ExportGroup URIs");
        return parameters;
    }

    @Override
    public String getDescription() {
        return "DB Repair looks at the specified ExportGroups and adds the specified Initiators to it and its ExportMasks";
    }

    @Override
    public String getExpectedDbVersion() {
        return "2.3";
    }

    @Override
    public boolean run(DbClient dbClient, Map<String, String> parameters, boolean commitChanges) {
        if (parameters.isEmpty()) {
            return false;
        }

        try {
            DbModelClient dbModelClient = new DbModelClientImpl(dbClient);
            Map<URI, ExportGroup> exportGroupsToUpdate = new HashMap<>();
            Map<URI, ExportMask> exportMasksToUpdate = new HashMap<>();
            String exportGroupURIsStrings = parameters.get(EXPORT_GROUPS);
            if (exportGroupURIsStrings == null) {
                System.out.println("ExportGroups parameter was not provided");
                return false;
            }
            String initiatorURIStrings = parameters.get(INITIATORS);
            if (initiatorURIStrings == null) {
                System.out.println("Initiators parameter was not provided");
                return false;
            }

            Map<URI, Initiator> uriInitiatorMap = new HashMap<>();
            for (String initiatorURIString : initiatorURIStrings.split(";")) {
                Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initiatorURIString));
                if (initiator != null && !initiator.getInactive()) {
                    uriInitiatorMap.put(initiator.getId(), initiator);
                } else {
                    System.out.printf("Initiator %s is not valid%n", initiatorURIString);
                }
            }

            for (String exportGroupURIString : exportGroupURIsStrings.split(";")) {
                URI exportGroupURI = URI.create(exportGroupURIString);
                ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
                if (exportGroup == null) {
                    continue;
                }
                if (exportGroup.forCluster()) {
                    if (!verifyInitiatorsExportGroupCluster(dbClient, exportGroup, uriInitiatorMap)) {
                        return false;
                    }
                    updateExportGroupWithMissingInitiators(uriInitiatorMap, exportGroup, exportGroupsToUpdate);
                    updateExportMaskWithMissingInitiators(dbClient, uriInitiatorMap, exportGroup, exportMasksToUpdate);
                }
            }

            if (commitChanges) {
                dbModelClient.update(exportMasksToUpdate.values());
                dbModelClient.update(exportGroupsToUpdate.values());
            } else {
                displayDiffs(dbClient, exportMasksToUpdate.values());
                displayDiffs(dbClient, exportGroupsToUpdate.values());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return true;
    }

    private boolean verifyInitiatorsExportGroupCluster(DbClient dbClient, ExportGroup exportGroup, Map<URI, Initiator> uriInitiatorMap)
            throws Exception {
        String exportGroupClusterName = null;
        StringSet clusterNames = exportGroup.getClusters();
        if (clusterNames.iterator().hasNext()) {
            Cluster cluster = dbClient.queryObject(Cluster.class, URI.create(clusterNames.iterator().next()));
            exportGroupClusterName = cluster.getLabel();
        }
        if (exportGroupClusterName != null) {
            for (Initiator initiator : uriInitiatorMap.values()) {
                if (!initiator.getClusterName().equals(exportGroupClusterName)) {
                    System.out.printf("%s does not belong to ExportGroup's cluster, %s%n", initiator.toString(), exportGroupClusterName);
                    return false;
                }
            }
        } else {
            System.out.println("Cluster could not be found for cluster ExportGroup");
            return false;
        }
        return true;
    }

    private void updateExportMaskWithMissingInitiators(DbClient dbClient, Map<URI, Initiator> uriInitiatorMap, ExportGroup exportGroup,
            Map<URI, ExportMask> exportMasksToUpdate) {
        List<ExportMask> exportMasks = dbClient.queryObject(ExportMask.class,
                StringSetUtil.stringSetToUriList(exportGroup.getExportMasks()));
        for (ExportMask exportMask : exportMasks) {
            System.out.printf("Examining ExportMask %s (%s)%n", exportMask.getMaskName(), exportMask.getId());
            for (Initiator initiator : uriInitiatorMap.values()) {
                if (!exportMask.hasInitiator(initiator.getId().toString())) {
                    System.out.printf("\t- Need to add initiator %s%n", initiator.toString());
                    exportMask.addInitiator(initiator);
                    exportMask.addToUserCreatedInitiators(initiator);
                    exportMasksToUpdate.put(exportMask.getId(), exportMask);
                }
            }
        }
    }

    private void updateExportGroupWithMissingInitiators(Map<URI, Initiator> uriInitiatorMap, ExportGroup exportGroup,
            Map<URI, ExportGroup> exportGroupsToUpdate) {
        System.out.printf("Examining ExportGroup %s (%s)%n", exportGroup.getLabel(), exportGroup.getId());
        for (Initiator initiator : uriInitiatorMap.values()) {
            if (!exportGroup.hasInitiator(initiator)) {
                System.out.printf("\t- Need to add initiator %s%n", initiator.toString());
                exportGroup.addInitiator(initiator);
                exportGroupsToUpdate.put(exportGroup.getId(), exportGroup);
            }
        }
    }
}
