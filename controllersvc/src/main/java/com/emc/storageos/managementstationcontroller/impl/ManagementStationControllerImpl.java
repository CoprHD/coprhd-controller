package com.emc.storageos.managementstationcontroller.impl;

import java.net.URI;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.managementstation.ManagementStationController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.workflow.WorkflowService;

public class ManagementStationControllerImpl implements ManagementStationController {

    private DbClient _dbClient;
    private WorkflowService _workflowService;
    private CoordinatorClient _coordinator;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public void setCoordinator(CoordinatorClient coordinatorClient) {
        this._coordinator = coordinatorClient;
    }

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

}
