/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
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

import com.emc.storageos.Controller;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.event.EventCreateParam;
import com.emc.storageos.model.event.EventList;
import com.emc.storageos.model.host.EventRestRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN }, writeRoles = {
        Role.TENANT_ADMIN }, readAcls = { ACL.ANY })
@Path("/vdc/events")
public class EventService extends TaskResourceService {

    protected final static Logger _log = LoggerFactory.getLogger(EventService.class);

    private static final String EVENT_SERVICE_TYPE = "event";

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
        return map(event);
    }

    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response deleteEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        _dbClient.markForDeletion(event);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/approve")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response approveEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());

        // TODO testing calling controller workflow
        URI hostId = URI.create(event.getMessage());
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Host.class, hostId, taskId,
                ResourceOperationTypeEnum.DELETE_HOST);

        try {
            Class controllerClass = Class.forName(event.getControllerClass());

            Controller controller = getController(controllerClass, null);

            Method m = controllerClass.getDeclaredMethod(event.getOrchestrationMethod(), URI.class, boolean.class, boolean.class,
                    String.class);
            m.invoke(controller, hostId, true, false, taskId);

        } catch (ClassNotFoundException e) {
            _log.error(e.getMessage());
            throw APIException.badRequests.parameterIsNotValid("controllerClass");
        } catch (NoSuchMethodException e) {
            _log.error(e.getMessage());
        } catch (SecurityException e) {
            _log.error(e.getMessage());
        } catch (IllegalAccessException e) {
            _log.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            _log.error(e.getMessage());
        } catch (InvocationTargetException e) {
            _log.error(e.getMessage());
        }
        //

        return Response.ok().build();
    }

    @POST
    @Path("/{id}/decline")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response declineEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        // this.getController(clazz, hw);
        // TODO decline the event
        _dbClient.markForDeletion(event);
        return Response.ok().build();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public EventRestRep createEvent(EventCreateParam createParam) {
        TenantOrg tenant = getTenantById(createParam.getTenant(), true);
        ActionableEvent event = new ActionableEvent();
        event.setId(URIUtil.createId(ActionableEvent.class));
        event.setTenant(tenant.getId());
        event.setMessage(createParam.getMessage());
        event.setControllerClass(createParam.getControllerClass());
        event.setOrchestrationMethod(createParam.getOrchestrationMethod());
        event.setLabel("Label[" + createParam.getMessage() + "]");
        _dbClient.createObject(event);
        return map(event);
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

    public static EventRestRep map(ActionableEvent from) {
        if (from == null) {
            return null;
        }
        EventRestRep to = new EventRestRep();
        to.setMessage(from.getMessage());
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant()));
        DbObjectMapper.mapDataObjectFields(from, to);
        return to;
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

    /**
     * Get tenant object from id
     * 
     * @param id the URN of a ViPR tenant
     * @return
     */
    private TenantOrg getTenantById(URI id, boolean checkInactive) {
        if (id == null) {
            return null;
        }
        TenantOrg org = _permissionsHelper.getObjectById(id, TenantOrg.class);
        ArgValidator.checkEntity(org, id, isIdEmbeddedInURL(id), checkInactive);
        return org;
    }

}
