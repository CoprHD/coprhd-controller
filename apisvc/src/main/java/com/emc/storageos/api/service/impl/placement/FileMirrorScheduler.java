/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.FileMirrorRecommendation.Target;
import com.emc.storageos.api.service.impl.placement.FileRecommendation.FileType;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileReplicaPolicyTarget;
import com.emc.storageos.db.client.model.FileReplicaPolicyTargetMap;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VolumeTopology;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class FileMirrorScheduler implements Scheduler {
    public final Logger _log = LoggerFactory
            .getLogger(FileMirrorScheduler.class);
    private static final String SCHEDULER_NAME = "filemirror";

    private DbClient _dbClient;
    private StorageScheduler _storageScheduler;
    private FileStorageScheduler _fileScheduler;

    public void setStorageScheduler(final StorageScheduler storageScheduler) {
        _storageScheduler = storageScheduler;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setFileScheduler(FileStorageScheduler fileScheduler) {
        _fileScheduler = fileScheduler;
    }

    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    /**
     * get list mirror recommendation for mirror file shares(local or remote)
     * Select and return one or more storage pools where the filesystem(s) should be created. The
     * placement logic is based on: - varray, only storage devices in the given varray are
     * candidates - destination varrays - vpool, specifies must-meet & best-meet service
     * specifications - access-protocols: storage pools must support all protocols specified in
     * vpool - file replication: if yes, only select storage pools with this capability -
     * best-match, select storage pools which meets desired performance - provision-mode: thick/thin
     * 
     * @param varray
     *            varray requested for source
     * @param project
     *            for the storage
     * @param vpool
     *            vpool requested
     * @param volumeTopology
     *            A reference to a volume topology instance.            
     * @param capabilities
     *            vpool capabilities parameters
     * @return list of Recommendation objects to satisfy the request
     */
    @Override
    public List getRecommendationsForResources(VirtualArray varray, Project project, VirtualPool vpool,
            VolumeTopology volumeTopology, VirtualPoolCapabilityValuesWrapper capabilities) {

        List<FileRecommendation> recommendations = null;
        if (capabilities.getFileReplicationType().equalsIgnoreCase(VirtualPool.FileReplicationType.REMOTE.name())) {
            recommendations = getRemoteMirrorRecommendationsForResources(varray, project, vpool, capabilities);
        } else {
            recommendations = getLocalMirrorRecommendationsForResources(varray, project, vpool, capabilities);
        }
        return recommendations;

    }

    private PolicyStorageResource findMatchedPolicyStorageResource(List<PolicyStorageResource> storageSystemResources,
            FileRecommendation sourceFileRecommendation) {
        for (PolicyStorageResource strRes : storageSystemResources) {
            if (sourceFileRecommendation.getSourceStorageSystem().equals(strRes.getStorageSystem())) {
                if (sourceFileRecommendation.getvNAS() != null
                        && sourceFileRecommendation.getvNAS().equals(strRes.getNasServer())) {
                    return strRes;
                } else if (strRes.getNasServer() != null && strRes.getNasServer().toString().contains("PhysicalNAS")) {
                    return strRes;
                }
            }
        }
        return null;
    }

    private void findAndUpdateMatchedPolicyStorageResource(List<PolicyStorageResource> storageSystemResources,
            FileRecommendation sourceFileRecommendation, VirtualPoolCapabilityValuesWrapper capabilities) {

        PolicyStorageResource matchedPolicyResource = findMatchedPolicyStorageResource(storageSystemResources, sourceFileRecommendation);
        if (matchedPolicyResource != null) {
            _log.info("Found the valid existing policy storage resource for system {} nas server {}",
                    matchedPolicyResource.getStorageSystem(), matchedPolicyResource.getNasServer());
            FileReplicaPolicyTargetMap targetMap = matchedPolicyResource.getFileReplicaPolicyTargetMap();
            if (targetMap != null && !targetMap.isEmpty()) {
                for (FileReplicaPolicyTarget target : targetMap.values()) {
                    if (target.getNasServer() != null && target.getStorageSystem() != null) {
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.TARGET_NAS_SERVER,
                                URI.create(target.getNasServer()));
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.TARGET_STORAGE_SYSTEM,
                                URI.create(target.getStorageSystem()));
                        break;
                    }
                }
            }
        }
        return;
    }

    /* local mirror related functions */
    /**
     * get list Recommendation for Local Mirror
     * 
     * @param vArray
     * @param project
     * @param vPool
     * @param capabilities
     * @return
     */
    public List getRemoteMirrorRecommendationsForResources(VirtualArray vArray,
            Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        List<FileRecommendation> targetFileRecommendations = null;
        List<FileMirrorRecommendation> fileMirrorRecommendations = new ArrayList<FileMirrorRecommendation>();
        // Get the source file system recommendations!!!
        capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_SOURCE);

        // Verify the replication policy was applied!!
        // Get the policy storage resources for applied policy
        // Choose the right target for the source
        List<PolicyStorageResource> storageSystemResources = null;
        if (!capabilities.isVpoolProjectPolicyAssign()) {
            storageSystemResources = FileOrchestrationUtils.getFilePolicyStorageResources(_dbClient, vPool, project,
                    null);
            if (storageSystemResources != null && !storageSystemResources.isEmpty()) {
                _log.info("Found replication policy for vpool and project, so get all source recommedation to match target");
                capabilities.put(VirtualPoolCapabilityValuesWrapper.GET_ALL_SOURCE_RECOMMENDATIONS, true);
            }
        }

        List<FileRecommendation> sourceFileRecommendations = new ArrayList<FileRecommendation>();
        // For vPool change get the recommendations from source file system!!!
        if (capabilities.createMirrorExistingFileSystem()) {
            sourceFileRecommendations = getFileRecommendationsForSourceFS(vArray, vPool, capabilities);
            // Remove the source file system from capabilities list
            // otherwise, try to find the remote pools from the same source system!!!
            capabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.SOURCE_STORAGE_SYSTEM);
        } else {
            // Get the recommendation for source from vpool!!!
            sourceFileRecommendations = _fileScheduler.getRecommendationsForResources(vArray, project, vPool,
                    new VolumeTopology(), capabilities);
            // Remove the source storage system from capabilities list
            // otherwise, try to find the remote pools from the same source system!!!
            if (capabilities.getFileProtectionSourceStorageDevice() != null) {
                capabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.FILE_PROTECTION_SOURCE_STORAGE_SYSTEM);
            }
        }

        if (capabilities.getAllSourceRecommnedations()) {
            capabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.GET_ALL_SOURCE_RECOMMENDATIONS);
        }

        // Process the each recommendations for targets
        for (FileRecommendation sourceFileRecommendation : sourceFileRecommendations) {
            String srcSystemType = sourceFileRecommendation.getDeviceType();
            Set<String> systemTypes = new StringSet();
            systemTypes.add(srcSystemType);

            // Based on the source recommendation nas server, should pick the right target nas server.
            // Both source and target nas servers should be similar.
            // If sourceFileRecommendation.getvNAS() is null means, the recommendation is for physical nas server!!
            // Remove the existing source nas server if any!!
            if (capabilities.getSourceVirtualNasServer() != null) {
                capabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.SOURCE_VIRTUAL_NAS_SERVER);
            }
            // add the new vnas server of current source recommendations!!!
            if (sourceFileRecommendation.getvNAS() != null) {
                capabilities.put(VirtualPoolCapabilityValuesWrapper.SOURCE_VIRTUAL_NAS_SERVER, sourceFileRecommendation.getvNAS());
            }
            // Findout is there any policy was created for the source recommendations!!!
            if (capabilities.getTargetNasServer() != null) {
                capabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.TARGET_NAS_SERVER);
            }
            if (capabilities.getTargetStorageSystem() != null) {
                capabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.TARGET_STORAGE_SYSTEM);
            }
            if (storageSystemResources != null && !storageSystemResources.isEmpty()) {
                findAndUpdateMatchedPolicyStorageResource(storageSystemResources, sourceFileRecommendation, capabilities);
            }

            for (String targetVArry : capabilities.getFileReplicationTargetVArrays()) {
                // Process for target !!!
                FileMirrorRecommendation fileMirrorRecommendation = new FileMirrorRecommendation(sourceFileRecommendation);
                VirtualPool targetVPool = _dbClient.queryObject(VirtualPool.class, capabilities.getFileReplicationTargetVPool());
                VirtualArray targetVArray = _dbClient.queryObject(VirtualArray.class, URI.create(targetVArry));
                // Filter the target storage pools!!!
                Map<String, Object> attributeMap = new HashMap<String, Object>();

                attributeMap.put(Attributes.system_type.toString(), systemTypes);
                attributeMap.put(Attributes.remote_copy_mode.toString(), capabilities.getFileRpCopyMode());
                attributeMap.put(Attributes.source_storage_system.name(), sourceFileRecommendation.getSourceStorageSystem().toString());

                capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY,
                        VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET);

                // Get target recommendations!!!
                targetFileRecommendations = _fileScheduler.placeFileShare(targetVArray, targetVPool, capabilities, project, attributeMap);
                if (targetFileRecommendations == null || targetFileRecommendations.isEmpty()) {
                    _log.info("No target recommendation found, so ignore the source recommedation as well.");
                    continue;
                }

                String copyMode = capabilities.getFileRpCopyMode();
                if (targetFileRecommendations != null && !targetFileRecommendations.isEmpty()) {
                    // prepare the target recommendation
                    FileRecommendation targetRecommendation = targetFileRecommendations.get(0);
                    prepareTargetFileRecommendation(copyMode,
                            targetVArray, targetRecommendation, fileMirrorRecommendation);

                    fileMirrorRecommendations.add(fileMirrorRecommendation);
                }
            }
            // File file system provisioning
            // Got sufficient number of recommendations!!
            if (!capabilities.isVpoolProjectPolicyAssign() && fileMirrorRecommendations.size() >= capabilities.getResourceCount()) {
                break;
            }
        }
        return fileMirrorRecommendations;
    }

    /* local mirror related functions */
    /**
     * get list Recommendation for Local Mirror
     * 
     * @param vArray
     * @param project
     * @param vPool
     * @param capabilities
     * @return
     */
    public List getLocalMirrorRecommendationsForResources(VirtualArray vArray,
            Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        List<FileRecommendation> targetFileRecommendations = null;
        List<FileMirrorRecommendation> fileMirrorRecommendations = new ArrayList<FileMirrorRecommendation>();

        List<FileRecommendation> sourceFileRecommendations = new ArrayList<FileRecommendation>();
        // For vPool change get the recommendations from source file system!!!
        if (capabilities.createMirrorExistingFileSystem()) {
            sourceFileRecommendations = getFileRecommendationsForSourceFS(vArray, vPool, capabilities);
        } else {
            // Get the recommendation for source from vpool!!!
            sourceFileRecommendations = _fileScheduler.getRecommendationsForResources(vArray, project, vPool,
                    new VolumeTopology(), capabilities);
        }

        // process the each recommendations for targets
        for (FileRecommendation sourceFileRecommendation : sourceFileRecommendations) {
            // set the source file recommendation
            FileMirrorRecommendation fileMirrorRecommendation = new FileMirrorRecommendation(sourceFileRecommendation);

            // attribute map of target storagesystem and varray
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Set<String> storageSystemSet = new HashSet<String>();
            storageSystemSet.add(sourceFileRecommendation.getSourceStorageSystem().toString());
            attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), storageSystemSet);

            Set<String> virtualArraySet = new HashSet<String>();
            virtualArraySet.add(vArray.getId().toString());
            attributeMap.put(AttributeMatcher.Attributes.varrays.name(), virtualArraySet);

            // get target recommendations -step2
            targetFileRecommendations = _fileScheduler.placeFileShare(vArray, vPool, capabilities, project, attributeMap);

            String copyMode = capabilities.getFileRpCopyMode();
            if (targetFileRecommendations != null && !targetFileRecommendations.isEmpty()) {
                // prepare the target recommendation
                FileRecommendation targetRecommendation = targetFileRecommendations.get(0);
                prepareTargetFileRecommendation(copyMode,
                        vArray, targetRecommendation, fileMirrorRecommendation);

                fileMirrorRecommendations.add(fileMirrorRecommendation);
            }

        }
        return fileMirrorRecommendations;
    }

    private void prepareTargetFileRecommendation(final String fsCopyMode, final VirtualArray targetVarray,
            FileRecommendation targetFileRecommendation,
            FileMirrorRecommendation fileMirrorRecommendation) {

        // Set target recommendations!!!
        Target target = new Target();
        target.setTargetPool(targetFileRecommendation.getSourceStoragePool());
        target.setTargetStorageDevice(targetFileRecommendation.getSourceStorageSystem());
        if (targetFileRecommendation.getStoragePorts() != null) {
            target.setTargetStoragePortUris(targetFileRecommendation.getStoragePorts());
        }

        if (targetFileRecommendation.getvNAS() != null) {
            target.setTargetvNASURI(targetFileRecommendation.getvNAS());
        }

        if (fileMirrorRecommendation.getVirtualArrayTargetMap() == null) {
            fileMirrorRecommendation.setVirtualArrayTargetMap(new HashMap<URI, FileMirrorRecommendation.Target>());
        }
        fileMirrorRecommendation.getVirtualArrayTargetMap().put(targetVarray.getId(), target);
        // File replication copy mode
        fileMirrorRecommendation.setCopyMode(fsCopyMode);
    }

    /**
     * Gets and verifies that the target varrays passed in the request are accessible to the tenant.
     * 
     * @param project
     *            A reference to the project.
     * @param vpool
     *            class of service, contains target varrays
     * @return A reference to the varrays
     * @throws java.net.URISyntaxException
     * @throws com.emc.storageos.db.exceptions.DatabaseException
     */
    public static List<VirtualArray> getTargetVirtualArraysForVirtualPool(final Project project,
            final VirtualPool vpool, final DbClient dbClient,
            final PermissionsHelper permissionHelper) {
        List<VirtualArray> targetVirtualArrays = new ArrayList<VirtualArray>();
        if (VirtualPool.getFileRemoteProtectionSettings(vpool, dbClient) != null) {
            for (URI targetVirtualArray : VirtualPool.getFileRemoteProtectionSettings(vpool, dbClient)
                    .keySet()) {
                VirtualArray nh = dbClient.queryObject(VirtualArray.class, targetVirtualArray);
                targetVirtualArrays.add(nh);
                permissionHelper.checkTenantHasAccessToVirtualArray(
                        project.getTenantOrg().getURI(), nh);
            }
        }
        return targetVirtualArrays;
    }

    @Override
    public List<Recommendation> getRecommendationsForVpool(VirtualArray vArray, Project project, VirtualPool vPool,
            VolumeTopology volumeTopology, VpoolUse vPoolUse, VirtualPoolCapabilityValuesWrapper capabilities, 
            Map<VpoolUse, List<Recommendation>> currentRecommendations) {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    private FileRecommendation getSourceRecommendationParameters(FileShare sourceFs, StorageSystem storageSystem) {

        FileRecommendation fileRecommendation = new FileRecommendation();
        fileRecommendation.setSourceStorageSystem(sourceFs.getStorageDevice());
        fileRecommendation.setFileType(FileType.FILE_SYSTEM_EXISTING_SOURCE);
        fileRecommendation.setVirtualArray(sourceFs.getVirtualArray());
        fileRecommendation.setDeviceType(storageSystem.getSystemType());
        // set vnas Server
        if (sourceFs.getVirtualNAS() != null) {
            fileRecommendation.setvNAS(sourceFs.getVirtualNAS());
        }
        // set the storageports
        if (sourceFs.getStoragePort() != null) {
            List<URI> ports = new ArrayList<URI>();
            ports.add(sourceFs.getStoragePort());
            fileRecommendation.setStoragePorts(ports);
        }
        return fileRecommendation;
    }

    private List<FileRecommendation> getFileRecommendationsForSourceFS(VirtualArray vArray,
            VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {

        List<FileRecommendation> sourceFileRecommendations = new ArrayList<FileRecommendation>();

        // Get the source file system and storage system
        // which was set at in FileService
        // to construct the source recommendations!!
        FileShare sourceFs = capabilities.getSourceFileSystem();
        StorageSystem storageSystem = capabilities.getSourceStorageDevice();

        // Get the Matched pools from target virtual pool
        // Verify that at least a matched pools from source storage system!!!
        List<StoragePool> candidatePools = _storageScheduler.getMatchingPools(vArray,
                vPool, capabilities, null);
        boolean gotMatchedPoolForSource = false;
        for (StoragePool pool : candidatePools) {
            if (pool.getStorageDevice().toString().equalsIgnoreCase(sourceFs.getStorageDevice().toString())) {
                gotMatchedPoolForSource = true;
                break;
            }
        }
        if (!gotMatchedPoolForSource) {
            _log.error("File system vpool change::No matched storage pools from source storage");
            throw APIException.badRequests.noMatchingStoragePoolsForFileSystemVpoolChange(vArray.getId(),
                    vPool.getId());
        }
        sourceFileRecommendations.add(getSourceRecommendationParameters(sourceFs, storageSystem));
        return sourceFileRecommendations;
    }

    @Override
    public String getSchedulerName() {
        return SCHEDULER_NAME;
    }

    @Override
    public boolean handlesVpool(VirtualPool vPool, VpoolUse vPoolUse) {
        return false;
    }
}
