/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.computesystemcontroller.impl.adapter.HostStateChange;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

public interface ComputeSystemController extends Controller {

    /**
     * 
     * @param tasks
     * @throws InternalException
     */
    public void discover(AsyncTask[] tasks) throws InternalException;

    /**
     * Detach all storage (export groups, fileshare exports) that are used by a host.
     * 
     * @param host
     *            URI of the host
     * @param deactivateOnComplete
     *            if true, deactivate the host when complete
     * @param deactivateBootVolume
     *            if true, and if the Host has a boot Volume associated with it, deactivate the boot volume
     * @param bootVolDescriptors
     * @param opId
     *            operation id created by the API
     * @throws InternalException
     */
    public void detachHostStorage(URI host, boolean deactivateOnComplete, boolean deactivateBootVolume,
            List<VolumeDescriptor> bootVolDescriptors, String opId) throws InternalException;

    /**
     * Detach all storage (export groups) that are used by a cluster.
     * 
     * @param cluster
     *            URI of the cluster
     * @param deactivateOnComplete
     *            if true, deactivate the cluster when complete
     * @param checkVms
     *            if true, fail if there are VMs in that cluster in vCenter
     * @param opId
     *            operation id created by the API
     * @throws InternalException
     */
    public void detachClusterStorage(URI cluster, boolean deactivateOnComplete, boolean checkVms, String opId) throws InternalException;

    /**
     * Detach all storage (export groups, fileshare exports) that are used by a vcenter.
     * 
     * @param vcenter
     *            URI of the vcenter
     * @param deactivateOnComplete
     *            if true, deactivate the vcenter when complete
     * @param opId
     *            operation id created by the API
     * @throws InternalException
     */
    public void detachVcenterStorage(URI vcenter, boolean deactivateOnComplete, String opId) throws InternalException;

    /**
     * Detach all storage (export groups, fileshare exports) that are used by a data center.
     * 
     * @param datacenter
     *            URI of the datacenter
     * @param deactivateOnComplete
     *            if true, deactivate the datacenter when complete
     * @param opId
     *            operation id created by the API
     * @throws InternalException
     */
    public void detachDataCenterStorage(URI datacenter, boolean deactivateOnComplete, String opId) throws InternalException;

    /**
     * Performs export group update operations to keep host and cluster export groups in synchronization with the
     * current state of the hosts
     * and clusters that were discovered
     * 
     * @param changes
     *            list of state changes for the host
     * @param deletedHosts
     *            list of deleted hosts that were not rediscovered
     * @param deletedClusters
     *            list of deleted clusters that were not rediscovered
     * @param isVCenter
     *            set to true if vCenter discovery has detected these host changes
     * @param taskId
     *            the task id
     * @throws ControllerException
     *             if an error occurs
     */
    public void processHostChanges(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters, boolean isVCenter,
            String taskId)
                    throws ControllerException;

    public void addInitiatorToExport(URI host, URI init, String taskId) throws ControllerException;

    public void addInitiatorsToExport(URI eventId, URI host, List<URI> init, String taskId) throws ControllerException;

    public void addInitiatorsToExport(URI host, List<URI> init, String taskId) throws ControllerException;

    public void removeInitiatorFromExport(URI eventId, URI host, URI init, String taskId) throws ControllerException;

    public void removeInitiatorFromExport(URI host, URI init, String taskId) throws ControllerException;

    public void removeInitiatorsFromExport(URI eventId, URI host, List<URI> init, String taskId) throws ControllerException;

    public void removeInitiatorsFromExport(URI host, List<URI> init, String taskId) throws ControllerException;

    public void addHostsToExport(URI eventId, List<URI> hostId, URI clusterId, String taskId, URI oldCluster, boolean isVcenter)
            throws ControllerException;

    public void addHostsToExport(List<URI> hostId, URI clusterId, String taskId, URI oldCluster, boolean isVcenter)
            throws ControllerException;

    public void removeHostsFromExport(URI eventId, List<URI> hostId, URI clusterId, boolean isVcenter, URI vCenterDataCenterId,
            String taskId)
                    throws ControllerException;

    public void removeHostsFromExport(List<URI> hostId, URI clusterId, boolean isVcenter, URI vCenterDataCenterId,
            String taskId)
                    throws ControllerException;

    public void removeIpInterfaceFromFileShare(URI hostId, URI ipInterface, String taskId) throws ControllerException;

    /*
    * Sets the host's boot volume association, optionally updates the hosts UCS san boot targets
    * @param hostId URI of the host
    * @param volumeId URI of the boot volume
    * @param updateSanBootTargets  set to true to update the UCS san boot targets
    * @param taskId the taskId
    * @throws ControllerException
    */
    public void setHostBootVolume(URI hostId, URI volumeId, boolean updateSanBootTargets, String taskId) throws ControllerException;

    /**
     * Updates export groups that contain the given host (both exclusive and shared export groups) by adding the newInitiators and removing
     * the oldInitiators.
     * 
     * @param eventId the actionable event id that triggered this workflow, or null if not triggered by an event
     * @param host the host id
     * @param newInitiators the initiators to be added to the host's export groups
     * @param oldInitiators the initiators to be removed from the host's export groups
     * @param taskId the task id
     */
    public void updateHostInitiators(URI eventId, URI host, List<URI> newInitiators, List<URI> oldInitiators, String taskId);
    
    /**
     * Release the host's associated compute element.
     *
     * @param hostId URI of the host
     * @param taskId the taskId
     */
    public void releaseHostComputeElement(URI hostId, String taskId);

    /**
     * Associate/bind the host to a new compute element.
     * @param hostId URI of the host
     * @param computeElementId URI of compute element
     * @param computeSystemId URI of compute system
     * @param computeVPoolId URI of compute virtual pool
     * @param taskId task id
     */
    public void associateHostComputeElement(URI hostId, URI computeElementId, URI computeSystemId, URI computeVPoolId,
            String taskId);

    /**
     * Verify if cluster exists on the vCenter
     * @param clusterId {@link URI} cluster id to be verified.
     * @param vCenterDataCenterId {@link URI} vcenter datacenterID to be verified on.
     * @return true if cluster is found else false.
     */
    public boolean verifyIfClusterExistsOnVCenter(URI clusterId, URI vCenterDataCenterId);
}
