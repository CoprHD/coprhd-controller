/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * File Protection policies are introduced in 3.6 release.
 * Virtual pool replication attributes are moved to file protection policy, Hence
 * 
 * Target vpool has to be updated in policy topology
 * 
 * 
 */
public class FileReplicationPolicyUpdateTargetPoolMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(FileReplicationPolicyUpdateTargetPoolMigration.class);

    public static final String SEPARATOR = "::";

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("File replication policy to update it's target pool in topology migration START");

        DbClient dbClient = getDbClient();
        try {
            List<URI> virtualPoolURIs = dbClient.queryByType(VirtualPool.class, true);
            Iterator<VirtualPool> virtualPools = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolURIs, true);
            List<FileReplicationTopology> replPolicyTopologies = new ArrayList<FileReplicationTopology>();

            Map<String, FilePolicy> replicationPolicies = getRemoteReplicationPolicies();
            if (replicationPolicies.isEmpty()) {
                logger.info("No remote replication policies found in system... ");
                return;
            }
            // Identify the corresponding replication policy for vpool,
            // Update the target vpool in topology for that policy
            while (virtualPools.hasNext()) {
                VirtualPool virtualPool = virtualPools.next();
                if (VirtualPool.Type.file.name().equals(virtualPool.getType())
                        && virtualPool.getFileReplicationType() != null
                        && FileReplicationType.REMOTE.name().equalsIgnoreCase(virtualPool.getFileReplicationType())) {
                    logger.info("Getting replicaiton policies associated with vpool {} ", virtualPool.getLabel());
                    // Get the existing replication policy
                    // which was created with vpool name followed by _Replication_Policy
                    String polName = virtualPool.getLabel() + "_Replication_Policy";
                    FilePolicy replPolicy = replicationPolicies.get(polName);
                    if (replPolicy != null) {
                        // Get the replication topologies for the policy!!
                        List<FileReplicationTopology> policyTopologies = queryDBReplicationTopologies(replPolicy);
                        if (policyTopologies != null && !policyTopologies.isEmpty()) {
                            // Get the target vpool from the vpool!
                            String targetVarray = null;
                            String targetVPool = null;

                            Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettings = virtualPool.getFileRemoteProtectionSettings(
                                    virtualPool, dbClient);
                            if (remoteSettings != null && !remoteSettings.isEmpty()) {
                                // till now CoprHD supports only single target!!
                                for (Map.Entry<URI, VpoolRemoteCopyProtectionSettings> entry : remoteSettings.entrySet()) {
                                    if (entry != null) {
                                        targetVarray = entry.getKey().toString();
                                        if (entry.getValue() != null && entry.getValue().getVirtualPool() != null) {
                                            targetVPool = entry.getValue().getVirtualPool().toString();
                                        }
                                        break;
                                    }
                                }
                            }

                            for (FileReplicationTopology topology : policyTopologies) {
                                if (virtualPool.getVirtualArrays().contains(topology.getSourceVArray().toASCIIString())
                                        && topology.getTargetVArrays().contains(targetVarray)) {
                                    if (targetVarray != null && targetVPool != null) {
                                        topology.setTargetVAVPool(targetVarray + SEPARATOR + targetVPool);
                                        replPolicyTopologies.add(topology);
                                    }
                                }
                            }
                        }

                    } else {
                        logger.info("No remote replication policy with name {} ", polName);
                    }

                }

            }

            // Udate DB
            if (!replPolicyTopologies.isEmpty()) {
                logger.info("Modified {} topologies ", replPolicyTopologies.size());
                dbClient.updateObject(replPolicyTopologies);
            }
        } catch (Exception ex) {
            logger.error("Exception occured while migrating file replication policy topology ");
            logger.error(ex.getMessage(), ex);
        }
        logger.info("File replication policy to update it's target pool in topology migration END");
    }

    private Map<String, FilePolicy> getRemoteReplicationPolicies() {
        Map<String, FilePolicy> remoteReplPolicies = new HashMap<String, FilePolicy>();
        List<URI> policies = dbClient.queryByType(FilePolicy.class, true);
        List<FilePolicy> filePolicies = dbClient.queryObject(FilePolicy.class, policies);
        for (FilePolicy policy : filePolicies) {
            if (policy != null && policy.getFilePolicyType().equalsIgnoreCase(FilePolicy.FilePolicyType.file_replication.name())
                    && FileReplicationType.REMOTE.name().equalsIgnoreCase(policy.getFileReplicationType())) {
                remoteReplPolicies.put(policy.getFilePolicyName(), policy);
            }
        }
        return remoteReplPolicies;
    }

    private List<FileReplicationTopology> queryDBReplicationTopologies(FilePolicy policy) {
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                    .getFileReplicationPolicyTopologyConstraint(policy.getId());
            List<FileReplicationTopology> topologies = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                    FileReplicationTopology.class,
                    containmentConstraint);
            return topologies;
        } catch (Exception e) {
            logger.error("Error while querying {}", e);
        }

        return null;
    }

}
