/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import models.security.UserInfo;
import util.OrderUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;

import controllers.catalog.Orders;

public class OrderDataTable extends DataTable {
    protected UserInfo userInfo;
    protected String tenantId;
    private static final String ORDER_MAX_COUNT = "6000";

    public OrderDataTable(String tenantId) {
        this.tenantId = tenantId;

        addColumn("number");
        addColumn("status").setRenderFunction("render.orderStatus");
        addColumn("summary");
        addColumn("submittedBy").hidden();
        addColumn("createdDate").setRenderFunction("render.localDate");
        addColumn("scheduledTime").setRenderFunction("render.localDate");

        setDefaultSort("createdDate", "desc");
        setRowCallback("createRowLink");
        sortAll();
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public List<OrderInfo> fetchAll() {
        List<OrderRestRep> orderRestReps = null;
        if (userInfo != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date startDate = null;
            //just for test
            try {
                startDate = sdf.parse("2016-08-08");
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Date endDate = new Date();
            orderRestReps = OrderUtils.getUserOrders(startDate, endDate, ORDER_MAX_COUNT);
        } else {
            orderRestReps = OrderUtils.getOrders(uri(this.tenantId));
        }

        return convert(orderRestReps);
    }

    protected List<OrderInfo> convert(List<OrderRestRep> orderRestReps) {
        List<OrderInfo> orderInfos = Lists.newArrayList();
        if (orderRestReps != null) {
            for (OrderRestRep orderRestRep : orderRestReps) {
                orderInfos.add(new OrderInfo(orderRestRep));
            }
        }
        return orderInfos;
    }

    public static class OrderInfo {
        public String rowLink;
        public String id;
        public String number;
        public String status;
        public String summary;
        public Long createdDate;
        public String submittedBy;
        public String scheduledEventId;
        public Long scheduledTime;

        public OrderInfo(OrderRestRep o) {
            this.id = o.getId().toString();
            this.number = o.getOrderNumber();
            this.rowLink = createLink(Orders.class, "receipt", "orderId", id);
            this.status = o.getOrderStatus();
            this.summary = o.getSummary();
            if (o.getCreationTime() != null) {
                this.createdDate = o.getCreationTime().getTime().getTime();
            }
            if (o.getSubmittedBy() != null) {
                this.submittedBy = o.getSubmittedBy().toString();
            }
            if (o.getScheduledEventId() != null) {
                this.scheduledEventId = o.getScheduledEventId().toString();
            }
            if (o.getScheduledTime() != null) {
                this.scheduledTime = o.getScheduledTime().getTime().getTime();
            }
        }
    }
}
