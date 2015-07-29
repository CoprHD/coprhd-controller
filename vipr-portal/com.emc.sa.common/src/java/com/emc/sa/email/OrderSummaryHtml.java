/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.email;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;

public class OrderSummaryHtml extends TemplateBase {

    private static final String ORDER_SUMMARY_TEMPLATE = readOrderSummaryTemplate();
    private static final String ORDER_SUMMARY_APPROVAL_TEMPLATE = readOrderSummaryApprovalTemplate();

    private static final String ORDER_NUMBER = "orderNumber";
    private static final String SERVICE_TITLE = "serviceTitle";
    private static final String ORDER_STATUS = "orderStatus";
    private static final String SUBMITTED_BY = "submittedBy";
    private static final String ORDER_APPROVAL = "orderApproval";

    private static final String APPROVAL_STATUS = "approvalStatus";
    private static final String APPROVED_BY = "approvedBy";

    public OrderSummaryHtml(Order order, CatalogService catalogService, ApprovalRequest approvalRequest) {
        setParameter(ORDER_NUMBER, order.getOrderNumber());
        setParameter(ORDER_STATUS, messages.get("OrderStatus." + order.getOrderStatus()));
        setParameter(SUBMITTED_BY, order.getSubmittedByUserId());

        setParameter(SERVICE_TITLE, catalogService.getTitle());

        if (approvalRequest != null && (approvalRequest.approved() || approvalRequest.rejected())) {
            setParameter(APPROVAL_STATUS, messages.get("notification.approval.by." + approvalRequest.getApprovalStatus()));
            setParameter(APPROVED_BY, approvalRequest.getApprovedBy());
        }
    }

    protected String getOrderSummary() {
        if (hasApproval()) {
            setParameter(ORDER_APPROVAL, getOrderSummaryApproval());
        }
        return evaluate(ORDER_SUMMARY_TEMPLATE);
    }

    protected String getOrderSummaryApproval() {
        return evaluate(ORDER_SUMMARY_APPROVAL_TEMPLATE);
    }

    protected boolean hasApproval() {
        return StringUtils.isNotBlank(getParameter(APPROVAL_STATUS)) || StringUtils.isNotBlank(getParameter(APPROVED_BY));
    }

    private static String readOrderSummaryTemplate() {
        return readTemplate("OrderSummary.html");
    }

    private static String readOrderSummaryApprovalTemplate() {
        return readTemplate("OrderSummaryApproval.html");
    }

}
