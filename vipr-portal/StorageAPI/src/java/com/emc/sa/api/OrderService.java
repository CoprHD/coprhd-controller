/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.OrderMapper.createNewObject;
import static com.emc.sa.api.mapper.OrderMapper.createOrderParameters;
import static com.emc.sa.api.mapper.OrderMapper.map;
import static com.emc.sa.api.mapper.OrderMapper.toExecutionLogList;
import static com.emc.sa.api.mapper.OrderMapper.toOrderLogList;
import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.db.client.URIUtil.asString;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.emc.sa.api.utils.OrderServiceJob;
import com.emc.sa.api.utils.OrderServiceJobConsumer;
import com.emc.sa.api.utils.OrderServiceJobSerializer;
import com.emc.sa.engine.scheduler.SchedulerDataManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.ScheduleTimeHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.db.client.util.ExecutionWindowHelper;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.systemservices.impl.logsvc.LogRequestParam;
import com.emc.vipr.model.catalog.*;
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
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.descriptor.ServiceFieldGroup;
import com.emc.sa.descriptor.ServiceFieldTable;
import com.emc.sa.descriptor.ServiceItem;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.model.EncryptionProvider;
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
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
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

    private static String ORDER_SERVICE_QUEUE_NAME="OrderService";

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
    private EncryptionProvider encryptionProvider;

    @Autowired
    private SchedulerDataManager dataManager;

    @Autowired
    private ModelClient client;

    private ScheduledExecutorService _executorService = new NamedScheduledThreadPoolExecutor("OrderScheduler", 1);

    private DistributedQueue<OrderServiceJob> queue;

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

        startJobQueue();
    }

    private void startJobQueue() {
        log.info("Starting oder service job queue");
        try {
            // no job consumer in geoclient
            queue = _coordinator.getQueue(ORDER_SERVICE_QUEUE_NAME, new OrderServiceJobConsumer(_dbClient, orderManager), new OrderServiceJobSerializer(), 1);
        } catch (Exception e) {
            log.error("can not startup geosvc job queue", e);
        }
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

        log.info("lbyc00");
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
            int maxCount = 6000;
            List<String> c= parameters.get(SearchConstants.ORDER_MAX_COUNT);
            if (c != null) {
                String maxCountParam = parameters.get(SearchConstants.ORDER_MAX_COUNT).get(0);
                maxCount = Integer.parseInt(maxCountParam);
            }

            log.info("lbyc0: maxCount={} startTime={}, endTime={}", maxCount, startTime, endTime);

            orders = orderManager.findOrdersByTimeRange(uri(tenantId), startTime, endTime, maxCount);
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

        ServiceDescriptor descriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), service.getBaseService());
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
            //orderManager.deleteOrder(order);
            orderManager.deleteOrder(order.getId(), "");
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
     * Get log data from the specified virtual machines that are filtered, merged,
     * and sorted based on the passed request parameters and streams the log
     * messages back to the client as JSON formatted strings.
     *
     * @brief Show logs from all or specified virtual machine
     * @param startTimeStr The start datetime of the desired time window. Value is
     *            inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or
     *            datetime in ms.
     *            Default: Set to yesterday same time
     * @param endTimeStr The end datetime of the desired time window. Value is
     *            inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or
     *            datetime in ms.
     * @prereq none
     * @return A reference to the StreamingOutput to which the log data is
     *         written.
     * @throws WebApplicationException When an invalid request is made.
     */
    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN })
    @Path("/export")
    public Response exportOrders( @QueryParam(LogRequestParam.START_TIME) String startTimeStr,
                               @QueryParam(LogRequestParam.END_TIME) String endTimeStr) throws Exception {
        log.info("lbyk:export orders startTime={}, endTime={}", startTimeStr, endTimeStr);

        // Validate the passed start and end times are valid.
        Date startTime = TimeUtils.getDateTimestamp(startTimeStr);
        Date endTime = TimeUtils.getDateTimestamp(endTimeStr);
        TimeUtils.validateTimestamps(startTime, endTime);
        log.info("Validated requested time window");

        // Setting default start time to yesterday
        if (startTime == null) {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DATE, -1);
            startTime = yesterday.getTime();
            log.info("Setting start time to yesterday {} ", startTime);
        }

        final long startTimeInMS = startTime.getTime();
        final long endTimeInMS = endTime.getTime();
        StreamingOutput out = new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {
                exportOrders(startTimeInMS, endTimeInMS, outputStream);
            }
        };

        return Response.ok(out).build();
    }

    private void exportOrders(long startTime, long endTime, OutputStream outputStream) {
        PrintStream out = new PrintStream(outputStream);
        AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getOrders(startTime, endTime);
        NamedElementQueryResultList ids = new NamedElementQueryResultList();
        _dbClient.queryByConstraint(constraint, ids);
        for (NamedElementQueryResultList.NamedElement namedID : ids) {
            URI id = namedID.getId();
            log.info("lbyh id={}", id);
            Order order = _dbClient.queryObject(Order.class, id);
            out.print(order.toString());
        }
    }

    /**
     * Gets the list of orders within a time range for current user
     *
     * @brief List Orders
     * @param startTimeStr
     * @param endTimeStr
     * @param maxCount The max number of orders this API returns
     * @return a list of orders
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderBulkRep getUserOrders(@DefaultValue("") @QueryParam(SearchConstants.START_TIME_PARAM) String startTimeStr,
                               @DefaultValue("") @QueryParam(SearchConstants.END_TIME_PARAM) String endTimeStr,
                               @DefaultValue("-1") @QueryParam(SearchConstants.ORDER_MAX_COUNT) String maxCount)
            throws DatabaseException {

        StorageOSUser user = getUserFromContext();

        log.info("lby0:starTime={} endTime={} maxCount={} user={}",
                new Object[] {startTimeStr, endTimeStr, maxCount, user.getName()});
        int max = Integer.parseInt(maxCount);

        Date startTime = new Date(0);
        if (!startTimeStr.isEmpty()) {
            startTime = getDateTimestamp(startTimeStr);
        }

        Date endTime = new Date(); // now by default

        if (!endTimeStr.isEmpty()) {
            endTime = getDateTimestamp(endTimeStr);
        }

        TimeUtils.validateTimestamps(startTime, endTime);

        long startTimeInMS= startTime.getTime();
        long endTimeInMS = endTime.getTime();

        log.info("lby00 start={} end={} max={}", startTimeInMS, endTimeInMS, max);
        List<Order> orders = orderManager.getUserOrders(user, startTimeInMS, endTimeInMS, max);
        log.info("lby0 done0");

        List<OrderRestRep> list = toOrders(orders, user);

        OrderBulkRep resp = new OrderBulkRep(list);
        log.info("lby0 done1");

        return resp;
    }

    private List<OrderRestRep> toOrders(List<Order> orders, StorageOSUser user) {

        List<OrderRestRep> orderList = new ArrayList();

        for (Order order : orders) {
            List<OrderParameter> orderParameters = orderManager.getOrderParameters(order.getId());
            orderList.add(map(order, orderParameters));
        }

        return orderList;
    }

    /**
     * Gets the number of orders within a time range for current user
     *
     * @brief Get number of orders created by current user
     * @param startTimeStr
     * @param endTimeStr
     * @return  number of orders
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/count")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderCount getUserOrderCount(@DefaultValue("") @QueryParam(SearchConstants.START_TIME_PARAM) String startTimeStr,
                                      @DefaultValue("") @QueryParam(SearchConstants.END_TIME_PARAM) String endTimeStr)
            throws DatabaseException {

        StorageOSUser user = getUserFromContext();

        log.info("lbyb0:starTime={} endTime={} maxCount={} user={}",
                new Object[] {startTimeStr, endTimeStr, user.getName()});

        long startTimeInMS = 0;
        long endTimeInMS = TimeUtils.getCurrentTime();

        if (!startTimeStr.isEmpty()) {
            Date startTime = getDateTimestamp(startTimeStr);
            startTimeInMS = startTime.getTime();
        }

        if (!endTimeStr.isEmpty()) {
            Date endTime = getDateTimestamp(endTimeStr);
            endTimeInMS = endTime.getTime();
        }

        /*
        ZonedDateTime now = ZonedDateTime.now();
        log.info("lbyt0: now={}", now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));

        if (!startTimeStr.isEmpty()) {
            ZonedDateTime startTime = ZonedDateTime.parse(startTimeStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            startTimeInMS = startTime.toInstant().toEpochMilli();
        }

        long endTimeInMS = System.currentTimeMillis();
        if (!endTimeStr.isEmpty()) {
            ZonedDateTime endTime = ZonedDateTime.parse(endTimeStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            endTimeInMS = endTime.toInstant().toEpochMilli();
        }
        */

        log.info("lbyb0 start={} end={}", startTimeInMS, endTimeInMS);

        if (startTimeInMS > endTimeInMS) {
            throw APIException.badRequests.endTimeBeforeStartTime(startTimeStr, endTimeStr);
        }

        long count = orderManager.getOrderCount(user, startTimeInMS, endTimeInMS);
        log.info("lbyb0 count={} done0", count);

        OrderCount resp = new OrderCount();
        resp.put(user.getName(), count);

        return resp;
    }

    /**
     * Gets the list of orders for current user
     *
     * @brief List Orders
     * @return a list of orders
     * @throws DatabaseException when a DB error occurs
     */
    /*
    @GET
    @Path("")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrderList getUserOrders() throws DatabaseException {

        StorageOSUser user = getUserFromContext();

        List<Order> orders = orderManager.getUserOrders(user, 0 , 0 , -1);
        OrderList list = toOrderList(orders);

        return list;
    }
    */

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
     *
     * @brief Deactivate Order
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @DELETE
    @Path("/remove")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deleteOrders(@DefaultValue("") @QueryParam(SearchConstants.START_TIME_PARAM) String startTime,
                                 @DefaultValue("") @QueryParam(SearchConstants.END_TIME_PARAM) String endTime,
                                 @DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenandID) {
        StorageOSUser user = getUserFromContext();

        log.info("lby0:starTime={} endTime={} tid={} user={}", new Object[] {startTime, endTime, tenandID, user.getName()});

        long now = System.currentTimeMillis();
        long startTimeInMacros = startTime.isEmpty() ? 0 : Long.parseLong(startTime)*1000;
        long endTimeInMacros = startTime.isEmpty() ? now : Long.parseLong(endTime)*1000;

        OrderServiceJob job = new OrderServiceJob(startTimeInMacros, endTimeInMacros, tenandID);
        try {
            queue.put(job);
        }catch (Exception e) {
            String errMsg = String.format("Failed to put the job into the queue %s", ORDER_SERVICE_QUEUE_NAME);
            log.error("{} e=", errMsg, e);
            APIException.internalServerErrors.genericApisvcError(errMsg, e);
        }

        /*
        AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getOrders(user.getName(), startTimeInMacros, endTimeInMacros, true);
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();
        long matchedCount = 0;
        _dbClient.queryByConstraint(constraint, queryResults);

        for (NamedElementQueryResultList.NamedElement e : queryResults) {
            matchedCount++;
        }

        log.info("lbyg0: {} to be deleted", matchedCount);
        */
        /*
        Order order = queryResource(id);
        ArgValidator.checkEntity(order, id, true);

        if (order.getScheduledEventId()!=null) {
            throw APIException.badRequests.scheduledOrderNotAllowed("deactivation");
        }

        orderManager.deleteOrder(order);

        auditOpSuccess(OperationTypeEnum.DELETE_ORDER, order.auditParameters());
        */

        return Response.ok().build();
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
        log.info("lbyh0: id={}", id);

        Order order = queryResource(id);
        log.info("lbyh0 order={}", order);

        orderManager.deleteOrder(id, "");

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
