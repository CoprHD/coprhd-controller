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

import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@Path("/platform/1/quota/quotas")
public class Quota extends BaseResource {
    private static Logger _log = LoggerFactory.getLogger(Quota.class);
    public static final int NUM_PAGE = 100;

    @XmlRootElement
    public static class QuotaResponse {
        @XmlElement
        ArrayList<IsilonSmartQuota> quotas = new ArrayList<IsilonSmartQuota>();
        @XmlElement
        String resume;
        @XmlElement
        int total;
        @XmlElement
        boolean success;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response listQuotas(@QueryParam("resume") String resume) {
        try {
            QuotaResponse quotaResp  = new QuotaResponse();

            // fetch an extra quota to use it's id as the resume token
            List<IsilonSmartQuota> list = _objectStore.listQuotas(resume, NUM_PAGE + 1);
            int size = list.size();
            if (list.size() == NUM_PAGE + 1) {
                // if existing the next page, set the pre-fetched quota's id as resume token
                quotaResp.resume = list.get(NUM_PAGE).getId();
                quotaResp.quotas = new ArrayList<IsilonSmartQuota>(list.subList(0, NUM_PAGE));
                quotaResp.total = NUM_PAGE + 1;
            } else {
                quotaResp.resume = null;
                quotaResp.quotas = new ArrayList<IsilonSmartQuota>(list);
                quotaResp.total = size;
            }
            quotaResp.success = true;

            return Response.status(200).entity(new Gson().toJson(quotaResp)).build();
        } catch (Exception e) {
            _log.error("listQuotas exception. resume : " + resume, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getQuota(@PathParam("id") String id) {
        try {
            IsilonSmartQuota isilonSmartQuota = _objectStore.getQuota(id);
            ArrayList<IsilonSmartQuota> arr = new ArrayList<IsilonSmartQuota>();
            arr.add(isilonSmartQuota);

            Map<String, Object> map = new HashMap<String, Object>();
            map.put("quotas", arr);
            map.put("success", true);

            _log.info("Quota get: " + id);

            return Response.status(200).entity(new Gson().toJson(map)).build();
        } catch (Exception e) {
            _log.error("getQuota exception. id : " + id, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public Response createQuota(String obj) {
        try{
            IsilonSmartQuota quota = new Gson().fromJson(obj, IsilonSmartQuota.class);
            String id = _objectStore.createQuota(quota.getPath(), quota.getThresholds().getHard());

            _log.info("Quota create: " + id + " obj: " + obj);

            return Response.ok(StandardResponse.getSuccessIdResponse(id)).build();
        } catch(Exception e) {
            _log.error("createQuota exception. obj : " + obj, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteQuota(@PathParam("id") String id) {
        try {
            _objectStore.deleteQuota(id);

            _log.info("Quota delete: " + id);

            return Response.ok().build();
        } catch(Exception e) {
            _log.error("deleteQuota exception. id : " + id, e);
            return Response.serverError().entity(StandardResponse.getErrorResponse(e.getMessage())).build();
        }
    }
}
