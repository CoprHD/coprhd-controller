/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import play.Logger;
import play.jobs.Job;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import util.api.ApiMapperUtils;

import com.emc.vipr.model.catalog.ApprovalInfo;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NotifyApprovalServiceJob extends Job {
    private static final String TIMEOUT = "3min";
    private String approvalUrl;
    private ApprovalRestRep approval;

    public NotifyApprovalServiceJob(String approvalUrl, OrderRestRep order, ApprovalRestRep approval) {
        this.approvalUrl = approvalUrl;
        this.approval = approval;
    }

    @Override
    public void doJob() throws Exception {
        WS.WSRequest wsReq = createRequest();
        Logger.debug("POSTING Approval: %s => %s", approvalUrl, wsReq.body);
        HttpResponse response = wsReq.post();
        if (isError(response)) {
            Logger.error("Approval POST failed: %s, %s %s", approvalUrl, response.getStatus(), response.getString());
        }
        else {
            Logger.debug("Approval POST succeeded: %s, %s %s", approvalUrl, response.getStatus(), response.getString());
        }
    }

    protected WS.WSRequest createRequest() {
        WS.WSRequest wsReq = WS.url(approvalUrl);
        wsReq.timeout(TIMEOUT);
        wsReq.setHeader("Accept", "application/json");
        wsReq.setHeader("Content-Type", "application/json");
        wsReq.body(createBody());
        return wsReq;
    }

    protected String createBody() {
        Gson gson = new GsonBuilder().serializeNulls().create();
        ApprovalInfo data = ApiMapperUtils.newApprovalInfo(approval);
        return gson.toJson(data);
    }

    protected boolean isError(HttpResponse response) {
        Integer statusCode = response.getStatus();
        if ((statusCode == null) || (statusCode >= 400)) {
            return true;
        }
        else {
            return false;
        }
    }
}
