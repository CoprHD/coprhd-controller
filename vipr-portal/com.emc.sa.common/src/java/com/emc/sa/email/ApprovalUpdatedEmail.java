/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.email;

import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;

public class ApprovalUpdatedEmail extends CatalogEmail {

    private static final String EMAIL_TEMPLATE = readEmailTemplate();

    private static final String ORDER_URL_TEMPLATE = "https://%s/orders/%s/receipt";

    private static final String ORDER_URL = "orderUrl";

    private static final String ORDER_SUMMARY = "orderSummary";

    private OrderSummaryHtml orderSummaryHtml;

    public ApprovalUpdatedEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest, String virtualIp) {
        setTitle(messages.get("notification.approvalUpdated.title", approvalRequest.getApprovalStatus()));
        orderSummaryHtml = new OrderSummaryHtml(order, catalogService, approvalRequest);
        setParameter(ORDER_SUMMARY, orderSummaryHtml.getOrderSummary());

        if (StringUtils.isNotBlank(virtualIp) && order != null) {
            setOrderUrl(String.format(ORDER_URL_TEMPLATE, virtualIp, order.getId()));
        }
    }

    public void setOrderUrl(String url) {
        setParameter(ORDER_URL, url);
    }

    @Override
    public String getBodyContent() {
        return evaluate(EMAIL_TEMPLATE);
    }

    private static String readEmailTemplate() {
        return readTemplate("ApprovalUpdatedEmail.html");
    }

}
