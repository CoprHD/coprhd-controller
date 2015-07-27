/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

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

    public OrderDataTable(String tenantId) {
        this.tenantId = tenantId;

        addColumn("number");
        addColumn("status").setRenderFunction("render.orderStatus");
        addColumn("summary");
        addColumn("submittedBy").hidden();
        addColumn("createdDate").setRenderFunction("render.localDate");

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
            orderRestReps = OrderUtils.getUserOrders(userInfo.getCommonName());
        }
        else {
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
        }
    }
}
