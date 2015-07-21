/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.model.varray.VirtualArrayInternalFlags;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

import static com.emc.storageos.api.mapper.VirtualArrayMapper.map;

/**
 * Internal API for operating flags of virtual array
 */

@Path("/internal/vdc/varrays")
public class InternalVirtualArrayService extends ResourceService {
    private static final Logger _log = LoggerFactory.getLogger(InternalVirtualArrayService.class);

    private static final String EVENT_SERVICE_TYPE = "internalVirtualArray";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get virtual array object from id
     * @param id the URN of a ViPR virtual array object
     * @return
     */
    private VirtualArray getVirtualArrayById(URI id, boolean checkInactive) {
        if (id == null)
            return null;

        VirtualArray n = _permissionsHelper.getObjectById(id, VirtualArray.class);
        ArgValidator.checkEntity(n, id, isIdEmbeddedInURL(id), checkInactive);

        return n;
    }

    /**
     * Set protection type for varray
     * @param id the URN of a ViPR varray
     * @param value the value of the protection type
     * @return the updated virtual array info
     */
    @PUT
    @Path("/{id}/protectionType")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public VirtualArrayRestRep setProtectionType(@PathParam("id") URI id, @QueryParam("value") String protectionType) {
        if (protectionType == null || protectionType.isEmpty()) {
            throw APIException.badRequests.invalidParameterProtectionTypeIsEmpty();
        }

        VirtualArray varray = getVirtualArrayById(id, true);

        varray.setProtectionType(protectionType);
        _dbClient.persistObject(varray);

        auditOp(OperationTypeEnum.SET_VARRAY_PROTECTIONTYPE, true, null, id.toString(), varray.getLabel(), protectionType);
        return map(varray);
    }

    /**
     * Get protectionType attached with a virtual array
     * @param id the URN of a ViPR varray
     * @return the VirtualArrayInternalFlags
     */
    @GET
    @Path("/{id}/protectionType")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public VirtualArrayInternalFlags getProtectionType(@PathParam("id") URI id) {

        String protectionType = "";
        VirtualArray varray = getVirtualArrayById(id, true);

        if (varray.getProtectionType() != null) {
            protectionType = varray.getProtectionType();
        }

        VirtualArrayInternalFlags varrayInternalFlags = new VirtualArrayInternalFlags();
        varrayInternalFlags.setProtectionType(protectionType);

        auditOp(OperationTypeEnum.GET_VARRAY_PROTECTIONTYPE, true, null, id.toString(), varray.getLabel(), protectionType);
        return varrayInternalFlags;
    }

    /**
     * Unset protection type assigned to the varray
     *
     * @param id the URN of a ViPR varry
     * @prereq none
     * @brief unset protection type field
     * @return No data returned in response body     
     */
    @DELETE
    @Path("/{id}/protectionType")
    public Response unsetProtectionType(@PathParam("id") URI id) {
        VirtualArray varray = getVirtualArrayById(id, true);

        String origProtectionType = (varray.getProtectionType() == null) ? "": varray.getProtectionType();

        varray.setProtectionType("");
        _dbClient.persistObject(varray);

        auditOp(OperationTypeEnum.UNSET_VARRAY_PROTECTIONTYPE, true, null, id.toString(), varray.getLabel(), origProtectionType);
        return Response.ok().build();
    }

    /**
     * Set device registered flag for varray
     * @param id the URN of a ViPR varray
     * @param value the device registered status
     * @return the updated virtual array info
     */
    @PUT
    @Path("/{id}/deviceRegistered")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public VirtualArrayRestRep setDeviceRegistered(@PathParam("id") URI id, @QueryParam("value") boolean deviceRegistered) {

        VirtualArray varray = getVirtualArrayById(id, true);

        varray.setDeviceRegistered(deviceRegistered);
        _dbClient.persistObject(varray);

        auditOp(OperationTypeEnum.SET_VARRAY_REGISTERED, true, null,
                id.toString(), varray.getLabel(), String.valueOf(deviceRegistered));
        return map(varray);
    }

    /**
     * Get device registered status of a virtual array
     * @param id the URN of a ViPR varray
     * @return the VirtualArrayInternalFlags
     */
    @GET
    @Path("/{id}/deviceRegistered")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public VirtualArrayInternalFlags getDeviceRegistered(@PathParam("id") URI id) {
        VirtualArray varray = getVirtualArrayById(id, true);

        VirtualArrayInternalFlags varrayInternalFlags = new VirtualArrayInternalFlags();
        varrayInternalFlags.setDeviceRegistered(varray.getDeviceRegistered());

        auditOp(OperationTypeEnum.GET_VARRAY_REGISTERED, true, null,
                id.toString(), varray.getLabel(), String.valueOf(varray.getDeviceRegistered()));
        return varrayInternalFlags;
    }
}

