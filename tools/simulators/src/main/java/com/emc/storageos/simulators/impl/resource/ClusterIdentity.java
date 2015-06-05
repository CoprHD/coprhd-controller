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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/platform/1/cluster/identity")
public class ClusterIdentity extends BaseResource {

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getIdentity() {
        StringBuilder str = new StringBuilder();
        str.append("{\"description\":\"\",");
        str.append("\"logon\":{\"motd\":\"\",\"motd_header\":\"\"},");
        str.append("\"name\":\"sim-isilon\",");
        str.append("\"guid\":\"sim-isilon\",");
        str.append("\"onefs-version-info\":");
        str.append(String.format("{\"build\":\"%1$s\",\"release\":\"vHEAD\",\"revision\":%2$s," +
                "\"type\":\"Simulated isilon papi interface\",\"version\":\"%3$s\"}", "0.1", "0.1", "0.1"));
        str.append(",\"success\":true}");
        return Response.status(200).entity(str.toString()).build();
    }

}
