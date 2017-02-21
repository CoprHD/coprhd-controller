/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.computecontroller.impl.ComputeDeviceController;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.hostmountadapters.HostDeviceInputOutput;
import com.emc.storageos.computesystemcontroller.hostmountadapters.HostMountAdapter;
import com.emc.storageos.computesystemcontroller.impl.adapter.ExportGroupState;
import com.emc.storageos.computesystemcontroller.impl.adapter.HostStateChange;
import com.emc.storageos.computesystemcontroller.impl.adapter.VcenterDiscoveryAdapter;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileMountInfo;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.linux.util.VolumeWWNUtils;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMWareException;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.StorageIORMConfigSpec;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.StorageResourceManager;
import com.vmware.vim25.mo.Task;

public class ComputeSystemControllerImpl implements ComputeSystemController {

    private static final Log _log = LogFactory.getLog(ComputeSystemControllerImpl.class);

    private WorkflowService _workflowService;
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    private static final String ADD_INITIATOR_STORAGE_WF_NAME = "ADD_INITIATOR_STORAGE_WORKFLOW";
    private static final String REMOVE_INITIATOR_STORAGE_WF_NAME = "REMOVE_INITIATOR_STORAGE_WORKFLOW";
    private static final String REMOVE_HOST_STORAGE_WF_NAME = "REMOVE_HOST_STORAGE_WORKFLOW";
    private static final String REMOVE_IPINTERFACE_STORAGE_WF_NAME = "REMOVE_IPINTERFACE_STORAGE_WORKFLOW";
    private static final String HOST_CHANGES_WF_NAME = "HOST_CHANGES_WORKFLOW";
    private static final String SYNCHRONIZE_SHARED_EXPORTS_WF_NAME = "SYNCHRONIZE_SHARED_EXPORTS_WORKFLOW";
    private static final String SET_SAN_BOOT_TARGETS_WF_NAME = "SET_SAN_BOOT_TARGETS_WORKFLOW";

    private static final String DETACH_HOST_STORAGE_WF_NAME = "DETACH_HOST_STORAGE_WORKFLOW";
    private static final String DETACH_CLUSTER_STORAGE_WF_NAME = "DETACH_CLUSTER_STORAGE_WORKFLOW";
    private static final String DETACH_VCENTER_STORAGE_WF_NAME = "DETACH_VCENTER_STORAGE_WORKFLOW";
    private static final String DETACH_VCENTER_DATACENTER_STORAGE_WF_NAME = "DETACH_VCENTER_DATACENTER_STORAGE_WORKFLOW";

    private static final String DELETE_EXPORT_GROUP_STEP = "DeleteExportGroupStep";
    private static final String UPDATE_EXPORT_GROUP_STEP = "UpdateExportGroupStep";
    private static final String UPDATE_FILESHARE_EXPORT_STEP = "UpdateFileshareExportStep";
    private static final String UNEXPORT_FILESHARE_STEP = "UnexportFileshareStep";
    private static final String UPDATE_HOST_AND_INITIATOR_CLUSTER_NAMES_STEP = "UpdateHostAndInitiatorClusterNamesStep";

    private static final String VERIFY_DATASTORE_STEP = "VerifyDatastoreStep";

    private static final String UNMOUNT_AND_DETACH_STEP = "UnmountAndDetachStep";
    private static final String MOUNT_AND_ATTACH_STEP = "MountAndAttachStep";

    private static final String VMFS_DATASTORE_PREFIX = "vipr:vmfsDatastore";
    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";

    private ComputeDeviceController computeDeviceController;
    private BlockStorageScheduler _blockScheduler;

    private Map<String, HostMountAdapter> _mountAdapters;

    public void setComputeDeviceController(ComputeDeviceController computeDeviceController) {
        this.computeDeviceController = computeDeviceController;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public void setBlockScheduler(BlockStorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public Map<String, HostMountAdapter> getMountAdapters() {
        return _mountAdapters;
    }

    public void setMountAdapters(Map<String, HostMountAdapter> mountAdapters) {
        this._mountAdapters = mountAdapters;
    }

    /**
     * Empty rollback method
     * 
     * @return workflow method that is empty
     */
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

    /**
     * A rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain. It says the rollback step succeeded,
     * which will then allow other rollback operations to execute for other
     * workflow steps executed by the other controller.
     * 
     * @param stepId
     *            The id of the step being rolled back.
     * 
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    @Override
    public void setHostBootVolume(URI host, URI bootVolumeId, boolean updateSanBootTargets,String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(host, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, SET_SAN_BOOT_TARGETS_WF_NAME, true, taskId);
            String waitFor = addStepsForBootVolume(workflow,  host, bootVolumeId);
            if (updateSanBootTargets){
                 waitFor = addStepsForSanBootTargets(workflow,  host, bootVolumeId, waitFor);
            }
            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch  (Exception ex) {
            String message = "setHostSanBootTargets caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }

    }
 
    @Override
    public void detachHostStorage(URI host, boolean deactivateOnComplete, boolean deactivateBootVolume, String taskId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(host, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            if (deactivateOnComplete) {
                waitFor = computeDeviceController.addStepsVcenterHostCleanup(workflow, waitFor, host);
            }

            waitFor = addStepsForExportGroups(workflow, waitFor, host);

            waitFor = addStepsForFileShares(workflow, waitFor, host);

            if (deactivateOnComplete) {
                waitFor = computeDeviceController.addStepsDeactivateHost(workflow, waitFor, host, deactivateBootVolume);
            }

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachHostStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void detachVcenterStorage(URI id, boolean deactivateOnComplete, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new VcenterCompleter(id, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DETACH_VCENTER_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            // We need to find all datacenters associated to the vcenter
            List<NamedElementQueryResultList.NamedElement> datacenterUris = ComputeSystemHelper.listChildren(_dbClient, id,
                    VcenterDataCenter.class, "label", "vcenter");
            for (NamedElementQueryResultList.NamedElement datacenterUri : datacenterUris) {
                waitFor = addStepForVcenterDataCenter(workflow, waitFor, datacenterUri.getId());
            }

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachVcenterStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void detachDataCenterStorage(URI datacenter, boolean deactivateOnComplete, String taskId) throws InternalException {
        TaskCompleter completer = null;
        try {
            completer = new VcenterDataCenterCompleter(datacenter, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DETACH_VCENTER_DATACENTER_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepForVcenterDataCenter(workflow, waitFor, datacenter);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachDataCenterStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    /**
     * Gets all export groups that contain references to the provided host or initiators
     * Export groups that don't contain initiators for a host may stil reference the host
     * 
     * @param _dbClient2
     * 
     * @param hostId
     *            the host id
     * @param initiators
     *            list of initiators for the given host
     * @return list of export groups containing references to the host or initiators
     */
    public static List<ExportGroup> getExportGroups(DbClient dbClient, URI hostId, List<Initiator> initiators) {
        HashMap<URI, ExportGroup> exports = new HashMap<URI, ExportGroup>();
        // Get all exports that use the host's initiators
        for (Initiator item : initiators) {
            List<ExportGroup> list = ComputeSystemHelper.findExportsByInitiator(dbClient, item.getId().toString());
            for (ExportGroup export : list) {
                exports.put(export.getId(), export);
            }
        }
        // Get all exports that reference the host (may not contain host initiators)
        List<ExportGroup> hostExports = ComputeSystemHelper.findExportsByHost(dbClient, hostId.toString());
        for (ExportGroup export : hostExports) {
            exports.put(export.getId(), export);
        }
        return new ArrayList<ExportGroup>(exports.values());
    }

    public String addStepForVcenterDataCenter(Workflow workflow, String waitFor, URI datacenterUri) {
        VcenterDataCenter dataCenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterUri);
        if (dataCenter != null && !dataCenter.getInactive()) {
            // clean all export related to host in datacenter
            List<NamedElementQueryResultList.NamedElement> hostUris = ComputeSystemHelper.listChildren(_dbClient,
                    dataCenter.getId(), Host.class, "label", "vcenterDataCenter");
            for (NamedElementQueryResultList.NamedElement hostUri : hostUris) {
                Host host = _dbClient.queryObject(Host.class, hostUri.getId());
                // do not detach storage of provisioned hosts
                if (host != null && !host.getInactive() && NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                    waitFor = addStepsForExportGroups(workflow, waitFor, host.getId());
                    waitFor = addStepsForFileShares(workflow, waitFor, host.getId());
                }
            }
            // clean all the export related to clusters in datacenter
            List<NamedElementQueryResultList.NamedElement> clustersUris = ComputeSystemHelper.listChildren(_dbClient,
                    dataCenter.getId(), Cluster.class, "label", "vcenterDataCenter");
            for (NamedElementQueryResultList.NamedElement clusterUri : clustersUris) {
                Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri.getId());
                if (cluster != null && !cluster.getInactive()) {
                    waitFor = addStepsForClusterExportGroups(workflow, waitFor, cluster.getId());
                }
            }
        }
        return waitFor;
    }

    public String addStepsForFileShares(Workflow workflow, String waitFor, URI hostId) {

        List<FileShare> fileShares = ComputeSystemHelper.getFileSharesByHost(_dbClient, hostId);
        List<String> endpoints = ComputeSystemHelper.getIpInterfaceEndpoints(_dbClient, hostId);
        for (FileShare fileShare : fileShares) {
            if (fileShare != null && fileShare.getFsExports() != null) {
                for (FileExport fileExport : fileShare.getFsExports().values()) {
                    if (fileExport != null && fileExport.getClients() != null) {
                        if (fileExportContainsEndpoint(fileExport, endpoints)) {
                            StorageSystem device = _dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());

                            List<String> clients = fileExport.getClients();
                            clients.removeAll(endpoints);
                            fileExport.setStoragePort(fileShare.getStoragePort().toString());
                            FileShareExport export = new FileShareExport(clients, fileExport.getSecurityType(),
                                    fileExport.getPermissions(),
                                    fileExport.getRootUserMapping(), fileExport.getProtocol(), fileExport.getStoragePortName(),
                                    fileExport.getStoragePort(), fileExport.getPath(), fileExport.getMountPath(), "", "");

                            if (clients.isEmpty()) {
                                _log.info("Unexporting file share " + fileShare.getId());
                                waitFor = workflow.createStep(UNEXPORT_FILESHARE_STEP,
                                        String.format("Unexport fileshare %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        unexportFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            } else {
                                _log.info("Updating export for file share " + fileShare.getId());
                                waitFor = workflow.createStep(UPDATE_FILESHARE_EXPORT_STEP,
                                        String.format("Update fileshare export %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        updateFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            }
                        }
                    }
                }
            }
        }

        return waitFor;
    }

    @Override
    public void addInitiatorToExport(URI hostId, URI initId, String taskId) throws ControllerException {
        List<URI> uris = Lists.newArrayList(initId);
        addInitiatorsToExport(hostId, uris, taskId);
    }

    @Override
    public void addInitiatorsToExport(URI eventId, URI hostId, List<URI> initiators, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new InitiatorCompleter(eventId, initiators, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, ADD_INITIATOR_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForAddInitiators(workflow, waitFor, hostId, initiators);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "addInitiatorToStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void removeInitiatorFromExport(URI eventId, URI hostId, URI initId, String taskId) throws ControllerException {
        List<URI> uris = Lists.newArrayList(initId);
        removeInitiatorsFromExport(eventId, hostId, uris, taskId);
    }

    @Override
    public void removeInitiatorsFromExport(URI eventId, URI hostId, List<URI> initiators, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new InitiatorCompleter(eventId, initiators, true, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_INITIATOR_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForRemoveInitiators(workflow, waitFor, hostId, initiators);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachHostStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void synchronizeSharedExports(URI clusterId, String taskId)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new ClusterCompleter(clusterId, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, SYNCHRONIZE_SHARED_EXPORTS_WF_NAME, true, taskId);
            String waitFor = null;

            List<URI> clusterHostIds = ComputeSystemHelper.getChildrenUris(_dbClient, clusterId, Host.class, "cluster");
            List<URI> exportGroups = Lists.newArrayList();

            // 1. For hosts in this cluster, remove them from other shared exports that don't belong to this current
            // cluster
            for (URI hostId : clusterHostIds) {
                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                for (ExportGroup exportGroup : getExportGroups(_dbClient, hostId, hostInitiators)) {
                    if (exportGroup.forCluster() && !exportGroup.hasCluster(clusterId)) {
                        _log.info("Export " + exportGroup.getId() + " contains reference to host " + hostId
                                + ". Will remove this host from the export");
                        exportGroups.add(exportGroup.getId());
                    }
                }
            }
            for (URI export : exportGroups) {
                waitFor = addStepsForRemoveHostFromExport(workflow, waitFor, clusterHostIds, export);
            }

            waitFor = addStepsForSynchronizeClusterExport(workflow, waitFor, clusterHostIds, clusterId);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "synchronizeSharedExports caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void addHostsToExport(URI eventId, List<URI> hostIds, URI clusterId, String taskId, URI oldCluster, boolean isVcenter)
            throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(eventId, hostIds, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            URI vCenterDataCenterId = NullColumnValueGetter.getNullURI();
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            if (cluster != null && !NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
                vCenterDataCenterId = cluster.getVcenterDataCenter();
            }

            if (!NullColumnValueGetter.isNullURI(oldCluster)) {
                waitFor = addStepsForRemoveHost(workflow, waitFor, hostIds, oldCluster, vCenterDataCenterId, isVcenter);
            }

            waitFor = addStepForUpdatingHostAndInitiatorClusterReferences(workflow, waitFor, hostIds, clusterId, vCenterDataCenterId);

            waitFor = addStepsForAddHost(workflow, waitFor, hostIds, clusterId, isVcenter);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "addHostToExport caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void removeHostsFromExport(URI eventId, List<URI> hostIds, URI clusterId, boolean isVcenter, URI vCenterDataCenterId,
            String taskId)
                    throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(eventId, hostIds, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForRemoveHost(workflow, waitFor, hostIds, clusterId, vCenterDataCenterId, isVcenter);

            waitFor = addStepForUpdatingHostAndInitiatorClusterReferences(workflow, waitFor, hostIds, NullColumnValueGetter.getNullURI(),
                    vCenterDataCenterId);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "removeHostFromExport caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    @Override
    public void removeIpInterfaceFromFileShare(URI hostId, URI ipId, String taskId) throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new IpInterfaceCompleter(ipId, true, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_IPINTERFACE_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForIpInterfaceFileShares(workflow, waitFor, hostId, ipId);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "removeIpInterfaceFromFileShare caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    public String addStepsForAddInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> inits) {
        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, inits);
        List<ExportGroup> exportGroups = ComputeSystemHelper.findExportsByHost(_dbClient, hostId.toString());

        for (ExportGroup export : exportGroups) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            List<Initiator> validInitiator = ComputeSystemHelper.validatePortConnectivity(_dbClient, export, initiators);
            if (!validInitiator.isEmpty()) {
                boolean update = false;
                for (Initiator initiator : validInitiator) {
                    // if the initiators is not already in the list add it.
                    if (!updatedInitiators.contains(initiator.getId())) {
                        updatedInitiators.add(initiator.getId());
                        update = true;
                    }
                }

                if (update) {
                    waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                            String.format("Updating export group %s", export.getId()), waitFor,
                            export.getId(), export.getId().toString(),
                            this.getClass(),
                            updateExportGroupMethod(export.getId(), updatedVolumesMap,
                                    updatedClusters, updatedHosts, updatedInitiators),
                            null, null);
                }
            }
        }
        return waitFor;
    }

    public String addStepsForRemoveInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> initiatorsURI) {

        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorsURI);
        List<ExportGroup> exportGroups = getExportGroups(_dbClient, hostId, initiators);

        for (ExportGroup export : exportGroups) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            // Only update if the list as changed
            if (updatedInitiators.removeAll(initiatorsURI)) {
                waitFor = workflow.createStep(
                        UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()),
                        waitFor,
                        export.getId(),
                        export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(), updatedInitiators.isEmpty() ? new HashMap<URI, Integer>()
                                : updatedVolumesMap,
                                updatedClusters, updatedHosts, updatedInitiators),
                        null, null);
            }
        }
        return waitFor;
    }

    public String addStepsForRemoveHost(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId, URI vcenterDataCenter,
            boolean isVcenter) {
        List<ExportGroup> exportGroups = getSharedExports(_dbClient, clusterId);
        String newWaitFor = waitFor;
        if (isVcenter && !NullColumnValueGetter.isNullURI(vcenterDataCenter)) {
            Collection<URI> exportIds = Collections2.transform(exportGroups, CommonTransformerFunctions.fctnDataObjectToID());
            Map<URI, Collection<URI>> hostExports = Maps.newHashMap();
            for (URI host : hostIds) {
                hostExports.put(host, exportIds);
            }
            newWaitFor = this.verifyDatastoreForRemoval(hostExports, vcenterDataCenter, newWaitFor, workflow);

            newWaitFor = this.unmountAndDetachVolumes(hostExports, vcenterDataCenter, newWaitFor, workflow);
        }
        for (ExportGroup export : exportGroups) {
            newWaitFor = addStepsForRemoveHostFromExport(workflow, newWaitFor, hostIds, export.getId());
        }
        return newWaitFor;
    }

    public String addStepsForRemoveHostFromExport(Workflow workflow, String waitFor, List<URI> hostIds, URI exportId) {
        ExportGroup export = _dbClient.queryObject(ExportGroup.class, exportId);
        String newWaitFor = waitFor;
        if (export != null) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            for (URI hostId : hostIds) {
                updatedHosts.remove(hostId);

                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                for (Initiator initiator : hostInitiators) {
                    updatedInitiators.remove(initiator.getId());
                }
            }

            if (updatedHosts.isEmpty()) {
                newWaitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                        String.format("Deleting export group %s", export.getId()), newWaitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        deleteExportGroupMethod(export.getId()),
                        null, null);
            } else {
                newWaitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()), newWaitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(),
                                updatedInitiators.isEmpty() ? new HashMap<URI, Integer>() : updatedVolumesMap,
                                updatedClusters, updatedHosts, updatedInitiators),
                        null, null);
            }
        }
        return newWaitFor;
    }

    /**
     * Synchronize a cluster's export groups by following steps:
     * - Add all hosts in the cluster that are not in the cluster's export groups
     * - Remove all hosts in cluster's export groups that don't belong to the cluster
     * 
     * @param workflow
     *            the workflow
     * @param waitFor
     *            waitfor step
     * @param clusterHostIds
     *            hosts that belong to the cluster
     * @param clusterId
     *            cluster id
     * @return
     */
    public String addStepsForSynchronizeClusterExport(Workflow workflow, String waitFor, List<URI> clusterHostIds,
            URI clusterId) {

        for (ExportGroup export : getSharedExports(_dbClient, clusterId)) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            // 1. Add all hosts in clusters that are not in the cluster's export groups
            for (URI clusterHost : clusterHostIds) {
                if (!updatedHosts.contains(clusterHost)) {
                    _log.info("Adding host " + clusterHost + " to cluster export group " + export.getId());
                    updatedHosts.add(clusterHost);
                    List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, clusterHost);
                    for (Initiator initiator : hostInitiators) {
                        updatedInitiators.add(initiator.getId());
                    }
                }
            }

            // 2. Remove all hosts in cluster's export groups that don't belong to the cluster
            Iterator<URI> updatedHostsIterator = updatedHosts.iterator();
            while (updatedHostsIterator.hasNext()) {
                URI hostId = updatedHostsIterator.next();
                if (!clusterHostIds.contains(hostId)) {
                    updatedHostsIterator.remove();
                    _log.info("Removing host " + hostId + " from shared export group " + export.getId()
                            + " because this host does not belong to the cluster");
                    List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                    for (Initiator initiator : hostInitiators) {
                        updatedInitiators.remove(initiator.getId());
                    }
                }
            }

            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", export.getId()), waitFor,
                    export.getId(), export.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(export.getId(), updatedInitiators.isEmpty() ? new HashMap<URI, Integer>() : updatedVolumesMap,
                            updatedClusters, updatedHosts, updatedInitiators),
                    null, null);
        }
        return waitFor;
    }

    public String addStepsForAddHost(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId, boolean isVcenter) {
        List<Host> hosts = _dbClient.queryObject(Host.class, hostIds);
        List<ExportGroup> exportGroups = getSharedExports(_dbClient, clusterId);

        for (ExportGroup eg : exportGroups) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(eg.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(eg.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(eg.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(eg.getVolumes());

            // add host reference to export group
            for (Host host : hosts) {
                if (!updatedHosts.contains(host.getId())) {
                    updatedHosts.add(host.getId());
                }

                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, host.getId());
                List<Initiator> validInitiators = ComputeSystemHelper.validatePortConnectivity(_dbClient, eg, hostInitiators);
                if (!validInitiators.isEmpty()) {
                    // if the initiators is not already in the list add it.
                    for (Initiator initiator : validInitiators) {
                        if (!updatedInitiators.contains(initiator.getId())) {
                            updatedInitiators.add(initiator.getId());
                        }
                    }
                }
            }

            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", eg.getId()), waitFor,
                    eg.getId(), eg.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(eg.getId(), updatedVolumesMap,
                            updatedClusters, updatedHosts, updatedInitiators),
                    null, null);
        }

        if (isVcenter) {
            Collection<URI> exportIds = Collections2.transform(exportGroups, CommonTransformerFunctions.fctnDataObjectToID());
            Map<URI, Collection<URI>> hostExports = Maps.newHashMap();
            for (URI host : hostIds) {
                hostExports.put(host, exportIds);
            }
            waitFor = this.attachAndMountVolumes(hostExports, waitFor, workflow);
        }

        return waitFor;
    }

    public String addStepForUpdatingHostAndInitiatorClusterReferences(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId,
            URI vCenterDataCenterId) {
        for (URI hostId : hostIds) {
            waitFor = workflow.createStep(UPDATE_HOST_AND_INITIATOR_CLUSTER_NAMES_STEP,
                    String.format("Updating host and initiator cluster names for host %s to %s", hostId, clusterId), waitFor,
                    hostId, hostId.toString(),
                    this.getClass(),
                    updateHostAndInitiatorClusterReferencesMethod(hostId, clusterId, vCenterDataCenterId),
                    null, null);
        }
        return waitFor;
    }

    public String addStepsForIpInterfaceFileShares(Workflow workflow, String waitFor, URI hostId, URI ipId) {

        List<FileShare> fileShares = ComputeSystemHelper.getFileSharesByHost(_dbClient, hostId);
        IpInterface ipinterface = _dbClient.queryObject(IpInterface.class, ipId);
        List<String> endpoints = Arrays.asList(ipinterface.getIpAddress());

        for (FileShare fileShare : fileShares) {
            if (fileShare != null && fileShare.getFsExports() != null) {
                for (FileExport fileExport : fileShare.getFsExports().values()) {
                    if (fileExport != null && fileExport.getClients() != null) {
                        if (fileExportContainsEndpoint(fileExport, endpoints)) {
                            StorageSystem device = _dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());

                            List<String> clients = fileExport.getClients();
                            clients.removeAll(endpoints);
                            fileExport.setStoragePort(fileShare.getStoragePort().toString());
                            FileShareExport export = new FileShareExport(clients, fileExport.getSecurityType(),
                                    fileExport.getPermissions(),
                                    fileExport.getRootUserMapping(), fileExport.getProtocol(), fileExport.getStoragePortName(),
                                    fileExport.getStoragePort(), fileExport.getPath(), fileExport.getMountPath(), "", "");

                            if (clients.isEmpty()) {
                                _log.info("Unexporting file share " + fileShare.getId());
                                waitFor = workflow.createStep(UNEXPORT_FILESHARE_STEP,
                                        String.format("Unexport fileshare %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        unexportFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            } else {
                                _log.info("Updating export for file share " + fileShare.getId());
                                waitFor = workflow.createStep(UPDATE_FILESHARE_EXPORT_STEP,
                                        String.format("Update fileshare export %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        updateFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        null, null);
                            }
                        }
                    }
                }
            }
        }

        return waitFor;
    }

    protected <T extends Controller> T getController(Class<T> clazz, String hw) throws CoordinatorException {
        return _coordinator.locateService(
                clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    public static List<ExportGroup> getSharedExports(DbClient _dbClient, URI clusterId) {
        Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
        if (cluster == null) {
            return Lists.newArrayList();
        }
        return CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, ExportGroup.class,
                AlternateIdConstraint.Factory.getConstraint(
                        ExportGroup.class, "clusters", cluster.getId().toString()));
    }

    /**
     * Returns true if the file export contains any of the provided endpoints
     * 
     * @param fileExport
     * @param endpoints
     * @return true if file export contains any of the endpoints, else false
     */
    private boolean fileExportContainsEndpoint(FileExport fileExport, List<String> endpoints) {
        for (String endpoint : endpoints) {
            if (fileExport.getClients().contains(endpoint)) {
                return true;
            }
        }
        return false;
    }

    public String addStepsForExportGroups(Workflow workflow, String waitFor, URI hostId) {

        List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);

        for (ExportGroup export : getExportGroups(_dbClient, hostId, hostInitiators)) {
            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            updatedHosts.remove(hostId);

            for (Initiator initiator : hostInitiators) {
                updatedInitiators.remove(initiator.getId());
            }

            if ((updatedInitiators.isEmpty() && export.getType().equals(ExportGroupType.Initiator.name())) ||
                    (updatedHosts.isEmpty() && export.getType().equals(ExportGroupType.Host.name()))) {
                waitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                        String.format("Deleting export group %s", export.getId()), waitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        deleteExportGroupMethod(export.getId()),
                        null, null);
            } else {
                waitFor = workflow.createStep(
                        UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()),
                        waitFor,
                        export.getId(),
                        export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(), updatedInitiators.isEmpty() ? new HashMap<URI, Integer>()
                                : updatedVolumesMap,
                                updatedClusters, updatedHosts, updatedInitiators),
                        null, null);
            }
        }
        return waitFor;
    }

    public Workflow.Method updateExportGroupMethod(URI exportGroupURI, Map<URI, Integer> newVolumesMap,
            Collection<URI> newClusters, Collection<URI> newHosts, Collection<URI> newInitiators) {
        return new Workflow.Method("updateExportGroup", exportGroupURI, newVolumesMap,
                newClusters, newHosts, newInitiators);
    }

    public void updateExportGroup(URI exportGroup, Map<URI, Integer> newVolumesMap,
            List<URI> newClusters, List<URI> newHosts, List<URI> newInitiators, String stepId) throws Exception {
        Map<URI, Integer> addedBlockObjects = new HashMap<URI, Integer>();
        Map<URI, Integer> removedBlockObjects = new HashMap<URI, Integer>();
        ExportGroup exportGroupObject = _dbClient.queryObject(ExportGroup.class, exportGroup);
        ExportUtils.getAddedAndRemovedBlockObjects(newVolumesMap, exportGroupObject, addedBlockObjects, removedBlockObjects);
        BlockExportController blockController = getController(BlockExportController.class, BlockExportController.EXPORT);

        try {
            if (exportGroupObject.checkInternalFlags(DataObject.Flag.TASK_IN_PROGRESS)) {
                throw new Exception("Export group is being updated by another operation");
            }
            exportGroupObject.addInternalFlags(DataObject.Flag.TASK_IN_PROGRESS);
            _dbClient.updateObject(exportGroupObject);

            _dbClient.createTaskOpStatus(ExportGroup.class, exportGroup,
                    stepId, ResourceOperationTypeEnum.UPDATE_EXPORT_GROUP);
            blockController.exportGroupUpdate(exportGroup, addedBlockObjects, removedBlockObjects, newClusters,
                    newHosts, newInitiators, stepId);
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    public Workflow.Method updateFileShareMethod(URI deviceId, String systemType, URI fileShareId, FileShareExport export) {
        return new Workflow.Method("updateFileShare", deviceId, systemType, fileShareId, export);
    }

    public void updateFileShare(URI deviceId, String systemType, URI fileShareId, FileShareExport export, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        FileController fileController = getController(FileController.class, systemType);
        FileShare fs = _dbClient.queryObject(FileShare.class, fileShareId);
        _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                stepId, ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM);
        fileController.export(deviceId, fileShareId, Arrays.asList(export), stepId);
        waitForAsyncFileExportTask(fileShareId, stepId);
    }

    public Workflow.Method unexportFileShareMethod(URI deviceId, String systemType, URI fileShareId, FileShareExport export) {
        return new Workflow.Method("unexportFileShare", deviceId, systemType, fileShareId, export);
    }

    public void unexportFileShare(URI deviceId, String systemType, URI fileShareId, FileShareExport export, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        FileController fileController = getController(FileController.class, systemType);
        FileShare fs = _dbClient.queryObject(FileShare.class, fileShareId);
        _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                stepId, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM);
        fileController.unexport(deviceId, fileShareId, Arrays.asList(export), stepId);
        waitForAsyncFileExportTask(fileShareId, stepId);
    }

    /**
     * Creates a workflow method to attach disks and mount datastores on a host for all volumes in a given export group
     * 
     * @param exportGroup
     *            export group that contains volumes
     * @param hostId
     *            host to attach and mount to
     * @param vcenter
     *            vcenter that the host belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the host belongs to
     * @return workflow method for attaching and mounting disks and datastores
     */
    public Workflow.Method attachAndMountMethod(URI exportGroup, URI hostId, URI vcenter, URI vcenterDatacenter) {
        return new Workflow.Method("attachAndMount", exportGroup, hostId, vcenter, vcenterDatacenter);
    }

    /**
     * Creates a workflow method to unmount datastores and detach disks from a host for all volumes in a given export
     * group
     * 
     * @param exportGroup
     *            export group that contains volumes
     * @param hostId
     *            host to unmount and detach from
     * @param vcenter
     *            vcenter that the host belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the host belongs to
     * @return workflow method for unmounting and detaching disks and datastores
     */
    public Workflow.Method unmountAndDetachMethod(URI exportGroup, URI hostId, URI vcenter, URI vcenterDatacenter) {
        return new Workflow.Method("unmountAndDetach", exportGroup, hostId, vcenter, vcenterDatacenter);
    }

    /**
     * Attaches and mounts every disk and datastore associated with the volumes in the export group.
     * For each volume in the export group, the associated disk is attached to the host and any datastores backed by the
     * volume are mounted
     * to the host.
     * 
     * @param exportGroupId
     *            export group that contains volumes
     * @param hostId
     *            host to attach and mount to
     * @param vcenterId
     *            vcenter that the host belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the host belongs to
     * @param stepId
     *            the id of the workflow step
     */
    public void attachAndMount(URI exportGroupId, URI hostId, URI vCenterId, URI vcenterDatacenter, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);

        try {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, vCenterId);
            VcenterDataCenter vCenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDatacenter);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupId);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);
            HostSystem hostSystem = api.findHostSystem(vCenterDataCenter.getLabel(), esxHost.getLabel());
            HostStorageAPI storageAPI = new HostStorageAPI(hostSystem);

            if (exportGroup != null && exportGroup.getVolumes() != null) {
                _log.info("Refreshing storage");
                storageAPI.refreshStorage();
                for (String volume : exportGroup.getVolumes().keySet()) {
                    BlockObject blockObject = BlockObject.fetch(_dbClient, URI.create(volume));
                    try {
                        for (HostScsiDisk entry : storageAPI.listScsiDisks()) {
                            if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), blockObject.getWWN())) {
                                _log.info("Attach SCSI Lun " + entry.getCanonicalName() + " on host " + esxHost.getLabel());
                                storageAPI.attachScsiLun(entry);
                            }
                        }
                    } catch (VMWareException ex) {
                        _log.warn(ex.getMessage(), ex);
                    }

                    storageAPI.getStorageSystem().rescanVmfs();

                    if (blockObject != null && blockObject.getTag() != null) {
                        for (ScopedLabel tag : blockObject.getTag()) {
                            String tagValue = tag.getLabel();
                            if (tagValue != null && tagValue.startsWith(VMFS_DATASTORE_PREFIX)) {
                                String datastoreName = getDatastoreName(tagValue);
                                try {
                                    Datastore datastore = api.findDatastore(vCenterDataCenter.getLabel(), datastoreName);
                                    if (datastore != null) {
                                        _log.info("Mounting datastore " + datastore.getName() + " on host " + esxHost.getLabel());
                                        storageAPI.mountDatastore(datastore);
                                    }
                                } catch (VMWareException ex) {
                                    _log.warn(ex.getMessage(), ex);
                                }
                            }
                        }
                    }
                }
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    /**
     * Verifies that datastores contained within an export group can be unmounted. It must not be entering maintenance mode or contain any
     * virtual machines.
     * 
     * @param exportGroup
     *            export group that contains volumes
     * @param vcenter
     *            vcenter that the datastore belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the datastore belongs to
     * @return workflow method for unmounting and detaching disks and datastores
     */
    public Workflow.Method verifyDatastoreMethod(URI exportGroup, URI vcenter, URI vcenterDatacenter) {
        return new Workflow.Method("verifyDatastore", exportGroup, vcenter, vcenterDatacenter);
    }

    /**
     * Verifies that datastores contained within an export group can be unmounted. It must not be entering maintenance mode or contain any
     * virtual machines.
     * 
     * @param exportGroupId
     *            export group that contains volumes
     * @param vcenterId
     *            vcenter that the host belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the host belongs to
     * @param stepId
     *            the id of the workflow step
     */
    public void verifyDatastore(URI exportGroupId, URI vCenterId, URI vcenterDatacenter, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);

        try {
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, vCenterId);
            VcenterDataCenter vCenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDatacenter);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupId);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);

            if (exportGroup != null && exportGroup.getVolumes() != null) {
                for (String volume : exportGroup.getVolumes().keySet()) {

                    BlockObject blockObject = BlockObject.fetch(_dbClient, URI.create(volume));
                    if (blockObject != null && blockObject.getTag() != null) {
                        for (ScopedLabel tag : blockObject.getTag()) {
                            String tagValue = tag.getLabel();
                            if (tagValue != null && tagValue.startsWith(VMFS_DATASTORE_PREFIX)) {
                                String datastoreName = getDatastoreName(tagValue);
                                Datastore datastore = api.findDatastore(vCenterDataCenter.getLabel(), datastoreName);
                                if (datastore != null) {
                                    ComputeSystemHelper.verifyDatastore(datastore, api);
                                }
                            }
                        }
                    }
                }
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    /**
     * Unmounts and detaches every datastore and disk associated with the volumes in the export group.
     * For each volume in the export group, the backed datastore is unmounted and the associated disk is detached from
     * the host.
     * 
     * @param exportGroupId
     *            export group that contains volumes
     * @param hostId
     *            host to attach and mount to
     * @param vcenterId
     *            vcenter that the host belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the host belongs to
     * @param stepId
     *            the id of the workflow step
     */
    public void unmountAndDetach(URI exportGroupId, URI hostId, URI vCenterId, URI vcenterDatacenter, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);

        try {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, vCenterId);
            VcenterDataCenter vCenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDatacenter);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupId);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);
            HostSystem hostSystem = api.findHostSystem(vCenterDataCenter.getLabel(), esxHost.getLabel());
            HostStorageAPI storageAPI = new HostStorageAPI(hostSystem);

            if (exportGroup != null && exportGroup.getVolumes() != null) {
                for (String volume : exportGroup.getVolumes().keySet()) {

                    BlockObject blockObject = BlockObject.fetch(_dbClient, URI.create(volume));
                    if (blockObject != null && blockObject.getTag() != null) {
                        for (ScopedLabel tag : blockObject.getTag()) {
                            String tagValue = tag.getLabel();
                            if (tagValue != null && tagValue.startsWith(VMFS_DATASTORE_PREFIX)) {
                                String datastoreName = getDatastoreName(tagValue);

                                Datastore datastore = api.findDatastore(vCenterDataCenter.getLabel(), datastoreName);
                                if (datastore != null) {
                                    boolean storageIOControlEnabled = datastore.getIormConfiguration().isEnabled();
                                    if (storageIOControlEnabled) {
                                        setStorageIOControl(api, datastore, false);
                                    }
                                    _log.info("Unmount datastore " + datastore.getName() + " from host " + esxHost.getLabel());
                                    storageAPI.unmountVmfsDatastore(datastore);
                                    if (storageIOControlEnabled) {
                                        setStorageIOControl(api, datastore, true);
                                    }
                                }
                            }
                        }
                    }
                    for (HostScsiDisk entry : storageAPI.listScsiDisks()) {
                        if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), blockObject.getWWN())) {
                            _log.info("Detach SCSI Lun " + entry.getCanonicalName() + " from host " + esxHost.getLabel());
                            storageAPI.detachScsiLun(entry);
                        }
                    }
                    storageAPI.refreshStorage();

                }
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    /**
     * Sets the Storage I/O control on a datastore
     * 
     * @param vcenter
     *            vcenter API for the vcenter
     * @param datastore
     *            the datastore to set storage I/O control
     * @param enabled
     *            if true, enables storage I/O control, otherwise disables storage I/O control
     */
    public void setStorageIOControl(VCenterAPI vcenter, Datastore datastore, boolean enabled) {
        StorageResourceManager manager = vcenter.getStorageResourceManager();
        StorageIORMConfigSpec spec = new StorageIORMConfigSpec();
        spec.setEnabled(enabled);

        Task task = null;
        try {
            _log.info("Setting Storage I/O to " + enabled + " on datastore " + datastore.getName());
            task = manager.configureDatastoreIORM_Task(datastore, spec);
            boolean cancel = false;
            long maxTime = System.currentTimeMillis() + (60 * 1000);
            while (!isComplete(task)) {
                Thread.sleep(5000);

                if (System.currentTimeMillis() > maxTime) {
                    cancel = true;
                    break;
                }
            }

            if (cancel) {
                cancelTask(task);
            }
        } catch (Exception e) {
            _log.error("Error setting storage i/o control");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            cancelTaskNoException(task);
        }
    }

    /**
     * Cancels the VMWare Task without throwing an exception
     * 
     * @param task
     *            the task to cancel
     */
    public void cancelTaskNoException(Task task) {
        try {
            cancelTask(task);
        } catch (Exception e) {
            _log.error("Error when cancelling VMware task");
        }
    }

    /**
     * Cancels a VMWare task
     * 
     * @param task
     *            the task to cancel
     * @throws Exception
     *             if an error occurs during task cancellation
     */
    public void cancelTask(Task task) throws Exception {
        if (task == null || task.getTaskInfo() == null) {
            _log.warn("VMware task is null or has no task info. Unable to cancel it.");
        } else {
            TaskInfoState state = task.getTaskInfo().getState();
            if (state == TaskInfoState.queued || state == TaskInfoState.running) {
                task.cancelTask();
            }
        }
    }

    /**
     * Checks if the VMWare task has completed
     * 
     * @param task
     *            the task to check
     * @return true if the task has completed, otherwise returns false
     * @throws Exception
     *             if an error occurs while monitoring the task
     */
    private boolean isComplete(Task task) throws Exception {
        TaskInfo info = task.getTaskInfo();
        TaskInfoState state = info.getState();
        if (state == TaskInfoState.success) {
            return true;
        } else if (state == TaskInfoState.error) {
            return true;
        }
        return false;
    }

    public Workflow.Method deleteExportGroupMethod(URI exportGroupURI) {
        return new Workflow.Method("deleteExportGroup", exportGroupURI);
    }

    public void deleteExportGroup(URI exportGroup, String stepId) {
        BlockExportController blockController = getController(BlockExportController.class, BlockExportController.EXPORT);
        _dbClient.createTaskOpStatus(ExportGroup.class, exportGroup,
                stepId, ResourceOperationTypeEnum.DELETE_EXPORT_GROUP);
        blockController.exportGroupDelete(exportGroup, stepId);
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

    @Override
    public void detachClusterStorage(URI cluster, boolean deactivateOnComplete, boolean checkVms, String taskId)
            throws InternalException {
        TaskCompleter completer = null;
        try {
            completer = new ClusterCompleter(cluster, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_CLUSTER_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            if (checkVms) {
                waitFor = computeDeviceController.addStepsVcenterClusterCleanup(workflow, waitFor, cluster);
            }

            waitFor = addStepsForClusterExportGroups(workflow, waitFor, cluster);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachClusterStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    public String addStepsForClusterExportGroups(Workflow workflow, String waitFor, URI clusterId) {

        List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, ExportGroup.class,
                AlternateIdConstraint.Factory.getConstraint(
                        ExportGroup.class, "clusters", clusterId.toString()));

        for (ExportGroup export : exportGroups) {

            List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            updatedClusters.remove(clusterId);

            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterId, Host.class, "cluster");
            for (URI hosturi : hostUris) {
                updatedHosts.remove(hosturi);
                updatedInitiators.removeAll(ComputeSystemHelper.getChildrenUris(_dbClient, hosturi, Initiator.class, "host"));
            }

            if (updatedInitiators.isEmpty()) {
                waitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                        String.format("Deleting export group %s", export.getId()), waitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        deleteExportGroupMethod(export.getId()),
                        null, null);
            } else {
                waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()), waitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(), updatedVolumesMap,
                                updatedClusters, updatedHosts, updatedInitiators),
                        null, null);
            }
        }
        return waitFor;
    }

    public Workflow.Method updateHostAndInitiatorClusterReferencesMethod(URI hostId, URI clusterId, URI vCenterDataCenterId) {
        return new Workflow.Method("updateHostAndInitiatorClusterReferences", hostId, clusterId, vCenterDataCenterId);
    }

    public void updateHostAndInitiatorClusterReferences(URI hostId, URI clusterId, URI vCenterDataCenterId, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        ComputeSystemHelper.updateHostAndInitiatorClusterReferences(_dbClient, clusterId, hostId);
        ComputeSystemHelper.updateHostVcenterDatacenterReference(_dbClient, hostId, vCenterDataCenterId);
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Waits for the file export or unexport task to complete.
     * This is required because FileDeviceController does not use a workflow.
     * 
     * @param fileShareId
     *            id of the FileShare being exported
     * @param stepId
     *            id of the workflow step
     */
    private void waitForAsyncFileExportTask(URI fileShareId, String stepId) {
        boolean done = false;
        try {
            while (!done) {
                Thread.sleep(1000);
                FileShare fsObj = _dbClient.queryObject(FileShare.class, fileShareId);
                if (fsObj.getOpStatus().containsKey(stepId)) {
                    Operation op = fsObj.getOpStatus().get(stepId);
                    if (op.getStatus().equalsIgnoreCase("ready")) {
                        WorkflowStepCompleter.stepSucceded(stepId);
                        done = true;
                    } else if (op.getStatus().equalsIgnoreCase("error")) {
                        WorkflowStepCompleter.stepFailed(stepId, op.getServiceError());
                        done = true;
                    }
                }
            }
        } catch (InterruptedException ex) {
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    private ExportGroupState getExportGroupState(Map<URI, ExportGroupState> exportGroups, ExportGroup export) {
        if (!exportGroups.containsKey(export.getId())) {
            ExportGroupState egh = new ExportGroupState(export.getId());
            exportGroups.put(export.getId(), egh);
        }
        return exportGroups.get(export.getId());
    }

    @Override
    public void processHostChanges(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters, boolean isVCenter,
            String taskId) {
        // Host changes are processed using AbstractDiscoveryAdapter
    }

    /**
     * Creates workflow steps for unmounting datastores and detaching disks
     * 
     * @param vCenterHostExportMap
     *            the map of hosts and export groups to operate on
     * @param waitFor
     *            the step to wait on for this workflow step
     * @param workflow
     *            the workflow to create the step
     * @return the step id
     */
    private String unmountAndDetachVolumes(Map<URI, Collection<URI>> vCenterHostExportMap, URI virtualDataCenter, String waitFor,
            Workflow workflow) {
        if (vCenterHostExportMap == null) {
            return waitFor;
        }
        for (URI hostId : vCenterHostExportMap.keySet()) {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            if (esxHost != null) {
                VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, virtualDataCenter);
                if (vcenterDataCenter != null) {
                    URI vCenterId = vcenterDataCenter.getVcenter();

                    for (URI export : vCenterHostExportMap.get(hostId)) {
                        waitFor = workflow.createStep(UNMOUNT_AND_DETACH_STEP,
                                String.format("Unmounting and detaching volumes from export group %s", export), waitFor,
                                export, export.toString(),
                                this.getClass(),
                                unmountAndDetachMethod(export, esxHost.getId(), vCenterId,
                                        vcenterDataCenter.getId()),
                                rollbackMethodNullMethod(), null);
                    }
                }
            }
        }
        return waitFor;
    }

    /**
     * Verifies that datastores contained within an export group can be unmounted. It must not be entering maintenance mode or contain any
     * virtual machines.
     * 
     * @param vCenterHostExportMap
     *            the map of hosts and export groups to operate on
     * @param virtualDataCenter
     *            the datacenter that the hosts belong to
     * @param waitFor
     *            the step to wait on for this workflow step
     * @param workflow
     *            the workflow to create the step
     * @return the step id
     */
    private String verifyDatastoreForRemoval(Map<URI, Collection<URI>> vCenterHostExportMap, URI virtualDataCenter, String waitFor,
            Workflow workflow) {
        if (vCenterHostExportMap == null) {
            return waitFor;
        }
        String wait = waitFor;
        for (URI hostId : vCenterHostExportMap.keySet()) {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            if (esxHost != null) {
                VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, virtualDataCenter);
                if (vcenterDataCenter != null) {
                    URI vCenterId = vcenterDataCenter.getVcenter();

                    for (URI export : vCenterHostExportMap.get(hostId)) {
                        wait = workflow.createStep(VERIFY_DATASTORE_STEP,
                                String.format("Verifying datastores for removal from export %s", export), wait,
                                export, export.toString(),
                                this.getClass(),
                                verifyDatastoreMethod(export, vCenterId,
                                        vcenterDataCenter.getId()),
                                rollbackMethodNullMethod(), null);
                    }
                }
            }
        }
        return wait;
    }

    /**
     * Creates workflow steps for attaching disks and mounting datastores
     * 
     * @param vCenterHostExportMap
     *            the map of hosts and export groups to operate on
     * @param waitFor
     *            the step to wait on for this workflow step
     * @param workflow
     *            the workflow to create the step
     * @return the step id
     */
    private String attachAndMountVolumes(Map<URI, Collection<URI>> vCenterHostExportMap, String waitFor, Workflow workflow) {
        if (vCenterHostExportMap == null) {
            return waitFor;
        }

        for (URI hostId : vCenterHostExportMap.keySet()) {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            if (esxHost != null) {
                URI virtualDataCenter = esxHost.getVcenterDataCenter();
                VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, virtualDataCenter);
                URI vCenterId = vcenterDataCenter.getVcenter();

                for (URI export : vCenterHostExportMap.get(hostId)) {
                    waitFor = workflow.createStep(MOUNT_AND_ATTACH_STEP,
                            String.format("Mounting and attaching volumes from export group %s", export), waitFor,
                            export, export.toString(),
                            this.getClass(),
                            attachAndMountMethod(export, esxHost.getId(), vCenterId,
                                    vcenterDataCenter.getId()),
                            rollbackMethodNullMethod(), null);
                }
            }
        }
        return waitFor;
    }

    /**
     * Adds the host and export to a map of host -> list of export groups
     * 
     * @param vCenterHostExportMap
     *            the map to add the host and export
     * @param hostId
     *            the host id
     * @param export
     *            the export group id
     */
    private void addVcenterHost(Map<URI, Collection<URI>> vCenterHostExportMap, URI hostId, URI export) {
        if (vCenterHostExportMap != null) {
            if (!vCenterHostExportMap.containsKey(hostId)) {
                vCenterHostExportMap.put(hostId, Lists.newArrayList());
            }
            vCenterHostExportMap.get(hostId).add(export);
        }
    }

    private String generateSteps(ExportGroupState export, String waitFor, Workflow workflow, boolean add) {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, export.getId());

        if (add) {
            export.getAddDiff(StringSetUtil.stringSetToUriList(exportGroup.getInitiators()),
                    StringSetUtil.stringSetToUriList(exportGroup.getHosts()),
                    StringSetUtil.stringSetToUriList(exportGroup.getClusters()),
                    StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes()));
        } else {
            export.getRemoveDiff(StringSetUtil.stringSetToUriList(exportGroup.getInitiators()),
                    StringSetUtil.stringSetToUriList(exportGroup.getHosts()),
                    StringSetUtil.stringSetToUriList(exportGroup.getClusters()),
                    StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes()));

        }

        _log.info("ExportGroupState for " + export.getId() + " = " + export);

        if (export.getInitiators().isEmpty()) {
            waitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                    String.format("Deleting export group %s", export.getId()), waitFor,
                    export.getId(), export.getId().toString(),
                    this.getClass(),
                    deleteExportGroupMethod(export.getId()),
                    null, null);
        } else {
            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", export.getId()), waitFor,
                    export.getId(), export.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(export.getId(), export.getVolumesMap(),
                            export.getClusters(), export.getHosts(), export.getInitiators()),
                    null, null);
        }

        return waitFor;
    }

    /**
     * Gets the datastore name from the tag supplied by the volume
     * 
     * @param tag
     *            the volume tag
     * @return the datastore name
     */
    public static String getDatastoreName(String tag) {
        if (tag != null) {
            Matcher matcher = MACHINE_TAG_REGEX.matcher(tag);
            if (matcher.matches()) {
                return matcher.group(2);
            }
        }
        return null;
    }
    public String addStepsForBootVolume(Workflow workflow,  URI hostId, URI volumeId) {
        String waitFor = null;
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host != null && host.getComputeElement() != null) {
           _log.info("Generating steps for setting boot volume associstion");
           waitFor = workflow.createStep(null,
                   "Set Boot Volume Association", waitFor, host.getId(), host.getLabel(),
                        this.getClass(), new Workflow.Method("setHostBootVolume", hostId, volumeId),
                        new Workflow.Method("rollbackHostBootVolume", hostId, volumeId), null);
        }
        return waitFor;
    }

    public void setHostBootVolume(URI hostId, URI volumeId, String stepId) throws ControllerException {
        _log.info("setHostBootVolume :"+ hostId.toString());
        Host host = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            host = _dbClient.queryObject(Host.class, hostId);
            if (host == null) {
                throw ComputeSystemControllerException.exceptions.hostNotFound(hostId.toString());
            }

            _log.info("Setting boot volume association for host");
            host.setBootVolumeId(volumeId);
            _dbClient.persistObject(host);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e){
            _log.error("unexpected exception: " + e.getMessage(), e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToSetBootVolume(
                    host != null ? host.getHostName() : hostId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }        

   }
   public void rollbackHostBootVolume(URI hostId, URI volumeId, String stepId) throws ControllerException {
        _log.info("rollbackHostBootVolume:"+ hostId.toString());
        Host host = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            host = _dbClient.queryObject(Host.class, hostId);
            if (host == null) {
                throw ComputeSystemControllerException.exceptions.hostNotFound(hostId.toString());
            }

            _log.info("Rolling back boot volume association for host");
            host.setBootVolumeId(NullColumnValueGetter.getNullURI());
            _dbClient.persistObject(host);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e){
            _log.error("unexpected exception: " + e.getMessage(), e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToRollbackBootVolume(
                    host != null ? host.getHostName() : hostId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

   }

    public String addStepsForSanBootTargets(Workflow workflow,  URI hostId, URI volumeId, String waitFor) {
        String newWaitFor = null;
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host != null && host.getComputeElement() != null) {
           _log.info("Generating steps for San Boot Targets");
           newWaitFor = workflow.createStep(null,
                   "Set UCS san boot targets for the host", waitFor, host.getId(), host.getLabel(), 
                        this.getClass(), new Workflow.Method("setHostSanBootTargets", hostId, volumeId), 
                        new Workflow.Method(ROLLBACK_METHOD_NULL), null);
        }
        return newWaitFor;
    }

    public void setHostSanBootTargets(URI hostId, URI volumeId, String stepId) throws ControllerException {
        Host host = null;
        try {
           WorkflowStepCompleter.stepExecuting(stepId);
           host = _dbClient.queryObject(Host.class, hostId);
           if (host == null) {
               throw ComputeSystemControllerException.exceptions.hostNotFound(hostId.toString());
           }

           if (host.getComputeElement() != null) {
               ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class, host.getComputeElement());

               if (computeElement != null) {
                   computeDeviceController
                        .setSanBootTarget(computeElement.getComputeSystem(), computeElement.getId(), hostId, volumeId, false);
               }
           }
           WorkflowStepCompleter.stepSucceded(stepId);
        }catch (Exception e){
            _log.error("unexpected exception: " + e.getMessage(), e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToSetSanBootTargets(
                    host != null ? host.getHostName() : hostId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

    }

    public String addStepsForMountDevice(Workflow workflow, HostDeviceInputOutput args) {
        String waitFor = null; // the wait for key returned by previous call
        _log.info("Generating steps for mounting device");
        // create a step
        String hostname = _dbClient.queryObject(Host.class, args.getHostId()).getHostName();
        String hostType = getHostType(args.getHostId());
        waitFor = workflow.createStep(null,
                String.format("Verifying mount point: %s for host: %s", args.getMountPath(), hostname),
                null, args.getHostId(),
                hostType,
                this.getClass(),
                verifyMountPointMethod(args),
                rollbackMethodNullMethod(), null);

        waitFor = workflow.createStep(null,
                String.format("Creating Directory: %s for host: %s", args.getMountPath(), hostname),
                waitFor, args.getHostId(),
                hostType,
                this.getClass(),
                createDirectoryMethod(args),
                createDirectoryRollBackMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Adding to etc/fstab:%n%s for host: %s", args.getMountPath(), hostname),
                waitFor, args.getHostId(),
                hostType,
                this.getClass(),
                addtoFSTabMethod(args),
                removeFromFSTabMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Mounting device:%n%s for host: %s", args.getResId(), hostname),
                waitFor, args.getHostId(),
                hostType,
                this.getClass(),
                mountDeviceMethod(args),
                unmountDeviceMethod(args), null);
        return waitFor;
    }

    public String addStepsForUnmountDevice(Workflow workflow, HostDeviceInputOutput args) {
        String waitFor = null; // the wait for key returned by previous call
        FileMountInfo fsMount = getMountInfo(args.getHostId(), args.getMountPath(), args.getResId());
        args.setFsType(fsMount.getFsType());
        args.setSecurity(fsMount.getSecurityType());
        args.setSubDirectory(fsMount.getSubDirectory());
        _log.info("Generating steps for mounting device");
        // create a step
        String hostname = _dbClient.queryObject(Host.class, args.getHostId()).getHostName();
        String hostType = getHostType(args.getHostId());
        waitFor = workflow.createStep(null,
                String.format("Unmounting device: %s for host: %s", args.getMountPath(), hostname),
                null, args.getHostId(),
                hostType,
                this.getClass(),
                unmountDeviceMethod(args),
                unmountRollBackMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("removing from etc/fstab:%n%s for host: %s", args.getMountPath(), hostname),
                waitFor, args.getHostId(),
                hostType,
                this.getClass(),
                removeFromFSTabMethod(args),
                removeFromFSTabRollBackMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Delete Directory:%n%s for host: %s", args.getMountPath(), hostname),
                waitFor, args.getHostId(),
                hostType,
                this.getClass(),
                deleteDirectoryMethod(args),
                createDirectoryMethod(args), null);
        return waitFor;
    }

    public void createDirectory(URI hostId, String mountPath, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.createDirectory(hostId, mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void addToFSTab(URI hostId, String mountPath, URI resId, String subDirectory, String security, String fsType, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.addToFSTab(hostId, mountPath, resId, subDirectory, security, fsType);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void mount(URI resId, URI hostId, String mountPath, String subDir, String security, String fsType, String stepId) {
        mountDir(resId, hostId, mountPath, subDir, security, fsType, stepId);
        createMountDBEntry(resId, hostId, mountPath, subDir, security, fsType);
    }

    public void verifyMountPoint(URI hostId, String mountPath, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.verifyMountPoint(hostId, mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void deleteDirectory(URI resId, URI hostId, String mountPath, String stepId) {
        deleteDir(resId, hostId, mountPath, stepId);
        removeMountDBEntry(resId, hostId, mountPath);
    }

    public void removeFromFSTab(URI hostId, String mountPath, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.removeFromFSTab(hostId, mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void unmount(URI hostId, String mountPath, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.unmountDevice(hostId, mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public FileMountInfo getMountInfo(URI hostId, String mountPath, URI resId) {
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileMountsConstraint(resId);
        List<FileMountInfo> fsDBMounts = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileMountInfo.class,
                containmentConstraint);

        if (fsDBMounts != null && !fsDBMounts.isEmpty()) {
            for (FileMountInfo dbMount : fsDBMounts) {
                if (dbMount.getHostId().toString().equalsIgnoreCase(hostId.toString())
                        && dbMount.getMountPath().equalsIgnoreCase(mountPath)) {
                    _log.debug("Found DB entry with mountpath {} " + mountPath);
                    return dbMount;
                }
            }
        }
        return null;
    }

    public void removeFromFSTabRollBack(URI hostId, String mountPath, URI resId, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            FileMountInfo fsMount = getMountInfo(hostId, mountPath, resId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.addToFSTab(hostId, mountPath, resId, fsMount.getSubDirectory(), fsMount.getSecurityType(), "auto");
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void deleteDir(URI resId, URI hostId, String mountPath, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.deleteDirectory(hostId, mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void unmountRollBack(URI resId, URI hostId, String mountPath, String subDir, String security, String fsType, String stepId) {
        mountDir(resId, hostId, mountPath, subDir, security, fsType, stepId);
    }

    public void mountDir(URI resId, URI hostId, String mountPath, String subDir, String security, String fsType, String stepId) {
        try {
            HostMountAdapter adapter = getMountAdapters().get(_dbClient.queryObject(Host.class, hostId).getType());
            WorkflowStepCompleter.stepExecuting(stepId);
            List<String> lockKeys = new ArrayList<String>();
            lockKeys.add(ControllerLockingUtil.getMountHostKey(_dbClient, hostId));
            _workflowService.acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.FILE_MOUNT_OPERATIONS));
            adapter.mountDevice(hostId, mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        } catch (Exception ex) {
            WorkflowStepCompleter.stepFailed(stepId, APIException.badRequests.commandFailedToComplete(ex.getMessage()));
            throw ex;
        }
    }

    public void createDirectoryRollBack(URI resId, URI hostId, String mountPath, String stepId) {
        deleteDir(resId, hostId, mountPath, stepId);
    }

    public Method createDirectoryMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("createDirectory", args.getHostId(), args.getMountPath());
    }

    public Method addtoFSTabMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("addToFSTab", args.getHostId(), args.getMountPath(), args.getResId(), args.getSubDirectory(),
                args.getSecurity(), args.getFsType());
    }

    public Method mountDeviceMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("mount", args.getResId(), args.getHostId(), args.getMountPath(), args.getSubDirectory(),
                args.getSecurity(), args.getFsType());
    }

    public Method verifyMountPointMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("verifyMountPoint", args.getHostId(), args.getMountPath());
    }

    public Method unmountDeviceMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("unmount", args.getHostId(), args.getMountPath());
    }

    public Method removeFromFSTabMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("removeFromFSTab", args.getHostId(), args.getMountPath());
    }

    public Method removeFromFSTabRollBackMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("removeFromFSTabRollBack", args.getHostId(), args.getMountPath(), args.getResId());
    }

    public Method deleteDirectoryMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("deleteDirectory", args.getResId(), args.getHostId(), args.getMountPath());
    }

    public Method createDirectoryRollBackMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("createDirectoryRollBack", args.getResId(), args.getHostId(), args.getMountPath());
    }

    public Method unmountRollBackMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("unmountRollBack", args.getResId(), args.getHostId(), args.getMountPath(), args.getSubDirectory(),
                args.getSecurity(), args.getFsType());
    }

    /**
     * Get the deviceType for a StorageSystem.
     * 
     * @param deviceURI
     *            -- StorageSystem URI
     * @return deviceType String
     */
    public String getHostType(URI hostURI) throws ControllerException {
        Host host = _dbClient.queryObject(Host.class, hostURI);
        if (host == null) {
            throw DeviceControllerException.exceptions.getDeviceTypeFailed(hostURI.toString());
        }
        return host.getType();
    }

    private void createMountDBEntry(URI resId, URI hostId, String mountPath, String subDir,
            String security, String fsType) {
        FileMountInfo fsMount = new FileMountInfo();
        fsMount.setId(URIUtil.createId(FileMountInfo.class));
        fsMount.setFsId(resId);
        fsMount.setFsType(fsType);
        fsMount.setHostId(hostId);
        fsMount.setMountPath(mountPath);
        fsMount.setSecurityType(security);
        fsMount.setSubDirectory(subDir);
        _log.debug("Storing New DB Mount Info {}" + fsMount);
        _dbClient.createObject(fsMount);

    }

    private void removeMountDBEntry(URI resId, URI hostId, String mountPath) {
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileMountsConstraint(resId);
        List<FileMountInfo> fsDBMounts = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileMountInfo.class,
                containmentConstraint);
        if (fsDBMounts != null && !fsDBMounts.isEmpty()) {
            for (FileMountInfo dbMount : fsDBMounts) {
                if (dbMount.getHostId().toString().equalsIgnoreCase(hostId.toString())
                        && dbMount.getMountPath().equalsIgnoreCase(mountPath)) {
                    _log.debug("Found DB entry with mountpath {} " + mountPath);
                    // Deactivate the entry!!
                    dbMount.setInactive(true);
                    _dbClient.updateObject(dbMount);

                }
            }
        }
    }

    @Override
    public void addInitiatorsToExport(URI host, List<URI> init, String taskId) throws ControllerException {
        addInitiatorsToExport(NullColumnValueGetter.getNullURI(), host, init, taskId);
    }

    @Override
    public void removeInitiatorFromExport(URI host, URI init, String taskId) throws ControllerException {
        removeInitiatorFromExport(NullColumnValueGetter.getNullURI(), host, init, taskId);

    }

    @Override
    public void removeInitiatorsFromExport(URI host, List<URI> init, String taskId) throws ControllerException {
        removeInitiatorsFromExport(NullColumnValueGetter.getNullURI(), host, init, taskId);

    }

    @Override
    public void addHostsToExport(List<URI> hostId, URI clusterId, String taskId, URI oldCluster, boolean isVcenter)
            throws ControllerException {
        addHostsToExport(NullColumnValueGetter.getNullURI(), hostId, clusterId, taskId, oldCluster, isVcenter);

    }

    @Override
    public void removeHostsFromExport(List<URI> hostId, URI clusterId, boolean isVcenter, URI vCenterDataCenterId, String taskId)
            throws ControllerException {
        removeHostsFromExport(NullColumnValueGetter.getNullURI(), hostId, clusterId, isVcenter, vCenterDataCenterId, taskId);

    }
}
