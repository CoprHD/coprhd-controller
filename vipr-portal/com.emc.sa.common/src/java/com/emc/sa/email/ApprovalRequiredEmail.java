/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.email;

import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;

public class ApprovalRequiredEmail extends CatalogEmail {

    private static final String EMAIL_TEMPLATE = readEmailTemplate();

    private static final String APPROVAL_URL_TEMPLATE = "https://%s/approvals/edit/%s";

    private static final String APPROVAL_URL = "approvalUrl";

    private static final String ORDER_SUMMARY = "orderSummary";

    private OrderSummaryHtml orderSummaryHtml;

    public ApprovalRequiredEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest, String virtualIp) {
        setTitle(messages.get("notification.approvalRequired.title"));
        orderSummaryHtml = new OrderSummaryHtml(order, catalogService, approvalRequest);
        setParameter(ORDER_SUMMARY, orderSummaryHtml.getOrderSummary());

        if (StringUtils.isNotBlank(virtualIp) && approvalRequest != null) {
            setApprovalUrl(String.format(APPROVAL_URL_TEMPLATE, virtualIp, approvalRequest.getId()));
        }
    }

    public void setApprovalUrl(String url) {
        setParameter(APPROVAL_URL, url);
    }

    @Override
    public String getBodyContent() {
        return evaluate(EMAIL_TEMPLATE);
    }

    private static String readEmailTemplate() {
        return readTemplate("ApprovalRequiredEmail.html");
    }

}
