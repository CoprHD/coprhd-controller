/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;
import static util.CatalogServiceUtils.getCatalogService;
import static util.OrderUtils.getOrder;

import java.net.URI;
import java.util.List;
import java.util.Map;

import models.datatable.ApprovalsDataTable;
import models.datatable.ApprovalsDataTable.ApprovalRequestInfo;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.ApprovalUpdateParam;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.Models;

/**
 * @author Chris Dail
 */
@With(Common.class)
@Restrictions({ @Restrict("TENANT_APPROVER") })
public class Approvals extends Controller {

    public static void index() {
        ApprovalsDataTable dataTable = new ApprovalsDataTable();
        render(dataTable);
    }

    public static void approvalsJson() {
        ViPRCatalogClient2 catalog = getCatalogClient();

        Map<URI, OrderRestRep> orders = Maps.newHashMap();
        Map<URI, CatalogServiceRestRep> catalogServices = Maps.newHashMap();
        List<ApprovalRequestInfo> approvalRequestInfos = Lists.newArrayList();

        List<ApprovalRestRep> approvals = catalog.approvals().getByUserTenant();
        for (ApprovalRestRep approval : approvals) {
            OrderRestRep order = null;
            if (approval.getOrder() != null) {
                if (orders.keySet().contains(approval.getOrder().getId()) == false) {
                    order = getOrder(approval.getOrder());
                    if (order != null) {
                        orders.put(order.getId(), order);
                    }
                }
                else {
                    order = orders.get(approval.getOrder().getId());
                }
            }
            CatalogServiceRestRep catalogService = null;
            if (order != null && order.getCatalogService() != null) {
                if (catalogServices.keySet().contains(order.getCatalogService().getId()) == false) {
                    catalogService = getCatalogService(order.getCatalogService());
                    if (catalogService != null) {
                        catalogServices.put(catalogService.getId(), catalogService);
                    }
                }
                else {
                    catalogService = catalogServices.get(order.getCatalogService().getId());
                }
            }

            approvalRequestInfos.add(new ApprovalRequestInfo(approval, order, catalogService));
        }

        renderJSON(DataTablesSupport.createJSON(approvalRequestInfos, params));
    }

    public static void edit(String id) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        ApprovalRestRep approval = catalog.approvals().get(uri(id));
        OrderRestRep order = null;
        CatalogServiceRestRep service = null;
        if (approval != null) {
            order = getOrder(approval.getOrder());
            if (order != null) {
                service = getCatalogService(order.getCatalogService());
            }
        }
        render(approval, order, service);
    }

    public static void submit(String id, ApprovalsForm approval) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        ApprovalRestRep approvalRep = catalog.approvals().get(uri(id));
        OrderRestRep order = getOrder(approvalRep.getOrder());

        if (approvalRep.isPending()) {
            approval.validate("approval");
            if (Validation.hasErrors()) {
                Common.handleError();
            }
            if (isApprove(approval.getSubmit())) {
                approve(approvalRep, approval.getMessage());
                flash.put("success", MessagesUtils.get("approval.approve.message", order.getSubmittedBy()));
            }
            else if (isReject(approval.getSubmit())) {
                reject(approvalRep, approval.getMessage());
                flash.put("warn", MessagesUtils.get("approval.reject.message", order.getSubmittedBy()));
            }
        }
        else {
            flash.error(MessagesUtils.get("approval.notPending.error", order.getOrderNumber()));
        }
        index();
    }

    protected static boolean isApprove(String value) {
        return "approve".equals(value);
    }

    protected static boolean isReject(String value) {
        return "reject".equals(value);
    }

    @Util
    public static void approve(ApprovalRestRep approval, String message) {
        action(approval.getId(), ApprovalRestRep.APPROVED, message);
    }

    @Util
    public static void reject(ApprovalRestRep approval, String message) {
        action(approval.getId(), ApprovalRestRep.REJECTED, message);
    }

    private static void action(URI approvalId, String status, String message) {
        ApprovalUpdateParam updateParam = new ApprovalUpdateParam();
        updateParam.setApprovalStatus(status);
        updateParam.setMessage(message);
        getCatalogClient().approvals().update(approvalId, updateParam);
    }

    public static class ApprovalsForm {
        @Required
        public String submit;
        @Required
        public String message;

        public ApprovalsForm(String submit, String message) {
            this.submit = submit;
            this.message = message;
        }

        public String getSubmit() {
            return this.submit;
        }

        public String getMessage() {
            return this.message;
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
        }
    }
}
