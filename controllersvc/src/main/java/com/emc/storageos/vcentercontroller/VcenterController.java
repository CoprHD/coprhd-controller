/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

public interface VcenterController extends Controller {
    public void createVcenterCluster(AsyncTask task, URI clusterUri, URI[] hostUris, URI[] volumeUris) throws InternalException;

    public void updateVcenterCluster(AsyncTask task, URI clusterUri, URI[] addHostUris, URI[] removeHostUris, URI[] volumeUris)
            throws InternalException;

    public List<String> getVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException;

    public List<String> getRunningVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException;

    public List<String> getVirtualMachines(URI datacenterUri, URI clusterUri) throws InternalException;

    public List<String> getRunningVirtualMachines(URI datacenterUri, URI clusterUri) throws InternalException;

    public void enterMaintenanceMode(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException;

    public void exitMaintenanceMode(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException;

    public void removeVcenterCluster(URI datacenterUri, URI clusterUri) throws InternalException;

    /**
     * Verifies if the host can see the volume, if so then check for VMs (powered on/off)
     * on the datastore identified for the given volume.
     *
     * @param vcenterDataCenter {@link URI} vcenter datacenter id
     * @param cluster {@link URI} cluster id
     * @param hostId {@link URI} host  id
     * @param bootVolumeId {@link URI} boot volume  id
     * @return status map specifying if VMs are present and if VMs are poweredoff.
     */
    public Map<String, Boolean> checkVMsOnHostBootVolume(URI vcenterDataCenter, URI cluster, URI hostId, URI bootVolumeId);

    /**
     * Verifies there are any VMs on the host's exclusive volumes, if so then check for VMs (powered on/off)
     * on the datastore.
     *
     * @param vcenterDataCenter {@link URI} vcenter datacenter id
     * @param cluster {@link URI} cluster id
     * @param hostId {@link URI} host  id
     * @return status map specifying if VMs are present and if VMs are poweredoff.
     */
    Map<String, Boolean> checkVMsOnHostExclusiveVolumes(URI datacenterUri, URI clusterUri, URI hostId);
}
