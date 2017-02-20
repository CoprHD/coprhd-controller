/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.computecontroller.ComputeDevice;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.UCSServiceProfileTemplate;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.imageservercontroller.exceptions.ImageServerControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.vcentercontroller.VcenterController;
import com.emc.storageos.vcentercontroller.exceptions.VcenterControllerException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterObjectConnectionException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterObjectNotFoundException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class ComputeDeviceControllerImpl implements ComputeDeviceController {

    private static final Logger log = LoggerFactory.getLogger(ComputeDeviceControllerImpl.class);
    private static final String EVENT_SERVICE_TYPE = "COMPUTE_DEVICE_CONTROLLER";
    private DbClient _dbClient;
    private WorkflowService _workflowService;
    private CoordinatorClient _coordinator;

    private Map<String, ComputeDevice> _devices;
    private BlockOrchestrationController blockOrchestrationController;
    private VcenterController vcenterController;

    private static final String DEACTIVATION_MAINTENANCE_MODE = "DEACTIVATION_MAINTENANCE_MODE";
    private static final String DEACTIVATION_REMOVE_HOST_VCENTER = "DEACTIVATION_REMOVE_HOST_VCENTER";
    private static final String DEACTIVATION_COMPUTE_SYSTEM_HOST = "DEACTIVATION_COMPUTE_SYSTEM_HOST";
    private static final String DEACTIVATION_COMPUTE_SYSTEM_BOOT_VOLUME = "DEACTIVATION_COMPUTE_SYSTEM_BOOT_VOLUME";
    private static final String CHECK_CLUSTER_VMS = "CHECK_CLUSTER_VMS";
    private static final String REMOVE_VCENTER_CLUSTER = "REMOVE_VCENTER_CLUSTER";
    private static final String UNBIND_HOST_FROM_TEMPLATE = "UNBIND_HOST_FROM_TEMPLATE";
    private static final String OS_INSTALL_PREPARE_OS_NETWORK = "OS_INSTALL_PREPARE_OS_NETWORK";
    private static final String PRE_OS_INSTALL_POWER_DOWN_STEP = "PRE_OS_INSTALL_POWER_DOWN_STEP";
    private static final String PRE_OS_INSTALL_POWER_ON_STEP = "PRE_OS_INSTALL_POWER_ON_STEP";
    private static final String OS_INSTALL_REMOVE_OS_NETWORK = "OS_INSTALL_REMOVE_OS_NETWORK";
    private static final String OS_INSTALL_SET_LAN_BOOT = "OS_INSTALL_SET_LAN_BOOT";
    private static final String OS_INSTALL_SET_SAN_BOOT_TARGET = "OS_INSTALL_SET_SAN_BOOT_TARGET";
    private static final String POST_OS_INSTALL_POWER_DOWN_STEP = "POST_OS_INSTALL_POWER_DOWN_STEP";
    private static final String POST_OS_INSTALL_POWER_ON_STEP = "POST_OS_INSTALL_POWER_ON_STEP";
    private static final String REBIND_HOST_TO_TEMPLATE = "REBIND_HOST_TO_TEMPLATE";

    private static final String ROLLBACK_NOTHING_METHOD = "rollbackNothingMethod";

    private static final long TASK_STATUS_POLL_FREQUENCY = 30 * 1000;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setDevices(Map<String, ComputeDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        _workflowService = workflowService;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this._coordinator = coordinator;
    }

    public ComputeDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }

    public void setBlockOrchestrationController(BlockOrchestrationController boc) {
        blockOrchestrationController = boc;
    }

    public void setVcenterController(VcenterController vcenterController) {
        this.vcenterController = vcenterController;
    }

    /**
     * Discover compute system
     *
     * @param csId
     *            {@link URI} computeSystem id
     */
    @Override
    public void discoverComputeSystem(URI csId) throws InternalException {
        log.info("discoverComputeSystems");
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, csId);

        if (cs == null) {
            log.error("Could not get discovery target: " + csId);
            throw ComputeSystemControllerException.exceptions.targetNotFound(csId.toString());
        }

        getDevice(cs.getSystemType()).discoverComputeSystem(cs.getId());
    }

    /**
     * Create host using the specified params
     *
     * @param csId
     *            {@link URI} computesystem Id
     * @param vcpoolId
     *            {@link URI} vcpoolId
     * @param varray
     *            {@link URI} varray Id
     * @param hostId
     *            {@link URI} host Id
     * @param opId
     *            (@link String} operation Id
     */
    @Override
    public void createHost(URI csId, URI vcpoolId, URI varray, URI hostId, String opId) throws InternalException {
        log.info("createHost");

        Host host = _dbClient.queryObject(Host.class, hostId);
        ComputeElement ce = _dbClient.queryObject(ComputeElement.class, host.getComputeElement());

        ComputeVirtualPool vcp = _dbClient.queryObject(ComputeVirtualPool.class, vcpoolId);
        VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, varray);

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, ce.getComputeSystem());
        TaskCompleter tc = new ComputeHostCompleter(hostId, opId, OperationTypeEnum.CREATE_HOST, EVENT_SERVICE_TYPE);
        getDevice(cs.getSystemType()).createHost(cs, host, vcp, vArray, tc);
    }

    /**
     * Create/Add Pre-OS install steps to the workflow.
     *
     * @param workflow
     *            {@link Workflow} instance
     * @param waitFor
     *            If non-null, the step will not be queued for execution in the
     *            Dispatcher until the Step or StepGroup indicated by the
     *            waitFor has completed. The waitFor may either be a string
     *            representation of a Step UUID, or the name of a StepGroup.
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param hostId
     *            {@link URI} host Id
     * @param prepStepId
     *            {@link String} step Id
     * @return waitFor step name
     */
    @Override
    public String addStepsPreOsInstall(Workflow workflow, String waitFor, URI computeSystemId, URI hostId,
            String prepStepId) {
        log.info("addStepsPreOsInstall");

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        Host host = _dbClient.queryObject(Host.class, hostId);
        ComputeElement ce = _dbClient.queryObject(ComputeElement.class, host.getComputeElement());
        URI computeElementId = ce.getId();
        log.info("sptId:" + ce.getSptId());

        if (ce.getSptId() != null) {
            URI sptId = URI.create(ce.getSptId());
            UCSServiceProfileTemplate template = _dbClient.queryObject(UCSServiceProfileTemplate.class, sptId);
            log.info("is updating:" + template.getUpdating());
            if (template.getUpdating()) {

                waitFor = workflow.createStep(UNBIND_HOST_FROM_TEMPLATE,
                        "prepare host for os install by unbinding it from service profile template",
                        waitFor, cs.getId(), cs.getSystemType(), this.getClass(),
                        new Workflow.Method("unbindHostFromTemplateStep", computeSystemId, hostId),
                        new Workflow.Method("rollbackUnbindHostFromTemplate", computeSystemId, hostId),
                        null);

            }
            // Set host to boot from lan
            waitFor = workflow.createStep(OS_INSTALL_SET_LAN_BOOT, "Set the host to boot from LAN", waitFor, cs.getId(),
                    cs.getSystemType(), this.getClass(),
                    new Workflow.Method("setLanBootTargetStep", computeSystemId, computeElementId, hostId),
                    new Workflow.Method("setNoBootStep", computeSystemId, computeElementId, hostId), null);

            // Set the OS install Vlan on the first vnic
            waitFor = workflow.createStep(OS_INSTALL_PREPARE_OS_NETWORK, "prepare network for os install", waitFor,
                    cs.getId(), cs.getSystemType(), this.getClass(),
                    new Workflow.Method("prepareOsInstallNetworkStep", computeSystemId, computeElementId),
                    new Workflow.Method("rollbackOsInstallNetwork", computeSystemId, computeElementId, prepStepId),
                    prepStepId);

        } else {
            log.error("sptId is null!");
            throw new IllegalArgumentException(
                    "addStepsPreOsInstall method failed.  Could not find Serviceprofile template id from computeElement"
                            + ce.getLabel());
        }
        return waitFor;
    }

    /**
     * Create/Add PostOsInstall steps to the workflow.
     *
     * @param workflow
     *            {@link Workflow} instance
     * @param waitFor
     *            If non-null, the step will not be queued for execution in the
     *            Dispatcher until the Step or StepGroup indicated by the
     *            waitFor has completed. The waitFor may either be a string
     *            representation of a Step UUID, or the name of a StepGroup.
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param computeElementId
     *            {@link URI} computeElement Id
     * @param hostId
     *            {@link URI} host Id
     * @param contextStepId
     *            {@link String} step Id
     * @param volumeId
     *            {@link URI} bootvolume Id
     * @return waitFor step name
     */
    @Override
    public String addStepsPostOsInstall(Workflow workflow, String waitFor, URI computeSystemId, URI computeElementId,
            URI hostId, String contextStepId, URI volumeId) {

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

        waitFor = workflow.createStep(OS_INSTALL_REMOVE_OS_NETWORK, "remove network after os install", waitFor,
                cs.getId(), cs.getSystemType(), this.getClass(),
                new Workflow.Method("removeOsInstallNetworkStep", computeSystemId, computeElementId, contextStepId),
                new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);

        waitFor = workflow.createStep(OS_INSTALL_SET_SAN_BOOT_TARGET,
                "Set the SAN boot target based on the storage ports used in the volume export", waitFor, cs.getId(),
                cs.getSystemType(), this.getClass(),
                new Workflow.Method("setSanBootTargetStep", computeSystemId, computeElementId, hostId, volumeId),
                new Workflow.Method("setNoBootStep", computeSystemId, computeElementId, hostId), null);

        ComputeElement ce = _dbClient.queryObject(ComputeElement.class, computeElementId);

        if (ce.getSptId() != null) {
            URI sptId = URI.create(ce.getSptId());
            UCSServiceProfileTemplate template = _dbClient.queryObject(UCSServiceProfileTemplate.class, sptId);
            if (template.getUpdating()) {
                waitFor = workflow.createStep(REBIND_HOST_TO_TEMPLATE,
                        "Rebind host to service profile template after OS install", waitFor, cs.getId(),
                        cs.getSystemType(), this.getClass(),
                        new Workflow.Method("rebindHostToTemplateStep", computeSystemId, hostId),
                        new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);
            }
        } else {
            log.error("Serviceprofile ID attribute is null.");
            throw new IllegalArgumentException(
                    "addStepsPostOsInstall method failed.  Could not find Serviceprofile template id from computeElement "
                            + ce.getLabel());
        }

        return waitFor;
    }

    /**
     * This is needed if any of the workflow steps have a real rollback method.
     *
     * @param stepId
     */
    public void rollbackNothingMethod(String stepId) {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Method is responsible for invoking the setting boot from SAN
     *
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param computeElementId
     *            {@link URI} computeElement Id
     * @param hostId
     *            {@link URI} host Id
     * @param volumeId
     *            {@link URI} boot volume id
     * @param stepId
     *            {@link String} step Id
     */
    public void setSanBootTargetStep(URI computeSystemId, URI computeElementId, URI hostId, URI volumeId,
            String stepId) {
        log.info("setSanBootTargetStep");
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_072);
            setSanBootTarget(computeSystemId, computeElementId, hostId, volumeId, true);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception setSanBootTargetStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception setSanBootTargetStep: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        }

    }

    /**
     * Method is responsible for setting a no boot step
     *
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param computeElementId
     *            {@link URI} computeElement Id
     * @param hostId
     *            {@link URI} host Id
     * @param stepId
     *            {@link String} step Id
     */
    public void setNoBootStep(URI computeSystemId, URI computeElementId, URI hostId, String stepId) {
        log.info("setNoBootStep");
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            setNoBoot(computeSystemId, computeElementId, hostId, true);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception setNoBootStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception setNoBootStep: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        }

    }

    /**
     * Method is responsible for setting to boot from LAN step
     *
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param computeElementId
     *            {@link URI} computeElement Id
     * @param hostId
     *            {@link URI} host Id
     * @param stepId
     *            {@link String} step Id
     */
    public void setLanBootTargetStep(URI computeSystemId, URI computeElementId, URI hostId, String stepId) {
        log.info("setLanBootTargetStep");
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_070);
            setLanBootTarget(computeSystemId, computeElementId, hostId, true);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception setLanBootTargetStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception setLanBootTargetStep: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        }

    }

    private void setLanBootTarget(URI computeSystemId, URI computeElementId, URI hostId, boolean waitForServerRestart)
            throws InternalException {

        log.info("setLanBootTarget");
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

        getDevice(cs.getSystemType()).setLanBootTarget(cs, computeElementId, hostId, waitForServerRestart);

    }

    private void setNoBoot(URI computeSystemId, URI computeElementId, URI hostId, boolean waitForServerRestart)
            throws InternalException {

        log.info("Compute Element %s is being set to No Boot", computeElementId);
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

        getDevice(cs.getSystemType()).setNoBoot(cs, computeElementId, hostId, waitForServerRestart);

    }

    /**
     * Method is responsible for setting boot from SAN
     *
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param computeElementId
     *            {@link URI} computeElement Id
     * @param hostId
     *            {@link URI} host Id
     * @param volumeId
     *            {@link URI} boot volume id
     * @param waitForServerRestart
     */
    @Override
    public void setSanBootTarget(URI computeSystemId, URI computeElementId, URI hostId, URI volumeId,
            boolean waitForServerRestart) throws InternalException {

        log.info("setSanBootTarget");
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

        getDevice(cs.getSystemType()).setSanBootTarget(cs, computeElementId, hostId, volumeId, waitForServerRestart);

    }

    /**
     * Powers up or powers down the compute element.
     * @param computeSystemId
     * @param computeElementId
     * @param powerState
     * @param stepId
     */
    public void setPowerComputeElementStep(URI computeSystemId, URI computeElementId, String powerState,
            String stepId) {
        log.info("setPowerComputeElementStep");
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            if ("up".equals(powerState)) {
                powerUpComputeElement(computeSystemId, computeElementId);//
            } else if ("down".equals(powerState)) {
                powerDownComputeElement(computeSystemId, computeElementId);
            }

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception setPowerComputeElementStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception setPowerComputeElementStep: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        }

    }

    private void powerUpComputeElement(URI computeSystemId, URI computeElementId) throws InternalException {
        log.info("powerUpComputeElement");

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        getDevice(cs.getSystemType()).powerUpComputeElement(computeSystemId, computeElementId);
    }

    private void powerDownComputeElement(URI computeSystemId, URI computeElementId) throws InternalException {
        log.info("powerDownComputeElement");

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        getDevice(cs.getSystemType()).powerDownComputeElement(computeSystemId, computeElementId);
    }

    private String unbindHostFromTemplate(URI computeSystemId, URI hostId) throws InternalException {
        log.info("unbindHostFromTemplate");

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        return getDevice(cs.getSystemType()).unbindHostFromTemplate(computeSystemId, hostId);
    }

    private void rebindHostToTemplate(URI computeSystemId, URI hostId) throws InternalException {
        log.info("rebindHostToTemplate");

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        getDevice(cs.getSystemType()).rebindHostToTemplate(computeSystemId, hostId);
    }

    /**
     * Unbind host from service profile template
     *
     * @param computeSystemId
     *            {@link URI} computeSystem URI
     * @param hostId
     *            {@link URI} host URI
     * @param stepId
     *            {@link String} step id
     */
    public void unbindHostFromTemplateStep(URI computeSystemId, URI hostId, String stepId) {
        log.info("unbindHostFromTemplateStep");

        ComputeSystem computeSystem = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            computeSystem = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

            String sptDn = unbindHostFromTemplate(computeSystemId, hostId);
            _workflowService.storeStepData(stepId, sptDn);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            log.error("Exception unbindHostStep: " + e.getMessage(), e);
            if (computeSystem != null) {
                throw ComputeSystemControllerException.exceptions.unableToPrepareHostForOSInstall(hostId.toString(), e);
            } else {
                String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
                throw ImageServerControllerException.exceptions.unexpectedException(opName, e);
            }
        } catch (Exception e) {
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            ImageServerControllerException controllerException = ImageServerControllerException.exceptions
                    .unexpectedException(opName, e);
            log.error("Unexpected exception unbindHostFromTemplateStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, controllerException);
        }

    }

    /**
     * Roll back method to undo changes of a unbindhost from template step
     *
     * @param computeSystemId
     *            {@link URI} computeSystem URI
     * @param hostId
     *            {@link URI} host URI
     * @param stepId
     *            {@link String} step id
     */
    public void rollbackUnbindHostFromTemplate(URI computeSystemId, URI hostId, String stepId) {
        log.info("rollbackUnbindHostFromTemplate");

        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

            rebindHostToTemplate(cs.getId(), hostId);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            ImageServerControllerException controllerException = ImageServerControllerException.exceptions
                    .unexpectedException(opName, e);
            log.error("Unexpected exception rollbackUnbindHostFromTemplate: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, controllerException);
        }
    }

    /**
     * Bind host to a service profile template step
     *
     * @param computeSystemId
     *            {@link URI} computeSystem URI
     * @param hostId
     *            {@link URI} host URI
     * @param stepId
     *            {@link String} step id
     */
    public void rebindHostToTemplateStep(URI computeSystemId, URI hostId, String stepId) {
        log.info("rebindHostToTemplateStep");

        ComputeSystem computeSystem = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            computeSystem = _dbClient.queryObject(ComputeSystem.class, hostId);

            rebindHostToTemplate(computeSystemId, hostId);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            log.error("Exception rebindHostToTemplateStep: " + e.getMessage(), e);
            if (computeSystem != null) {
                throw ComputeSystemControllerException.exceptions.unableToUpdateHostAfterOSInstall(hostId.toString(),
                        e);
            } else {
                String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
                throw ImageServerControllerException.exceptions.unexpectedException(opName, e);
            }
        } catch (Exception e) {
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            ImageServerControllerException controllerException = ImageServerControllerException.exceptions
                    .unexpectedException(opName, e);
            log.error("Unexpected exception rebindHostToTemplateStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, controllerException);
        }

    }

    /**
     * Method or step responsible for setting up the required OS install network
     *
     * @param computeSystemId
     *            {@link URI} compute system URI
     * @param computeElementId
     *            {@link URI} compute element URI
     * @param stepId
     *            {@link String} step id
     */
    public void prepareOsInstallNetworkStep(URI computeSystemId, URI computeElementId, String stepId) {
        log.info("prepareOsInstallNetworkStep");

        ComputeSystem computeSystem = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            computeSystem = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_071);
            Map<String, Boolean> vlanMap = prepareOsInstallNetwork(computeSystemId, computeElementId);
            _workflowService.storeStepData(stepId, vlanMap);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            log.error("Exception prepareOsInstallNetworkStep: " + e.getMessage(), e);
            if (computeSystem != null) {
                throw ComputeSystemControllerException.exceptions.unableToSetOsInstallNetwork(
                        computeSystem.getOsInstallNetwork(), computeElementId.toString(), e);
            } else {
                String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
                throw ImageServerControllerException.exceptions.unexpectedException(opName, e);
            }
        } catch (Exception e) {
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            ImageServerControllerException controllerException = ImageServerControllerException.exceptions
                    .unexpectedException(opName, e);
            log.error("Unexpected exception prepareOsInstallNetworkStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, controllerException);
        }

    }

    /**
     * Roll back method to undo changes of a prepareOsInstallNetworkStep
     *
     * @param computeSystemId
     *            {@link URI} compute system URI
     * @param computeElementId
     *            {@link URI} compute element URI
     * @param prepareStepId
     *            parent workflow step id
     * @param stepId
     *            current step id
     */
    public void rollbackOsInstallNetwork(URI computeSystemId, URI computeElementId, String prepareStepId,
            String stepId) {
        log.info("rollbackOsInstallNetwork");

        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            ComputeElement ce = _dbClient.queryObject(ComputeElement.class, computeElementId);
            ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);

            @SuppressWarnings("unchecked")
            Map<String, Boolean> vlanMap = (Map<String, Boolean>) _workflowService.loadStepData(prepareStepId);
            log.info("vlanMap {}", vlanMap);

            if (vlanMap != null) {
                removeOsInstallNetwork(cs.getId(), cs.getSystemType(), ce.getId(), vlanMap);
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (Exception e) {
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            ImageServerControllerException controllerException = ImageServerControllerException.exceptions
                    .unexpectedException(opName, e);
            log.error("Unexpected exception rollbackOsInstallNetwork: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, controllerException);
        }
    }

    private Map<String, Boolean> prepareOsInstallNetwork(URI computeSystemId, URI computeElementId)
            throws InternalException {
        log.info("prepareOsInstallNetwork");

        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        return getDevice(cs.getSystemType()).prepareOsInstallNetwork(computeSystemId, computeElementId);
    }

    /**
     * Method to remove OS install network post OS installation
     *
     * @param computeSystemId
     *            {@link URI} computeSystem Id
     * @param computeElementId
     *            {@link URI} computeElement Id
     * @param contextStepId
     *            {@link String} parent step Id
     * @param stepId
     *            current step id
     */
    public void removeOsInstallNetworkStep(URI computeSystemId, URI computeElementId, String contextStepId,
            String stepId) {
        log.info("removeOsInstallNetworkStep");
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            @SuppressWarnings("unchecked")
            Map<String, Boolean> vlanMap = (Map<String, Boolean>) _workflowService.loadStepData(contextStepId);

            ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
            removeOsInstallNetwork(computeSystemId, cs.getSystemType(), computeElementId, vlanMap);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception removeOsInstallNetworkStep: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception removeOsInstallNetworkStep: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        }
    }

    private void removeOsInstallNetwork(URI computeSystemId, String csType, URI computeElementId,
            Map<String, Boolean> vlanMap) {
        getDevice(csType).removeOsInstallNetwork(computeSystemId, computeElementId, vlanMap);
    }

    /**
     * Method to add steps to perform host cleanup operations on the vcenter
     *
     * @param workflow
     *            {@link Workflow} instance
     * @param waitFor
     *            {@link String} If non-null, the step will not be queued for
     *            execution in the Dispatcher until the Step or StepGroup
     *            indicated by the waitFor has completed. The waitFor may either
     *            be a string representation of a Step UUID, or the name of a
     *            StepGroup.
     * @param hostId
     *            {@link URI} hostId URI
     * @return waitFor step name
     */
    @Override
    public String addStepsVcenterHostCleanup(Workflow workflow, String waitFor, URI hostId) throws InternalException {
        Host host = _dbClient.queryObject(Host.class, hostId);

        if (NullColumnValueGetter.isNullURI(host.getComputeElement())) {
            /**
             * No steps need to be added - as this was not a host that we
             * created in ViPR. If it was computeElement property of the host
             * would have been set.
             */
            return waitFor;
        }

        ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class, host.getComputeElement());

        if (computeElement != null) {
            ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeElement.getComputeSystem());

            waitFor = workflow.createStep(DEACTIVATION_MAINTENANCE_MODE,
                    "If synced with vCenter, put the host in maintenance mode", waitFor, cs.getId(),
                    cs.getSystemType(), this.getClass(), new Workflow.Method("putHostInMaintenanceMode", hostId),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD),
                    null);

            waitFor = workflow.createStep(DEACTIVATION_REMOVE_HOST_VCENTER,
                    "If synced with vCenter, remove the host from the cluster", waitFor, cs.getId(),
                    cs.getSystemType(), this.getClass(), new Workflow.Method("removeHostFromVcenterCluster", hostId),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD),
                    null);
        }

        return waitFor;
    }

    /**
     * Method to add required steps to deactivate a host
     *
     * @param workflow
     *            {@link Workflow} instance
     * @param waitFor
     *            {@link String} If non-null, the step will not be queued for
     *            execution in the Dispatcher until the Step or StepGroup
     *            indicated by the waitFor has completed. The waitFor may either
     *            be a string representation of a Step UUID, or the name of a
     *            StepGroup.
     * @param hostId
     *            {@link URI} host URI
     * @param deactivateBootVolume
     *            boolean indicating if boot volume has to be deleted.
     * @return waitFor step name
     */
    @Override
    public String addStepsDeactivateHost(Workflow workflow, String waitFor, URI hostId, boolean deactivateBootVolume)
            throws InternalException {

        Host host = _dbClient.queryObject(Host.class, hostId);

        if (host == null) {
            log.error("No host found with Id: {}", hostId);
            return waitFor;
        } else if (NullColumnValueGetter.isNullURI(host.getComputeElement())) {
            /**
             * No steps need to be added - as this was not a host that we
             * created in ViPR. If it was computeElement property of the host
             * would have been set.
             */
            log.info(
                    "Host: {} has no associated computeElement. So skipping service profile and boot volume deletion steps",
                    host.getLabel());
            return waitFor;
        }

        ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class, host.getComputeElement());

        if (computeElement != null) {
            ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeElement.getComputeSystem());
            if (cs == null){
                log.error("ComputeElement " + computeElement.getLabel() + " has an invalid computeSystem reference: " + computeElement.getComputeSystem());
                return waitFor;
            }
            //TODO: need to break this up into individual smaller steps so that we can try to recover using rollback if decommission failed
            waitFor = workflow.createStep(DEACTIVATION_COMPUTE_SYSTEM_HOST, "Unbind blade from service profile",
                    waitFor, cs.getId(), cs.getSystemType(), this.getClass(), new Workflow.Method(
                            "deactiveComputeSystemHost", cs.getId(), hostId),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);

            if (deactivateBootVolume && host.getBootVolumeId() != null) {
                waitFor = workflow.createStep(DEACTIVATION_COMPUTE_SYSTEM_BOOT_VOLUME,
                        "Delete the boot volume for the host", waitFor, cs.getId(), cs.getSystemType(),
                        this.getClass(), new Workflow.Method("deleteBlockBootVolume", hostId),
                        new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);
            } else if (!deactivateBootVolume) {
                log.info("flag deactivateBootVolume set to false");
            } else if (host.getBootVolumeId() == null){
                log.info("Host "+ host.getLabel() + " has no bootVolume association");
            }
        } else {
            log.error("Host "+ host.getLabel()+ " has associated computeElementURI: "+ host.getComputeElement()+ " which is an invalid reference");
        }

        return waitFor;
    }

    /**
     * A cluster could have only discovered hosts, only provisioned hosts, or mixed.
     * If cluster has only provisioned hosts, then the hosts will be deleted from vCenter.
     * If cluster has only discovered hosts, then the hosts will not be deleted from vCenter.
     * If cluster is mixed, then the hosts will not be deleted from the vCenter; however, the
     * provisioned hosts will still be decommissioned, and their state in vCenter will be "disconnected".
     * If a cluster is provisioned or mixed, then check VMs step will be executed since hosts with running
     * VMs may endup decommissioned.
     */
    @Override
    public String addStepsVcenterClusterCleanup(Workflow workflow, String waitFor, URI clusterId)
            throws InternalException {
        Cluster cluster = _dbClient.queryObject(Cluster.class, clusterId);
        if (null == cluster) {
            log.info("Could not find cluster instance for cluster having id {}", clusterId.toString());
            return waitFor;
        }

        if (NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
            log.info("cluster is not synced to vcenter");
            return waitFor;
        }
        List<URI> clusterHosts = ComputeSystemHelper.getChildrenUris(_dbClient, clusterId, Host.class, "cluster");
        // Check if cluster has hosts, if cluster is empty then safely remove from vcenter.
        
        //VBDU TODO: Cluster without any hosts is kind of negative case, and this information is not verified against
        // the environment, do we need to take liberty of removing the cluster from VCenter?
        
        if (null == clusterHosts || clusterHosts.isEmpty()) {
            VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class,
                    cluster.getVcenterDataCenter());
            log.info("Cluster has no hosts, removing empty cluster : {}, from vCenter : {}", cluster.getLabel(),
                    vcenterDataCenter.getLabel());
            waitFor = workflow.createStep(REMOVE_VCENTER_CLUSTER, "If synced with vCenter, remove the cluster", waitFor,
                    clusterId, clusterId.toString(), this.getClass(),
                    new Workflow.Method("removeVcenterCluster", cluster.getId(), cluster.getVcenterDataCenter()), null,
                    null);
            return waitFor;
        }

        boolean hasDiscoveredHosts = false;
        boolean hasProvisionedHosts = false;
        List<Host> hosts = _dbClient.queryObject(Host.class, clusterHosts);
        for (Host host : hosts) {
            if (NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                hasDiscoveredHosts = true;
            } else {
                hasProvisionedHosts = true;
            }
        }
        log.info("cluster has provisioned hosts: {}, and discovered hosts: {}", hasProvisionedHosts,
                hasDiscoveredHosts);

        /*
         * Check for VMs only if the cluster was provisioned or is mixed.
         */
        if (hasProvisionedHosts) {
            waitFor = workflow.createStep(CHECK_CLUSTER_VMS,
                    "If synced with vCenter, check if there are VMs in the cluster", waitFor, clusterId,
                    clusterId.toString(), this.getClass(),
                    new Workflow.Method("checkClusterVms", cluster.getId(), cluster.getVcenterDataCenter()),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);
        }

        /*
         * Remove cluster from vcenter only if all hosts are provisioned.
         */
        if (hasProvisionedHosts && !hasDiscoveredHosts) {
            waitFor = workflow.createStep(REMOVE_VCENTER_CLUSTER, "If synced with vCenter, remove the cluster", waitFor,
                    clusterId, clusterId.toString(), this.getClass(),
                    new Workflow.Method("removeVcenterCluster", cluster.getId(), cluster.getVcenterDataCenter()),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);
        }

        return waitFor;
    }

    /**
     * Deactivates or deletes the boot volume
     *
     * @param hostId
     *            {@link URI} hostId URI
     * @param stepId
     *            {@link String} step id
     */
    public void deleteBlockBootVolume(URI hostId, String stepId) {

        log.info("deleteBlockBootVolume");

        Host host = null;

        try {

            WorkflowStepCompleter.stepExecuting(stepId);

            host = _dbClient.queryObject(Host.class, hostId);

            List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();

            if (host != null && !NullColumnValueGetter.isNullURI(host.getBootVolumeId())) {

                Volume bootVolume = _dbClient.queryObject(Volume.class, host.getBootVolumeId());
                if (bootVolume.getPool() != null) {
                    StoragePool storagePool = _dbClient.queryObject(StoragePool.class, bootVolume.getPool());
                    if (storagePool != null && storagePool.getStorageDevice() != null) {
                        volumeDescriptors.add(new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                                storagePool.getStorageDevice(), host.getBootVolumeId(), null, null));
                    }
                } //TODO: below commented code is delete for vplex boot volume and this will be removed
                /* else if (bootVolume.isVPlexVolume(_dbClient)) {
                    URI systemURI = bootVolume.getProtectionController();
                    if(NullColumnValueGetter.isNullURI(systemURI)) {
                        systemURI = bootVolume.getStorageController();
                    }
                    VolumeDescriptor descriptor = new VolumeDescriptor(VolumeDescriptor.Type.VPLEX_VIRT_VOLUME,
                            systemURI, bootVolume.getId(), null, null);
                    volumeDescriptors.add(descriptor);
                    // Add descriptors for each of the associated volume
                    if(null != bootVolume.getAssociatedVolumes()) {
                        for (String  associatedVolumeId : bootVolume.getAssociatedVolumes()) {
                            Volume associatedVolume = _dbClient.queryObject(Volume.class, URI.create(associatedVolumeId));
                            if (null != associatedVolume && !associatedVolume.getInactive() && associatedVolume.getNativeId() != null) {
                                VolumeDescriptor assocDesc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                                        associatedVolume.getStorageController(), associatedVolume.getId(), null, null);
                                volumeDescriptors.add(assocDesc);
                            }
                        }
                    }
                }*/

                String task = UUID.randomUUID().toString();

                Operation op = _dbClient.createTaskOpStatus(Volume.class, bootVolume.getId(), task,
                        ResourceOperationTypeEnum.DELETE_BLOCK_VOLUME);
                bootVolume.getOpStatus().put(task, op);

                _dbClient.updateObject(bootVolume);

                final String workflowKey = "deleteVolumes";
                if (!WorkflowService.getInstance().hasWorkflowBeenCreated(task, workflowKey)) {
                    blockOrchestrationController.deleteVolumes(volumeDescriptors, task);
                    // Mark this workflow as created/executed so we don't do it
                    // again on retry/resume
                    WorkflowService.getInstance().markWorkflowBeenCreated(task, workflowKey);

                    while (true) {
                        Thread.sleep(TASK_STATUS_POLL_FREQUENCY);
                        bootVolume = _dbClient.queryObject(Volume.class, host.getBootVolumeId());

                        switch (Status.toStatus(bootVolume.getOpStatus().get(task).getStatus())) {
                        case ready:
                            WorkflowStepCompleter.stepSucceded(stepId);
                            return;
                        case error:
                            log.warn("Unable to delete block volume associated with Host...",
                                    ComputeSystemControllerException.exceptions
                                    .unableToDeactivateBootVolumeAssociatedWithHost(host.getHostName(),
                                            host.getId().toASCIIString(),
                                            host.getBootVolumeId().toASCIIString(),
                                            bootVolume.getOpStatus().get(task).getMessage()));
                            WorkflowStepCompleter.stepFailed(stepId, ComputeSystemControllerException.exceptions
                                    .unableToDeactivateBootVolumeAssociatedWithHost(host.getHostName(),
                                            host.getId().toASCIIString(), host.getBootVolumeId().toASCIIString(),
                                            bootVolume.getOpStatus().get(task).getMessage()));
                            return;
                        case pending:
                            break;

                        }
                    }
                }

            } else {
                /**
                 * Nothing to do... No-op it
                 */
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }
        } catch (Exception exception) {
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions
                    .unableToDeactivateHost(host != null ? host.getHostName() : hostId.toString(), exception);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }

    }

    /**
     * This will attempt to put host into maintenance mode on a Vcenter.
     *
     * @param hostId
     * @param stepId
     */
    public void putHostInMaintenanceMode(URI hostId, String stepId) {
        log.info("putHostInMaintenanceMode {}", hostId);
        Host host = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            host = _dbClient.queryObject(Host.class, hostId);

            if (NullColumnValueGetter.isNullURI(host.getVcenterDataCenter())) {
                log.info("datacenter is null, nothing to do");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }
            if (NullColumnValueGetter.isNullURI(host.getCluster())) {
                log.warn("cluster is null, nothing to do");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            vcenterController.enterMaintenanceMode(host.getVcenterDataCenter(), host.getCluster(), host.getId());

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VcenterControllerException e) {
            log.warn("VcenterControllerException when trying to putHostInMaintenanceMode: " + e.getMessage(), e);
            if (e.getCause() instanceof VcenterObjectNotFoundException) {
                log.info("did not find the host, considering success");
                WorkflowStepCompleter.stepSucceded(stepId);
            } else if (e.getCause() instanceof VcenterObjectConnectionException) {
                log.info("host is not connected, considering success");
                WorkflowStepCompleter.stepSucceded(stepId);
            } else {
                log.error("failure " + e);
                WorkflowStepCompleter.stepFailed(stepId, e);
            }
        } catch (InternalException e) {
            log.error("InternalException when trying to putHostInMaintenanceMode: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("unexpected exception" + e.getMessage(), e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions
                    .unableToPutHostInMaintenanceMode(host != null ? host.getHostName() : hostId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }
    }

    /**
     * This will attempt to remove host from vCenter cluster.
     *
     * @param hostId
     * @param stepId
     */
    public void removeHostFromVcenterCluster(URI hostId, String stepId) {
        log.info("removeHostFromVcenterCluster {}", hostId);
        Host host = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            host = _dbClient.queryObject(Host.class, hostId);

            if (NullColumnValueGetter.isNullURI(host.getVcenterDataCenter())) {
                log.info("datacenter is null, nothing to do");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }
            if (NullColumnValueGetter.isNullURI(host.getCluster())) {
                log.warn("cluster is null, nothing to do");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_068);
            String taskId = UUID.randomUUID().toString();
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.UPDATE_VCENTER_CLUSTER);
            _dbClient.createTaskOpStatus(VcenterDataCenter.class, host.getVcenterDataCenter(), taskId, op);
            AsyncTask task = new AsyncTask(VcenterDataCenter.class, host.getVcenterDataCenter(), taskId);
            vcenterController.updateVcenterCluster(task, host.getCluster(), null, new URI[] { host.getId() }, null);

            log.info("Monitor remove host " + host.getHostName() + " update vCenter task...");
            //VBDU TODO: Anti pattern - completers are responsible for updating step status.
            while (true) {
                Thread.sleep(TASK_STATUS_POLL_FREQUENCY);
                VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class,
                        host.getVcenterDataCenter());

                switch (Status.toStatus(vcenterDataCenter.getOpStatus().get(taskId).getStatus())) {
                case ready:
                    log.info("vCenter update request succeeded");
                    WorkflowStepCompleter.stepSucceded(stepId);
                    return;
                case error:
                    log.info("vCenter update request failed - Best effort only so consider success");
                    WorkflowStepCompleter.stepSucceded(stepId); // Only best
                    // effort
                    return;
                case pending:
                    break;

                }
            }
        } catch (VcenterControllerException e) {
            log.warn("VcenterControllerException when trying to removeHostFromVcenterCluster: " + e.getMessage(), e);
            if (e.getCause() instanceof VcenterObjectNotFoundException) {
                log.info("did not find the host, considering success");
                WorkflowStepCompleter.stepSucceded(stepId);
            } else if (e.getCause() instanceof VcenterObjectConnectionException) {
                log.info("host is not connected, considering success");
                WorkflowStepCompleter.stepSucceded(stepId);
            } else {
                log.error("failure " + e);
                WorkflowStepCompleter.stepFailed(stepId, e);
            }
        } catch (InternalException e) {
            log.error("InternalException when trying to removeHostFromVcenterCluster: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("unexpected exception: " + e.getMessage(), e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions
                    .unableToRemoveHostVcenterCluster(host != null ? host.getHostName() : hostId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }
    }

    /**
     * Checks if the cluster in Vcenter has VMs. Exception is thrown if VMs are
     * present.
     *
     * @param clusterId
     * @param datacenterId
     * @param stepId
     */
    public void checkClusterVms(URI clusterId, URI datacenterId, String stepId) {
        log.info("checkClusterVms {} {}", clusterId, datacenterId);
        Cluster cluster = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            cluster = _dbClient.queryObject(Cluster.class, clusterId);

            List<String> vmList = vcenterController.getVirtualMachines(datacenterId, clusterId);

            if (!vmList.isEmpty()) {
                log.error("there are {} VMs in the cluster", vmList.size());
                throw ComputeSystemControllerException.exceptions
                .clusterHasVms(cluster != null ? cluster.getLabel() : clusterId.toString());
            } else {
                log.info("there are no VMs in the cluster, step successful");
            }

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VcenterControllerException e) {
            log.warn("VcenterControllerException when trying to checkClusterVms: " + e.getMessage(), e);
            if (e.getCause() instanceof VcenterObjectNotFoundException) {
                log.info("did not find the datacenter or cluster, considering success");
                WorkflowStepCompleter.stepSucceded(stepId);
            } else {
                log.error("failure " + e);
                WorkflowStepCompleter.stepFailed(stepId, e);
            }
        } catch (InternalException e) {
            log.error("InternalException when trying to checkClusterVms: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("unexpected exception " + e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions
                    .unableToCheckClusterVms(cluster != null ? cluster.getLabel() : clusterId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }
    }

    /**
     * Remove cluster from vCenter.
     *
     * @param clusterId
     * @param datacenterId
     * @param stepId
     */
    public void removeVcenterCluster(URI clusterId, URI datacenterId, String stepId) {
        log.info("removeVcenterCluster {} {}", clusterId, datacenterId);
        Cluster cluster = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            cluster = _dbClient.queryObject(Cluster.class, clusterId);

            vcenterController.removeVcenterCluster(datacenterId, clusterId);
            log.info("Remove vCenter cluster success");

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VcenterControllerException e) {
            log.warn("VcenterControllerException when trying to removeVcenterCluster: " + e.getMessage(), e);
            if (e.getCause() instanceof VcenterObjectNotFoundException) {
                log.info("did not find the datacenter or cluster, considering success");
                WorkflowStepCompleter.stepSucceded(stepId);
            } else {
                log.error("failure " + e);
                WorkflowStepCompleter.stepFailed(stepId, e);
            }
        } catch (InternalException e) {
            log.error("InternalException when trying to removeVcenterCluster: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("unexpected exception " + e);
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions
                    .unableToRemoveVcenterCluster(cluster != null ? cluster.getLabel() : clusterId.toString(), e);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
        }
    }

    /**
     * Deactivate compute system host
     *
     * @param csId
     *            {@link URI} compute system URI
     * @param hostId
     *            {@link URI} hsot URI
     * @param stepId
     *            step id
     */
    public void deactiveComputeSystemHost(URI csId, URI hostId, String stepId) {

        log.info("deactiveComputeSystemHost");

        Host host = null;

        try {

            WorkflowStepCompleter.stepExecuting(stepId);

            ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, csId);

            host = _dbClient.queryObject(Host.class, hostId);
            if (null != host) {
            	//VBDU TODO: Need to check initiators inside the host as well.
                if (NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                    // NO-OP
                    log.info("Host " + host.getLabel() + " has no computeElement association");
                    WorkflowStepCompleter.stepSucceded(stepId);
                    return;
                } else {
                    ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class,
                            host.getComputeElement());
                    if (NullColumnValueGetter.isNullValue(computeElement.getDn())) {
                        log.info("Host " + host.getLabel() + " has computeElement " + host.getComputeElement()
                        + " with label " + computeElement.getLabel() + " and Dn " + computeElement.getDn());
                        WorkflowStepCompleter.stepSucceded(stepId);
                        return;
                    }
                }

                getDevice(cs.getSystemType()).deactivateHost(cs, host);
            }else {
                throw new RuntimeException("Host null for uri "+ hostId);
            }

        } catch (Exception exception) {
            ServiceCoded serviceCoded = ComputeSystemControllerException.exceptions
                    .unableToDeactivateHost(host != null ? host.getHostName() : hostId.toString(), exception);
            WorkflowStepCompleter.stepFailed(stepId, serviceCoded);
            return;
        }

        WorkflowStepCompleter.stepSucceded(stepId);

    }
}
