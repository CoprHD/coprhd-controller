/**
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

import com.emc.storageos.isilon.restapi.IsilonSnapshot;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/platform/1/snapshot/snapshots")
public class Snapshots extends BaseResource {
    private static Logger _log = LoggerFactory.getLogger(Snapshots.class);

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response listSnapshots() {
        return Response.serverError().entity(StandardResponse.getErrorResponse("Not implemented")).build();
    }


    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getSnapshot(@PathParam("id") String id) {
        return Response.serverError().entity(StandardResponse.getErrorResponse("Not implemented")).build();
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public Response createSnapshot(String obj) {
        try{
            IsilonSnapshot snap = new Gson().fromJson(obj, IsilonSnapshot.class);
            String id = _objectStore.createSnapshot(snap.getName(), snap.getPath());

            _log.info("Snapshot create: " + id + " obj: " + obj);

            return Response.ok(StandardResponse.getSuccessIdResponse(id)).build();
        } catch(Exception e) {
            _log.error("createSnapshot exception. obj : " + obj, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteSnapshot(@PathParam("id") String id) {
        try {
            _objectStore.deleteSnapshot(id);

            _log.info("Snapshot delete: " + id);

            return Response.ok(StandardResponse.getSuccessResponse()).build();
        } catch(Exception e) {
            _log.error("deleteSnapshot exception. id : " + id, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }
}
