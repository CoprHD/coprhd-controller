/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.net.URI;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.emc.vipr.model.sys.SysSvcTask;
import com.emc.vipr.model.sys.SysSvcTaskList;

import com.emc.storageos.model.event.EventParameters;

public class OpenSourceCallHomeServiceImpl extends BaseLogSvcResource implements CallHomeService {

    @Override
    public SysSvcTask sendInternalAlert(String source, int eventId, List<String> nodeIds,  List<String> nodeNames, List<String> logNames,
                              int severity, String start, String end, String msgRegex, int maxCount,
                              EventParameters eventParameters) throws Exception {
        throw new WebApplicationException(501);
    }

    @Override
    public SysSvcTask sendAlert(String source, int eventId, List<String> nodeIds, List<String> nodeNames, List<String> logNames, int severity,
                              String start, String end, String msgRegex, int maxCount, boolean forceAttachLogs,
                              int force, EventParameters eventParameters) throws Exception {
        throw new WebApplicationException(501);
    }

    @Override
    public SysSvcTaskList getTasks(URI id) {
        throw new WebApplicationException(501);
    }

    @Override
    public SysSvcTask getTask(URI id, String opId) {
        throw new WebApplicationException(501);
    }

    @Override
    public Response sendRegistrationEvent() {
        return Response.status(501).build();
    }

    @Override
    public Response sendHeartbeatEvent() {
        return Response.status(501).build();
    }

    @Override
    public Response getNodeDataForEsrs() {
        return Response.status(501).build();
    }
}
