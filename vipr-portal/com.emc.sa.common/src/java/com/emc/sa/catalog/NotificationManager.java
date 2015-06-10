/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.util.Map;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.vipr.model.catalog.ApprovalRestRep;

public interface NotificationManager {

    public void notifyUserOfOrderStatus(Order order, CatalogService service, ApprovalRequest approvalRequest);

    public void notifyApproversOfApprovalRequest(Order order, CatalogService service, ApprovalRequest approval);

    public void notifyUserOfApprovalStatus(Order order, CatalogService service, ApprovalRequest approval);

    public void sendOrderStatusEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest);

    public void sendApproversApprovalRequestEmail(Order order, CatalogService catalogService,
            ApprovalRequest approvalRequest);

    public void sendApprovalStatusEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest);

    public CoordinatorClient getCoordinatorClient();

    public void setCoordinatorClient(CoordinatorClient coordinatorClient);

    public boolean containsSmtpSettings(Map<String, String> properties);

    public ApprovalRestRep map(ApprovalRequest from);

}
