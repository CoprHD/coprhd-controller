/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.utils.ClusterConsistencyGroupWrapper;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.workflow.Workflow;

public interface ConsistencyGroupManager {

    /**
     * Adds the workflow steps to create the VPLEX consistency group and add
     * newly created VPLEX volumes to the consistency group.
     * 
     * @param workflow The workflow to which the steps are added.
     * @param waitFor The previous workflow step for which these steps will
     *            wait.
     * @param vplexSystem A reference to the VPLEX storage system.
     * @param vplexVolumeURIs The URIs of the volumes to add to the consistency
     *            group.
     * @param willBeRemovedByEarlierStep true if the CG will be removed by a
     *            prior step in the workflow, false otherwise
     * 
     * @return The workflow step for which any additional steps should wait.
     * 
     * @throws ControllerException When an error occurs configuring the
     *             consistency group workflow steps.
     */
    public String addStepsForCreateConsistencyGroup(Workflow workflow, String waitFor,
            StorageSystem vplexSystem, List<URI> vplexVolumeURIs,
            boolean willBeRemovedByEarlierStep) throws ControllerException;

    /**
     * Deletes the VPLEX consistency group with the passed URI on the VPLEX
     * storage system with the passed URI. Assumes that the consistency group
     * has no volumes in at the time of deletion.
     * 
     * @param workflow The workflow.
     * @param vplexURI The URI of the VPlex storage system.
     * @param cgURI The URI of the consistency group.
     * @param opId The unique task identifier.
     * 
     * @throws InternalException When an error occurs configuring the
     *             consistency group deletion workflow.
     */
    public void deleteConsistencyGroup(Workflow workflow, URI vplexURI, URI cgURI, String opId)
            throws InternalException;

    /**
     * Updates the VPLEX consistency group by adding/removing the passed volumes
     * to/from the consistency group.
     * 
     * @param workflow The workflow.
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group.
     * @param addVolumesList The URIs of the volumes to be added.
     * @param removeVolumesList The URIs of the volumes to be removed.
     * @param opId The unique task identifier.
     * 
     * @throws InternalException When an error occurs configuring the
     *             consistency update workflow.
     */
    public void updateConsistencyGroup(Workflow workflow, URI vplexURI, URI cgURI,
            List<URI> addVolumesList, List<URI> removeVolumesList, String opId)
            throws InternalException;

    /**
     * Deletes a consistency group volume.
     * 
     * @param vplexURI
     * @param volume
     * @param cgName
     * @throws URISyntaxException
     * @throws Exception
     */
    public void deleteConsistencyGroupVolume(URI vplexURI, Volume volume, String cgName) throws URISyntaxException, Exception;

    /**
     * Adds a VPlex volume to a VPlex consistency group.
     * 
     * @param cgURI The BlockConsistencyGroup URI.
     * @param vplexVolume The VPlex virtual volume.
     * @param client The VPlex client reference.
     * @param addToViPRCg If true, removes the vplexVolume from the ViPR BlockConsistencyGroup by
     *            setting the CG URI reference to null on the volume. Does nothing when false.
     * @throws Exception
     */
    public void addVolumeToCg(URI cgURI, Volume vplexVolume, VPlexApiClient client, boolean addToViPRCg) throws Exception;

    /**
     * Removes a VPlex volume from a VPlex consistency group.
     * 
     * @param cgURI The BlockConsistencyGroup URI.
     * @param vplexVolume The VPlex virtual volume.
     * @param client The VPlex client reference.
     * @param removeFromViPRCg If true, adds the vplexVolume to the ViPR BlockConsistencyGroup by
     *            setting the CG URI references on the volume. Does nothing when false.
     * @throws Exception
     */
    public void removeVolumeFromCg(URI cgURI, Volume vplexVolume, VPlexApiClient client, boolean removeFromViPRCg) throws Exception;

    public ClusterConsistencyGroupWrapper getClusterConsistencyGroup(Volume vplexVolume, String cgName) throws Exception;
    
    /**
     * 
     * @param workflow
     * @param vplexSystem
     * @param vplexVolumeURIs
     * @param waitFor
     * @return
     * @throws Exception
     */
    public String addStepsForAddingVolumesToSRDFTargetCG(Workflow workflow, StorageSystem vplexSystem,
            List<URI> vplexVolumeURIs, String waitFor) throws Exception;
}
