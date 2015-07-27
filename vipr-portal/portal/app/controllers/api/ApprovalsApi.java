/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.api;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static render.RenderApiModel.renderApi;
import static util.BourneUtil.getCatalogClient;
import static util.api.ApiMapperUtils.newApprovalInfo;
import static util.api.ApiMapperUtils.newApprovalReference;

import java.net.URI;
import java.util.List;

import play.mvc.Controller;
import play.mvc.With;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.ApprovalUpdateParam;
import com.emc.vipr.model.catalog.Reference;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

/**
 * Approvals API
 *
 * @author Chris Dail
 */
@With(Common.class)
@Restrictions({ @Restrict("TENANT_APPROVER") })
public class ApprovalsApi extends Controller {
    public static void pending() {
        List<Reference> approvals = Lists.newArrayList();
        List<ApprovalRestRep> pendingApprovals = getCatalogClient().approvals().search().byStatus(ApprovalRestRep.PENDING).run();
        for (ApprovalRestRep request: pendingApprovals) {
            approvals.add(newApprovalReference(request.getId().toString()));
        }
        renderApi(approvals);
    }

    public static void approvals() {
        List<Reference> approvals = Lists.newArrayList();
        List<NamedRelatedResourceRep> allApprovals = getCatalogClient().approvals().listByUserTenant();
        for (NamedRelatedResourceRep element: allApprovals) {
            approvals.add(newApprovalReference(element.getId().toString()));
        }
        renderApi(approvals);
    }

    public static void approval(String approvalId) {
        ApprovalRestRep approval = getCatalogClient().approvals().get(uri(approvalId));
        renderApi(newApprovalInfo(approval));
    }

    public static void approve(String approvalId, String message) {
        action(uri(approvalId), ApprovalRestRep.APPROVED, message);
    }

    public static void reject(String approvalId, String message) {
        action(uri(approvalId), ApprovalRestRep.REJECTED, message);
    }

    private static void action(URI approvalId, String status, String message) {
        ApprovalUpdateParam updateParam = new ApprovalUpdateParam();
        updateParam.setApprovalStatus(status);
        updateParam.setMessage(message);
        getCatalogClient().approvals().update(approvalId, updateParam);

        ApprovalRestRep approval = getCatalogClient().approvals().get(approvalId);
        renderApi(newApprovalInfo(approval));
    }
}
