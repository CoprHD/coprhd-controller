/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.impl.resource;

import com.emc.storageos.isilon.restapi.IsilonExport;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Path("/platform/1/protocols/nfs/exports")
public class Export extends BaseResource {
    private static Logger _log = LoggerFactory.getLogger(Export.class);

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response listExports() {
        return Response.serverError().entity(StandardResponse.getErrorResponse("Not implemented")).build();
    }

    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getExport(@PathParam("id") String id) {
        try {
            IsilonExport isilonExport = _objectStore.getExport(id);
            ArrayList<IsilonExport> arr = new ArrayList<IsilonExport>();
            arr.add(isilonExport);

            Map<String, Object> map = new HashMap<String, Object>();
            map.put("exports", arr);
            map.put("success", true);

            _log.info("Export get: " + id);

            return Response.status(200).entity(new Gson().toJson(map)).build();
        } catch (Exception e) {
            _log.error("getExport exception. id : " + id, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createExport(String obj) {
        try {
            IsilonExport exp = new Gson().fromJson(obj, IsilonExport.class);

            // if map_all and map_root both set, returns error
            if (exp.getMap_all() != null && exp.getMap_root() != null)
                return Response.serverError().entity(StandardResponse.getErrorResponse("invalid parameters")).build();

            String id = _objectStore.createExport(exp);

            _log.info("Export post: " + id + " obj: " + obj);

            return Response.ok(StandardResponse.getSuccessIdResponse(id)).build();
        } catch (Exception e) {
            _log.error("createExport exception. obj : " + obj, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteExport(@PathParam("id") String id) {
        try {
            _objectStore.deleteExport(id);

            _log.info("Export delete: " + id);

            return Response.ok().build();
        } catch (Exception e) {
            _log.error("deleteExport exception. id : " + id, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response modifyExport(@PathParam("id") String id, String obj) {
        try {
            IsilonExport exp = new Gson().fromJson(obj, IsilonExport.class);
            _objectStore.modifyExport(id, exp);

            _log.info("Export modify: " + id + " obj: " + obj);

            return Response.status(204).build();
        } catch (Exception e) {
            _log.error("modifyExport exception. id : " + id + ", obj : " + obj, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

}
