/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyPriority;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FileReplicaPolicyTarget;
import com.emc.storageos.db.client.model.FileReplicaPolicyTargetMap;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * File Protection policies are introduced in 3.6 release.
 * Virtual pool replication attributes are moved to file protection policy, Hence
 * 
 * 1. Replication attributes should be converted to FilePolicy(replication).
 * 2. If there are any file systems with the replication vpool,
 * policy storage resources has to create between policy and file system resource.
 * 
 * 
 */
public class VirtualPoolFileReplicationPolicyMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VirtualPoolFileReplicationPolicyMigration.class);

    public static final String FILE_STORAGE_RESOURCE = "FILESTORAGERESOURCE";
    public static final String FILE_STORAGE_DEVICE_TYPE = "ISILON";
    public static final String SEPARATOR = "::";

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("File Virtual pool to file replication policy migration START");

        DbClient dbClient = getDbClient();
        try {
            List<URI> virtualPoolURIs = dbClient.queryByType(VirtualPool.class, true);
            Iterator<VirtualPool> virtualPools = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolURIs, true);
            List<VirtualPool> modifiedVpools = new ArrayList<VirtualPool>();
            List<FilePolicy> replPolicies = new ArrayList<FilePolicy>();

            // Create replication policy from vpool replication attributes
            // Identify any file systems created with the replication vpool.
            // Establish relation from policy to FilePolicyResource
            while (virtualPools.hasNext()) {
                VirtualPool virtualPool = virtualPools.next();
                if (VirtualPool.Type.file.name().equals(virtualPool.getType())
                        && virtualPool.getFileReplicationType() != null
                        && !FileReplicationType.NONE.name().equalsIgnoreCase(virtualPool.getFileReplicationType())) {

                    logger.info("vpool {} has enabled with replication, Creating appropriate file policy.....", virtualPool.getLabel());
                    // Create replication policy
                    FilePolicy replPolicy = new FilePolicy();
                    replPolicy.setId(URIUtil.createId(FilePolicy.class));
                    replPolicy.setFilePolicyDescription(
                            "Policy created from virtual pool " + virtualPool.getLabel() + " while system upgrade");
                    String polName = virtualPool.getLabel() + "_Replication_Policy";
                    replPolicy.setLabel(polName);
                    replPolicy.setFilePolicyName(polName);
                    replPolicy.setLabel(polName);
                    replPolicy.setFilePolicyType(FilePolicyType.file_replication.name());
                    replPolicy.setFilePolicyVpool(virtualPool.getId());
                    // Replication policy was created always at file system level!!
                    replPolicy.setApplyAt(FilePolicyApplyLevel.file_system.name());
                    if (virtualPool.getFileReplicationCopyMode().equals(VirtualPool.RPCopyMode.ASYNCHRONOUS.name())) {
                        replPolicy.setFileReplicationCopyMode(FilePolicy.FileReplicationCopyMode.ASYNC.name());
                    } else {
                        replPolicy.setFileReplicationCopyMode(FilePolicy.FileReplicationCopyMode.SYNC.name());
                    }
                    replPolicy.setFileReplicationType(virtualPool.getFileReplicationType());
                    replPolicy.setPriority(FilePolicyPriority.Normal.toString());

                    // Set the policy schedule based on vPool RPO
                    if (virtualPool.getFrRpoValue() != null && virtualPool.getFrRpoType() != null) {
                        replPolicy.setScheduleRepeat((long) virtualPool.getFrRpoValue());
                        replPolicy.setScheduleTime("00:00AM");
                        replPolicy.setScheduleFrequency(virtualPool.getFrRpoType().toLowerCase());
                        // Virtual pool was supporting only Minutes/Hours/Days for RPO type
                        // Day of the week and month is not applicable!!
                        replPolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                        replPolicy.setScheduleDayOfMonth(0L);
                    }

                    // Create replication topology
                    // set topology reference to policy
                    if (FileReplicationType.REMOTE.name().equalsIgnoreCase(virtualPool.getFileReplicationType())) {
                        logger.info("Creating replication topology for remote replication vpool {} .....", virtualPool.getLabel());
                        StringSet replicationTopologies = new StringSet();
                        StringSet targetVarrays = new StringSet();
                        String targetVarray = null;
                        String targetVPool = null;
                        Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettings = virtualPool.getFileRemoteProtectionSettings(
                                virtualPool,
                                dbClient);
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
                            if (targetVarray != null) {
                                targetVarrays.add(targetVarray);
                            }

                        }

                        if (virtualPool.getVirtualArrays() != null && !virtualPool.getVirtualArrays().isEmpty()) {
                            for (String srcvArray : virtualPool.getVirtualArrays()) {
                                FileReplicationTopology dbReplTopology = new FileReplicationTopology();
                                dbReplTopology.setId(URIUtil.createId(FileReplicationTopology.class));
                                dbReplTopology.setPolicy(replPolicy.getId());
                                dbReplTopology.setSourceVArray(URI.create(srcvArray));
                                dbReplTopology.setTargetVArrays(targetVarrays);
                                if (targetVarray != null && targetVPool != null) {
                                    dbReplTopology.setTargetVAVPool(targetVarray + SEPARATOR + targetVPool);
                                }
                                dbClient.createObject(dbReplTopology);
                                replicationTopologies.add(dbReplTopology.getId().toString());
                            }
                            replPolicy.setReplicationTopologies(replicationTopologies);
                            logger.info("Created {} replication topologies from vpool {}", replicationTopologies.size(),
                                    virtualPool.getLabel().toString());
                        }
                    }

                    // Fetch if there are any file system were provisioned with the vpool
                    // if present, link them to replication policy!!
                    URIQueryResultList resultList = new URIQueryResultList();
                    dbClient.queryByConstraint(
                            ContainmentConstraint.Factory.getVirtualPoolFileshareConstraint(virtualPool.getId()), resultList);
                    for (Iterator<URI> fileShareItr = resultList.iterator(); fileShareItr.hasNext();) {
                        FileShare fs = dbClient.queryObject(FileShare.class, fileShareItr.next());
                        if (!fs.getInactive() && fs.getPersonality() != null
                                && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.name())) {
                            StorageSystem system = dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
                            updatePolicyStorageResouce(system, replPolicy, fs);
                            fs.addFilePolicy(replPolicy.getId());
                            dbClient.updateObject(fs);
                        }

                    }

                    replPolicies.add(replPolicy);
                    virtualPool.setAllowFilePolicyAtFSLevel(true);
                    virtualPool.setFileReplicationSupported(true);
                    modifiedVpools.add(virtualPool);
                }
            }
            // Udate DB
            if (!replPolicies.isEmpty()) {
                logger.info("Created {} replication policies ", replPolicies.size());
                dbClient.createObject(replPolicies);
            }

            if (!modifiedVpools.isEmpty()) {
                logger.info("Modified {} vpools ", modifiedVpools.size());
                dbClient.updateObject(modifiedVpools);
            }
        } catch (Exception ex) {
            logger.error("Exception occured while migrating file replication policy for Virtual pools");
            logger.error(ex.getMessage(), ex);
        }
        logger.info("Virtual pool file replication policy migration END");
    }

    private PhysicalNAS getSystemPhysicalNAS(StorageSystem system) {
        List<URI> nasServers = dbClient.queryByType(PhysicalNAS.class, true);
        List<PhysicalNAS> phyNasServers = dbClient.queryObject(PhysicalNAS.class, nasServers);
        for (PhysicalNAS nasServer : phyNasServers) {
            if (nasServer.getStorageDeviceURI().toString().equalsIgnoreCase(system.getId().toString())) {
                return nasServer;
            }
        }
        return null;
    }

    private String generateNativeGuidForFilePolicyResource(StorageSystem device, String nasServer, String policyType,
            String path) {
        return String.format("%s+%s+%s+%s+%s+%s", FILE_STORAGE_DEVICE_TYPE, device.getSerialNumber(), FILE_STORAGE_RESOURCE,
                nasServer, policyType, path);
    }

    private void updatePolicyStorageResouce(StorageSystem system, FilePolicy filePolicy, FileShare fs) {

        logger.info("Creating policy storage resource for storage {} fs {} and policy {} ", system.getLabel(), fs.getLabel(),
                filePolicy.getFilePolicyName());
        PolicyStorageResource policyStorageResource = new PolicyStorageResource();
        policyStorageResource.setId(URIUtil.createId(PolicyStorageResource.class));
        policyStorageResource.setFilePolicyId(filePolicy.getId());
        policyStorageResource.setStorageSystem(system.getId());
        policyStorageResource.setPolicyNativeId(fs.getName());
        policyStorageResource.setAppliedAt(fs.getId());
        policyStorageResource.setResourcePath(fs.getNativeId());
        NASServer nasServer = null;
        if (fs.getVirtualNAS() != null) {
            nasServer = dbClient.queryObject(VirtualNAS.class, fs.getVirtualNAS());
        } else {
            // Get the physical NAS for the storage system!!
            PhysicalNAS pNAS = getSystemPhysicalNAS(system);
            if (pNAS != null) {
                nasServer = pNAS;
            }
        }
        if (nasServer != null) {
            logger.info("Found NAS server {} ", nasServer.getNasName());
            policyStorageResource.setNasServer(nasServer.getId());
            policyStorageResource.setNativeGuid(generateNativeGuidForFilePolicyResource(system,
                    nasServer.getNasName(), filePolicy.getFilePolicyType(), fs.getNativeId()));
        }

        if (fs.getMirrorfsTargets() != null && !fs.getMirrorfsTargets().isEmpty()) {
            String[] targetFSs = fs.getMirrorfsTargets().toArray(new String[fs.getMirrorfsTargets().size()]);
            // Today we support single target!!
            FileShare fsTarget = dbClient.queryObject(FileShare.class, URI.create(targetFSs[0]));
            // In older release, policy name was set to target file system lable!!
            policyStorageResource.setPolicyNativeId(fsTarget.getLabel());
            // Update the target resource details!!!
            FileReplicaPolicyTargetMap fileReplicaPolicyTargetMap = new FileReplicaPolicyTargetMap();
            FileReplicaPolicyTarget target = new FileReplicaPolicyTarget();

            target.setAppliedAt(filePolicy.getApplyAt());
            target.setStorageSystem(fsTarget.getStorageDevice().toString());
            target.setPath(fsTarget.getNativeId());

            NASServer targetNasServer = null;
            if (fsTarget.getVirtualNAS() != null) {
                targetNasServer = dbClient.queryObject(VirtualNAS.class, fsTarget.getVirtualNAS());
            } else {
                StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, fsTarget.getStorageDevice());
                // Get the physical NAS for the storage system!!
                PhysicalNAS pNAS = getSystemPhysicalNAS(targetSystem);
                if (pNAS != null) {
                    targetNasServer = pNAS;
                }
            }
            if (targetNasServer != null) {
                target.setNasServer(targetNasServer.getId().toString());
            }

            String key = target.getFileTargetReplicaKey();
            fileReplicaPolicyTargetMap.put(key, target);
            policyStorageResource.setFileReplicaPolicyTargetMap(fileReplicaPolicyTargetMap);

        }

        dbClient.createObject(policyStorageResource);

        filePolicy.addPolicyStorageResources(policyStorageResource.getId());
        filePolicy.addAssignedResources(fs.getId());
        logger.info("PolicyStorageResource object created successfully for {} ",
                system.getLabel() + policyStorageResource.getAppliedAt());
    }

}
