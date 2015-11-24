/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.net.URI;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.systemservices.impl.ipreconfig.IpReconfigManager;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;
import com.emc.vipr.model.sys.ipreconfig.ClusterNetworkReconfigStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.sun.jersey.api.client.ClientHandlerException;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.ServicesMetadata;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import static com.emc.storageos.coordinator.client.model.Constants.*;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.dbrepair.RepairStatusManager;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.impl.recovery.RecoveryManager;

/**
 * Control service is used to
 * restart a service on a specified node
 * reboot a node
 */
@Path("/control/")
public class ControlService {
    @Autowired
    private AuditLogManager _auditMgr;

    private static final Logger _log = LoggerFactory.getLogger(ConfigService.class);
    private static final AlertsLogger _alertsLog = AlertsLogger.getAlertsLogger();
    private static final String EVENT_SERVICE_TYPE = "control";

    private CoordinatorClientExt _coordinator = null;

    @Autowired
    private RecoveryManager recoveryManager;

    @Autowired
    private RepairStatusManager repairStatusManager;
    
    @Autowired
    private IpReconfigManager ipreconfigManager;

    @Autowired
    private PropertyManager propertyManager;
    private final static String FORCE = "1";

    @Context
    protected SecurityContext sc;

    private ArrayList<String> aliveNodes = new ArrayList<String>();

    /**
     * Get StorageOSUser from the security context
     * 
     * @return
     */
    protected StorageOSUser getUserFromContext() {
        if (!hasValidUserInContext()) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        return (StorageOSUser) sc.getUserPrincipal();
    }

    /**
     * Determine if the security context has a valid StorageOSUser object
     * 
     * @return true if the StorageOSUser is present
     */
    protected boolean hasValidUserInContext() {
        if ((sc != null) && (sc.getUserPrincipal() instanceof StorageOSUser)) {
            return true;
        } else {
            return false;
        }
    }

    private enum ControlState {
        RUN,
        RESTART,
        REBOOT,
        END
    }

    private volatile ControlState controlState = ControlState.RUN;

    private final String lock = "Lock";

    public void setProxy(CoordinatorClientExt proxy) {
        _coordinator = proxy;
    }

    @PostConstruct
    public void initControlThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                _log.info("Control thread started.");
                synchronized (lock) {
                    while (!controlState.equals(ControlState.END)) {
                        if (controlState.equals(ControlState.REBOOT)) {
                            _log.info("Node is being rebooted.");
                            try {
                                controlState = ControlState.END;
                                LocalRepository.getInstance().reboot();
                            } catch (LocalRepositoryException e1) {
                                _log.error("Error rebooting: {}", e1.toString());
                                controlState = ControlState.RUN;
                            }
                        } else if (controlState.equals(ControlState.RESTART)) {
                            _log.info("Restarting syssvc.");
                            try {
                                controlState = ControlState.END;
                                System.exit(0);
                            } catch (LocalRepositoryException e2) {
                                _log.error("Error restarting syssvc: {}", e2.toString());
                                controlState = ControlState.RUN;
                            }
                        } else {
                            try {
                                lock.wait();

                            } catch (InterruptedException e) {
                                _log.warn("Control thread interrupted", e);
                            }
                        }
                    }
                }
            }
        }).start();
    }

    @PreDestroy
    public void destroyControlThread() {
        synchronized (lock) {
            controlState = ControlState.END;
            lock.notifyAll();
        }
    }

    /**
     * Restart a service on a virtual machine
     * 
     * @brief Restart a service on a virtual machine
     * @param nodeId Virtual machine id (e.g. vipr1)
     * @param nodeName node name of Virtual machine
     * @prereq none
     * @param name Service name
     */
    @POST
    @Path("service/restart")
    @CheckPermission(roles = {Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response restartService(@QueryParam("node_id") String nodeId, @QueryParam("name") String name, @QueryParam("node_name") String nodeName) {

        nodeId=determineNodeId(nodeId,nodeName);

        List<String> controlNodeServiceNames = ServicesMetadata.getControlNodeServiceNames();
        if (_coordinator.getMyNodeId().equals(nodeId)) {
            if (!controlNodeServiceNames.contains(name)) {
                throw APIException.badRequests.parameterIsNotOneOfAllowedValues("service name", controlNodeServiceNames.toString());
            }
            auditControl(OperationTypeEnum.RESTART_SERVICE,
                    AuditLogManager.AUDITLOG_SUCCESS, null, name, nodeId);
            return restartNodeService(name);
        } else {
            // get endpoint of node
            URI endpoint = _coordinator.getNodeEndpoint(nodeId);
            if (endpoint == null) {
                throw APIException.badRequests.parameterIsNotValid("node id");
            }

            // check available service name exists
            boolean isControlNode = CONTROL_NODE_ID_PATTERN.matcher(nodeId).matches();
            List<String> availableServices = isControlNode ?
                    controlNodeServiceNames : ServicesMetadata.getExtraNodeServiceNames();
            if (!availableServices.contains(name)) {
                throw APIException.badRequests.parameterIsNotOneOfAllowedValues("service name", availableServices.toString());
            }

            try {
                SysClientFactory.getSysClient(endpoint)
                        .post(URI.create(SysClientFactory.URI_RESTART_SERVICE.getPath() + "?name=" + name), null, null);
            } catch (SysClientException e) {
                throw APIException.internalServerErrors.serviceRestartError(name, nodeId);
            }
            auditControl(OperationTypeEnum.RESTART_SERVICE,
                    AuditLogManager.AUDITLOG_SUCCESS, null, name, nodeId);
            return Response.status(Response.Status.ACCEPTED).build();
        }
    }

    @POST
    @Path("internal/service/restart")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response restartNodeService(@QueryParam("name") String name) {
        _log.info("Restart node service: {}", name);
        // restart service
        if (name.equals("syssvc")) {
            synchronized (lock) {
                _log.info("Notify the control thread!");
                controlState = ControlState.RESTART;
                lock.notifyAll();
            }
            _log.info("Activated the control thread!");
            return Response.status(Response.Status.ACCEPTED).build(); // Return the accepted status code
        } else {
            try {
                LocalRepository.getInstance().restart(name);
            } catch (LocalRepositoryException e) {
                throw APIException.internalServerErrors.serviceRestartError(name, _coordinator.getMyNodeId());
            }
            return Response.ok().build();
        }
    }

    /**
     * Reboot a virtual machine
     * 
     * @brief Reboot a virtual machine
     * @param nodeId Virtual machine id (e.g. vipr1)
     * @param nodeName node name of Virtual machine
     * @prereq none
     */
    @POST
    @Path("node/reboot")
    @CheckPermission(roles = {Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response rebootNode(@QueryParam("node_id") String nodeId, @QueryParam("node_name") String nodeName) {

        nodeId=determineNodeId(nodeId,nodeName);

        _log.info("Reboot node: "+nodeId);
        if(_coordinator.getMyNodeId().equals(nodeId)){
            auditControl(OperationTypeEnum.REBOOT_NODE, AuditLogManager.AUDITLOG_SUCCESS,
                    null, nodeId);
            return rebootNode();
        } else {
            // Otherwise get endpoint of node
            URI endpoint = _coordinator.getNodeEndpoint(nodeId);
            if (endpoint == null) {
                throw APIException.badRequests.parameterIsNotValid("node id");
            }
            try {
                SysClientFactory.getSysClient(endpoint)
                        .post(SysClientFactory.URI_REBOOT_NODE, null, null);
            } catch (SysClientException e) {
                throw APIException.internalServerErrors.sysClientError("reboot node");
            }
            auditControl(OperationTypeEnum.REBOOT_NODE, AuditLogManager.AUDITLOG_SUCCESS,
                    null, nodeId);
            return Response.status(Response.Status.ACCEPTED).build();
        }
    }

    @POST
    @Path("internal/node/reboot")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response rebootNode() {
        _log.info("Reboot node");
        synchronized (lock) {
            _log.info("Notify the control thread!");
            controlState = ControlState.REBOOT;
            lock.notifyAll();
        }
        _log.info("Activated the control thread!");
        return Response.status(Response.Status.ACCEPTED).build(); // Return the accepted status code
    }

    @POST
    @Path("internal/node/poweroff-agreement")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response receivePoweroffAgreement(@QueryParam("sender") String svcId) {
        _log.info("Receiving poweroff agreement");
        propertyManager.getPoweroffAgreementsKeeper().add(svcId);
        return Response.ok().build();
    }

    /**
     * This method will set the target poweroff state to PowerOffState.State.START
     * If UpgradeManager sees the target poweroff state is not NONE,
     * node will start to sync with each other's poweroff state until they agree to power off.
     * Node's poweroff state will change from NOTICED to ACKNOWLEDGED. During the process, all nodes
     * are checked to see each other's state until they can move to next state.
     * After all nodes move to ACKNOWLEDGED state, they move to POWEROFF state,
     * in which, they are free to power off
     * 
     * @brief Power off ViPR controller appliance
     * @prereq none
     * @return Status of ViPR controller appliance
     * @throws Exception
     */
    @POST
    @Path("cluster/poweroff")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response powerOffCluster(@QueryParam("force") String forceSet) throws Exception {
        _log.debug("Poweroff cluster");

        PowerOffState.State targetPoweroffState = _coordinator.getTargetInfo(PowerOffState.class).getPowerOffState();
        _log.info("Current target poweroff state is: {}", targetPoweroffState.toString());
        if (targetPoweroffState.equals(PowerOffState.State.FORCESTART) || targetPoweroffState.equals(PowerOffState.State.START)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("A poweroff proccess is in progress, cannot accept another poweroff request.").build();
        }

        PowerOffState poweroffState;
        if (FORCE.equals(forceSet)) {
            poweroffState = new PowerOffState(PowerOffState.State.FORCESTART);
        } else {
            poweroffState = new PowerOffState(PowerOffState.State.START);
        }
        // set target poweroff state to START or FORCESTART
        try {
            _coordinator.setTargetInfo(poweroffState, false);
            propertyManager.wakeupOtherNodes();
            _alertsLog.warn("power off start");
        } catch (ClientHandlerException e) {
            if (!FORCE.equals(forceSet)) {
                throw APIException.internalServerErrors.poweroffWakeupError(e);
            } else {
                // if the force option is specified, ignore sysclient exceptions since the poweroff will succeed anyways.
                _log.warn("failed to wakeup all nodes. Will poweroff the cluster by force.");
            }
        } catch (Exception e) {
            throw APIException.internalServerErrors.setObjectToError("target poweroff state", "coordinator", e);
        }

        auditControl(OperationTypeEnum.POWER_OFF_CLUSTER, AuditLogManager.AUDITLOG_SUCCESS, null);
        try {
            return Response.status(Response.Status.ACCEPTED).build();
        } finally {
            propertyManager.wakeup();
        }
    }

    /**
     * Power off current node
     */
    @POST
    @Path("internal/node/poweroff")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response powerOffNode() {
        _log.info("Poweroff node");
        recoveryManager.poweroff();
        return Response.status(Response.Status.ACCEPTED).build(); // Return the accepted status code
    }

    /**
     * Trigger minority node recovery
     */
    @POST
    @Path("cluster/recovery")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response clusterRecovery() throws Exception {
        _log.info("Received a cluster recovery request");

        try {
            if (ipreconfigManager.underIpReconfiguration()) {
                String errstr = "Failed to trigger node recovery, ip reconfiguration is ongoing...";
                _log.warn(errstr);
                throw new IllegalStateException(errstr);
            }
            recoveryManager.triggerNodeRecovery();
            auditControl(OperationTypeEnum.RECOVER_NODES, AuditLogManager.AUDITLOG_SUCCESS, null);
        } catch (Exception e) {
            auditControl(OperationTypeEnum.RECOVER_NODES, AuditLogManager.AUDITLOG_FAILURE, null);
            throw APIException.internalServerErrors.triggerRecoveryFailed(e.getMessage());
        }
        _log.info("Accepted the cluster recovery request");
        return Response.status(Response.Status.ACCEPTED).build();
    }

    /**
     * Show node recovery status
     * 
     * @brief Show node recovery status
     * @prereq none
     * @return Node recovery status information
     * @throws Exception
     */
    @GET
    @Path("cluster/recovery")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public RecoveryStatus getRecoveryStatus() throws Exception {
        _log.info("Received a getting cluster recovery status request");
        return recoveryManager.queryNodeRecoveryStatus();
    }

    /**
     * Get the db repair status.
     * 
     * @brief Show db repair status
     * @prereq none
     * @return db repair status
     * @throws Exception
     */
    @GET
    @Path("cluster/dbrepair-status")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public DbRepairStatus getDbRepairStatus() throws Exception {
        _log.info("Received a getting db repair status request");
        return repairStatusManager.getDbRepairStatus();
    }

    /**
     * Retrieve db repair status from dbsvc/geodbsvc on current node
     */
    @GET
    @Path("internal/node/dbrepair-status")
    @Produces({ MediaType.APPLICATION_JSON })
    public DbRepairStatus getDbRepairStatusFromLocalNode() throws Exception {
        _log.info("Check db repair status");
        return repairStatusManager.getDbRepairStatus();
    }

    /**
     * Trigger ip reconfiguration
     */
    @POST
    @Path("cluster/ipreconfig")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response clusterIpReconfig(ClusterIpInfo clusterIpInfo,
            @DefaultValue("reboot") @QueryParam("postOperation") String postOperation) throws Exception {
        _log.info("Received a cluster ip reconfiguration request");
        try {
            ipreconfigManager.triggerIpReconfig(clusterIpInfo, postOperation);
            auditControl(OperationTypeEnum.RECONFIG_IP, AuditLogManager.AUDITLOG_SUCCESS, null);
        } catch (Exception e) {
            auditControl(OperationTypeEnum.RECONFIG_IP, AuditLogManager.AUDITLOG_FAILURE, null);
            throw APIException.internalServerErrors.triggerIpReconfigFailed(e.getMessage());
        }
        _log.info("Accepted the cluster ip reconfiguration request");
        return Response.status(Response.Status.ACCEPTED).build();
    }

    /**
     * Query ip reconfiguration status
     */
    @GET
    @Path("cluster/ipreconfig_status")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ClusterNetworkReconfigStatus getClusterNetworkReconfigStatus() throws Exception {
        _log.info("Querying cluster ip reconfiguration status");
        return ipreconfigManager.queryClusterNetworkReconfigStatus();
    }

    /**
     * Query current ip configuration
     */
    @GET
    @Path("cluster/ipinfo")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ClusterIpInfo getClusterIpinfo() throws Exception {
        _log.info("Querying cluster ip configuration");
        return ipreconfigManager.queryCurrentClusterIpinfo();
    }

    /**
     * Record audit log for control service
     * 
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public void auditControl(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {

        URI username = null;

        username = URI.create(getUserFromContext().getName());

        _auditMgr.recordAuditLog(null, username,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(), operationalStatus,
                description,
                descparams);
    }

    /**
     * Verify only one parameter is provided for selecting node
     * Determine if nodeId should be used or nodeName should be converted to nodeId and used
     *
     * @param nodeId Id of the node
     * @param nodeName Name of the node
     */
    private String determineNodeId(String nodeId, String nodeName){
        if (nodeName != null) {
            //check that nodeId is empty
            if (nodeId != null) {
                throw APIException.badRequests.theParametersAreNotValid("cannot use node_id and node_name");
            }

            //get nodeIds for node names
            nodeId = _coordinator.getMatchingNodeId(nodeName);
            _log.info("Found node id {} for node name {}",nodeId,nodeName);

            //verify nodeId found
            if (nodeId == null) {
                throw APIException.badRequests.parameterIsNotValid("node name");
            }
        }
        return nodeId;
    }
}
