/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import render.RenderSupportOrderPackage;
import util.BourneUtil;
import util.MessagesUtils;
import util.OrderUtils;
import util.datatable.DataTableParams;

import com.emc.vipr.model.catalog.OrderCount;
import com.emc.vipr.model.catalog.OrderJobInfo;
import com.emc.vipr.model.catalog.OrderRestRep;

import util.support.SupportOrderPackageCreator;

public class RecentOrdersDataTable extends OrderDataTable {
    private int maxOrders = 0;
    /** Only displays orders newer than the given number of days (defaults to 7 days). */
    private int maxAgeInDays = 7;
    public static final String JOB_TYPE_DELETE = "DELETE_ORDER";
    public static final String JOB_TYPE_DOWNLOAD = "DOWNLOAD_ORDER";

    public RecentOrdersDataTable(String tenantId) {
        super(tenantId);
    }

    public RecentOrdersDataTable(String tenantId, int offsetInMinutes) {
        super(tenantId, offsetInMinutes);
    }

    public int getMaxOrders() {
        return maxOrders;
    }

    public void setMaxOrders(int maxOrders) {
        this.maxOrders = maxOrders;
    }

    public int getMaxAgeInDays() {
        return maxAgeInDays;
    }

    public void setMaxAgeInDays(int daysAgo) {
        this.maxAgeInDays = daysAgo;
    }

    @Override
    public List<OrderInfo> fetchAll() {
        if (startDate == null && endDate == null) {
            super.setStartAndEndDatesByMaxDays(maxAgeInDays);
        }

        List<OrderRestRep> orders = OrderUtils.findByTimeRange(startDate, endDate, tenantId, ORDER_MAX_COUNT_STR);
        if (userInfo != null) {// used for DashboardOrdersDataTable
            filterByUserId(orders);
        } else {
            // no need to filter by tenant, because already find by tenantId
            // filterByTenant(orders);
        }
        return convert(orders);
    }

    public List<OrderInfo> fetchData(DataTableParams params) {
        List<OrderInfo> orders = fetchAll();
        if (maxOrders > 0) {
            Collections.sort(orders, RECENT_ORDER_INFO_COMPARATOR);
            while (orders.size() > maxOrders) {
                orders.remove(orders.size() - 1);
            }
        }
        return orders;
    }

    @Override
    public OrderCount fetchCount() {
        return OrderUtils.getOrdersCount(startDate, endDate, uri(tenantId));
    }

    public void deleteOrders() {
        OrderUtils.deleteOrders(startDate, endDate, uri(tenantId));
    }

    public String getDeleteJobStatus() {
        OrderJobInfo info = OrderUtils.queryOrderJob(JOB_TYPE_DELETE);
        String status = null; // if the job is done or no job, return null
        if (info != null && !info.isNoJobOrJobDone()) {
            status = MessagesUtils.get("orders.delete.status", new Date(info.getStartTime()), new Date(info.getEndTime()),
                    info.getCompleted(), info.getTotal(), info.getFailed());
            if (info.getTotal() >= OrderDataTable.ORDER_MAX_DELETE_PER_GC) {
                status = String.format("%s %s", status, MessagesUtils.get("orders.delete.all.threshold",
                        OrderDataTable.ORDER_MAX_DELETE_PER_GC));
            }
        }
        Logger.info("getDeleteJobStatus: {}", status);
        return status;
    }

    public void downloadOrders(String startDate, String endDate, Integer maxDays, String orderIDs) {
        SupportOrderPackageCreator creator = new SupportOrderPackageCreator(BourneUtil.getCatalogClient());
        if (StringUtils.isNotEmpty(orderIDs)) {
            creator.setOrderIDs(orderIDs);
        } else {
            this.setByStartEndDateOrMaxDays(startDate, endDate, maxDays);
            creator.setStartDate(this.startDate);
            creator.setEndDate(this.endDate);
            creator.setTenantIDs(this.tenantId);
        }
        RenderSupportOrderPackage.renderSupportAuditPackage(creator);
    }

    public String getDownloadJobStatus() {
        OrderJobInfo info = OrderUtils.queryOrderJob(JOB_TYPE_DOWNLOAD);
        String status = null; // if the job is done or no job, return null
        if (info != null && !info.isNoJobOrJobDone()) {
            if (info.getStartTime() == 0) {
                status = MessagesUtils.get("orders.download.status.notime",
                        info.getCompleted(), info.getTotal(), info.getFailed());
            } else {
                status = MessagesUtils.get("orders.download.status", new Date(info.getStartTime()), new Date(info.getEndTime()),
                        info.getCompleted(), info.getTotal(), info.getFailed());
            }
        }
        Logger.info("getDownloadJobStatus: {}", status);
        return status;
    }
    
    public static String getCanBeDeletedOrderStatuses() {
        List<OrderStatus> canBeDeletedStatus = new ArrayList<OrderStatus>(Arrays.asList(OrderStatus.values()));
        CollectionUtils.filter(canBeDeletedStatus, new Predicate() {
            @Override
            public boolean evaluate(Object o) {
                return ((OrderStatus) o).canBeDeleted();
            }
        });

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < canBeDeletedStatus.size(); i++) {
            if (i != 0) {
                builder.append(", ");
            }
            if (i == canBeDeletedStatus.size() - 1) {
                builder.append("or ");
            }
            builder.append(canBeDeletedStatus.get(i).name());
        }
        return builder.toString();
    }

    /**
     * Filters out orders that are not associated with the selected tenant.
     *
     * @param orders
     *            the orders.
     */
    protected void filterByTenant(List<OrderRestRep> orders) {
        Iterator<OrderRestRep> iter = orders.iterator();
        while (iter.hasNext()) {
            if (!StringUtils.equals(tenantId, iter.next().getTenant().getId().toString())) {
                iter.remove();
            }
        }
    }

    /**
     * Filters out orders that are not submitted by the selected user (if applicable).
     *
     * @param orders
     *            the orders.
     */
    protected void filterByUserId(List<OrderRestRep> orders) {
        if (userInfo != null) {
            String userId = userInfo.getIdentifier();
            Iterator<OrderRestRep> iter = orders.iterator();
            while (iter.hasNext()) {
                if (!StringUtils.equals(userId, iter.next().getSubmittedBy())) {
                    iter.remove();
                }
            }
        }
    }

    protected static Comparator<OrderRestRep> RECENT_ORDER_COMPARATOR = new Comparator<OrderRestRep>() {
        @Override
        public int compare(OrderRestRep a, OrderRestRep b) {
            return ObjectUtils.compare(b.getCreationTime(), a.getCreationTime());
        }
    };

    protected static Comparator<OrderInfo> RECENT_ORDER_INFO_COMPARATOR = new Comparator<OrderInfo>() {
        @Override
        public int compare(OrderInfo a, OrderInfo b) {
            return ObjectUtils.compare(b.createdDate, a.createdDate);
        }
    };
}
