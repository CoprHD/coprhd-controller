/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.util.Calendar;
import java.util.List;

import models.datatable.ScheduledOrdersDataTable;
import models.datatable.ScheduledOrdersDataTable.ScheduledOrderInfo;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.ExecutionWindowUtils;
import util.OrderUtils;
import util.datatable.DataTableParams;
import util.datatable.DataTablesSupport;

import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class ScheduledOrders extends Controller {

    protected static final String CANCELLED = "ScheduledOrder.cancel.success";

    @Util
    public static void addNextExecutionWindow() {
        Calendar now = Calendar.getInstance();
        ExecutionWindowRestRep nextWindow = ExecutionWindowUtils.getNextExecutionWindow(now);
        if (nextWindow != null) {
            Calendar nextWindowTime = ExecutionWindowUtils.calculateNextWindowTime(now, nextWindow);
            renderArgs.put("nextWindowName", nextWindow.getName());
            renderArgs.put("nextWindowTime", nextWindowTime.getTime());
        }
    }

    public static void list() {
        addNextExecutionWindow();
        renderArgs.put("dataTable", new ScheduledOrdersDataTable());
        TenantSelector.addRenderArgs();
        render();
    }

    public static void listJson() {
        DataTableParams dataTableParams = DataTablesSupport.createParams(params);
        ScheduledOrdersDataTable dataTable = new ScheduledOrdersDataTable();
        renderJSON(DataTablesSupport.createJSON(dataTable.fetchData(dataTableParams), params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<ScheduledOrderInfo> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    OrderRestRep order = OrderUtils.getOrder(uri(id));
                    if (order != null) {
                        Models.checkAccess(order.getTenant());
                        results.add(new ScheduledOrderInfo(order));
                    }
                }
            }
        }
        renderJSON(DataTablesSupport.toJson(results));
    }

    public static void cancel(@As(",") String[] ids) {
        if ((ids != null) && (ids.length > 0)) {
            Logger.info("Cancel: " + StringUtils.join(ids, ", "));

            for (String orderId : ids) {
                if (StringUtils.isNotBlank(orderId)) {
                    OrderUtils.cancelOrder(uri(orderId));
                }
            }
        }
        list();
    }

    public static void showOrder(String orderId) {
        redirect("Orders.receipt", orderId);
    }
}
