/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.client.catalog.search.OrderSearchBuilder;
import com.emc.vipr.client.core.TenantResources;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.*;
import com.sun.jersey.api.client.ClientResponse;

public class Orders2 extends AbstractCatalogBulkResources<OrderRestRep>implements TenantResources<OrderRestRep> {

    public Orders2(ViPRCatalogClient2 parent, RestClient client) {
        super(parent, client, OrderRestRep.class, PathConstants.ORDER2_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<OrderRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<OrderRestRep> getByUserTenant(ResourceFilter<OrderRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_ALL_URL);
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_ID_PARAM, tenantId);
        }
        OrderList response = client.getURI(OrderList.class, uriBuilder.build());
        return ResourceUtils.defaultList(response.getOrders());
    }

    @Override
    public List<OrderRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<OrderRestRep> getByTenant(URI tenantId, ResourceFilter<OrderRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

    @Override
    protected List<OrderRestRep> getBulkResources(BulkIdParam input) {
        OrderBulkRep response = client.post(OrderBulkRep.class, input, getBulkUrl());
        return defaultList(response.getOrders());
    }

    /**
     * Creates a search builder specifically for creating order search queries.
     *
     * @return a order search builder.
     */
    @Override
    public OrderSearchBuilder search() {
        return new OrderSearchBuilder(this);
    }

    /**
     * Submit an order
     * <p>
     * API Call: <tt>POST /catalog/orders</tt>
     *
     * @param input
     *            the order
     * @return the newly created order
     */
    public OrderRestRep submit(OrderCreateParam input) {

        OrderRestRep order = client
                .post(OrderRestRep.class, input, PathConstants.ORDER2_URL);
        return order;
    }

    /**
     * Schedule an order
     * <p>
     * API Call: <tt>POST /catalog/events</tt>
     *
     * @param input - event creation parameters
     * @return the scheduled events
     */
    public ScheduledEventRestRep submitScheduledEvent(ScheduledEventCreateParam input) {
        ScheduledEventRestRep event = client
                .post(ScheduledEventRestRep.class, input, PathConstants.SCHEDULED_EVENTS_URL);
        return event;
    }

    /**
     * Convenience method for submitting orders using a map of parameters.
     * <p>
     * API Call: <tt>POST /catalog/orders</tt>
     *
     * @param tenantId Tenant ID to place the order in
     * @param serviceId Service ID of the service to order
     * @param params Map of parameters to place the order with
     * @return the newly created order
     */
    public OrderRestRep submit(URI tenantId, URI serviceId, Map<String, String> params) {
        OrderCreateParam input = new OrderCreateParam();
        input.setCatalogService(serviceId);
        input.setTenantId(tenantId);

        List<Parameter> parameters = new ArrayList<Parameter>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            parameters.add(new Parameter(entry.getKey(), entry.getValue(), entry.getValue()));
        }
        input.setParameters(parameters);
        return submit(input);
    }

    public List<OrderRestRep> listUserOrders(String startTime, String endTime, String maxCount, boolean ordersOnly) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_MY_URL);
        if (startTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.START_TIME_PARAM, startTime);
        }
        if (endTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.END_TIME_PARAM, endTime);
        }
        if (maxCount != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.ORDER_MAX_COUNT, maxCount);
        }

        uriBuilder = uriBuilder.queryParam(SearchConstants.ORDERS_ONLY, ordersOnly);
        OrderBulkRep response = client.getURI(OrderBulkRep.class, uriBuilder.build());
        return response.getOrders();
    }

    /**
     * Return list of the current user's orders
     * <p>
     * API Call: <tt>GET /catalog/orders/my-orders</tt>
     *
     * @param startTime
     * @param endTime
     * @param maxCount The max number this API returns
     * @return list of user orders
     */
    public List<OrderRestRep> getUserOrders(String startTime, String endTime, String maxCount, boolean ordersOnly) {
        return listUserOrders(startTime, endTime, maxCount, ordersOnly);
    }

    /**
     * Gets the number of orders within a time range for current user
     *
     * <p>
     * API Call: <tt>GET /catalog/orders/my-order-count</tt>
     *
     * @param startTime
     * @param endTime
     * @return
     */
    public OrderCount getUserOrdersCount(String startTime, String endTime) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_MY_COUNT);
        if (startTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.START_TIME_PARAM, startTime);
        }
        if (endTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.END_TIME_PARAM, endTime);
        }
        OrderCount orderCount = client.getURI(OrderCount.class, uriBuilder.build());
        return orderCount;
    }

    /**
     * Get number of orders within a time range for the given tenants
     * <p>
     * API Call: <tt>GET /catalog/orders/count</tt>
     *
     * @param startTime
     * @param endTime
     * @param tenantId
     * @return
     */
    public OrderCount getOrdersCount(String startTime, String endTime, URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_ALL_COUNT);
        if (startTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.START_TIME_PARAM, startTime);
        }
        if (endTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.END_TIME_PARAM, endTime);
        }
        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_IDS_PARAM, tenantId.toString());
        }
        OrderCount orderCount = client.getURI(OrderCount.class, uriBuilder.build());
        return orderCount;
    }

    /**
     * Delete orders within a time range
     *
     * <p>
     * API Call: <tt>DELETE /catalog/orders</tt>
     *
     * @param startTime
     * @param endTime
     * @param tenantId
     */
    public void deleteOrders(String startTime, String endTime, URI tenantId, String orderStatus) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_DELETE_ORDERS);
        if (startTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.START_TIME_PARAM, startTime);
        }

        if (endTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.END_TIME_PARAM, endTime);
        }

        if (tenantId != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_IDS_PARAM, tenantId.toString());
        }

        if (orderStatus != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.ORDER_STATUS_PARAM2, orderStatus);
        }

        client.deleteURI(String.class, uriBuilder.build());
    }

    /**
     * Query order job status
     *
     * <p>
     * API Call: <tt>GET /catalog/orders/job-status</tt>
     *
     * @param jobType
     * @return
     */
    public OrderJobInfo queryOrderJob(String jobType) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_QUERY_ORDER_JOB);
        uriBuilder = uriBuilder.queryParam(SearchConstants.JOB_TYPE, jobType);

        OrderJobInfo jobInfo = client.getURI(OrderJobInfo.class, uriBuilder.build());

        return jobInfo;
    }

    /**
     * Download orders
     *
     * <p>
     * API Call: <tt>GET /catalog/orders/download</tt>
     *
     * @param startTime
     * @param endTime
     * @param tenantIDs
     * @param orderIDs
     */
    private URI getDownloadOrdersURI(String startTime, String endTime, String tenantIDs, String orderIDs,
            String status) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.ORDER2_DOWNLOAD_ORDER);

        if (startTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.START_TIME_PARAM, startTime);
        }
        if (endTime != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.END_TIME_PARAM, endTime);
        }
        if (tenantIDs != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.TENANT_IDS_PARAM, tenantIDs.toString());
        }
        if (orderIDs != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.ORDER_IDS, orderIDs.toString());
        }

        if (status != null) {
            uriBuilder = uriBuilder.queryParam(SearchConstants.ORDER_STATUS_PARAM2, status.toString());
        }

        return uriBuilder.build();
    }

    public InputStream downloadOrdersAsStream(String startTime, String endTime, String tenantIDs, String orderIDs, String status) {
        URI uri = getDownloadOrdersURI(startTime, endTime, tenantIDs, orderIDs, status);
        ClientResponse response = client.resource(uri).accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    public InputStream downloadOrdersAsText(String startTime, String endTime, String tenantIDs, String orderIDs, String status) {
        URI uri = getDownloadOrdersURI(startTime, endTime, tenantIDs, orderIDs, status);
        ClientResponse response = client.resource(uri).accept(MediaType.TEXT_PLAIN).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    /**
     * Return execution state for an order
     * <p>
     * API Call: <tt>GET /catalog/orders/{id}/execution</tt>
     *
     * @return order's execution state
     */
    public ExecutionStateRestRep getExecutionState(URI orderId) {
        return client.get(ExecutionStateRestRep.class, PathConstants.ORDER2_EXECUTION_STATE_URL, orderId);
    }

    /**
     * Return execution logs for an order
     * <p>
     * API Call: <tt>GET /catalog/orders/{id}/execution/logs</tt>
     *
     * @return order's execution logs
     */
    public List<ExecutionLogRestRep> getExecutionLogs(URI orderId) {
        ExecutionLogList response = client.get(ExecutionLogList.class, PathConstants.ORDER2_EXECUTION_LOGS_URL, orderId);
        return response.getExecutionLogs();
    }

    /**
     * Return logs for an order
     * <p>
     * API Call: <tt>GET /catalog/orders/{id}/logs</tt>
     *
     * @return order's logs
     */
    public List<OrderLogRestRep> getLogs(URI orderId) {
        OrderLogList response = client.get(OrderLogList.class, PathConstants.ORDER2_LOGS_URL, orderId);
        return response.getOrderLogs();
    }

    /**
     * Cancel a scheduled order
     * <p>
     * API Call: <tt>POST /catalog/orders/{id}/cancel</tt>
     *
     */
    public void cancelOrder(URI id) {
        client.post(String.class, PathConstants.ORDER2_CANCEL_URL, id);
    }

    /**
     * Deactivates the given order by ID.
     * <p>
     * API Call: <tt>POST /catalog/orders/{id}/deactivate</tt>
     *
     * @param id
     *            the ID of catalog order to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    /**
     * Return scheduled event for an order
     * <p>
     * API Call: <tt>GET /catalog/events/{id}</tt>
     *
     * @return order's logs
     */
    public ScheduledEventRestRep getScheduledEvent(URI eventId) {
        ScheduledEventRestRep event = client.get(ScheduledEventRestRep.class, PathConstants.SCHEDULED_EVENTS_URL + "/{id}", eventId);
        return event;
    }

    /**
     * Update an order
     * <p>
     * API Call: <tt>PUT /catalog/events/{id}</tt>
     *
     * @param eventId - URN for the event
     * @param input - event update parameters
     * @return the scheduled events
     */
    public ScheduledEventRestRep updateScheduledEvent(URI eventId, ScheduledEventUpdateParam input) {
        String uri = String.format("%s/%s", PathConstants.SCHEDULED_EVENTS_URL, eventId);
        ScheduledEventRestRep event = client
                .put(ScheduledEventRestRep.class, input, uri);
        return event;
    }

    /**
     * Deactivate an recurring event
     * <p>
     * API Call: <tt>POST /catalog/events/{id}/deactivate</tt>
     *
     * @param eventId - URN for the event
     */
    public void deactivateScheduledEvent(URI eventId) {
        client.post(String.class, PathConstants.SCHEDULED_EVENTS_DEACTIVATE_URL, eventId);
    }

    /**
     * Cancellation an recurring event
     * <p>
     * API Call: <tt>POST /catalog/events/{id}/cancel</tt>
     *
     * @param eventId - URN for the event
     */
    public void cancelScheduledEvent(URI eventId) {
        client.post(String.class, PathConstants.SCHEDULED_EVENTS_CANCELLATION_URL, eventId);
    }
}
