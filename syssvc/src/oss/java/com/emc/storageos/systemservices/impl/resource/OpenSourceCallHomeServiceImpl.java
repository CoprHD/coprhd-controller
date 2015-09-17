/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.emc.storageos.model.event.EventParameters;
import com.emc.storageos.model.TaskResourceRep;

public class OpenSourceCallHomeServiceImpl extends BaseLogSvcResource implements CallHomeService {

    @Override
    public TaskResourceRep sendInternalAlert(String source, int eventId, List<String> nodeIds,  List<String> nodeNames, List<String> logNames,
                              int severity, String start, String end, String msgRegex, int maxCount,
                              EventParameters eventParameters) throws Exception {
        throw new WebApplicationException(501);
    }

    @Override
    public TaskResourceRep sendAlert(String source, int eventId, List<String> nodeIds, List<String> nodeNames, List<String> logNames, int severity,
                              String start, String end, String msgRegex, int maxCount, boolean forceAttachLogs,
                              int force, EventParameters eventParameters) throws Exception {
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
