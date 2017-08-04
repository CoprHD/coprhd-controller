/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.computecontroller.impl.ComputeDeviceController;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.hostmountadapters.HostDeviceInputOutput;
import com.emc.storageos.computesystemcontroller.hostmountadapters.HostMountAdapter;
import com.emc.storageos.computesystemcontroller.impl.InitiatorCompleter.InitiatorOperation;
import com.emc.storageos.computesystemcontroller.impl.adapter.ExportGroupState;
import com.emc.storageos.computesystemcontroller.impl.adapter.HostStateChange;
import com.emc.storageos.computesystemcontroller.impl.adapter.VcenterDiscoveryAdapter;
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
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileMountInfo;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.Volume;
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
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportUpdateCompleter;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.iwave.ext.linux.util.VolumeWWNUtils;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.StorageIORMConfigSpec;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VmfsDatastoreInfo;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.StorageResourceManager;
import com.vmware.vim25.mo.Task;

public class ComputeSystemControllerImpl implements ComputeSystemController {

    private static final Logger _log = LoggerFactory.getLogger(ComputeSystemControllerImpl.class);

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
    private static final String UPDATE_HOST_INITIATOR_WF_NAME = "UPDATE_HOST_INITIATOR_WORKFLOW";

    private static final String DETACH_HOST_STORAGE_WF_NAME = "DETACH_HOST_STORAGE_WORKFLOW";
    private static final String DETACH_CLUSTER_STORAGE_WF_NAME = "DETACH_CLUSTER_STORAGE_WORKFLOW";
    private static final String DETACH_VCENTER_STORAGE_WF_NAME = "DETACH_VCENTER_STORAGE_WORKFLOW";
    private static final String DETACH_VCENTER_DATACENTER_STORAGE_WF_NAME = "DETACH_VCENTER_DATACENTER_STORAGE_WORKFLOW";

    private static final String RESCAN_HOST_STORAGE_STEP = "RescanHostStorageStep";

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
    private static final String REMOVE_HOST_FROM_CLUSTER_STEP = "removeHostFromClusterStep";

    private ComputeDeviceController computeDeviceController;
    private BlockStorageScheduler _blockScheduler;

    private Map<String, HostMountAdapter> _mountAdapters;

    private int MAXIMUM_RESCAN_ATTEMPTS = 5;

    private static long RESCAN_DELAY_MS = 10000; // 10 seconds

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

    private String addStepsForBootVolume(Workflow workflow,  URI hostId, URI volumeId) {
        String waitFor = null;
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host != null && host.getComputeElement() != null) {
            _log.info("Generating steps for setting boot volume associstion");
            waitFor = workflow.createStep(null,
                    "Validate Boot Volume export", waitFor, host.getId(), host.getLabel(),
                    this.getClass(), new Workflow.Method("validateBootVolumeExport", hostId, volumeId),
                    new Workflow.Method("rollbackMethodNull"), null);

            waitFor = workflow.createStep(null,
                    "Set Boot Volume Association", waitFor, host.getId(), host.getLabel(),
                    this.getClass(), new Workflow.Method("setHostBootVolumeId", hostId, volumeId),
                    new Workflow.Method("rollbackHostBootVolumeId", hostId, volumeId), null);
        }
        return waitFor;
    }

    public void validateBootVolumeExport(URI hostId, URI volumeId, String stepId) throws ControllerException {
        _log.info("validateBootVolumeExport :"+ hostId.toString()+" volume: "+ volumeId.toString());
        Host host = null;
        try{
            WorkflowStepCompleter.stepExecuting(stepId);

            host = _dbClient.queryObject(Host.class, hostId);
            if (host == null) {
                throw ComputeSystemControllerException.exceptions.hostNotFound(hostId.toString());
            }
            Volume volume = _dbClient.queryObject(Volume.class, volumeId);
            if (volume == null){
                throw ComputeSystemControllerException.exceptions.volumeNotFound(volumeId.toString());
            }
            boolean validExport = computeDeviceController.validateBootVolumeExport(hostId, volumeId);
            if (validExport){
                WorkflowStepCompleter.stepSucceded(stepId);
            }else{
                ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.invalidBootVolumeExport(host.getLabel(),volume.getLabel());
                WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
            }
        } catch (Exception e){
            _log.error("unexpected exception: " + e.getMessage(), e);
            String hostString = hostId.toString();
            if (host!=null){
                hostString = host.getHostName();
            }
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToValidateBootVolumeExport(
                    hostString, volumeId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

    }

    public void setHostBootVolumeId(URI hostId, URI volumeId, String stepId) throws ControllerException {
        _log.info("setHostBootVolumeId :"+ hostId.toString());
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
            String hostString = hostId.toString();
            if (host!=null){
                hostString = host.getHostName();
            }
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToSetBootVolume(
                    hostString, e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

    }

    public void rollbackHostBootVolumeId(URI hostId, URI volumeId, String stepId) throws ControllerException {
        _log.info("rollbackHostBootVolumeId:"+ hostId.toString());
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
            String hostString = hostId.toString();
            if (host!=null){
                hostString = host.getHostName();
            }
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToRollbackBootVolume(
                    hostString, e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

    }

    private String addStepsForSanBootTargets(Workflow workflow,  URI hostId, URI volumeId, String waitFor) {
        String newWaitFor = null;
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host != null && !NullColumnValueGetter.isNullURI(host.getComputeElement())) {
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
                }else {
                    _log.error("Invalid compute element association");
                    throw ComputeSystemControllerException.exceptions.cannotSetSanBootTargets(host.getHostName(),"Invalid compute elemnt association");
                }
            }else {
                _log.error("Host " + host.getHostName() + " does not have a compute element association.");
                throw ComputeSystemControllerException.exceptions.cannotSetSanBootTargets(host.getHostName(),"Host does not have a blade association");
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        }catch (Exception e){
            _log.error("unexpected exception: " + e.getMessage(), e);
            String hostString = hostId.toString();
            if (host!=null){
                hostString = host.getHostName();
            }
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions.unableToSetSanBootTargets(
                    hostString, e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

    }

    @Override
    public void detachHostStorage(URI host, boolean deactivateOnComplete, boolean deactivateBootVolume,
            List<VolumeDescriptor> volumeDescriptors, String taskId)
                    throws ControllerException {
        TaskCompleter completer = null;
        try {
            completer = new HostCompleter(host, deactivateOnComplete, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, DETACH_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;
            Host hostObj = _dbClient.queryObject(Host.class, host);
            if (hostObj != null && !NullColumnValueGetter.isNullURI(hostObj.getBootVolumeId())
                    && hostObj.getType() != null && (hostObj.getType().equalsIgnoreCase(Host.HostType.Esx.name()))) {
                // check if host's boot-volume has any VMs on it before
                // proceeding further.
                waitFor = computeDeviceController.addStepsCheckVMsOnHostBootVolume(workflow, waitFor, hostObj.getId());
            }

            if (deactivateOnComplete) {
                waitFor = computeDeviceController.addStepsVcenterHostCleanup(workflow, waitFor, host);
            }

            waitFor = unmountHostStorage(workflow, waitFor, host);

            waitFor = addStepsForExportGroups(workflow, waitFor, host);

            waitFor = addStepsForFileShares(workflow, waitFor, host);

            if (deactivateOnComplete) {
                waitFor = computeDeviceController.addStepsDeactivateHost(workflow, waitFor, host, deactivateBootVolume,
                        volumeDescriptors);
            }

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "detachHostStorage caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }
    }

    /**
     * Unmounts storage for a cluster
     * 
     * @param workflow the workflow to use
     * @param waitFor the step to wait for
     * @param clusterId the cluster id to unmount storage
     * @return wait step
     */
    private String unmountClusterStorage(Workflow workflow, String waitFor, URI clusterId) {
        Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);

        String newWaitFor = waitFor;
        if (!NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
            List<ExportGroup> exportGroups = getSharedExports(_dbClient, clusterId);
            List<URI> clusterHosts = ComputeSystemHelper.getChildrenUris(_dbClient, clusterId, Host.class, "cluster");

            URI vcenterDataCenter = cluster.getVcenterDataCenter();
            Collection<URI> exportIds = Collections2.transform(exportGroups, CommonTransformerFunctions.fctnDataObjectToID());
            Map<URI, Collection<URI>> hostExports = Maps.newHashMap();

            for (URI host : clusterHosts) {
                hostExports.put(host, exportIds);
            }

            newWaitFor = this.verifyDatastoreForRemoval(hostExports, vcenterDataCenter, newWaitFor, workflow);

            newWaitFor = this.unmountAndDetachVolumes(hostExports, vcenterDataCenter, newWaitFor, workflow);
        }

        return newWaitFor;
    }

    /**
     * Unmounts storage for a host
     * 
     * @param workflow the workflow to use
     * @param waitFor the step to wait for
     * @param hostId the host id to unmount storage
     * @return wait step
     */
    private String unmountHostStorage(Workflow workflow, String waitFor, URI hostId) {
        Host host = _dbClient.queryObject(Host.class, hostId);

        String newWaitFor = waitFor;
        if (!NullColumnValueGetter.isNullURI(host.getVcenterDataCenter())) {

            URI vcenterDataCenter = host.getVcenterDataCenter();
            List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
            List<ExportGroup> exportGroups = getExportGroups(_dbClient, hostId, hostInitiators);

            Collection<URI> exportIds = Collections2.transform(exportGroups, CommonTransformerFunctions.fctnDataObjectToID());
            Map<URI, Collection<URI>> hostExports = Maps.newHashMap();

            hostExports.put(hostId, exportIds);

            newWaitFor = this.verifyDatastoreForRemoval(hostExports, vcenterDataCenter, newWaitFor, workflow);

            newWaitFor = this.unmountAndDetachVolumes(hostExports, vcenterDataCenter, newWaitFor, workflow);
        }

        return newWaitFor;
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
        // VBDU [DONE]: COP-28451, This method brings in cluster export Groups as well, which ends up in removing the
        // shared and exclusive volumes from the host, maybe it's intentional, need to verify.
        // Yes this is intentional. We are going to remove the host and its initiators from all export groups (exclusive and shared)
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
                    waitFor = unmountHostStorage(workflow, waitFor, host.getId());
                }
            }

            List<NamedElementQueryResultList.NamedElement> clustersUris = ComputeSystemHelper.listChildren(_dbClient,
                    dataCenter.getId(), Cluster.class, "label", "vcenterDataCenter");
            for (NamedElementQueryResultList.NamedElement clusterUri : clustersUris) {
                Cluster cluster = _dbClient.queryObject(Cluster.class, clusterUri.getId());
                if (cluster != null && !cluster.getInactive()) {
                    waitFor = unmountClusterStorage(workflow, waitFor, cluster.getId());
                }
            }

            for (NamedElementQueryResultList.NamedElement hostUri : hostUris) {
                Host host = _dbClient.queryObject(Host.class, hostUri.getId());
                // do not detach storage of provisioned hosts
                if (host != null && !host.getInactive() && NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                    waitFor = addStepsForExportGroups(workflow, waitFor, host.getId());
                    waitFor = addStepsForFileShares(workflow, waitFor, host.getId());
                }
            }
            // clean all the export related to clusters in datacenter
            // VBDU TODO: COP-28457, The above code runs host unexport only if compute element is null. Will there be
            // any cases where few hosts in the cluster got skipped because of associated compute element, but this
            // cluster unexport removes the storage.(because we didn't consider the skipped hosts above)
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
        String newWaitFor = waitFor;
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
                                newWaitFor = workflow.createStep(UNEXPORT_FILESHARE_STEP,
                                        String.format("Unexport fileshare %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        unexportFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        rollbackMethodNullMethod(), null);
                            } else {
                                _log.info("Updating export for file share " + fileShare.getId());
                                newWaitFor = workflow.createStep(UPDATE_FILESHARE_EXPORT_STEP,
                                        String.format("Update fileshare export %s", fileShare.getId()), waitFor,
                                        fileShare.getId(), fileShare.getId().toString(),
                                        this.getClass(),
                                        updateFileShareMethod(device.getId(), device.getSystemType(), fileShare.getId(), export),
                                        rollbackMethodNullMethod(), null);
                            }
                        }
                    }
                }
            }
        }

        return newWaitFor;
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
            completer = new InitiatorCompleter(eventId, initiators, InitiatorOperation.ADD, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, ADD_INITIATOR_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForAddInitiators(workflow, waitFor, hostId, initiators, eventId);

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
            completer = new InitiatorCompleter(eventId, initiators, InitiatorOperation.REMOVE, taskId);
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
    public void addHostsToExport(URI eventId, List<URI> hostIds, URI clusterId, String taskId, URI oldCluster, boolean isVcenter)
            throws ControllerException {
        HostCompleter completer = null;
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            URI vCenterDataCenterId = NullColumnValueGetter.getNullURI();
            Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
            if (cluster != null && !NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
                vCenterDataCenterId = cluster.getVcenterDataCenter();
            }

            completer = new HostCompleter(eventId, hostIds, false, taskId);

            if (!NullColumnValueGetter.isNullURI(oldCluster) && !oldCluster.equals(clusterId)) {
                waitFor = addStepsForRemoveHost(workflow, waitFor, hostIds, oldCluster, vCenterDataCenterId, isVcenter);
            }

            waitFor = addStepForUpdatingHostAndInitiatorClusterReferences(workflow, waitFor, hostIds, clusterId, vCenterDataCenterId,
                    completer);

            waitFor = addStepsForAddHost(workflow, waitFor, hostIds, clusterId, isVcenter, eventId);

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
        HostCompleter completer = null;
        try {
            completer = new HostCompleter(eventId, hostIds, false, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, REMOVE_HOST_STORAGE_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForRemoveHost(workflow, waitFor, hostIds, clusterId, vCenterDataCenterId, isVcenter);

            // Pass a null completer so if an error occurs we do not change the host/initiator cluster value or host
            // vcenterdatacenter value.
            waitFor = addStepForUpdatingHostAndInitiatorClusterReferences(workflow, waitFor, hostIds, NullColumnValueGetter.getNullURI(),
                    vCenterDataCenterId, null);

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

    public String addStepsForAddInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> inits, URI eventId) {
        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, inits);
        List<ExportGroup> exportGroups = ComputeSystemHelper.findExportsByHost(_dbClient, hostId.toString());

        for (ExportGroup export : exportGroups) {
            List<URI> existingInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();
            Set<URI> addedInitiators = new HashSet<>();
            Set<URI> removedInitiators = new HashSet<>();

            List<Initiator> validInitiator = ComputeSystemHelper.validatePortConnectivity(_dbClient, export, initiators);
            if (!validInitiator.isEmpty()) {
                boolean update = false;
                for (Initiator initiator : validInitiator) {
                    // if the initiators is not already in the list add it.
                    if (!existingInitiators.contains(initiator.getId()) && !addedInitiators.contains(initiator.getId())) {
                        addedInitiators.add(initiator.getId());
                        update = true;
                    }
                }

                if (update) {
                    waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                            String.format("Updating export group %s", export.getId()), waitFor,
                            export.getId(), export.getId().toString(),
                            this.getClass(),
                            updateExportGroupMethod(export.getId(), updatedVolumesMap,
                                    addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                            updateExportGroupRollbackMethod(export.getId()), null);
                }
            } else if (!NullColumnValueGetter.isNullURI(eventId)) {
                throw ComputeSystemControllerException.exceptions.noInitiatorPortConnectivity(StringUtils.join(initiators, ","),
                        export.forDisplay());
            }
        }
        return waitFor;
    }

    public String addStepsForRemoveInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> initiatorsURI) {

        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorsURI);
        List<ExportGroup> exportGroups = getExportGroups(_dbClient, hostId, initiators);

        for (ExportGroup export : exportGroups) {
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();
            Set<URI> addedInitiators = new HashSet<>();
            Set<URI> removedInitiators = new HashSet<>(initiatorsURI);

            // Only update if the list as changed
            if (!CollectionUtils.isEmpty(initiatorsURI)) {
                waitFor = workflow.createStep(
                        UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()),
                        waitFor,
                        export.getId(),
                        export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(), updatedVolumesMap,
                                addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                        updateExportGroupRollbackMethod(export.getId()), null);
            }
        }
        return waitFor;
    }

    /**
     * Assembles steps to remove a host
     *
     * @param workflow The current workflow
     * @param waitFor The current waitFor
     * @param hostIds List of hosts to remove
     * @param clusterId Cluster ID if this is a clustered host
     * @param vcenterDataCenter vCenter ID if this is a vCenter
     * @param isVcenter Boolean flag for determining if vCenter enabled
     * @return Next step
     */
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

        if (isVcenter && !NullColumnValueGetter.isNullURI(vcenterDataCenter)) {
            newWaitFor = this.rescanHostStorage(hostIds, vcenterDataCenter, newWaitFor, workflow);
        }

        return newWaitFor;
    }

    /**
     * Assembles steps to remove a host from an export group
     *
     * @param workflow The current workflow
     * @param waitFor The current waitFor
     * @param hostIds List of hosts to remove
     * @param exportId The ID of the export group to remove the host from
     * @return Next step
     */
    public String addStepsForRemoveHostFromExport(Workflow workflow, String waitFor, List<URI> hostIds, URI exportId) {
        ExportGroup export = _dbClient.queryObject(ExportGroup.class, exportId);
        String newWaitFor = waitFor;

        Set<URI> addedClusters = new HashSet<>();
        Set<URI> removedClusters = new HashSet<>();
        Set<URI> addedHosts = new HashSet<>();
        Set<URI> removedHosts = new HashSet<>(hostIds);
        Set<URI> addedInitiators = new HashSet<>();
        Set<URI> removedInitiators = new HashSet<>();

        if (export != null) {
            List<URI> updatedHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            for (URI hostId : hostIds) {
                updatedHosts.remove(hostId);
                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                for (Initiator initiator : hostInitiators) {
                    removedInitiators.add(initiator.getId());
                }
            }
            // VBDU TODO: COP-28458, This exposes concurrency issue, what if another thread was adding new host into the
            // same export group we might be deleting the export group, without knowing that another thread was adding a
            // host. I think, this we can raise an enhancemnet and fix this later.
            if (updatedHosts.isEmpty()) {
                newWaitFor = workflow.createStep(DELETE_EXPORT_GROUP_STEP,
                        String.format("Deleting export group %s", export.getId()), newWaitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        deleteExportGroupMethod(export.getId()),
                        rollbackMethodNullMethod(), null);
            } else {
                newWaitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                        String.format("Updating export group %s", export.getId()), newWaitFor,
                        export.getId(), export.getId().toString(),
                        this.getClass(),
                        updateExportGroupMethod(export.getId(),
                                CollectionUtils.isEmpty(export.getInitiators()) ? new HashMap<URI, Integer>() : updatedVolumesMap,
                                        addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                        updateExportGroupRollbackMethod(export.getId()), null);
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
            List<URI> existingInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<URI> existingHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            List<URI> updatedClusters = StringSetUtil.stringSetToUriList(export.getClusters());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();
            Set<URI> addedInitiators = new HashSet<>();
            Set<URI> removedInitiators = new HashSet<>();

            // 1. Add all hosts in clusters that are not in the cluster's export groups
            for (URI clusterHost : clusterHostIds) {
                if (!existingHosts.contains(clusterHost)) {
                    _log.info("Adding host " + clusterHost + " to cluster export group " + export.getId());
                    addedHosts.add(clusterHost);
                    List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, clusterHost);
                    for (Initiator initiator : hostInitiators) {
                        addedInitiators.add(initiator.getId());
                    }
                }
            }

            // 2. Remove all hosts in cluster's export groups that don't belong to the cluster
            Iterator<URI> existingHostsIterator = existingHosts.iterator();
            while (existingHostsIterator.hasNext()) {
                URI hostId = existingHostsIterator.next();
                if (!clusterHostIds.contains(hostId)) {
                    removedHosts.add(hostId);
                    _log.info("Removing host " + hostId + " from shared export group " + export.getId()
                    + " because this host does not belong to the cluster");
                    List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
                    for (Initiator initiator : hostInitiators) {
                        removedInitiators.add(initiator.getId());
                    }
                }
            }

            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", export.getId()), waitFor,
                    export.getId(), export.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(export.getId(), updatedVolumesMap,
                            addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                    updateExportGroupRollbackMethod(export.getId()), null);
        }
        return waitFor;
    }

    public String addStepsForAddHost(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId, boolean isVcenter, URI eventId) {
        List<Host> hosts = _dbClient.queryObject(Host.class, hostIds);
        List<ExportGroup> exportGroups = getSharedExports(_dbClient, clusterId);

        for (ExportGroup eg : exportGroups) {

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();
            Set<URI> addedInitiators = new HashSet<>();
            Set<URI> removedInitiators = new HashSet<>();

            List<URI> existingInitiators = StringSetUtil.stringSetToUriList(eg.getInitiators());
            List<URI> existingHosts = StringSetUtil.stringSetToUriList(eg.getHosts());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(eg.getVolumes());

            // add host reference to export group
            for (Host host : hosts) {
                if (!existingHosts.contains(host.getId())) {
                    addedHosts.add(host.getId());
                }

                List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, host.getId());
                List<Initiator> validInitiators = ComputeSystemHelper.validatePortConnectivity(_dbClient, eg, hostInitiators);
                if (!validInitiators.isEmpty()) {
                    // if the initiators is not already in the list add it.
                    for (Initiator initiator : validInitiators) {
                        if (!existingInitiators.contains(initiator.getId())) {
                            addedInitiators.add(initiator.getId());
                        }
                    }
                } else if (!NullColumnValueGetter.isNullURI(eventId)) {
                    throw ComputeSystemControllerException.exceptions.noInitiatorPortConnectivity(StringUtils.join(hostInitiators, ","),
                            eg.forDisplay());
                }
            }

            _log.info("Initiators to add: {}", addedInitiators);
            _log.info("Initiators to remove: {}", removedInitiators);
            _log.info("Hosts to add: {}", addedHosts);
            _log.info("Hosts to remove: {}", removedHosts);
            _log.info("Clusters to add: {}", addedClusters);
            _log.info("Clusters to remove: {}", removedClusters);

            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", eg.getId()), waitFor,
                    eg.getId(), eg.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(eg.getId(), updatedVolumesMap,
                            addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                    updateExportGroupRollbackMethod(eg.getId()), null);
        }

        if (isVcenter) {
            Collection<URI> exportIds = Collections2.transform(exportGroups, CommonTransformerFunctions.fctnDataObjectToID());
            Map<URI, Collection<URI>> hostExports = Maps.newHashMap();
            for (URI host : hostIds) {
                hostExports.put(host, exportIds);
            }
            // VBDU TODO: COP-28457, There might be few hosts skipped due to port connectivity above, we might need to
            // skip those as well.
            waitFor = this.attachAndMountVolumes(hostExports, waitFor, workflow);
        }

        return waitFor;
    }

    public String addStepForUpdatingHostAndInitiatorClusterReferences(Workflow workflow, String waitFor, List<URI> hostIds, URI clusterId,
            URI vCenterDataCenterId, HostCompleter completer) {
        for (URI hostId : hostIds) {

            URI oldClusterId = NullColumnValueGetter.getNullURI();
            URI oldvCenterDataCenterId = NullColumnValueGetter.getNullURI();

            Host host = _dbClient.queryObject(Host.class, hostId);
            if (host != null) {
                if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
                    oldClusterId = host.getCluster();
                }
                if (!NullColumnValueGetter.isNullURI(host.getVcenterDataCenter())) {
                    oldvCenterDataCenterId = host.getVcenterDataCenter();
                }
            }

            if (completer != null) {
                // Update the completer with the correct values to rollback
                completer.addOldHostClusterAndVcenterDataCenter(hostId, oldClusterId, oldvCenterDataCenterId);
            }

            waitFor = workflow.createStep(UPDATE_HOST_AND_INITIATOR_CLUSTER_NAMES_STEP,
                    String.format("Updating host and initiator cluster names for host %s to %s", hostId, clusterId), waitFor,
                    hostId, hostId.toString(),
                    this.getClass(),
                    updateHostAndInitiatorClusterReferencesMethod(hostId, clusterId, vCenterDataCenterId),
                    updateHostAndInitiatorClusterReferencesMethod(hostId, oldClusterId, oldvCenterDataCenterId), null);
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
            List<URI> existingHosts = StringSetUtil.stringSetToUriList(export.getHosts());
            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();
            Set<URI> addedInitiators = new HashSet<>();
            Set<URI> removedInitiators = new HashSet<>();

            existingHosts.remove(hostId);

            for (Initiator initiator : hostInitiators) {
                removedInitiators.add(initiator.getId());
            }
            // VBDU [DONE]: COP-28452 This is dangerous ..Delete export Group in controller means export all volumes in
            // the export group. This call's intention is to remove a host, if for some reason one of the export group
            // doesn't have the right set of initiator then we might end up in unexporting all volumes from all the
            // hosts rather than executing remove Host.
            // Fixed to only perform export update instead of delete
            waitFor = workflow.createStep(
                    UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", export.getId()),
                    waitFor,
                    export.getId(),
                    export.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(export.getId(), updatedVolumesMap,
                            addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                    updateExportGroupRollbackMethod(export.getId()), null);
        }
        return waitFor;
    }

    public Workflow.Method updateExportGroupMethod(URI exportGroupURI, Map<URI, Integer> newVolumesMap,
            Set<URI> addedClusters, Set<URI> removedClusters, Set<URI> adedHosts, Set<URI> removedHosts,
            Set<URI> addedInitiators, Set<URI> removedInitiators) {
        return new Workflow.Method("updateExportGroup", exportGroupURI, newVolumesMap,
                addedClusters, removedClusters, adedHosts, removedHosts, addedInitiators,
                removedInitiators);
    }

    public Workflow.Method updateExportGroupRollbackMethod(URI exportGroupURI) {
        return new Workflow.Method("updateExportGroupRollback", exportGroupURI);
    }

    public void updateExportGroup(URI exportGroup, Map<URI, Integer> newVolumesMap,
            Set<URI> addedClusters, Set<URI> removedClusters, Set<URI> adedHosts, Set<URI> removedHosts,
            Set<URI> addedInitiators,
            Set<URI> removedInitiators, String stepId) throws Exception {

        Map<URI, Integer> addedBlockObjects = new HashMap<URI, Integer>();
        Map<URI, Integer> removedBlockObjects = new HashMap<URI, Integer>();

        try {
            ExportGroup exportGroupObject = _dbClient.queryObject(ExportGroup.class, exportGroup);
            ExportUtils.getAddedAndRemovedBlockObjects(newVolumesMap, exportGroupObject, addedBlockObjects, removedBlockObjects);
            BlockExportController blockController = getController(BlockExportController.class, BlockExportController.EXPORT);

            Operation op = _dbClient.createTaskOpStatus(ExportGroup.class, exportGroup,
                    stepId, ResourceOperationTypeEnum.UPDATE_EXPORT_GROUP);
            exportGroupObject.getOpStatus().put(stepId, op);
            _dbClient.updateObject(exportGroupObject);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_026);

            blockController.exportGroupUpdate(exportGroup, addedBlockObjects, removedBlockObjects, addedClusters,
                    removedClusters, adedHosts, removedHosts, addedInitiators, removedInitiators, stepId);

            // No code should be added following the call to the block controller to preserve rollback integrity
        } catch (Exception ex) {
            _log.error("Exception occured while updating export group {}", exportGroup, ex);
            // Clean up any pending tasks
            ExportTaskCompleter taskCompleter = new ExportUpdateCompleter(exportGroup, addedBlockObjects, removedBlockObjects,
                    addedInitiators, removedInitiators, adedHosts, removedHosts, addedClusters, removedClusters, stepId);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
            // Fail the step
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    public void updateExportGroupRollback(URI exportGroup, String stepId) throws Exception {
        // No specific steps yet, just pass through.
        WorkflowStepCompleter.stepSucceded(stepId);
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
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_054);

            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, vCenterId);
            VcenterDataCenter vCenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDatacenter);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupId);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);
            HostSystem hostSystem = api.findHostSystem(vCenterDataCenter.getLabel(), esxHost.getLabel());

            if (hostSystem == null) {
                _log.info("Not able to find host " + esxHost.getLabel() + " in vCenter. Unable to attach disks and mount datastores");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            HostStorageAPI storageAPI = new HostStorageAPI(hostSystem);

            if (exportGroup != null && exportGroup.getVolumes() != null) {
                _log.info("Refreshing storage");
                storageAPI.refreshStorage();
                Set<BlockObject> blockObjects = Sets.newHashSet();
                for (String volume : exportGroup.getVolumes().keySet()) {
                    BlockObject blockObject = BlockObject.fetch(_dbClient, URI.create(volume));
                    blockObjects.add(blockObject);
                    for (HostScsiDisk entry : storageAPI.listScsiDisks()) {
                        if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), blockObject.getWWN())) {
                            if (VMwareUtils.isDiskOff(entry)) {
                                _log.info("Attach SCSI Lun " + entry.getCanonicalName() + " on host " + esxHost.getLabel());
                                storageAPI.attachScsiLun(entry);
                            }
                            // Test mechanism to invoke a failure. No-op on production systems.
                            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_055);
                            break;
                        }
                    }
                }

                int retries = 0;

                while (retries++ < MAXIMUM_RESCAN_ATTEMPTS && !blockObjects.isEmpty()) {

                    _log.info("Rescanning VMFS for host " + esxHost.getLabel());

                    storageAPI.getStorageSystem().rescanVmfs();

                    _log.info("Waiting for {} milliseconds before checking for datastores", RESCAN_DELAY_MS);

                    Thread.sleep(RESCAN_DELAY_MS);

                    _log.info("Looking for datastores for {} volumes", blockObjects.size());

                    Map<String, Datastore> wwnDatastores = getWwnDatastoreMap(hostSystem);

                    Iterator<BlockObject> objectIterator = blockObjects.iterator();
                    while (objectIterator.hasNext()) {
                        BlockObject blockObject = objectIterator.next();
                        if (blockObject != null) {

                            Datastore datastore = getDatastoreByWwn(wwnDatastores, blockObject.getWWN());
                            if (datastore != null && VMwareUtils.isDatastoreMountedOnHost(datastore, hostSystem)) {
                                _log.info("Datastore {} is already mounted on {}", datastore.getName(), esxHost.getLabel());
                                objectIterator.remove();
                            } else if (datastore != null && !VMwareUtils.isDatastoreMountedOnHost(datastore, hostSystem)) {
                                _log.info("Mounting datastore {} on host {}", datastore.getName(), esxHost.getLabel());
                                storageAPI.mountDatastore(datastore);
                                objectIterator.remove();
                                // Test mechanism to invoke a failure. No-op on production systems.
                                InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_056);
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
     * Creates a workflow method to rescan HBAs for an ESX host
     * 
     * @param hostId the host id
     * @param vcenter the vcenter id
     * @param vcenterDatacenter the vcenter datacenter id
     * @return workflow method
     */
    public Workflow.Method rescanHostStorageMethod(URI hostId, URI vcenter, URI vcenterDatacenter) {
        return new Workflow.Method("rescanHostStorage", hostId, vcenter, vcenterDatacenter);
    }

    /**
     * Rescans HBAs and storage system for an ESX host
     * 
     * @param hostId the host id
     * @param vCenterId the vcenter id
     * @param vcenterDatacenter the vcenter datacenter id
     * @param stepId the workflow step
     */
    public void rescanHostStorage(URI hostId, URI vCenterId, URI vcenterDatacenter, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);

        try {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, vCenterId);
            VcenterDataCenter vCenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDatacenter);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);
            HostSystem hostSystem = api.findHostSystem(vCenterDataCenter.getLabel(), esxHost.getLabel());

            if (hostSystem == null) {
                _log.info("Not able to find host {} in vCenter. Unable to refresh HBAs", esxHost.getLabel());
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            HostStorageAPI storageAPI = new HostStorageAPI(hostSystem);
            storageAPI.refreshStorage();

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
    }

    /**
     * Verifies that datastores contained within an export group can be unmounted. It must not be entering maintenance mode or contain any
     * virtual machines running on the given ESXi host.
     *
     * @param exportGroup
     *            export group that contains volumes
     * @param vcenter
     *            vcenter that the datastore belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the datastore belongs to
     * @param esxHostName
     *            the hostname of the ESXi host
     *
     * @return workflow method for unmounting and detaching disks and datastores
     */
    public Workflow.Method verifyDatastoreMethod(URI exportGroup, URI vcenter, URI vcenterDatacenter, String esxHostName) {
        return new Workflow.Method("verifyDatastore", exportGroup, vcenter, vcenterDatacenter, esxHostName);
    }

    /**
     * Verifies that datastores contained within an export group can be unmounted. It must not be entering maintenance mode or contain any
     * virtual machines running on the given ESXi host.
     *
     * @param exportGroupId
     *            export group that contains volumes
     * @param vcenterId
     *            vcenter that the host belongs to
     * @param vcenterDatacenter
     *            vcenter datacenter that the host belongs to
     * @param esxHostname
     *            the hostname of the ESXi host
     *
     * @param stepId
     *            the id of the workflow step
     */
    public void verifyDatastore(URI exportGroupId, URI vCenterId, URI vcenterDatacenter, String esxHostname, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);

        try {
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, vCenterId);
            VcenterDataCenter vCenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, vcenterDatacenter);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupId);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);
            HostSystem host = api.findHostSystem(vCenterDataCenter.getLabel(), esxHostname);

            if (host == null) {
                _log.info("Not able to find host " + esxHostname + " in vCenter. Unable to validate");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            Map<String, Datastore> wwnDatastores = getWwnDatastoreMap(host);

            if (exportGroup != null && exportGroup.getVolumes() != null) {
                for (String volume : exportGroup.getVolumes().keySet()) {
                    BlockObject blockObject = BlockObject.fetch(_dbClient, URI.create(volume));
                    Datastore datastore = getDatastoreByWwn(wwnDatastores, blockObject.getWWN());
                    if (datastore != null) {
                        ComputeSystemHelper.verifyDatastore(datastore, host);
                    }
                }
            }

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_029);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }

    }

    /**
     * Gets a map of volume wwn to datastore for all datastores that the host has access to
     * 
     * @param host the host
     * @return map of volume wwn to datastore
     * @throws InvalidProperty
     * @throws RuntimeFault
     * @throws RemoteException
     */
    public Map<String, Datastore> getWwnDatastoreMap(HostSystem host) throws InvalidProperty, RuntimeFault, RemoteException {
        Map<String, Datastore> result = Maps.newHashMap();
        HostStorageAPI hostApi = new HostStorageAPI(host);

        for (Datastore datastore : host.getDatastores()) {
            if (datastore.getInfo() instanceof VmfsDatastoreInfo) {
                Collection<HostScsiDisk> disks = hostApi.getDisksByPartition(datastore).values();
                for (HostScsiDisk disk : disks) {
                    result.put(VMwareUtils.getDiskWwn(disk), datastore);
                }
            }
        }
        return result;
    }

    /**
     * Gets the datastore that is backed by volume wwn
     * 
     * @param wwnDatastoreMap the map of volume wwn to datastore
     * @param wwn the wwn to search for
     * @return datastore that is backed by the volume wwn or null if not found
     */
    public Datastore getDatastoreByWwn(Map<String, Datastore> wwnDatastoreMap, String wwn) {
        for (String datastoreWwn : wwnDatastoreMap.keySet()) {
            if (VolumeWWNUtils.wwnMatches(datastoreWwn, wwn)) {
                return wwnDatastoreMap.get(datastoreWwn);
            }
        }
        return null;
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

            if (hostSystem == null) {
                _log.info("Not able to find host " + esxHost.getLabel() + " in vCenter. Unable to validate");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            } 

            HostStorageAPI storageAPI = new HostStorageAPI(hostSystem);

            Map<String, Datastore> wwnDatastores = getWwnDatastoreMap(hostSystem);

            if (exportGroup != null && exportGroup.getVolumes() != null) {
                for (String volume : exportGroup.getVolumes().keySet()) {
                    BlockObject blockObject = BlockObject.fetch(_dbClient, URI.create(volume));
                    if (blockObject != null) {
                        Datastore datastore = getDatastoreByWwn(wwnDatastores, blockObject.getWWN());
                        if (datastore != null) {
                            if (VMwareUtils.isDatastoreMountedOnHost(datastore, hostSystem)) {
                                boolean storageIOControlEnabled = datastore.getIormConfiguration().isEnabled();
                                if (storageIOControlEnabled) {
                                    setStorageIOControl(api, datastore, false);
                                }
                                _log.info("Unmount datastore " + datastore.getName() + " from host " + esxHost.getLabel());
                                storageAPI.unmountVmfsDatastore(datastore);
                                if (storageIOControlEnabled) {
                                    setStorageIOControl(api, datastore, true);
                                }
                            } else {
                                _log.info("Datastore " + datastore.getName() + " is not mounted on host " + esxHost.getLabel()
                                        + ". Skipping unmounting of datastore.");
                            }
                        }
                    }
                    // VBDU TODO: COP-28459, If datastore doesn't match, why do we need to run DetachSCSILun() ?
                    // Test mechanism to invoke a failure. No-op on production systems.
                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_030);
                    for (HostScsiDisk entry : storageAPI.listScsiDisks()) {
                        if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), blockObject.getWWN())) {
                            if (!VMwareUtils.isDiskOff(entry)) {
                                _log.info("Detach SCSI Lun " + entry.getCanonicalName() + " from host " + esxHost.getLabel());
                                storageAPI.detachScsiLun(entry);
                            } else {
                                _log.info("SCSI Lun " + entry.getCanonicalName() + " is not in a valid state to detach");
                            }
                        }
                    }
                    storageAPI.refreshStorage();

                    // Test mechanism to invoke a failure. No-op on production systems.
                    InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_031);
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
        try {
            BlockExportController blockController = getController(BlockExportController.class, BlockExportController.EXPORT);
            _dbClient.createTaskOpStatus(ExportGroup.class, exportGroup,
                    stepId, ResourceOperationTypeEnum.DELETE_EXPORT_GROUP);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_027);
            blockController.exportGroupDelete(exportGroup, stepId);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_028);
        } catch (Exception ex) {
            _log.error("Exception occured while deleting export group {}", exportGroup, ex);
            // Clean up any pending tasks
            ExportTaskCompleter taskCompleter = new ExportDeleteCompleter(exportGroup, false, stepId);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);

            // Fail the step
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
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
                waitFor = computeDeviceController.addStepsVcenterClusterCleanup(workflow, waitFor, cluster, deactivateOnComplete);
            }
  
            waitFor = unmountClusterStorage(workflow, waitFor, cluster);

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

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();
            Set<URI> addedInitiators = new HashSet<>();
            Set<URI> removedInitiators = new HashSet<>();

            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            removedClusters.add(clusterId);

            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterId, Host.class, "cluster");
            for (URI hosturi : hostUris) {
                removedHosts.add(hosturi);
                removedInitiators.addAll(ComputeSystemHelper.getChildrenUris(_dbClient, hosturi, Initiator.class, "host"));
            }

            // VBDU [DONE]: COP-28452, This doesn't look that dangerous, as we might see more than one cluster in export
            // group. Delete export Group in controller means export all volumes in the export group.
            // This call's intention is to remove a host, if for some reason one of the export group doesn't have the
            // right set of initiator then we might end up in unexporting all volumes from all the hosts rather than
            // executing remove Host.
            // Fixed to only perform export update instead of delete
            waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                    String.format("Updating export group %s", export.getId()), waitFor,
                    export.getId(), export.getId().toString(),
                    this.getClass(),
                    updateExportGroupMethod(export.getId(), updatedVolumesMap,
                            addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                    updateExportGroupRollbackMethod(export.getId()), null);
        }
        return waitFor;
    }

    public Workflow.Method updateHostAndInitiatorClusterReferencesMethod(URI hostId, URI clusterId, URI vCenterDataCenterId) {
        return new Workflow.Method("updateHostAndInitiatorClusterReferences", hostId, clusterId, vCenterDataCenterId);
    }

    /**
     * Updates the host and initiator references for the cluster
     *
     * @param hostId Host ID
     * @param clusterId Cluster ID
     * @param vCenterDataCenterId vCenter ID
     * @param stepId Current step ID
     */
    public void updateHostAndInitiatorClusterReferences(URI hostId, URI clusterId, URI vCenterDataCenterId, String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_042);

            ComputeSystemHelper.updateHostAndInitiatorClusterReferences(_dbClient, clusterId, hostId);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_032);
            ComputeSystemHelper.updateHostVcenterDatacenterReference(_dbClient, hostId, vCenterDataCenterId);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_033);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception ex) {
            _log.error("Exception occured while updating host and initiator cluster references {} - {}", hostId, clusterId, ex);
            WorkflowStepCompleter.stepFailed(stepId, DeviceControllerException.errors.jobFailed(ex));
        }
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
                                attachAndMountMethod(export, esxHost.getId(), vCenterId,
                                        vcenterDataCenter.getId()), null);
                    }
                }
            }
        }
        return waitFor;
    }

    /**
     * Creates workflow steps for rescanning HBAs and host storage for a list of ESX hosts
     * 
     * @param hostIds list of host ids
     * @param virtualDataCenter the virtual datacenter id that the hosts belong to
     * @param waitFor the step to wait on for this workflow step
     * @param workflow the workflow
     * @return the step id
     */
    private String rescanHostStorage(List<URI> hostIds, URI virtualDataCenter, String waitFor,
            Workflow workflow) {
        String newWaitFor = waitFor;
        for (URI hostId : hostIds) {
            Host esxHost = _dbClient.queryObject(Host.class, hostId);
            if (esxHost != null) {
                VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class, virtualDataCenter);
                if (vcenterDataCenter != null) {
                    URI vCenterId = vcenterDataCenter.getVcenter();
                    newWaitFor = workflow.createStep(RESCAN_HOST_STORAGE_STEP,
                            String.format("Refreshing HBAs for host %s", esxHost.forDisplay()), newWaitFor,
                            esxHost.getId(), esxHost.getId().toString(),
                            this.getClass(),
                            rescanHostStorageMethod(esxHost.getId(), vCenterId,
                                    vcenterDataCenter.getId()),
                            rollbackMethodNullMethod(), null);
                }
            }
        }
        return newWaitFor;
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
                                String.format("Verifying datastores for removal from export %s with host %s", export, esxHost.forDisplay()),
                                wait,
                                export, export.toString(),
                                this.getClass(),
                                verifyDatastoreMethod(export, vCenterId,
                                        vcenterDataCenter.getId(), esxHost.getHostName()),
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

        _log.info("ExportGroupState for " + export.getId() + " = " + export);

        if (export.getInitiators().isEmpty()) {
            /**
             * TODO if Two threads accessing the same EG.
             * Thread 1. Removing all initiators from EG.
             * Thread 2. Adding a new set of Initiators to same EG
             * In this case, we should not delete the EG
             */
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
                            new HashSet<>(export.getAddedClusters()), new HashSet<>(export.getRemovedClusters()),
                            new HashSet<>(export.getAddedHosts()), new HashSet<>(export.getRemovedHosts()),
                            new HashSet<>(export.getAddedInitiators()), new HashSet<>(export.getRemovedInitiators())),
                    updateExportGroupRollbackMethod(export.getId()), null);
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

    @Override
    public void updateHostInitiators(URI eventId, URI host, List<URI> newInitiators, List<URI> oldInitiators, String taskId) {
        TaskCompleter completer = null;
        try {
            completer = new HostInitiatorsCompleter(eventId, host, newInitiators, oldInitiators, taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_HOST_INITIATOR_WF_NAME, true, taskId);
            String waitFor = null;

            waitFor = addStepsForUpdateInitiators(workflow, waitFor, host, newInitiators, oldInitiators, eventId);

            workflow.executePlan(completer, "Success", null, null, null, null);
        } catch (Exception ex) {
            String message = "updateHostInitiators caught an exception.";
            _log.error(message, ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            completer.error(_dbClient, serviceError);
        }

    }

    public static class InitiatorChange {

        public Initiator initiator;
        public InitiatorOperation op;

        public InitiatorChange(Initiator initiator, InitiatorOperation op) {
            this.initiator = initiator;
            this.op = op;
        }
    }

    public static Initiator getNextInitiatorOperation(URI exportGroupId, Map<URI, List<InitiatorChange>> map, InitiatorOperation op) {
        if (!NullColumnValueGetter.isNullURI(exportGroupId)) {
            List<InitiatorChange> initiators = map.get(exportGroupId);
            if (initiators != null) {
                Iterator<InitiatorChange> iterator = initiators.iterator();
                while (iterator.hasNext()) {
                    InitiatorChange result = iterator.next();
                    if (result.op.equals(op)) {
                        iterator.remove();
                        return result.initiator;
                    }
                }
            }
        }
        return null;
    }

    private Map<URI, List<InitiatorChange>> getInitiatorOperations(URI hostId, Collection<URI> newInitiatorIds,
            Collection<URI> oldInitiatorIds) {
        Map<URI, List<InitiatorChange>> result = Maps.newHashMap();

        List<Initiator> newInitiators = _dbClient.queryObject(Initiator.class, newInitiatorIds);
        List<Initiator> oldInitiators = _dbClient.queryObject(Initiator.class, oldInitiatorIds);

        List<ExportGroup> exportGroups = getExportGroups(_dbClient, hostId, oldInitiators);

        for (ExportGroup export : exportGroups) {

            result.put(export.getId(), Lists.newArrayList());
            List<URI> existingInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());

            for (Initiator oldInit : oldInitiators) {
                if (existingInitiators.contains(oldInit.getId())) {
                    result.get(export.getId()).add(new InitiatorChange(oldInit, InitiatorOperation.REMOVE));
                } else {
                    _log.info("Export Group " + export.forDisplay() + " doesn't contain " + oldInit.forDisplay());
                }
            }

            for (Initiator newInit : newInitiators) {
                if (!existingInitiators.contains(newInit.getId())) {
                    _log.info("Export Group " + export.forDisplay() + " already contains " + newInit.forDisplay());
                } else if (!ComputeSystemHelper.validatePortConnectivity(_dbClient, export, Lists.newArrayList(newInit)).isEmpty()) {
                    _log.info("Export Group " + export.forDisplay() + " does not have port connectivity for " + newInit.forDisplay());
                } else {
                    result.get(export.getId()).add(new InitiatorChange(newInit, InitiatorOperation.ADD));
                }
            }
        }
        return result;
    }

    public String addStepsForUpdateInitiators(Workflow workflow, String waitFor, URI hostId, Collection<URI> newInitiatorIds,
            Collection<URI> oldInitiatorIds, URI eventId) {

        verifyNotRemovingAllInitiatorsFromExportGroup(hostId, newInitiatorIds, oldInitiatorIds);
        Map<URI, List<InitiatorChange>> map = getInitiatorOperations(hostId, newInitiatorIds, oldInitiatorIds);

        for (URI eg : map.keySet()) {

            ExportGroup export = _dbClient.queryObject(ExportGroup.class, eg);

            Map<URI, Integer> updatedVolumesMap = StringMapUtil.stringMapToVolumeMap(export.getVolumes());

            Set<URI> addedClusters = new HashSet<>();
            Set<URI> removedClusters = new HashSet<>();
            Set<URI> addedHosts = new HashSet<>();
            Set<URI> removedHosts = new HashSet<>();

            boolean removingInitiator = true;
            boolean done = false;

            while (!done) {

                Set<URI> addedInitiators = new HashSet<>();
                Set<URI> removedInitiators = new HashSet<>();

                boolean update = false;
                // Alternate between removing and then adding initiator to the export group
                if (removingInitiator) {
                    Initiator remove = getNextInitiatorOperation(export.getId(), map, InitiatorOperation.REMOVE);
                    if (remove != null) {
                        removedInitiators.add(remove.getId());
                        update = true;
                    }
                } else {
                    Initiator add = getNextInitiatorOperation(export.getId(), map, InitiatorOperation.ADD);
                    if (add != null) {
                        addedInitiators.add(add.getId());
                        update = true;
                    }
                }

                if (update) {
                    waitFor = workflow.createStep(UPDATE_EXPORT_GROUP_STEP,
                            String.format("Updating export group %s", export.getId()), waitFor,
                            export.getId(), export.getId().toString(),
                            this.getClass(),
                            updateExportGroupMethod(export.getId(), updatedVolumesMap,
                                    addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators, removedInitiators),
                            updateExportGroupRollbackMethod(export.getId()), null);
                }

                if (map.get(export.getId()).isEmpty()) {
                    done = true;
                }

                removingInitiator = !removingInitiator;
            }
        }
        return waitFor;
    }

    /**
     * Verify that we are not going to remove the last initiator of the host from the export groups. Throw an exception if we will end up
     * removing the
     * last initiator.
     * 
     * @param hostId the host id
     * @param newInitiatorIds the new initiators to add
     * @param oldInitiatorIds the old initiators to remove
     */
    private void verifyNotRemovingAllInitiatorsFromExportGroup(URI hostId, Collection<URI> newInitiatorIds,
            Collection<URI> oldInitiatorIds) {
        List<Initiator> oldInitiators = _dbClient.queryObject(Initiator.class, oldInitiatorIds);
        Host host = _dbClient.queryObject(Host.class, hostId);
        List<ExportGroup> exportGroups = getExportGroups(_dbClient, hostId, oldInitiators);

        for (ExportGroup export : exportGroups) {

            List<URI> allInitiatorIds = StringSetUtil.stringSetToUriList(export.getInitiators());
            List<Initiator> allInitiators = _dbClient.queryObject(Initiator.class, allInitiatorIds);
            List<URI> exportGroupHostInitiators = Lists.newArrayList();
            // Get list of all initiators in this export group that belong to the host that has the initiator changes
            for (Initiator initiator : allInitiators) {
                if (!NullColumnValueGetter.isNullURI(initiator.getHost()) && initiator.getHost().equals(hostId)) {
                    exportGroupHostInitiators.add(initiator.getId());
                }
            }

            if (!oldInitiatorIds.isEmpty() && oldInitiatorIds.containsAll(exportGroupHostInitiators)
                    && (oldInitiatorIds.size() == 1 || newInitiatorIds.isEmpty())) {
                throw ComputeSystemControllerException.exceptions.removingAllInitiatorsFromExportGroup(export.forDisplay(),
                        host.forDisplay());
            }
        }

    }
}
