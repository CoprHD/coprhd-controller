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

import com.emc.storageos.isilon.restapi.IsilonStats;
import com.google.gson.Gson;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Path("/platform/1/statistics")
public class Statistics extends BaseResource {
    public static class CustomStatValueCurrent<T> extends IsilonStats.StatValueCurrent<T> {
        public CustomStatValueCurrent(ArrayList<String> error, long time, T value) {
            this.error = error;
            this.time = time;
            this.value = value;
        }
    }

    public static class CustomStatValueHistory<T> extends IsilonStats.StatValueHistory<T> {
        public CustomStatValueHistory(ArrayList<String> error, HashMap<Long, T> values) {
            this.error = error;
            this.values = values;
        }
    }

    public static class CustomProtocols extends IsilonStats.Protocol {
        public CustomProtocols(String name, IsilonStats.StatProtocolType type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class CustomPolicy extends IsilonStats.Policy {
        public CustomPolicy(int interval, boolean peristent, long retention) {
            this.interval = interval;
            this.peristent = peristent;
            this.retention = retention;
        }
    }

    public static class CustomPolicies extends IsilonStats.Policies {
        public CustomPolicies(int cache_time, ArrayList<IsilonStats.Policy> policies) {
            this.cache_time = cache_time;
            this.policies = policies;
        }
    }

    @GET
    @Path("/current")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getStatsCurrent(@QueryParam("key") String key, @QueryParam("devid") String devid) {
        /*ArrayList<String> errors = new ArrayList<String>();
        errors.add("0");
        CustomStatValueCurrent<Integer> statValueCurrent = new CustomStatValueCurrent<Integer>(errors, 
                System.currentTimeMillis()/1000, 2);

        Map<String, IsilonStats.StatValueCurrent<Integer>> devMap = new HashMap<String, IsilonStats.StatValueCurrent<Integer>>();
        devMap.put(devid, statValueCurrent);
        Map<String, Object> keyMap = new HashMap<String, Object>();
        keyMap.put(key, devMap);

        return Response.status(200).entity(new Gson().toJson(keyMap)).build();  */
        return Response.status(500).entity("Not implemented").build();
    }

    @GET
    @Path("/history")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getStatsHistory(@QueryParam("key") String key, @QueryParam("devid") String devid) {
        /*ArrayList<String> errors = new ArrayList<String>();
        ArrayList<ArrayList<Object>> values = new ArrayList<ArrayList<Object>>();
        ArrayList<Object> value = new ArrayList<Object>();

        errors.add("0");
        value.add("1335739393");
        value.add(918);
        values.add(value);
        value = new ArrayList<Object>();
        value.add("1335739513");
        value.add(948);
        values.add(value);
        CustomStatValueHistory<Object> statValueHistory = new CustomStatValueHistory<Object>(errors, values);

        Map<String, IsilonStats.StatValueHistory<Object>> devMap = new HashMap<String, IsilonStats.StatValueHistory<Object>>();
        devMap.put(devid, statValueHistory);
        Map<String, Object> keyMap = new HashMap<String, Object>();
        keyMap.put(key, devMap);

        return Response.status(200).entity(new Gson().toJson(keyMap)).build(); */
        return Response.status(500).entity("Not implemented").build();
    }

    @GET
    @Path("/protocols")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getStatsProtocols() {
        Map<String, ArrayList<IsilonStats.Protocol>> protocols = new HashMap<String, ArrayList<IsilonStats.Protocol>>();
        ArrayList<IsilonStats.Protocol> list = new ArrayList<IsilonStats.Protocol>();

        list.add(new CustomProtocols("nfs3", IsilonStats.StatProtocolType.internal));
        list.add(new CustomProtocols("ifs", IsilonStats.StatProtocolType.external));

        protocols.put("protocols",list);

        return Response.status(200).entity(new Gson().toJson(protocols)).build();
    }
}
