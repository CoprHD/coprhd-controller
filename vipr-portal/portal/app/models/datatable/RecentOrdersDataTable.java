/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import util.OrderUtils;
import util.datatable.DataTableParams;

import com.emc.vipr.model.catalog.OrderRestRep;

public class RecentOrdersDataTable extends OrderDataTable {
    private int maxOrders = 0;
    /** Only displays orders newer than the given number of days (defaults to 7 days). */
    private int maxAgeInDays = 7;

    public RecentOrdersDataTable(String tenantId) {
        super(tenantId);
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

    protected Date calculateStartTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -maxAgeInDays);
        return cal.getTime();
    }

    protected Date now() {
        return new Date();
    }

    @Override
    public List<OrderInfo> fetchAll() {
        Date startTime = calculateStartTime();
        Date endTime = now();

        List<OrderRestRep> orders = OrderUtils.findByTimeRange(startTime, endTime, tenantId);
        if (userInfo != null) {
            filterByUserId(orders);
        }
        else {
            filterByTenant(orders);
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
