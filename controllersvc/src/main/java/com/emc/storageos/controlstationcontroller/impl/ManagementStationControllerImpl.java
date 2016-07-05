package com.emc.storageos.controlstationcontroller.impl;

import java.net.URI;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.managementstation.ManagementStationController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;

public class ManagementStationControllerImpl implements ManagementStationController {

    private static final Log _log = LogFactory.getLog(ManagementStationControllerImpl.class);

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

    @Override
    public void discover(AsyncTask[] tasks) throws InternalException {
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.CS_DATA_COLLECTION_LOCK, ControllerServiceImpl.CS_DISCOVERY);
        } catch (Exception e) {
            _log.error(String.format("Failed to schedule discovery job due to %s ", e.getMessage()));
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }

}
