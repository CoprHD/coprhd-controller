/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.OrderMapper.createNewObject;
import static com.emc.sa.api.mapper.OrderMapper.createOrderParameters;
import static com.emc.sa.api.mapper.OrderMapper.map;
import static com.emc.sa.api.mapper.OrderMapper.toExecutionLogList;
import static com.emc.sa.api.mapper.OrderMapper.toOrderLogList;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.db.client.URIUtil.asString;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.OrderFilter;
import com.emc.sa.api.mapper.OrderMapper;
import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.CatalogServiceManager;
import com.emc.sa.catalog.OrderManager;
import com.emc.sa.catalog.WorkflowServiceDescriptor;
import com.emc.sa.catalog.ServiceDescriptorUtil;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.descriptor.ServiceFieldGroup;
import com.emc.sa.descriptor.ServiceFieldTable;
import com.emc.sa.descriptor.ServiceItem;
import com.emc.sa.engine.scheduler.SchedulerDataManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.ScheduleTimeHelper;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderAndParams;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.db.client.model.uimodels.ScheduledEvent;
import com.emc.storageos.db.client.model.uimodels.ScheduledEventStatus;
import com.emc.storageos.db.client.model.uimodels.ScheduledEventType;
import com.emc.storageos.db.client.util.ExecutionWindowHelper;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.ExecutionLogList;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderBulkRep;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.OrderList;
import com.emc.vipr.model.catalog.OrderLogList;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.emc.vipr.model.catalog.ScheduleInfo;
import com.google.common.collect.Lists;

@DefaultPermissions(
        readRoles = {},
        writeRoles = {})
@Path("/catalog/orders")
public class OrderService extends CatalogTaggedResourceService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static final String EVENT_SERVICE_TYPE = "catalog-order";

    private static Charset UTF_8 = Charset.forName("UTF-8");

    private static int SCHEDULED_EVENTS_SCAN_INTERVAL = 300;
    private int scheduleInterval = SCHEDULED_EVENTS_SCAN_INTERVAL;

    private static final String LOCK_NAME = "orderscheduler";

    @Autowired
    private CoordinatorClient coordinatorClient;

    private InterProcessLock lock;

    @Autowired
    private RecordableEventManager eventManager;

    @Autowired
    private OrderManager orderManager;

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    @Autowired
    private WorkflowServiceDescriptor workflowServiceDescriptor;

    @Autowired
    private EncryptionProvider encryptionProvider;

    @Autowired
    private SchedulerDataManager dataManager;

    @Autowired
    private ModelClient client;

    private ScheduledExecutorService _executorService = new NamedScheduledThreadPoolExecutor("OrderScheduler", 1);

    @Override
    protected Order queryResource(URI id) {
        return getOrderById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Order order = queryResource(id);
        return uri(order.getTenant());
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.ORDER;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new OrderResRepFilter(user, permissionsHelper);
    }

    public void setScheduleInterval(int scheduleInterval) {
        this.scheduleInterval = scheduleInterval;
    }

    public int getScheduleInterval() {
        return scheduleInterval;
    }

    /**
     * init method, this will be called by Spring framework after create bean successfully
     */
    public void init() {
        // scan scheduled event CF to schedule REOCCURRENCE orders at regular inteval
        _executorService.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            scheduleReoccurenceOrders();
                        } catch (Exception e) {
                            log.debug("Exception is throwed when scheduling orders", e);
                        }
                    }
                },
                0, scheduleInterval, TimeUnit.SECONDS);

    }

    /**
     * List data for the specified orders.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified orders
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public OrderBulkRep getBulkResources(BulkIdParam param) {
        return (OrderBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Order> getResourceClass() {
        return Order.class;
    }

    @Override
    public OrderBulkRep queryBulkResourceReps(List<URI> ids) {
        List<OrderRestRep> orderRestReps =
                new ArrayList<OrderRestRep>();
        List<OrderAndParams> ordersAndParams =
                orderManager.getOrdersAndParams(ids);
        for (OrderAndParams orderAndParams : ordersAndParams) {
            orderRestReps.add(OrderMapper.map(orderAndParams.getOrder(),
                    orderAndParams.getParameters()));
        }
        return new OrderBulkRep(orderRestReps);
    }

    @Override
    public OrderBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        OrderFilter filter = new OrderFilter(getUserFromContext(), _permissionsHelper);

        List<OrderRestRep> orderRestReps =
                new ArrayList<OrderRestRep>();
        List<OrderAndParams> ordersAndParams =
                orderManager.getOrdersAndParams(ids);
        for (OrderAndParams orderAndParams : ordersAndParams) {
            if (filter.isAccessible(orderAndParams.getOrder())) {
                orderRestReps.add(OrderMapper.map(orderAndParams.getOrder(),
                        orderAndParams.getParameters()));
            }
        }
        return new OrderBulkRep(orderRestReps);
    }

    /**
     * parameter: 'orderStatus' The status for the order
     * parameter: 'startTime' Start time to search for orders
     * parameter: 'endTime' End time to search for orders
     * 
     * @return Return a list of matching orders or an empty list if no match was found.
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {

        StorageOSUser user = getUserFromContext();
        String tenantId = user.getTenantId();
        if (parameters.containsKey(SearchConstants.TENANT_ID_PARAM)) {
            tenantId = parameters.get(SearchConstants.TENANT_ID_PARAM).get(0);
        }
        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        if (!parameters.containsKey(SearchConstants.ORDER_STATUS_PARAM) && !parameters.containsKey(SearchConstants.START_TIME_PARAM)
                && !parameters.containsKey(SearchConstants.END_TIME_PARAM)) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(),
                    SearchConstants.ORDER_STATUS_PARAM + " or " + SearchConstants.START_TIME_PARAM + " or "
                            + SearchConstants.END_TIME_PARAM);
        }

        if (parameters.containsKey(SearchConstants.ORDER_STATUS_PARAM)
                && (parameters.containsKey(SearchConstants.START_TIME_PARAM) || parameters.containsKey(SearchConstants.END_TIME_PARAM))) {
            throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(getResourceClass().getName(),
                    SearchConstants.ORDER_STATUS_PARAM, SearchConstants.START_TIME_PARAM + " or " + SearchConstants.END_TIME_PARAM);
        }

        List<Order> orders = Lists.newArrayList();
        if (parameters.containsKey(SearchConstants.ORDER_STATUS_PARAM)) {
            String orderStatus = parameters.get(SearchConstants.ORDER_STATUS_PARAM).get(0);
            ArgValidator.checkFieldNotEmpty(orderStatus, SearchConstants.ORDER_STATUS_PARAM);
            orders = orderManager.findOrdersByStatus(uri(tenantId), OrderStatus.valueOf(orderStatus));
        }
        else if (parameters.containsKey(SearchConstants.START_TIME_PARAM) || parameters.containsKey(SearchConstants.END_TIME_PARAM)) {
            Date startTime = null;
            if (parameters.containsKey(SearchConstants.START_TIME_PARAM)) {
                startTime = getDateTimestamp(parameters.get(SearchConstants.START_TIME_PARAM).get(0));
            }
            Date endTime = null;
            if (parameters.containsKey(SearchConstants.END_TIME_PARAM)) {
                endTime = getDateTimestamp(parameters.get(SearchConstants.END_TIME_PARAM).get(0));
            }
            if (startTime == null && endTime == null) {
                throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(),
                        SearchConstants.ORDER_STATUS_PARAM + " or " + SearchConstants.START_TIME_PARAM + " or "
                                + SearchConstants.END_TIME_PARAM);
            }
            orders = orderManager.findOrdersByTimeRange(uri(tenantId), startTime, endTime);
        }

        ResRepFilter<SearchResultResourceRep> resRepFilter =
                (ResRepFilter<SearchResultResourceRep>) getPermissionFilter(getUserFromContext(), _permissionsHelper);

        List<SearchResultResourceRep> searchResultResourceReps = Lists.newArrayList();
        for (Order order : orders) {
            RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), order.getId()));
            SearchResultResourceRep searchResultResourceRep = new SearchResultResourceRep();
            searchResultResourceRep.setId(order.getId());
            searchResultResourceRep.setLink(selfLink);
            if (authorized || resRepFilter.isAccessible(searchResultResourceRep)) {
                searchResultResourceReps.add(searchResultResourceRep);
            }
        }

        SearchResults result = new SearchResults();
        result.setResource(searchResultResourceReps);
        return result;
    }

    @POST
    @Path("")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderRestRep createOrder(OrderCreateParam createParam) {
        StorageOSUser user = getUserFromContext();

        URI tenantId = createParam.getTenantId();
        if (tenantId != null) {
            verifyAuthorizedInTenantOrg(tenantId, user);
        }
        else {
            tenantId = uri(user.getTenantId());
        }

        Order order = createNewOrder(user, tenantId, createParam);

        orderManager.processOrder(order);

        order = orderManager.getOrderById(order.getId());
        List<OrderParameter> orderParameters = orderManager.getOrderParameters(order.getId());

        auditOpSuccess(OperationTypeEnum.CREATE_ORDER, order.auditParameters());
        return map(order, orderParameters);
    }

    public Order createNewOrder(StorageOSUser user, URI tenantId, OrderCreateParam createParam) {
        ArgValidator.checkFieldNotNull(createParam.getCatalogService(), "catalogService");
        CatalogService service = catalogServiceManager.getCatalogServiceById(createParam.getCatalogService());
        if (service == null) {
            throw APIException.badRequests.orderServiceNotFound(
                    asString(createParam.getCatalogService()));
        }

        final ServiceDescriptor descriptor = ServiceDescriptorUtil.getServiceDescriptorByName(serviceDescriptors, workflowServiceDescriptor, service.getBaseService());
        if (descriptor == null) {
            throw APIException.badRequests.orderServiceDescriptorNotFound(
                    service.getBaseService());
        }

        Order order = createNewObject(tenantId, createParam);

        addLockedFields(service.getId(), descriptor, createParam);

        validateParameters(descriptor, createParam.getParameters(), service.getMaxSize());

        List<OrderParameter> orderParams = createOrderParameters(order, createParam, encryptionProvider);

        orderManager.createOrder(order, orderParams, user);

        return order;
    }

    private void addLockedFields(URI catalogServiceId, ServiceDescriptor serviceDescriptor, OrderCreateParam createParam) {
        Map<String, String> locked = catalogServiceManager.getLockedFields(catalogServiceId);
        for (ServiceField field : serviceDescriptor.getAllFieldList()) {
            if (locked.containsKey(field.getName())) {
                String lockedValue = locked.get(field.getName());
                Parameter parameter = createParam.findParameterByLabel(field.getName());
                if (parameter != null) {
                    parameter.setValue(lockedValue);
                }
                else {
                    Parameter newLockedParameter = new Parameter();
                    newLockedParameter.setLabel(field.getName());
                    newLockedParameter.setValue(lockedValue);
                    createParam.getParameters().add(newLockedParameter);
                }
            }
        }

    }

    @GET
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public OrderRestRep getOrder(@PathParam("id") String id) {

        Order order = queryResource(uri(id));

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(order.getTenant()), user);

        List<OrderParameter> orderParameters = orderManager.getOrderParameters(order.getId());

        return map(order, orderParameters);

    }

    @POST
    @Path("/{id}/cancel")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response cancelOrder(@PathParam("id") String id) {

        Order order = queryResource(uri(id));
        ArgValidator.checkEntity(order, uri(id), true);

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(order.getTenant()), user);

        if (!OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.SCHEDULED)) {
            throw APIException.badRequests.unexpectedValueForProperty("orderStatus", OrderStatus.SCHEDULED.toString(),
                    order.getOrderStatus());
        }

        if (order.getScheduledEventId()!=null) {
            ScheduledEvent scheduledEvent = client.scheduledEvents().findById(order.getScheduledEventId());
            if (scheduledEvent.getEventType().equals(ScheduledEventType.ONCE)) {
                scheduledEvent.setEventStatus(ScheduledEventStatus.CANCELLED);
                client.save(scheduledEvent);
            }
            order.setOrderStatus(OrderStatus.CANCELLED.name());
            client.save(order);
        } else {
            orderManager.deleteOrder(order);
        }

        return Response.ok().build();
    }

    /**
     * Gets the order logs
     * 
     * @param orderId the URN of an order
     * @brief List Order Logs
     * @return a list of order logs
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/logs")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderLogList getOrderLogs(@PathParam("id") String orderId) throws DatabaseException {

        Order order = queryResource(uri(orderId));

        StorageOSUser user = getUserFromContext();

        verifyAuthorizedInTenantOrg(uri(order.getTenant()), user);

        List<ExecutionLog> executionLogs = orderManager.getOrderExecutionLogs(order);

        return toOrderLogList(executionLogs);
    }

    /**
     * Gets the order execution
     * 
     * @param orderId the URN of an order
     * @brief Get Order Execution
     * @return an order execution
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/execution")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ExecutionStateRestRep getOrderExecutionState(@PathParam("id") String orderId) throws DatabaseException {

        Order order = queryResource(uri(orderId));
        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(order.getTenant()), user);

        ExecutionState executionState = orderManager.getOrderExecutionState(order.getExecutionStateId());

        return map(executionState);
    }

    /**
     * Gets the order execution logs
     * 
     * @param orderId the URN of an order
     * @brief Get Order Execution Logs
     * @return order execution logs
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/execution/logs")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ExecutionLogList getOrderExecutionTaskLogs(@PathParam("id") String orderId) throws DatabaseException {

        Order order = queryResource(uri(orderId));
        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(order.getTenant()), user);

        List<ExecutionTaskLog> executionTaskLogs = orderManager.getOrderExecutionTaskLogs(order);

        return toExecutionLogList(executionTaskLogs);
    }

    /**
     * Gets the list of orders
     * 
     * @param tenantId the URN of a tenant
     * @brief List Orders
     * @return a list of orders
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/all")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderList getOrders(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId) throws DatabaseException {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), getUserFromContext());

        List<Order> orders = orderManager.getOrders(uri(tenantId));

        return toOrderList(orders);
    }

    /**
     * Gets the list of orders for current user
     * 
     * @brief List Orders
     * @return a list of orders
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderList getUserOrders() throws DatabaseException {

        StorageOSUser user = getUserFromContext();

        List<Order> orders = orderManager.getUserOrders(user);

        return toOrderList(orders);
    }

    private Order getOrderById(URI id, boolean checkInactive) {
        Order order = orderManager.getOrderById(id);
        ArgValidator.checkEntity(order, id, isIdEmbeddedInURL(id), checkInactive);
        return order;
    }

    private OrderList toOrderList(List<Order> orders) {
        OrderList list = new OrderList();
        for (Order order : orders) {
            NamedRelatedResourceRep resourceRep = toNamedRelatedResource(ResourceTypeEnum.ORDER,
                    order.getId(), order.getLabel());
            list.getOrders().add(resourceRep);
        }
        return list;
    }

    /**
     * Deactivates the order
     * 
     * @param id the URN of an catalog order to be deactivated
     * @brief Deactivate Order
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deactivateOrder(@PathParam("id") URI id) throws DatabaseException {
        Order order = queryResource(id);
        ArgValidator.checkEntity(order, id, true);

        if (order.getScheduledEventId()!=null) {
            throw APIException.badRequests.scheduledOrderNotAllowed("deactivation");
        }

        orderManager.deleteOrder(order);

        auditOpSuccess(OperationTypeEnum.DELETE_ORDER, order.auditParameters());

        return Response.ok().build();
    }

    private void validateParameters(ServiceDescriptor descriptor, List<Parameter> parameters, Integer storageSize) {
        validateParameters(descriptor.getItems().values(), parameters, storageSize);
    }

    private void validateParameters(Collection<? extends ServiceItem> items, List<Parameter> parameters, Integer storageSize) {
        // Validate the parameters
        for (ServiceItem item : items) {
            if (item instanceof ServiceFieldTable) {
                validateParameters(((ServiceFieldTable) item).getItems().values(), parameters, storageSize);
            }
            else if (item instanceof ServiceFieldGroup) {
                validateParameters(((ServiceFieldGroup) item).getItems().values(), parameters, storageSize);
            }
            else if (item instanceof ServiceField) {
                ServiceField field = (ServiceField) item;

                String value = getFieldValue(field, parameters);
                for (String fieldValue : TextUtils.parseCSV(value)) {
                    ValidationUtils.validateField(storageSize, field, fieldValue);
                }
            }
        }
    }

    private String getFieldValue(ServiceField field, List<Parameter> parameters) {
        Parameter parameter = getParameter(field, parameters);
        if (parameter != null) {
            return parameter.getValue();
        }
        return null;
    }

    private Parameter getParameter(ServiceField field, List<Parameter> parameters) {
        for (Parameter param : parameters) {
            if (StringUtils.equals(field.getName(), param.getLabel())) {
                return param;
            }
        }
        return null;
    }

    private static Date getDateTimestamp(String timestampStr) {
        if (StringUtils.isBlank(timestampStr)) {
            return null;
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
            return dateFormat.parse(timestampStr);
        } catch (ParseException pe) {
            return getDateFromLong(timestampStr);
        }
    }

    private static Date getDateFromLong(String timestampStr) {
        try {
            return new Date(Long.parseLong(timestampStr));
        } catch (NumberFormatException n) {
            throw APIException.badRequests.invalidDate(timestampStr);
        }
    }

    public static class OrderResRepFilter<E extends RelatedResourceRep> extends ResRepFilter<E>
    {
        public OrderResRepFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            Order obj = _permissionsHelper.getObjectById(id, Order.class);
            if (obj == null) {
                return false;
            }
            if (obj.getTenant().toString().equals(_user.getTenantId())) {
                return true;
            }
            ret = isTenantAccessible(uri(obj.getTenant()));
            return ret;
        }
    }

    private void scheduleReoccurenceOrders() throws Exception {
        lock = coordinatorClient.getLock(LOCK_NAME);
        try {
            lock.acquire();

            List<ScheduledEvent> scheduledEvents = dataManager.getAllReoccurrenceEvents();
            for (ScheduledEvent event: scheduledEvents) {
                if (event.getEventStatus() != ScheduledEventStatus.APPROVED) {
                    log.debug("Skipping event {} which is not in APPROVED status.", event.getId());
                    continue;
                }

                URI orderId = event.getLatestOrderId();
                Order order = getOrderById(orderId, false);
                if (! (OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.SUCCESS) ||
                       OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.PARTIAL_SUCCESS) ||
                       OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.ERROR) ||
                       OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.CANCELLED)) ) {
                    log.debug("Skipping event {} whose latest order {} is not finished yet.", event.getId(), order.getId());
                    continue;
                }

                log.info("Trying to schedule a new order for event {} : {}", event.getId(),
                        ScheduleInfo.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(event.getScheduleInfo().getBytes(UTF_8))).toString());

                StorageOSUser user = StorageOSUser.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(event.getStorageOSUser().getBytes(UTF_8)));

                OrderCreateParam createParam = OrderCreateParam.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(event.getOrderCreationParam().getBytes(UTF_8)));
                ScheduleInfo scheduleInfo = ScheduleInfo.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(event.getScheduleInfo().getBytes(UTF_8)));
                Calendar nextScheduledTime = ScheduleTimeHelper.getNextScheduledTime(order.getScheduledTime(), scheduleInfo);

                int retry = 0;
                if (order.getExecutionWindowId() != null &&
                        !order.getExecutionWindowId().getURI().equals(ExecutionWindow.NEXT)) {
                    ExecutionWindow window = client.executionWindows().findById(order.getExecutionWindowId().getURI());
                    if (window != null) {
                        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);
                        if (nextScheduledTime!=null && !helper.isActive(nextScheduledTime)) {
                            log.warn("Execution window {} might be changed after the event is scheduled.", order.getExecutionWindowId().getURI());
                            log.warn("Otherwise it is a HOURLY scheduled event");

                            do {
                                nextScheduledTime = ScheduleTimeHelper.getNextScheduledTime(nextScheduledTime, scheduleInfo);
                                retry++;
                            } while (nextScheduledTime!=null && !helper.isActive(nextScheduledTime) && retry<ScheduleTimeHelper.SCHEDULE_TIME_RETRY_THRESHOLD);

                            if (retry == ScheduleTimeHelper.SCHEDULE_TIME_RETRY_THRESHOLD) {
                                log.error("Failed to find next scheduled time that match with {}", order.getExecutionWindowId().getURI());
                                nextScheduledTime = null;
                            }
                        }

                    } else {
                        log.error("Execution window {} does not exist.", order.getExecutionWindowId().getURI());
                    }
                }

                if (nextScheduledTime == null) {
                    log.info("Scheduled event {} should be set finished.", event.getId());
                    event.setEventStatus(ScheduledEventStatus.FINISHED);
                } else {
                    createParam.setScheduledTime(ScheduleTimeHelper.convertCalendarToStr(nextScheduledTime));

                    order = createNewOrder(user, uri(order.getTenant()), createParam);
                    orderManager.processOrder(order);

                    event.setLatestOrderId(order.getId());
                    log.info("Scheduled an new order {} for event {} ...", order.getId(), event.getId());
                }
                client.save(event);

            }
        } catch (Exception e) {
            log.error("Failed to schedule next orders", e);
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Error releasing order scheduler lock", e);
            }
        }
    }
}
