/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.managementstation;

import java.net.URI;
import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

public interface ManagementStationController extends Controller {
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

}
