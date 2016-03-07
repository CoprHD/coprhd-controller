/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getDataObject;
import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getVPlexAPIClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.utils.ClusterConsistencyGroupWrapper;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexConsistencyGroupInfo;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class RPVplexConsistencyGroupManager extends AbstractConsistencyGroupManager {

    // logger reference.
    private static final Logger log = LoggerFactory
            .getLogger(RPVplexConsistencyGroupManager.class);

    @Override
    public String addStepsForCreateConsistencyGroup(Workflow workflow, String waitFor,
            StorageSystem vplexSystem, List<URI> vplexVolumeURIs,
            boolean willBeRemovedByEarlierStep) throws ControllerException {
        // No volumes, all done.
        if (vplexVolumeURIs.isEmpty()) {
            log.info("No volumes specified consistency group.");
            return waitFor;
        }

        // Grab the first volume
        Volume firstVolume = getDataObject(Volume.class, vplexVolumeURIs.get(0), dbClient);
        URI cgURI = firstVolume.getConsistencyGroup();

        URI vplexURI = vplexSystem.getId();
        String nextStep = waitFor;

        // Load the ViPR consistency group. This single ViPR consistency group will map to
        // potentially several different VPlex consistency groups.
        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);

        // Create a step to create the CG.
        String stepMsg = String.format(
                "Create and add volumes to VPLEX consistency group. VPLEX: %s (%s) Consistency group: %s (%s) Volumes: %s (%s) ",
                vplexSystem.getLabel(), vplexURI, cg.getLabel(), cg.getId(),
                getVolumeLabels(vplexVolumeURIs), StringUtils.collectionToCommaDelimitedString(vplexVolumeURIs));
        nextStep = workflow.createStep(CREATE_CG_STEP, stepMsg,
                nextStep, vplexURI, vplexSystem.getSystemType(), this.getClass(),
                createCGMethod(vplexURI, cgURI, vplexVolumeURIs),
                createRemoveVolumesFromCGMethod(vplexURI, cgURI, vplexVolumeURIs), null);
        log.info("Created step for consistency group creation and add volumes.");

        return nextStep;
    }

    private String getVolumeLabels(Collection<URI> volUris) {
        List<String> labels = new ArrayList<String>();
        Iterator<Volume> volItr = dbClient.queryIterativeObjects(Volume.class, volUris);
        while (volItr.hasNext()) {
            labels.add(volItr.next().getLabel());
        }
        return StringUtils.collectionToCommaDelimitedString(labels);
    }

    /**
     * A method the creates the method to create a new VPLEX consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group to be created.
     * @param vplexVolumeURI The URI of the VPLEX volume that will be used
     *            to determine if a consistency group will be created and where.
     * 
     * @return A reference to the consistency group creation workflow method.
     */
    private Workflow.Method createCGMethod(URI vplexURI, URI cgURI, Collection<URI> vplexVolumeURIList) {
        return new Workflow.Method(CREATE_CG_METHOD_NAME, vplexURI, cgURI, vplexVolumeURIList);
    }

    /**
     * Called by the workflow to create a new VPLEX consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the Bourne consistency group
     * @param vplexVolumeURIs The list of URIs of the VPLEX volumes being
     *            added to the vplex CG
     * @param stepId The workflow step id.
     * 
     * @throws WorkflowException When an error occurs updating the workflow step
     *             state.
     */
    public void createCG(URI vplexURI, URI cgURI, Collection<URI> vplexVolumeURIs, String stepId)
            throws WorkflowException {
        try {
            // Update workflow step.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated step state for consistency group creation to execute.");

            if (vplexVolumeURIs == null || vplexVolumeURIs.isEmpty()) {
                log.info("empty volume list; no CG will be created");
                // Update workflow step state to success.
                WorkflowStepCompleter.stepSucceded(stepId);
                log.info("Updated workflow step for consistency group creation to success.");
                return;
            }

            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.debug("Got VPLEX API client.");

            // For the following cases we need special steps for the CG to choose the HA side/leg on the VPLEX to be the winner:
            // 1. In an RP+VPLEX distributed setup, the user can choose to protect only the HA side.
            // 2. In a MetroPoint setup, the user can choose the HA side as the Active side.
            Volume firstVplexVolume = getDataObject(Volume.class, vplexVolumeURIs.iterator().next(), dbClient);
            if (firstVplexVolume != null && NullColumnValueGetter.isNotNullValue(firstVplexVolume.getPersonality()) &&
                    firstVplexVolume.getPersonality().equals(PersonalityTypes.SOURCE.toString())) {
                VirtualPool vpool = getDataObject(VirtualPool.class, firstVplexVolume.getVirtualPool(), dbClient);
                boolean haIsWinningCluster = VirtualPool.isRPVPlexProtectHASide(vpool)
                        || (vpool.getMetroPoint() && NullColumnValueGetter.isNotNullValue(vpool.getHaVarrayConnectedToRp()));

                if (haIsWinningCluster) {
                    log.info("Force HA side as winning cluster for VPLEX CG.");
                    // Temporarily change the varray to the HA varray.
                    // NOTE: Do not persist!
                    firstVplexVolume.setLabel("DO NOT PERSIST THIS VOLUME");
                    firstVplexVolume.setVirtualArray(URI.create(vpool.getHaVarrayConnectedToRp()));
                }
            }

            // acquire a lock to serialize create cg requests to the VPLEX
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getConsistencyGroupStorageKey(dbClient, cgURI, vplexURI));
            workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.RP_VPLEX_CG));

            // Get the consistency group
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);

            // group the volumes by the cluster/CG that they will go in
            Map<String, List<URI>> cgToVolListMap = new HashMap<String, List<URI>>();
            for (URI vplexVolumeURI : vplexVolumeURIs) {

                Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);

                // Lets determine the VPlex consistency group that need to be created for this volume.
                ClusterConsistencyGroupWrapper clusterConsistencyGroup =
                        getClusterConsistencyGroup(vplexVolume, cg);

                String cgName = clusterConsistencyGroup.getCgName();
                String clusterName = clusterConsistencyGroup.getClusterName();
                boolean isDistributed = clusterConsistencyGroup.isDistributed();

                String cgKey = String.format("%s:%s:%s", cgName, clusterName, (isDistributed ? "dist" : "local"));
                if (!cgToVolListMap.containsKey(cgKey)) {
                    cgToVolListMap.put(cgKey, new ArrayList<URI>());
                }
                cgToVolListMap.get(cgKey).add(vplexVolumeURI);
            }

            // loop through each cluster/CG; create the CG and add the volumes
            for (Entry<String, List<URI>> entry : cgToVolListMap.entrySet()) {

                String[] elems = StringUtils.delimitedListToStringArray(entry.getKey(), ":");
                if (elems.length != 3) {
                    // serious coding error see above loop which creates the key
                    log.error("Error in vplex cg mapping key. Expect <cgname>:<clustername>:<dist|local>; got: " + entry.getKey());
                    continue;
                }

                String cgName = elems[0];
                String clusterName = elems[1];
                boolean isDistributed = elems[2].equals("dist");

                // Verify that the VPlex CG has been created for this VPlex system and cluster.
                if (!BlockConsistencyGroupUtils.isVplexCgCreated(cg, vplexURI.toString(), clusterName, cgName, isDistributed)) {
                    createVplexCG(vplexSystem, client, cg, firstVplexVolume, cgName, clusterName, isDistributed);
                } else {
                    modifyCGSettings(client, cgName, clusterName, isDistributed);
                }

                addVolumesToCG(cgURI, entry.getValue(), cgName, clusterName, client);
            }

            // Update workflow step state to success.
            WorkflowStepCompleter.stepSucceded(stepId);
            log.info("Updated workflow step for consistency group creation to success.");
        } catch (Exception ex) {
            log.error("Exception creating consistency group: " + ex.getMessage(), ex);
            ServiceError serviceError = VPlexApiException.errors.jobFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * @param client
     * @param cgName
     * @param clusterName
     * @param isDistributed
     */
    private void modifyCGSettings(VPlexApiClient client, String cgName, String clusterName, boolean isDistributed) {
        log.info(String.format("VPlex consistency group %s already exists on cluster %s.", cgName, clusterName));

        // See if the CG is created but contains no volumes. This CG may have existed
        // previously for other volumes, but the volumes were deleted and removed from the CG.
        // The visibility and cluster info would have been set for those volumes and may not be
        // appropriate for these volumes.
        boolean cgContainsVolumes = false;

        // This is not ideal but we need to call the VPlex client to fetch all consistency
        // groups so we can find the one we are looking for. We need to see if there are
        // any associated virtual volumes. The BlockServiceApiImpl code associates the volumes
        // with the BlockConsistencyGroup even though these volumes are not yet part of
        // the VPlex consistency group. Might be worth changing this logic.
        List<VPlexConsistencyGroupInfo> consistencyGroupsInfo = client.getConsistencyGroups();
        for (VPlexConsistencyGroupInfo consistencyGroupInfo : consistencyGroupsInfo) {
            if (consistencyGroupInfo.getName().equals(cgName)
                    && consistencyGroupInfo.getClusterName().equals(clusterName)) {
                if (consistencyGroupInfo.getVirtualVolumes() != null
                        && !consistencyGroupInfo.getVirtualVolumes().isEmpty()) {
                    cgContainsVolumes = true;
                }
                break;
            }
        }

        if (!cgContainsVolumes) {
            log.info("Updating VPLEX consistency group properties.");
            // Now we can update the consistency group properties.
            client.updateConsistencyGroupProperties(cgName, clusterName, isDistributed);
            log.info("Updated VPLEX consistency group properties.");
        }
    }

    /**
     * @param vplexSystem
     * @param client
     * @param cg
     * @param firstVplexVolume
     * @param cgName
     * @param clusterName
     * @param isDistributed
     */
    private void createVplexCG(StorageSystem vplexSystem, VPlexApiClient client, BlockConsistencyGroup cg, Volume firstVplexVolume,
            String cgName,
            String clusterName, boolean isDistributed) {
        log.info(String.format("Creating VPlex consistency group %s on cluster %s.", cgName, clusterName));
        // Create the VPlex consistency group
        client.createConsistencyGroup(cgName, clusterName, isDistributed);
        log.info(String.format("Created VPlex consistency group %s on cluster %s.", cgName, clusterName));

        // Enable the VPLEX CG with "recoverpoint-enabled" flag if the volumes are RP protected.
        log.info("VplexDeviceController : Update the CG with RP enabled flag");
        client.updateConsistencyRPEnabled(cgName, clusterName, true);
        log.info("VplexDeviceController : Done update of the CG with RP enabled flag");

        // Find the associated RP source volume so we can properly set the virtual array and
        // storage controller references. We only want to set these based off the source volume.
        // NOTE: We will hit this code for both the source and target VPlex storage systems in the
        // case we have remote VPlex protection. This will simply overwrite the existing
        // value for storageController which isn't an issue.
        Volume sourceVolume = RPHelper.getRPSourceVolume(dbClient, firstVplexVolume);

        if (sourceVolume != null) {
            if (cg.getVirtualArray() == null) {
                // Set the virtual array for the CG based off the source volume.
                cg.setVirtualArray(sourceVolume.getVirtualArray());
            }

            URI sourceStorageSystem = sourceVolume.getStorageController();

            if (sourceStorageSystem != null) {
                // The source virtual array storage controller must be referenced by the
                // BlockConsistencyGroup or else a failure will occur because the source virtual
                // array does not reference any ports of the target storage controller. This
                // failure will occur in the BlockService where we validate the BlockConsistency
                // group against the source virtual array.
                cg.setStorageController(sourceStorageSystem);
            }
        }

        if (isDistributed) {
            // Since this is a distributed volume, ensure there is a CG entry for each cluster
            cg.addSystemConsistencyGroup(vplexSystem.getId().toString(),
                    BlockConsistencyGroupUtils.buildClusterCgName(BlockConsistencyGroupUtils.CLUSTER_1, cgName));
            cg.addSystemConsistencyGroup(vplexSystem.getId().toString(),
                    BlockConsistencyGroupUtils.buildClusterCgName(BlockConsistencyGroupUtils.CLUSTER_2, cgName));
        } else {
            cg.addSystemConsistencyGroup(vplexSystem.getId().toString(),
                    BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName));
        }
        
        cg.addConsistencyGroupTypes(Types.VPLEX.name());
        cg.addConsistencyGroupTypes(Types.RP.name());
        dbClient.persistObject(cg);
        log.info("Updated consistency group in DB.");
    }

    /**
     * adds a list of volumes to a vplex cg
     * 
     * @param cgURI
     * @param vplexVolumeURIList
     * @param cgName
     * @param clusterName
     * @param client
     */
    private void addVolumesToCG(URI cgURI, Collection<URI> vplexVolumeURIList, String cgName, String clusterName, VPlexApiClient client) {
        // Get the names of the volumes to be added.
        List<String> vplexVolumeNames = new ArrayList<String>();
        List<Volume> vplexVolumes = new ArrayList<Volume>();
        for (URI vplexVolumeURI : vplexVolumeURIList) {
            Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
            if (vplexVolume == null || vplexVolume.getInactive()) {
                log.error(String.format("skipping null or inactive vplex volume %s", vplexVolumeURI.toString()));
                continue;
            }
            vplexVolume.setConsistencyGroup(cgURI);
            vplexVolumes.add(vplexVolume);
            log.info(String.format("Adding VPLEX volume: %s (device label %s) to CG %s on cluster %s",
                    vplexVolume.getNativeId(), vplexVolume.getDeviceLabel(), cgName, clusterName));
            vplexVolumeNames.add(vplexVolume.getDeviceLabel());
        }

        // Add the volumes to the CG.
        client.addVolumesToConsistencyGroup(cgName, vplexVolumeNames);
        log.info("Added volumes to consistency group.");

        dbClient.updateObject(vplexVolumes);
    }

    /**
     * Maps a VPlex cluster/consistency group to its volumes.
     * 
     * @param vplexVolume The virtual volume from which to obtain the VPlex cluster.
     * @param clusterConsistencyGroupVolumes The map to store cluster/cg/volume relationships.
     * @param cgName The VPlex consistency group name.
     * @throws Exception
     */
    @Override
    public ClusterConsistencyGroupWrapper getClusterConsistencyGroup(Volume vplexVolume, BlockConsistencyGroup cg) 
            throws Exception {
        ClusterConsistencyGroupWrapper clusterConsistencyGroup = new ClusterConsistencyGroupWrapper();
        String vplexCgName = null;
        // Find the correct VPLEX cluster for this volume
        String vplexCluster = VPlexControllerUtils.getVPlexClusterName(dbClient, vplexVolume);
        // Determine if the volume is distributed
        boolean distributed = false;
        StringSet assocVolumes = vplexVolume.getAssociatedVolumes();                
        if (assocVolumes.size() > 1) {            
            distributed = true;
        }
        // Keep a reference to the VPLEX
        URI vplexURI = vplexVolume.getStorageController();
        
        log.info(String.format("Find correct VPLEX CG for VPLEX %s volume [%s](%s) at cluster [%s] on VPLEX (%s)", 
                (distributed ? "distribitued" : "local"), vplexVolume.getLabel(), 
                vplexVolume.getId(), vplexCluster, vplexURI));
                                
        // Check to see if the CG name already exists...
        // First: Let's try to see if the CG value has been populated on the volume
        // Second: Manually try and line up the CG name from the ViPR CG to the VPLEX CGs
        if (NullColumnValueGetter.isNotNullValue(vplexVolume.getReplicationGroupInstance())) {              
            vplexCgName = BlockConsistencyGroupUtils.fetchCgName(vplexVolume.getReplicationGroupInstance());
            log.info(String.format("CG name already set on volume: %s", vplexCgName));
        } else if (cg.created(vplexURI)) {
            log.info("CG already exists on VPLEX, but we need to figure out the correct one to use...");
            List<String> validVPlexCGsForCluster = new ArrayList<String>();

            // Extract all the CG names for the VPLEX
            // These names are in the format of: cluster-1:cgName or cluster-2:cgName
            StringSet cgNames = cg.getSystemConsistencyGroups().get(vplexURI.toString());
            
            // Iterate over the names to line up with the cluster
            Iterator<String> cgNamesIter = cgNames.iterator();
            while (cgNamesIter.hasNext()) {          
                String clusterCgName = cgNamesIter.next();                
                String cluster = BlockConsistencyGroupUtils.fetchClusterName(clusterCgName);
                String cgName = BlockConsistencyGroupUtils.fetchCgName(clusterCgName);
                
                // If the clusters match or if this is a distributed volume,
                // add it to the list of valid CGs.
                // NOTE: A distributed CG lives on both clusters 1 and 2 so we can't
                //       narrow down the CGs based on cluster so we need all CGs.
                //       We will narrow this down afterwards for distributed.
                if (vplexCluster.equals(cluster) || distributed) {
                    validVPlexCGsForCluster.add(cgName);
                }
            }
            
            // Get the API client.
            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.info("Got VPLEX API client.");
            
            // This is not ideal but we need to call the VPlex client to fetch all consistency
            // groups so we can find the exact one we are looking for.
            List<VPlexConsistencyGroupInfo> vplexCGs = client.getConsistencyGroups();
            log.info("Iterating over returned VPLEX CGs to find the one we should use...");
            for (VPlexConsistencyGroupInfo vplexCG : vplexCGs) {
                boolean cgNameFound = validVPlexCGsForCluster.contains(vplexCG.getName());   
                boolean volumeFound = vplexCG.getVirtualVolumes().contains(vplexVolume.getDeviceLabel());
                // The CG Names will match or we might find the volume in the VPLEX CG,
                // either way it's valid.
                if (cgNameFound || volumeFound) {
                    // Determine if the VPLEX CG is distributed.
                    // NOTE: VPlexConsistencyGroupInfo.isDistributed() is not returning
                    //       the correct information. This is probably due to teh fact that
                    //       visibleClusters is not being returned in the response.
                    //       Instead we are checking getStorageAtClusters().size(). 
                    boolean vplexCGIsDistributed = (vplexCG.getStorageAtClusters().size() > 1);                    
                    if ((distributed && vplexCGIsDistributed)
                            || (!distributed && !vplexCGIsDistributed)) {                    
                       log.info(String.format("Found correct VPLEX CG: %s", vplexCG.getName()));
                       vplexCgName = vplexCG.getName();
                       break;
                   } 
                }
            }                                   
        }  
        
        // If the CG name is still null at this point, we will create it.
        // The format for VPLEX distributed: cgName-dist
        // The format for VPLEX local: cgName-vplexCluster
        if (vplexCgName == null) {            
            // If we do not have a VPLEX CG name it's time to create one.
            if (distributed) {
                // Add '-dist' to the end of the CG name for distributed consistency groups.
                vplexCgName = cg.getLabel() + "-dist";
            } else {
                vplexCgName = cg.getLabel() + "-" + vplexCluster;
            }
            log.info(String.format("There was no existing VPLEX CG, created CG name: %s", vplexCgName));
        }
        
        clusterConsistencyGroup.setClusterName(vplexCluster);
        clusterConsistencyGroup.setCgName(vplexCgName);
        clusterConsistencyGroup.setDistributed(distributed);

        return clusterConsistencyGroup;
    }
}
