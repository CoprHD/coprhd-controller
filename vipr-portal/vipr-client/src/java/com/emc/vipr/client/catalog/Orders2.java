/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

public class Orders2 extends AbstractCatalogBulkResources<OrderRestRep> implements TenantResources<OrderRestRep> {

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
     *        the order
     * @return the newly created order
     */
    public OrderRestRep submit(OrderCreateParam input) {
        OrderRestRep order = client
                .post(OrderRestRep.class, input, PathConstants.ORDER2_URL);
        return order;
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
    public OrderRestRep submit(URI tenantId, URI serviceId, Map<String,String> params) {
        OrderCreateParam input = new OrderCreateParam();
        input.setCatalogService(serviceId);
        input.setTenantId(tenantId);

        List<Parameter> parameters = new ArrayList<Parameter>();
        for (Map.Entry<String,String> entry: params.entrySet()) {
            parameters.add(new Parameter(entry.getKey(), entry.getValue(), entry.getValue()));
        }
        input.setParameters(parameters);
        return submit(input);
    }
    
    /**
     * Lists the currents user's orders 
     * <p>
     * API Call: <tt>GET /catalog/orders</tt>
     * 
     * @return list of user orders
     */    
    public List<NamedRelatedResourceRep> listUserOrders() {
        OrderList response = client.get(OrderList.class, PathConstants.ORDER2_URL);
        return response.getOrders();
    }
    
    /**
     * Return list of the current user's orders 
     * <p>
     * API Call: <tt>GET /catalog/orders</tt>
     * 
     * @return list of user orders
     */    
    public List<OrderRestRep> getUserOrders() {
        return getByRefs(listUserOrders());
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
     *        the ID of catalog order to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }    
    

}
