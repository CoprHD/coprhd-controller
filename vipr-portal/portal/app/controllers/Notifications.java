/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package controllers;


import static util.BourneUtil.getCatalogClient;
import static util.CatalogServiceUtils.getCatalogService;
import static util.OrderUtils.getOrder;

import java.net.URI;
import java.util.Date;
import java.util.List;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.MessagesUtils;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;

import controllers.util.Models;

@With(Common.class)
public class Notifications extends Controller {
    
    private static final int WAIT_DELAY = 5000;
    private static final String APPROVAL_REQUEST_TYPE = "ApprovalRequest";
    private static final String ORDER_TYPE = "Order";

    @Util
    public static List<Notification> getNotifications() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        List<Notification> notifications = Lists.newArrayList();
        List<ApprovalRestRep> approvals = catalog.approvals().search().byStatus(ApprovalRestRep.PENDING).run();
        for (ApprovalRestRep approval : approvals) {
            if (approval.getOrder() != null) {
                OrderRestRep order = getOrder(approval.getOrder());
                if (order != null) {
                    CatalogServiceRestRep service = getCatalogService(order.getCatalogService());
        
                    Notification notification = new Notification();
                    notification.id = approval.getId().toString();
                    notification.orderId = order.getId().toString();
                    if (service != null) {
                        notification.image = service.getImage();
                        notification.message = MessagesUtils.get("notification.approvalPending", service.getTitle(),
                                order.getSubmittedBy());
                    }
                    notification.lastUpdated = approval.getDateActioned() != null ? approval.getDateActioned() : approval.getCreationTime().getTime();
                    notifications.add(notification);
                }
            }
        }
        return notifications;
    }

    public static void notifications(Long lastUpdated, Integer count) {
        if (lastUpdated == null) {
            lastUpdated = 0L;
        }
        if (count == null) {
            count = 0;
        }
        List<Notification> notifications = waitForNotifications(lastUpdated, count);
        render(notifications);
    }

    private static List<Notification> waitForNotifications(long lastUpdated, int count) {
        List<Notification> notifications = (List<Notification>) renderArgs.get(Common.NOTIFICATIONS);
        while (!hasNewer(notifications, lastUpdated, count)) {
            Logger.trace("Waiting for notifications (lastUpdated: %s, count: %s)", lastUpdated, count);
            await(WAIT_DELAY);
            notifications = getNotifications();
        }
        Logger.debug("Found notifications (lastUpdated: %s, count: %s)", lastUpdated, count);
        return notifications;
    }

    private static boolean hasNewer(List<Notification> notifications, long lastUpdated, int count) {
        if (notifications.size() != count) {
            return true;
        }
        else if (notifications.isEmpty()) {
            return false;
        }
        else if (lastUpdated == 0) {
            return true;
        }
        Date date = new Date(lastUpdated);
        for (Notification notification : notifications) {
            if (date.before(notification.lastUpdated)) {
                return true;
            }
        }
        return false;
    }

    public static void showTarget(String id) {
        URI uri = URI.create(id);
        String type = Models.getTypeName(uri);
        if (APPROVAL_REQUEST_TYPE.equalsIgnoreCase(type)) {
            redirect("Approvals.edit", id);
        }
        else if (ORDER_TYPE.equalsIgnoreCase(type)) {
            String orderId = id;
            redirect("Orders.receipt", orderId);
        }
        else {
            notFound();
        }
    }

    public static class Notification {
        public String id;
        public String orderId;
        public String image;
        public String message;
        public Date lastUpdated;
    }
}
