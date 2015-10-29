/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapVirtualNas;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.vnas.VirtualNASBulkRep;
import com.emc.storageos.model.vnas.VirtualNASList;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * VirtualNasService resource implementation
 */
@Path("/vdc/vnas-servers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class VirtualNasService extends TaggedResource {

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
