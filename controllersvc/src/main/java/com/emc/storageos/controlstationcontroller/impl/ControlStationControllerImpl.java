package com.emc.storageos.controlstationcontroller.impl;

import java.net.URI;
import java.util.List;

import com.emc.storageos.controlstation.ControlStationController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

public class ControlStationControllerImpl implements ControlStationController {

    @Override
    public void createVcenterCluster(AsyncTask task, URI clusterUri, URI[] hostUris, URI[] volumeUris) throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateVcenterCluster(AsyncTask task, URI clusterUri, URI[] addHostUris, URI[] removeHostUris, URI[] volumeUris)
            throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public List<String> getVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getRunningVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getVirtualMachines(URI datacenterUri, URI clusterUri) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getRunningVirtualMachines(URI datacenterUri, URI clusterUri) throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void enterMaintenanceMode(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public void exitMaintenanceMode(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeVcenterCluster(URI datacenterUri, URI clusterUri) throws InternalException {
        // TODO Auto-generated method stub

    }

}
