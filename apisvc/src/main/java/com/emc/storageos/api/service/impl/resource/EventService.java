/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.EventMapper;
import com.emc.storageos.api.mapper.functions.MapEvent;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.event.EventBulkRep;
import com.emc.storageos.model.event.EventDetailsRestRep;
import com.emc.storageos.model.event.EventList;
import com.emc.storageos.model.event.EventRestRep;
import com.emc.storageos.model.event.EventStatsRestRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A service that provides APIs for viewing, approving, declining and removing actionable events.
 */
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN }, writeRoles = {
        Role.TENANT_ADMIN }, readAcls = { ACL.ANY })
@Path("/vdc/events")
public class EventService extends TaggedResource {

    protected final static Logger _log = LoggerFactory.getLogger(EventService.class);

    private static final String EVENT_SERVICE_TYPE = "event";
    private static final String TENANT_QUERY_PARAM = "tenant";

    private static final String RESOURCE_QUERY_PARAM = "resource";

    private static final String DETAILS_SUFFIX = "Details";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventRestRep getEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        return EventMapper.map(event);
    }

    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deleteEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        _dbClient.markForDeletion(event);
        _log.info(
                "Deleting Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: " + event.getDescription()
                        + " Warning: " + event.getWarning()
                        + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                        + event.getEventCode());
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/approve")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskList approveEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());

        if (!StringUtils.equalsIgnoreCase(event.getEventStatus(), ActionableEvent.Status.pending.name())) {
            throw APIException.badRequests.eventCannotBeApproved(event.getEventStatus());
        }

        _log.info(
                "Approving Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: " + event.getDescription()
                        + " Warning: " + event.getWarning()
                        + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                        + event.getEventCode());

        return executeEventMethod(event, true);
    }

    @GET
    @Path("/{id}/details")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventDetailsRestRep eventDetails(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        EventDetailsRestRep eventDetails = new EventDetailsRestRep();
        eventDetails.setApproveDetails(getEventDetails(event, true));
        eventDetails.setDeclineDetails(getEventDetails(event, false));
        return eventDetails;
    }

    /**
     * Gets details for an event
     * 
     * @param event the event to get details for
     * @param approve if true, get the approve details, if false the get the decline details
     * @return event details
     */
    public List<String> getEventDetails(ActionableEvent event, boolean approve) {

        byte[] method = approve ? event.getApproveMethod() : event.getDeclineMethod();

        if (method == null || method.length == 0) {
            _log.info("Method is null or empty for event " + event.getId());
            return Lists.newArrayList("N/A");
        }

        ActionableEvent.Method eventMethod = ActionableEvent.Method.deserialize(method);
        if (eventMethod == null) {
            _log.info("Event method is null or empty for event " + event.getId());
            return Lists.newArrayList("N/A");
        }

        String eventMethodName = eventMethod.getMethodName() + DETAILS_SUFFIX;

        try {
            Method classMethod = getMethod(EventService.class, eventMethodName);
            if (classMethod == null) {
                return Lists.newArrayList("N/A");
            } else {
                return (List<String>) classMethod.invoke(this, eventMethod.getArgs());
            }
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            _log.error(e.getMessage(), e.getCause());
            throw APIException.badRequests.errorInvokingEventMethod(event.getId(), eventMethodName);
        }
    }

    /**
     * Executes an actionable event method
     * 
     * @param event the event to execute
     * @param approve if true, the action is to approve, if false the action is to decline
     * @return list of tasks
     */
    public TaskList executeEventMethod(ActionableEvent event, boolean approve) {
        TaskList taskList = new TaskList();

        byte[] method = approve ? event.getApproveMethod() : event.getDeclineMethod();
        String eventStatus = approve ? ActionableEvent.Status.approved.name() : ActionableEvent.Status.declined.name();

        event.setEventExecutionTime(Calendar.getInstance());
        event.setApproveDetails(new StringSet(getEventDetails(event, true)));
        event.setDeclineDetails(new StringSet(getEventDetails(event, false)));

        if (method == null || method.length == 0) {
            _log.info("Method is null or empty for event " + event.getId());
            event.setEventStatus(eventStatus);
            _dbClient.updateObject(event);
            return taskList;
        }

        ActionableEvent.Method eventMethod = ActionableEvent.Method.deserialize(method);
        if (eventMethod == null) {
            _log.info("Event method is null or empty for event " + event.getId());
            event.setEventStatus(eventStatus);
            _dbClient.updateObject(event);
            return taskList;
        }

        try {
            Method classMethod = getMethod(EventService.class, eventMethod.getMethodName());
            TaskResourceRep result = (TaskResourceRep) classMethod.invoke(this, eventMethod.getArgs());
            event.setEventStatus(eventStatus);
            if (result != null && result.getId() != null) {
                Collection<String> taskCollection = Lists.newArrayList(result.getId().toString());
                event.setTaskIds(new StringSet(taskCollection));
            }
            _dbClient.updateObject(event);
            taskList.addTask(result);
            return taskList;
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            _log.error(e.getMessage(), e.getCause());
            throw APIException.badRequests.errorInvokingEventMethod(event.getId(), eventMethod.getMethodName());
        }
    }

    /**
     * Get details for the addInitiator method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the id if the initiator to add
     * @return list of event details
     */
    @SuppressWarnings("unused")  // Invoked using reflection for the event framework
    public List<String> addInitiatorDetails(URI initiatorId) {
        List<String> result = Lists.newArrayList();
        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
        if (initiator != null) {
            List<ExportGroup> exportGroups = ComputeSystemHelper.findExportsByHost(_dbClient, initiator.getHost().toString());

            for (ExportGroup export : exportGroups) {
                List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());

                List<Initiator> validInitiator = ComputeSystemHelper.validatePortConnectivity(_dbClient, export,
                        Lists.newArrayList(initiator));
                if (!validInitiator.isEmpty()) {
                    boolean update = false;
                    for (Initiator initiatorObj : validInitiator) {
                        // if the initiators is not already in the list add it.
                        if (!updatedInitiators.contains(initiator.getId())) {
                            updatedInitiators.add(initiator.getId());
                            update = true;
                        }
                    }

                    if (update) {
                        result.addAll(getVolumes(initiator.getHost(), export.getVolumes(), true));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Method to add an initiator to existing exports for a host.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the initiator to add
     * @return task for adding an initiator
     */
    public TaskResourceRep addInitiator(URI initiatorId) {
        Initiator initiator = queryObject(Initiator.class, initiatorId, true);
        Host host = queryObject(Host.class, initiator.getHost(), true);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiatorId, taskId,
                ResourceOperationTypeEnum.ADD_HOST_INITIATOR);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.addInitiatorsToExport(initiator.getHost(), Arrays.asList(initiator.getId()), taskId);
        } else {
            // No updates were necessary, so we can close out the task.
            _dbClient.ready(Initiator.class, initiator.getId(), taskId);
        }

        auditOp(OperationTypeEnum.CREATE_HOST_INITIATOR, true, null,
                initiator.auditParameters());
        return toTask(initiator, taskId, op);
    }

    /**
     * Get details for the removeInitiator method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the id if the initiator to remove
     * @return list of event details
     */
    @SuppressWarnings("unused")  // Invoked using reflection for the event framework
    public List<String> removeInitiatorDetails(URI initiatorId) {
        List<String> result = Lists.newArrayList();

        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
        if (initiator != null) {
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getExportGroups(_dbClient, initiator.getId(),
                    Lists.newArrayList(initiator));

            for (ExportGroup export : exportGroups) {
                List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
                // Only update if the list as changed
                if (updatedInitiators.remove(initiatorId)) {
                    result.addAll(getVolumes(initiator.getHost(), export.getVolumes(), false));
                }
            }
        }

        return result;
    }

    /**
     * Method to remove an initiator from existing exports for a host.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the initiator to remove
     * @return task for removing an initiator
     */
    public TaskResourceRep removeInitiator(URI initiatorId) {
        Initiator initiator = queryObject(Initiator.class, initiatorId, true);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiator.getId(), taskId,
                ResourceOperationTypeEnum.DELETE_INITIATOR);

        if (ComputeSystemHelper.isInitiatorInUse(_dbClient, initiatorId.toString())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.removeInitiatorFromExport(initiator.getHost(), initiator.getId(), taskId);
        } else {
            _dbClient.ready(Initiator.class, initiator.getId(), taskId);
            _dbClient.markForDeletion(initiator);
        }

        auditOp(OperationTypeEnum.DELETE_HOST_INITIATOR, true, null,
                initiator.auditParameters());

        return toTask(initiator, taskId, op);
    }

    /**
     * Unassign host from a vCenter
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host to unassign
     * @return task for updating host
     */
    public TaskResourceRep hostVcenterUnassign(URI hostId) {
        return hostClusterChange(hostId, NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullURI(), true);
    }

    /**
     * Get details for the hostVcenterUnassign method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host id to unassign from vCenter
     * @return list of event details
     */
    @SuppressWarnings("unused")  // Invoked using reflection for the event framework
    public List<String> hostVcenterUnassignDetails(URI hostId) {
        List<String> result = Lists.newArrayList();
        Host host = queryObject(Host.class, hostId, true);
        if (host != null) {
            result.addAll(hostClusterChangeDetails(hostId, NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullURI(), true));
        }
        return result;
    }

    /**
     * Get details for the hostDatacenterChange method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving datacenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return list of event details
     */
    @SuppressWarnings("unused")  // Invoked using reflection for the event framework
    public List<String> hostDatacenterChangeDetails(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        List<String> result = Lists.newArrayList();
        Host host = queryObject(Host.class, hostId, true);
        VcenterDataCenter datacenter = queryObject(VcenterDataCenter.class, datacenterId, true);
        if (host != null && datacenter != null) {
            result.addAll(hostClusterChangeDetails(hostId, clusterId, datacenterId, isVcenter));
        }
        return result;
    }

    /**
     * Method to move a host to a new datacenter and update shared exports.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving datacenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return task for updating export groups
     */

    public TaskResourceRep hostDatacenterChange(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        return hostClusterChange(hostId, clusterId, datacenterId, isVcenter);
    }

    /**
     * Get details for the hostVcenterChange method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving vcenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return list of event details
     */
    @SuppressWarnings("unused")  // Invoked using reflection for the event framework
    public List<String> hostVcenterChangeDetails(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        List<String> result = Lists.newArrayList();
        Host host = queryObject(Host.class, hostId, true);
        VcenterDataCenter datacenter = queryObject(VcenterDataCenter.class, datacenterId, true);
        if (host != null && datacenter != null) {
            result.addAll(hostClusterChangeDetails(hostId, clusterId, datacenterId, isVcenter));
        }
        return result;
    }

    /**
     * Method to move a host to a new vcenter and update shared exports.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving vcenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return task for updating export groups
     */

    public TaskResourceRep hostVcenterChange(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        return hostClusterChange(hostId, clusterId, datacenterId, isVcenter);
    }

    private Set<String> getHostVolumes(URI hostId) {
        List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
        Set<String> volumeIds = Sets.newHashSet();
        for (ExportGroup export : ComputeSystemControllerImpl.getExportGroups(_dbClient, hostId, hostInitiators)) {
            volumeIds.addAll(export.getVolumes().keySet());
        }
        return volumeIds;
    }

    private List<String> getVolumes(URI hostId, StringMap volumes, boolean gainAccess) {
        List<String> result = Lists.newArrayList();
        Set<String> hostVolumes = Sets.newHashSet();

        for (Entry<String, String> volume : volumes.entrySet()) {
            // if host has access to volume in an exclusive export, skip it from the list of changes
            if (hostVolumes.contains(volume.getKey())) {
                continue;
            }
            URI project = null;
            String volumeName = null;
            URI blockURI = URI.create(volume.getKey());
            if (URIUtil.isType(blockURI, Volume.class)) {
                Volume block = _dbClient.queryObject(Volume.class, blockURI);
                project = block.getProject().getURI();
                volumeName = block.getLabel();
            } else if (URIUtil.isType(blockURI, BlockSnapshot.class)) {
                BlockSnapshot block = _dbClient.queryObject(BlockSnapshot.class, blockURI);
                project = block.getProject().getURI();
                volumeName = block.getLabel();
            } else if (URIUtil.isType(blockURI, BlockMirror.class)) {
                BlockMirror block = _dbClient.queryObject(BlockMirror.class, blockURI);
                project = block.getProject().getURI();
                volumeName = block.getLabel();
            }

            Project projectObj = _dbClient.queryObject(Project.class, project);
            String projectName = null;
            if (projectObj != null) {
                projectName = projectObj.getLabel();
            }

            result.add("Host will" + (gainAccess ? " gain " : " lose ") + "access to volume: Project "
                    + (projectName == null ? "N/A" : projectName) + " " + (volumeName == null ? "N/A" : volumeName)
                    + " ID: " + blockURI);
        }
        return result;
    }

    /**
     * Get details for the hostClusterChange method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving clusters
     * @param clusterId the cluster the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @param vCenterDataCenterId the datacenter to assign the host to
     * @return list of event details
     */
    @SuppressWarnings("unused")         // Invoked using reflection for the event framework
    public List<String> hostClusterChangeDetails(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter) {
        List<String> result = Lists.newArrayList();
        Host host = queryObject(Host.class, hostId, true);
        if (host == null) {
            return Lists.newArrayList("Host has been deleted");
        }
        URI oldClusterURI = host.getCluster();

        ComputeSystemController controller = getController(ComputeSystemController.class, null);

        if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)) {
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, oldClusterURI);
            for (ExportGroup export : exportGroups) {
                if (export != null) {
                    result.addAll(getVolumes(hostId, export.getVolumes(), false));
                }
            }
        } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Non-clustered host being added to a cluster
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, clusterId);
            for (ExportGroup eg : exportGroups) {
                result.addAll(getVolumes(hostId, eg.getVolumes(), true));
            }

        } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && !oldClusterURI.equals(clusterId)
                && (ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                        || ComputeSystemHelper.isClusterInExport(_dbClient, clusterId))) {
            // Clustered host being moved to another cluster
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, oldClusterURI);
            for (ExportGroup export : exportGroups) {
                if (export != null) {
                    result.addAll(getVolumes(hostId, export.getVolumes(), false));
                }
            }
            exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, clusterId);
            for (ExportGroup eg : exportGroups) {
                result.addAll(getVolumes(hostId, eg.getVolumes(), true));
            }
        }

        return result;
    }

    /**
     * Method to move a host to a new cluster and update shared exports.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving clusters
     * @param clusterId the cluster the host is moving to
     * @param vCenterDataCenterId the vcenter datacenter id to set
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return task for updating export groups
     */
    public TaskResourceRep hostClusterChange(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter) {
        Host hostObj = queryObject(Host.class, hostId, true);
        URI oldClusterURI = hostObj.getCluster();
        String taskId = UUID.randomUUID().toString();

        Operation op = _dbClient.createTaskOpStatus(Host.class, hostId, taskId,
                ResourceOperationTypeEnum.UPDATE_HOST);

        ComputeSystemController controller = getController(ComputeSystemController.class, null);

        if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)) {
            // Remove host from shared export
            controller.removeHostsFromExport(Arrays.asList(hostId), oldClusterURI, isVcenter, vCenterDataCenterId, taskId);
        } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Non-clustered host being added to a cluster
            controller.addHostsToExport(Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
        } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && !oldClusterURI.equals(clusterId)
                && (ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                        || ComputeSystemHelper.isClusterInExport(_dbClient, clusterId))) {
            // Clustered host being moved to another cluster
            controller.addHostsToExport(Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
        } else {
            ComputeSystemHelper.updateHostAndInitiatorClusterReferences(_dbClient, clusterId, hostId);
            ComputeSystemHelper.updateHostVcenterDatacenterReference(_dbClient, hostId, vCenterDataCenterId);
            _dbClient.ready(Host.class, hostId, taskId);
        }

        return toTask(hostObj, taskId, op);
    }

    /**
     * Returns a reference to a method for the given class with the given name
     * 
     * @param clazz class which the method belongs
     * @param name the name of the method
     * @return method or null if it doesn't exist
     */
    private Method getMethod(Class clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equalsIgnoreCase(name)) {
                return method;
            }
        }
        return null;
    }

    @POST
    @Path("/{id}/decline")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskList declineEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());

        if (!StringUtils.equalsIgnoreCase(event.getEventStatus(), ActionableEvent.Status.pending.name())) {
            throw APIException.badRequests.eventCannotBeDeclined(event.getEventStatus());
        }

        _log.info(
                "Declining Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: " + event.getDescription()
                        + " Warning: " + event.getWarning()
                        + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                        + event.getEventCode());

        return executeEventMethod(event, false);
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     * @param param POST data containing the id list.
     * @brief List data of event resources
     * @return list of representations.
     *
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public EventBulkRep getBulkResources(BulkIdParam param) {
        return (EventBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ActionableEvent> getResourceClass() {
        return ActionableEvent.class;
    }

    @Override
    public EventBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<ActionableEvent> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new EventBulkRep(BulkList.wrapping(_dbIterator, MapEvent.getInstance()));
    }

    @Override
    public EventBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<ActionableEvent> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.EventFilter(getUserFromContext(), _permissionsHelper);
        return new EventBulkRep(BulkList.wrapping(_dbIterator, MapEvent.getInstance(), filter));
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventList listEvents(@QueryParam("tenant") final URI tid) throws DatabaseException {
        URI tenantId;
        StorageOSUser user = getUserFromContext();
        if (tid == null || StringUtils.isBlank(tid.toString())) {
            tenantId = URI.create(user.getTenantId());
        } else {
            tenantId = tid;
        }
        // this call validates the tenant id
        TenantOrg tenant = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tenantId, isIdEmbeddedInURL(tenantId), true);

        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(tenantId, user);
        // get all host children
        EventList list = new EventList();
        list.setEvents(DbObjectMapper.map(ResourceTypeEnum.EVENT, listChildren(tenantId, ActionableEvent.class, "label", "tenant")));
        return list;
    }

    @GET
    @Path("/stats")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventStatsRestRep getStats(@QueryParam(TENANT_QUERY_PARAM) URI tenantId) {
        verifyAuthorizedInTenantOrg(tenantId, getUserFromContext());

        int approved = 0;
        int declined = 0;
        int pending = 0;
        Constraint constraint = AggregatedConstraint.Factory.getAggregationConstraint(ActionableEvent.class, "tenant",
                tenantId.toString(), "eventStatus");
        AggregationQueryResultList queryResults = new AggregationQueryResultList();

        _dbClient.queryByConstraint(constraint, queryResults);

        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        while (it.hasNext()) {
            AggregationQueryResultList.AggregatedEntry entry = it.next();
            if (entry.getValue().equals(ActionableEvent.Status.approved.name())) {
                approved++;
            } else if (entry.getValue().equals(ActionableEvent.Status.declined.name())) {
                declined++;
            } else {
                pending++;
            }
        }

        return new EventStatsRestRep(pending, approved, declined);
    }

    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults searchResults = new SearchResults();

        if (parameters.containsKey(RESOURCE_QUERY_PARAM)) {
            URI resourceId = URI.create(parameters.get(RESOURCE_QUERY_PARAM).get(0));

            List<ActionableEvent> events = EventUtils.findResourceEvents(_dbClient, resourceId);

            searchResults.getResource().addAll(toSearchResults(events));
        }

        return searchResults;
    }

    private List<SearchResultResourceRep> toSearchResults(List<ActionableEvent> items) {
        List<SearchResultResourceRep> results = Lists.newArrayList();

        for (ActionableEvent item : items) {
            results.add(toSearchResult(item.getId()));
        }

        return results;
    }

    private SearchResultResourceRep toSearchResult(URI uri) {
        RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), uri));
        return new SearchResultResourceRep(uri, selfLink, null);
    }

    protected ActionableEvent queryEvent(DbClient dbClient, URI id) throws DatabaseException {
        return queryObject(ActionableEvent.class, id, false);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return queryObject(ActionableEvent.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        return event.getTenant();
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.EVENT;
    }
}
