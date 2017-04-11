/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.vcentercontroller.VcenterApiClient;
import com.emc.storageos.vcentercontroller.VcenterClusterCompleter;
import com.emc.storageos.vcentercontroller.VcenterController;
import com.emc.storageos.vcentercontroller.exceptions.VcenterControllerException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterObjectConnectionException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterObjectNotFoundException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterServerConnectionException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class VcenterControllerImpl implements VcenterController {

    private static final Logger _log = LoggerFactory.getLogger(VcenterControllerImpl.class);
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

    @Override
    public void createVcenterCluster(AsyncTask task, URI clusterUri, URI[] hostUris, URI[] volumeUris) throws InternalException {
        createOrUpdateVcenterCluster(true, task, clusterUri, hostUris, null, volumeUris);
    }

    @Override
    public void updateVcenterCluster(AsyncTask task, URI clusterUri, URI[] addHostUris, URI[] removeHostUris, URI[] volumeUris)
            throws InternalException {
        createOrUpdateVcenterCluster(false, task, clusterUri, addHostUris, removeHostUris, volumeUris);
    }

    private List<String> getVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri, boolean runningOnly) throws InternalException {
        VcenterApiClient vcenterApiClient = null;
        try {
            Host host = _dbClient.queryObject(Host.class, hostUri);
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());

            _log.info("Request to get virtual machines for " + vcenter.getLabel() + "/" + vcenterDataCenter.getLabel() + "/"
                    + cluster.getLabel() + "/" + host.getHostName());

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            return runningOnly ? vcenterApiClient.getRunningVirtualMachines(vcenterDataCenter.getLabel(), cluster.getExternalId(),
                    host.getHostName()) : vcenterApiClient.getVirtualMachines(vcenterDataCenter.getLabel(), cluster.getExternalId(),
                            host.getHostName());
        } catch (VcenterObjectConnectionException e) {
            throw VcenterControllerException.exceptions.objectConnectionException(e.getLocalizedMessage(), e);
        } catch (VcenterObjectNotFoundException e) {
            throw VcenterControllerException.exceptions.objectNotFoundException(e.getLocalizedMessage(), e);
        } catch (VcenterServerConnectionException e) {
            throw VcenterControllerException.exceptions.serverConnectionException(e.getLocalizedMessage(), e);
        } catch (Exception e) {
            _log.error("getVirtualMachines exception " + e);
            throw VcenterControllerException.exceptions.unexpectedException(e.getLocalizedMessage(), e);
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    @Override
    public List<String> getVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        return getVirtualMachines(datacenterUri, clusterUri, hostUri, false);
    }

    @Override
    public List<String> getRunningVirtualMachines(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        return getVirtualMachines(datacenterUri, clusterUri, hostUri, true);
    }

    private List<String> getVirtualMachines(URI datacenterUri, URI clusterUri, boolean runningOnly) throws InternalException {
        VcenterApiClient vcenterApiClient = null;
        try {
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());
            _log.info("Request to get virtual machines for " + vcenter.getLabel() + "/" + vcenterDataCenter.getLabel() + "/"
                    + cluster.getLabel());

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            return runningOnly ? vcenterApiClient.getRunningVirtualMachines(vcenterDataCenter.getLabel(), cluster.getExternalId())
                    : vcenterApiClient.getVirtualMachines(vcenterDataCenter.getLabel(), cluster.getExternalId());
        } catch (VcenterObjectNotFoundException e) {
            throw VcenterControllerException.exceptions.objectNotFoundException(e.getLocalizedMessage(), e);
        } catch (VcenterServerConnectionException e) {
            throw VcenterControllerException.exceptions.serverConnectionException(e.getLocalizedMessage(), e);
        } catch (Exception e) {
            _log.error("getVirtualMachines exception " + e);
            throw VcenterControllerException.exceptions.unexpectedException(e.getLocalizedMessage(), e);
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    @Override
    public List<String> getVirtualMachines(URI datacenterUri, URI clusterUri) throws InternalException {
        return getVirtualMachines(datacenterUri, clusterUri, false);
    }

    @Override
    public List<String> getRunningVirtualMachines(URI datacenterUri, URI clusterUri) throws InternalException {
        return getVirtualMachines(datacenterUri, clusterUri, true);
    }

    @Override
    public void enterMaintenanceMode(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        VcenterApiClient vcenterApiClient = null;
        try {
            Host host = _dbClient.queryObject(Host.class, hostUri);
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());
            _log.info("Request to enter maintenance mode for " + vcenter.getLabel() + "/" + vcenterDataCenter.getLabel() + "/"
                    + cluster.getLabel() + "/" + host.getHostName());

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            vcenterApiClient.enterMaintenanceMode(vcenterDataCenter.getLabel(), cluster.getExternalId(), host.getHostName());
        } catch (VcenterObjectConnectionException e) {
            throw VcenterControllerException.exceptions.objectConnectionException(e.getLocalizedMessage(), e);
        } catch (VcenterObjectNotFoundException e) {
            throw VcenterControllerException.exceptions.objectNotFoundException(e.getLocalizedMessage(), e);
        } catch (VcenterServerConnectionException e) {
            throw VcenterControllerException.exceptions.serverConnectionException(e.getLocalizedMessage(), e);
        } catch (Exception e) {
            _log.error("enterMaintenanceMode exception " + e);
            throw VcenterControllerException.exceptions.unexpectedException(e.getLocalizedMessage(), e);
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    @Override
    public void exitMaintenanceMode(URI datacenterUri, URI clusterUri, URI hostUri) throws InternalException {
        VcenterApiClient vcenterApiClient = null;
        try {
            Host host = _dbClient.queryObject(Host.class, hostUri);
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());
            _log.info("Request to exit maintenance mode for " + vcenter.getLabel() + "/" + vcenterDataCenter.getLabel() + "/"
                    + cluster.getLabel() + "/" + host.getHostName());

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            vcenterApiClient.exitMaintenanceMode(vcenterDataCenter.getLabel(), cluster.getExternalId(), host.getHostName());
        } catch (VcenterObjectConnectionException e) {
            throw VcenterControllerException.exceptions.objectConnectionException(e.getLocalizedMessage(), e);
        } catch (VcenterObjectNotFoundException e) {
            throw VcenterControllerException.exceptions.objectNotFoundException(e.getLocalizedMessage(), e);
        } catch (VcenterServerConnectionException e) {
            throw VcenterControllerException.exceptions.serverConnectionException(e.getLocalizedMessage(), e);
        } catch (Exception e) {
            _log.error("exitMaintenanceMode exception " + e);
            throw VcenterControllerException.exceptions.unexpectedException(e.getLocalizedMessage(), e);
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    private void createOrUpdateVcenterCluster(boolean createCluster, AsyncTask task, URI clusterUri, URI[] addHostUris,
            URI[] removeHostUris, URI[] volumeUris) throws InternalException {
        TaskCompleter completer = null;
        try {
            _log.info("createOrUpdateVcenterCluster " + createCluster + " " + task + " " + clusterUri + " " + addHostUris + " "
                    + removeHostUris);

            if (task == null) {
                _log.error("AsyncTask is null");
                throw new Exception("AsyncTask is null");
            }
            URI vcenterDataCenterId = task._id;
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDataCenterId);

            if (clusterUri == null) {
                _log.error("Cluster URI is null");
                throw new Exception("Cluster URI is null");
            }
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);

            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());
            _log.info("Request to create or update cluster " + vcenter.getIpAddress() + "/" + vcenterDataCenter.getLabel() + "/"
                    + cluster.getLabel());

            Collection<Host> addHosts = new ArrayList<Host>();
            if (addHostUris == null || addHostUris.length == 0) {
                _log.info("Add host URIs is null or empty - Cluster will be created without hosts");
            } else {
                for (URI hostUri : addHostUris) {
                    _log.info("createOrUpdateVcenterCluster " + clusterUri + " with add host " + hostUri);
                }
                addHosts = _dbClient.queryObject(Host.class, addHostUris);
            }

            Collection<Host> removeHosts = new ArrayList<Host>();
            if (removeHostUris == null || removeHostUris.length == 0) {
                _log.info("Remove host URIs is null or empty - Cluster will have no removed hosts");
            } else {
                for (URI hostUri : removeHostUris) {
                    _log.info("createOrUpdateVcenterCluster " + clusterUri + " with remove host " + hostUri);
                }
                removeHosts = _dbClient.queryObject(Host.class, removeHostUris);
            }

            Collection<Volume> volumes = new ArrayList<Volume>();
            if (volumeUris == null || volumeUris.length == 0) {
                _log.info("Volume URIs is null or empty - Cluster will be created without datastores");
            } else {
                for (URI volumeUri : volumeUris) {
                    _log.info("createOrUpdateVcenterCluster " + clusterUri + " with volume " + volumeUri);
                }
                volumes = _dbClient.queryObject(Volume.class, volumeUris);
            }

            completer = new VcenterClusterCompleter(vcenterDataCenterId, task._opId, OperationTypeEnum.CREATE_UPDATE_VCENTER_CLUSTER,
                    "VCENTER_CONTROLLER");
            Workflow workflow = _workflowService.getNewWorkflow(this, "CREATE_UPDATE_VCENTER_CLUSTER_WORKFLOW", true, task._opId);
            String clusterStep = workflow.createStep("CREATE_UPDATE_VCENTER_CLUSTER_STEP",
                    String.format("vCenter cluster operation in vCenter datacenter %s", vcenterDataCenterId), null,
                    vcenterDataCenterId, vcenterDataCenterId.toString(),
                    this.getClass(),
                    new Workflow.Method("createUpdateVcenterClusterOperation", createCluster, vcenter.getId(), vcenterDataCenter.getId(),
                            cluster.getId()),
                    null,
                    null);

            String lastStep = clusterStep;
            if (!removeHosts.isEmpty()) {
                for (Host host : removeHosts) {
                    String hostStep = workflow.createStep(
                            "VCENTER_CLUSTER_REMOVE_HOST",
                            String.format("vCenter cluster remove host operation %s", host.getId()),
                            clusterStep,
                            vcenterDataCenterId,
                            vcenterDataCenterId.toString(),
                            this.getClass(),
                            new Workflow.Method("vcenterClusterRemoveHostOperation", vcenter.getId(), vcenterDataCenter.getId(), cluster
                                    .getId(), host.getId()),
                            null,
                            null);
                    lastStep = hostStep; // add host will wait on last of these
                }
            }

            if (!addHosts.isEmpty()) {
                for (Host host : addHosts) {
                    String hostStep = workflow.createStep(
                            "VCENTER_CLUSTER_ADD_HOST",
                            String.format("vCenter cluster add host operation %s", host.getId()),
                            lastStep,
                            vcenterDataCenterId,
                            vcenterDataCenterId.toString(),
                            this.getClass(),
                            new Workflow.Method("vcenterClusterAddHostOperation", vcenter.getId(), vcenterDataCenter.getId(), cluster
                                    .getId(), host.getId()),
                            null,
                            null);
                }
            }

            workflow.executePlan(completer, "Success");
        } catch (Exception e) {
            _log.error("createOrUpdateVcenterCluster caught an exception.", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(_dbClient, serviceError);
        }

    }

    public void createUpdateVcenterClusterOperation(boolean createCluster, URI vcenterId, URI vcenterDataCenterId, URI clusterId,
            String stepId) {
        VcenterApiClient vcenterApiClient = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDataCenterId);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterId);

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            String vcenterClusterId = null;
            if (createCluster) {
                _log.info("Create cluster with name " + cluster.getLabel());
                vcenterClusterId = vcenterApiClient.createCluster(vcenterDataCenter.getLabel(), cluster.getLabel());
            }
            else {
                // Use MoRef if present but don't stop if we fail to find cluster based off this because we'll fall back and try on name
                String externalId = cluster.getExternalId();
                if (externalId != null && !externalId.trim().equals("")) {
                    _log.info("Update cluster with MoRef " + externalId);
                    try {
                        vcenterClusterId = vcenterApiClient.updateCluster(vcenterDataCenter.getLabel(), externalId);
                    } catch (VcenterObjectNotFoundException e) {
                        _log.info("Ignore VcenterObjectNotFoundException updateCluster when using MoRef... Try name based search next");
                    }
                }
                if (vcenterClusterId == null) {
                    _log.info("Update cluster with name " + cluster.getLabel());
                    vcenterClusterId = vcenterApiClient.updateCluster(vcenterDataCenter.getLabel(), cluster.getLabel());
                }
            }
            _log.info("Successfully created or updated cluster " + cluster.getLabel() + " " + vcenterClusterId);

            cluster.setVcenterDataCenter(vcenterDataCenter.getId());
            cluster.setExternalId(vcenterClusterId);
            _dbClient.updateAndReindexObject(cluster);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            _log.error("createUpdateVcenterClusterOperation exception " + e);
            WorkflowStepCompleter.stepFailed(stepId, VcenterControllerException.exceptions.clusterException(e.getLocalizedMessage(), e));
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    public void vcenterClusterRemoveHostOperation(URI vcenterId, URI vcenterDataCenterId, URI clusterId, URI hostId, String stepId) {
        VcenterApiClient vcenterApiClient = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDataCenterId);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterId);
            Host host = _dbClient.queryObject(Host.class, hostId);

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            vcenterApiClient.removeHost(vcenterDataCenter.getLabel(), cluster.getExternalId(), host.getHostName());
            _log.info("Successfully removed host " + host.getHostName());

            host.setVcenterDataCenter(NullColumnValueGetter.getNullURI());
            _dbClient.updateObject(host);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            _log.error("vcenterClusterRemoveHostOperation exception ", e);
            WorkflowStepCompleter.stepFailed(stepId, VcenterControllerException.exceptions.hostException(e.getLocalizedMessage(), e));
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    public void vcenterClusterAddHostOperation(URI vcenterId, URI vcenterDataCenterId, URI clusterId, URI hostId, String stepId) {
        VcenterApiClient vcenterApiClient = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDataCenterId);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterId);
            Host host = _dbClient.queryObject(Host.class, hostId);

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            String key = vcenterApiClient.addHost(vcenterDataCenter.getLabel(), cluster.getExternalId(), host.getHostName(),
                    host.getUsername(), host.getPassword());
            _log.info("Successfully added or located host " + host.getHostName() + " " + key);

            host.setVcenterDataCenter(vcenterDataCenter.getId());
            _dbClient.updateAndReindexObject(host);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            _log.error("vcenterClusterAddHostOperation exception " + e);
            WorkflowStepCompleter.stepFailed(stepId, VcenterControllerException.exceptions.hostException(e.getLocalizedMessage(), e));
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    // Find a host connected and powered on then refresh it
    public void vcenterClusterSelectHostOperation(URI vcenterId, URI vcenterDataCenterId, URI clusterId, URI[] hostUris, String stepId) {
        VcenterApiClient vcenterApiClient = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDataCenterId);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterId);
            Collection<Host> hosts = _dbClient.queryObject(Host.class, hostUris);

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());

            Host hostForStorageOperations = null;
            for (Host host : hosts) {
                try {
                    vcenterApiClient.checkHostConnectedPoweredOn(vcenterDataCenter.getLabel(), cluster.getExternalId(), host.getHostName());
                    hostForStorageOperations = host;
                    _log.info("Host " + host.getHostName() + " to be used for storage operations");
                    break;
                } catch (Exception e) {
                    _log.info("Host " + host.getHostName() + " not valid for storage operations due to exception "
                            + e.getLocalizedMessage());
                }
            }
            if (hostForStorageOperations == null) {
                _log.error("No host valid for performing storage operations thus cannot perform datastore operations");
                throw new Exception("No host valid for performing storage operations thus cannot perform datastore operations");
            }
            vcenterApiClient.refreshRescanHostStorage(vcenterDataCenter.getLabel(), cluster.getExternalId(),
                    hostForStorageOperations.getHostName());

            // persist hostForStorageOperations ID in wf data
            _workflowService.storeStepData(stepId, hostForStorageOperations.getId());

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            _log.error("vcenterClusterSelectHostOperation exception " + e);
            WorkflowStepCompleter.stepFailed(stepId, VcenterControllerException.exceptions.hostException(e.getLocalizedMessage(), e));
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    public void vcenterClusterCreateDatastoreOperation(URI vcenterId, URI vcenterDataCenterId, URI clusterId, URI volumeId,
            String selectHostStepId, String stepId) {
        VcenterApiClient vcenterApiClient = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            URI hostId = (URI) _workflowService.loadStepData(selectHostStepId);
            if (hostId == null) {
                _log.error("Workflow loadStepData on " + selectHostStepId + " is null");
                throw new Exception("Workflow loadStepData on " + selectHostStepId + " is null");
            }

            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDataCenterId);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterId);
            Host host = _dbClient.queryObject(Host.class, hostId);
            Volume volume = _dbClient.queryObject(Volume.class, volumeId);

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            String key = vcenterApiClient.createDatastore(vcenterDataCenter.getLabel(), cluster.getExternalId(), host.getHostName(),
                    volume.getWWN(), volume.getLabel());
            _log.info("Successfully created or located datastore " + volume.getLabel() + " " + key);

            host.setVcenterDataCenter(vcenterDataCenter.getId());
            _dbClient.updateAndReindexObject(host);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            _log.error("vcenterClusterCreateDatastoreOperation exception " + e);
            WorkflowStepCompleter.stepFailed(stepId, VcenterControllerException.exceptions.hostException(e.getLocalizedMessage(), e));
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    @Override
    public void removeVcenterCluster(URI datacenterUri, URI clusterUri) throws InternalException {
        VcenterApiClient vcenterApiClient = null;
        try {
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());
            _log.info("Request to remove cluster " + vcenter.getLabel() + "/" + vcenterDataCenter.getLabel() + "/" + cluster.getLabel());

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            vcenterApiClient.removeCluster(vcenterDataCenter.getLabel(), cluster.getExternalId());
        } catch (VcenterObjectConnectionException e) {
            throw VcenterControllerException.exceptions.objectConnectionException(e.getLocalizedMessage(), e);
        } catch (VcenterObjectNotFoundException e) {
            throw VcenterControllerException.exceptions.objectNotFoundException(e.getLocalizedMessage(), e);
        } catch (VcenterServerConnectionException e) {
            throw VcenterControllerException.exceptions.serverConnectionException(e.getLocalizedMessage(), e);
        } catch (Exception e) {
            _log.error("removeVcenterCluster exception " + e);
            throw VcenterControllerException.exceptions.unexpectedException(e.getLocalizedMessage(), e);
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
    }

    @Override
    public boolean checkVMsOnHostBootVolume(URI datacenterUri, URI clusterUri, URI hostId, URI bootVolumeId) {
        VcenterApiClient vcenterApiClient = null;
        boolean isVMsPresent = false;
        try {
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri);
            Vcenter vcenter = _dbClient.queryObject(Vcenter.class, vcenterDataCenter.getVcenter());
            Host host = _dbClient.queryObject(Host.class, hostId);
            Volume volume = _dbClient.queryObject(Volume.class, bootVolumeId);
            _log.info("Request to check VMs on boot volume {} of host {}", volume.getLabel() +" - " +bootVolumeId, host.getLabel());

            vcenterApiClient = new VcenterApiClient(_coordinator.getPropertyInfo());
            vcenterApiClient.setup(vcenter.getIpAddress(), vcenter.getUsername(), vcenter.getPassword(), vcenter.getPortNumber());
            isVMsPresent = vcenterApiClient.checkVMsOnHostVolume(vcenterDataCenter.getLabel(), cluster.getExternalId(),
                    host.getHostName(), volume.getWWN());
        } catch (VcenterObjectConnectionException e) {
            throw VcenterControllerException.exceptions.objectConnectionException(e.getLocalizedMessage(), e);
        } catch (VcenterObjectNotFoundException e) {
            throw VcenterControllerException.exceptions.objectNotFoundException(e.getLocalizedMessage(), e);
        } catch (VcenterServerConnectionException e) {
            throw VcenterControllerException.exceptions.serverConnectionException(e.getLocalizedMessage(), e);
        } catch (Exception e) {
            _log.error("checkVMsOnHostBootVolume exception ", e);
            throw VcenterControllerException.exceptions.unexpectedException(e.getLocalizedMessage(), e);
        } finally {
            if (vcenterApiClient != null) {
                vcenterApiClient.destroy();
            }
        }
        return isVMsPresent;
    }
}
