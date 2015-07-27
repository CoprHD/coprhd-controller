/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.catalog.impl.ApiListUtils.getApiListUri;
import static com.emc.vipr.client.catalog.impl.ApiListUtils.postApiList;
import static com.emc.vipr.client.catalog.impl.PathConstants.EXECUTION_URL_FORMAT;

import java.util.Date;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.vipr.client.ViPRCatalogClient;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.ApiList;
import com.emc.vipr.model.catalog.ExecutionInfo;
import com.emc.vipr.model.catalog.OrderInfo;
import com.emc.vipr.model.catalog.Reference;
import com.sun.jersey.api.client.GenericType;
/**
 * 
 * @deprecated Replaced by
 * @see Orders2
 */
@Deprecated
public class Orders extends AbstractBulkResources<OrderInfo> {
    public Orders(ViPRCatalogClient parent, RestClient client) {
        super(parent, client, OrderInfo.class, PathConstants.ORDER_URL);
    }

    /**
     * Lists all orders for the current user.
     * <p>
     * API Call: GET /api/orders
     *
     * @return Order references
     */
    @Deprecated
    public List<Reference> list() {
        return doList();
    }

    /**
     * Retrieves all orders for the current user.
     * <p>
     * API Call: GET /api/orders
     *
     * @return All orders for the current user.
     */
    @Deprecated
    public List<OrderInfo> getAll() {
        return doGetAll();
    }

    /**
     * Retrieves all order references for a specific time range.
     * <p>
     * API Call: GET /api/orders/all
     *
     * @return All order references for a specific time range.
     */
    @Deprecated
    public List<Reference> listByTimeRange(Date start, Date end) {
        Long startTime = null;
        if (start != null) {
            startTime = start.getTime();
        }
        Long endTime = null;
        if (end != null) {
            endTime = end.getTime();
        }
        return listByTimeRange(startTime, endTime);
    }    
    
    /**
     * Retrieves all order references for a specific time range.
     * <p>
     * API Call: GET /api/orders/all
     *
     * @return All order references for a specific time range.
     */
    @Deprecated
    public List<Reference> listByTimeRange(Long startTime, Long endTime) {
        UriBuilder builder = client.uriBuilder(baseUrl + "/all");
        if (startTime != null) {
            builder.queryParam("startTime", startTime);
        }
        if (endTime != null) {
            builder.queryParam("endTime", endTime);
        }
        return getApiListUri(client, new GenericType<List<Reference>>() {}, builder.build());
    }     
    
    /**
     * Retrieves all orders for a specific time range.
     * <p>
     * API Call: GET /api/orders/all
     *
     * @return All orders for a specific time range.
     */
    @Deprecated
    public List<OrderInfo> getByTimeRange(Date start, Date end) {
        List<Reference> apiList = listByTimeRange(start, end);
        return getByRefs(apiList);
    }    
    
    /**
     * Retrieves all orders for a specific time range.
     * <p>
     * API Call: GET /api/orders/all
     *
     * @return All orders for a specific time range.
     */
    @Deprecated
    public List<OrderInfo> getByTimeRange(Long startTime, Long endTime) {
        List<Reference> apiList = listByTimeRange(startTime, endTime);
        return getByRefs(apiList);
    }            
    
    /**
     * Retrieves the execution info for the specified order.
     * <p>
     * API Call: GET /api/orders/{id}/execution
     *
     * @param id Identifier of the order to retrieve execution information for.
     * @return Execution information.
     */
    @Deprecated
    public ExecutionInfo getExecutionInfo(String id) {
        return client.get(ExecutionInfo.class, String.format(EXECUTION_URL_FORMAT, baseUrl), id);
    }

    @Override
    protected List<OrderInfo> getBulkResources(BulkIdParam input) {
        List<OrderInfo> orders = postApiList(client, input, new GenericType<List<OrderInfo>>() {}, getBulkUrl());
        return orders;
    }
}
