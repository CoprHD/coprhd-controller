/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapVirtualNas;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.vnas.VirtualNASBulkRep;
import com.emc.storageos.model.vnas.VirtualNASList;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.storageos.model.vnas.VirtualNasCreateParam;
import com.emc.storageos.model.vnas.VirtualNasUpdateParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * VirtualNasService resource implementation
 */
@Path("/vdc/vnas-servers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class VirtualNasService extends TaggedResource {

    private static final Logger _log = LoggerFactory.getLogger(VirtualNasService.class);

    protected static final String EVENT_SERVICE_SOURCE = "VirtualNasService";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    private static final String EVENT_SERVICE_TYPE = "virtualNAS";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    // how many times to retry a procedure before returning failure to the user.
    // Is used with "system delete" operation.
    private int _retry_attempts;

    public void setRetryAttempts(int retries) {
        _retry_attempts = retries;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Gets the virtual NAS with the passed id from the database.
     * 
     * @param id the URN of a ViPR virtual NAS.
     * @return A reference to the registered VirtualNAS.
     * @throws BadRequestException When the vNAS is not registered.
     */
    protected VirtualNAS queryRegisteredResource(URI id) {
        ArgValidator.checkUri(id);
        VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, id);
        ArgValidator.checkEntity(vNas, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                vNas.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(
                    VirtualNAS.class.getSimpleName(), id);
        }

        return vNas;
    }

    @Override
    protected VirtualNAS queryResource(URI id) {
        ArgValidator.checkUri(id);
        VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, id);
        ArgValidator.checkEntity(vNas, id, isIdEmbeddedInURL(id));
        return vNas;
    }

    /**
     * Gets the ids and self links for all virtual NAS.
     * 
     * @brief List virtual NAS servers
     * @return A VirtualNASList reference specifying the ids and self links for
     *         the virtual NAS servers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VirtualNASList getVirtualNasServers() {

        VirtualNASList vNasList = new VirtualNASList();
        List<URI> ids = _dbClient.queryByType(VirtualNAS.class, true);
        for (URI id : ids) {
            VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, id);
            if ((vNas != null)) {
                vNasList.getVNASServers().add(
                        toNamedRelatedResource(vNas, vNas.getNasName()));
            }
        }

        return vNasList;
    }

    /**
     * Gets the details of virtual NAS.
     * 
     * @param id the URN of a ViPR virtual NAS.
     * 
     * @brief Show virtual NAS
     * @return A VirtualNASRestRep reference specifying the data for the
     *         virtual NAS with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VirtualNASRestRep getVirtualNasServer(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VirtualNAS.class, "id");
        VirtualNAS vNas = queryResource(id);
        return MapVirtualNas.getInstance(_dbClient).toVirtualNasRestRep(vNas);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VIRTUAL_NAS;
    }

    /**
     * Create Virtual NAS server
     * 
     * @param vnasParam
     *            Virtual NAS create parameters
     * @brief Create Virtual NAS server
     * @return Task resource representation
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public TaskResourceRep createVirtualNasServer(VirtualNasCreateParam vnasParam) {

        // Make VNAS name as mandatory field
        ArgValidator.checkFieldNotNull(vnasParam.getvNasName(), "nasName");

        // Check for duplicate VNAS name
        if (vnasParam.getvNasName() != null && !vnasParam.getvNasName().isEmpty()) {
            checkForDuplicateName(vnasParam.getvNasName(), VirtualNAS.class);
        }

        ArgValidator.checkFieldUriType(vnasParam.getStorageSystem(), StorageSystem.class, "_id");

        _log.info("Creating Virtual NAS server...");
        String task = UUID.randomUUID().toString();

        VirtualNAS vnas = new VirtualNAS();
        vnas.setId(URIUtil.createId(VirtualNAS.class));
        vnas.setLabel(vnasParam.getvNasName());
        vnas.setNasName(vnasParam.getvNasName());
        StringSet protocols = new StringSet(vnasParam.getAddProtocols());
        vnas.setProtocols(protocols);
        vnas.setStorageDeviceURI(vnasParam.getStorageSystem());

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, vnasParam.getStorageSystem());
        FileController controller = getController(FileController.class, device.getSystemType());

        vnas.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_VIRTUAL_NAS_SERVER);
        op.setDescription("Create Virtual Nas Server");
        vnas.getOpStatus().createTaskStatus(task, op);
        _dbClient.createObject(vnas);

        try {
            controller.createVirtualNas(device.getId(), vnas.getId(), vnasParam, task);
            auditOp(OperationTypeEnum.CREATE_VIRTUAL_NAS, true, AuditLogManager.AUDITOP_BEGIN,
                    vnas.getLabel(), vnas.getId().toString(), device.getId().toString());
        } catch (InternalException e) {
            vnas.setInactive(true);
            throw e;
        }

        return toTask(vnas, task, op);
    }

    @PUT
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public TaskResourceRep updateVirtualNasServer(@PathParam("id") URI vnasId, VirtualNasUpdateParam vnasParam) {

        ArgValidator.checkFieldUriType(vnasId, VirtualNAS.class, "id");
        VirtualNAS vNas = queryResource(vnasId);
        ArgValidator.checkReference(VirtualNAS.class, vnasId, checkForDelete(vNas));

        _log.info("Updating Virtual NAS server...");
        String task = UUID.randomUUID().toString();

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, vNas.getStorageDeviceURI());
        FileController controller = getController(FileController.class, device.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(VirtualNAS.class, vNas.getId(), task,
                ResourceOperationTypeEnum.UPDATE_VIRTUAL_NAS_SERVER);
        try {
            controller.deleteVirtualNas(device.getId(), vNas.getId(), task);
            auditOp(OperationTypeEnum.UPDATE_VIRTUAL_NAS, true, AuditLogManager.AUDITOP_BEGIN,
                    vNas.getLabel(), vNas.getId().toString(), device.getId().toString());
        } catch (InternalException e) {
            vNas.setInactive(false);
            throw e;
        }
        return toTask(vNas, task, op);

    }

    /**
     * Delete Virtual NAS server
     * 
     * @param vnasId
     *            the URN of a Virtual NAS server
     * @brief Delete Virtual NAS server
     * @return Task resource representation
     */
    @DELETE
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public TaskResourceRep deleteVirtualNasServer(@PathParam("id") URI vnasId) {

        ArgValidator.checkFieldUriType(vnasId, VirtualNAS.class, "id");
        VirtualNAS vNas = queryResource(vnasId);
        ArgValidator.checkReference(VirtualNAS.class, vnasId, checkForDelete(vNas));

        _log.info("Deleting Virtual NAS server...");
        String task = UUID.randomUUID().toString();

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, vNas.getStorageDeviceURI());
        FileController controller = getController(FileController.class, device.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(VirtualNAS.class, vNas.getId(), task,
                ResourceOperationTypeEnum.DELETE_VIRTUAL_NAS_SERVER);
        try {
            controller.deleteVirtualNas(device.getId(), vNas.getId(), task);
            auditOp(OperationTypeEnum.DELETE_VIRTUAL_NAS, true, AuditLogManager.AUDITOP_BEGIN,
                    vNas.getLabel(), vNas.getId().toString(), device.getId().toString());
        } catch (InternalException e) {
            vNas.setInactive(false);
            throw e;
        }
        return toTask(vNas, task, op);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of virtual NAS resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public VirtualNASBulkRep getBulkResources(BulkIdParam param) {
        return (VirtualNASBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<VirtualNAS> getResourceClass() {
        return VirtualNAS.class;
    }

    @Override
    public VirtualNASBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<VirtualNAS> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new VirtualNASBulkRep(BulkList.wrapping(_dbIterator,
                MapVirtualNas.getInstance(_dbClient)));
    }

    @Override
    public VirtualNASBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

}
