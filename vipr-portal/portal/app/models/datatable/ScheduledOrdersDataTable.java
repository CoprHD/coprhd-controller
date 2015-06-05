/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import java.util.List;

import util.ExecutionWindowUtils;
import util.MessagesUtils;
import util.OrderUtils;
import util.datatable.DataTableParams;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;

import controllers.util.Models;

public class ScheduledOrdersDataTable extends OrderDataTable {
    private int maxOrders = 0;

    public ScheduledOrdersDataTable() {
        super(Models.currentAdminTenant());
        alterColumn("submittedBy").setVisible(true);
        addColumn("executionWindowId").hidden().setSearchable(false);
        addColumn("executionWindow");
    }

    public int getMaxOrders() {
        return maxOrders;
    }

    public void setMaxOrders(int maxOrders) {
        this.maxOrders = maxOrders;
    }

    public List<ScheduledOrderInfo> fetchData(DataTableParams params) {
        List<OrderRestRep> orders = OrderUtils.getScheduledOrders();
        if (maxOrders > 0) {
            while (orders.size() > maxOrders) {
                orders.remove(orders.size() - 1);
            }
        }

        List<ScheduledOrderInfo> orderInfos = Lists.newArrayList();
        if (orders != null) {
            for (OrderRestRep orderRestRep : orders) {
                orderInfos.add(new ScheduledOrderInfo(orderRestRep));
            }
        }
        return orderInfos;
    }

    protected Object convert(OrderRestRep item) {
        return new ScheduledOrderInfo(item);
    }

    public static class ScheduledOrderInfo extends OrderInfo {
        public String executionWindowId;
        public String executionWindow;

        public ScheduledOrderInfo(OrderRestRep o) {
            super(o);
            RelatedResourceRep windowId = o.getExecutionWindow();
            if (windowId != null && !ExecutionWindowRestRep.NEXT.equals(windowId.getId())) {
                this.executionWindowId = windowId.getId().toString();
                ExecutionWindowRestRep window = ExecutionWindowUtils.getExecutionWindow(windowId);
                if (window != null) {
                    this.executionWindow = window.getName();
                }
            }
            else {
                this.executionWindow = MessagesUtils.get("scheduledOrders.nextExecutionWindow");
            }
        }
    }

}
