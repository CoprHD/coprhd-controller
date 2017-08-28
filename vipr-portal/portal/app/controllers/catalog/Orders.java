/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.joda.time.DateTime;

import com.emc.sa.util.TextUtils;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.Tags;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.TaskUtil;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.emc.vipr.model.catalog.ScheduledEventCreateParam;
import com.emc.vipr.model.catalog.ScheduledEventRestRep;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.Tasks;
import controllers.Tasks.WorkflowStep;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.resources.AffectedResources;
import controllers.resources.AffectedResources.ResourceDetails;
import controllers.security.Security;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;
import models.BreadCrumb;
import models.datatable.OrderDataTable;
import models.datatable.OrderDataTable.OrderInfo;
import models.datatable.RecentOrdersDataTable;
import play.Logger;
import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Http;
import play.mvc.Util;
import play.mvc.With;
import util.BourneUtil;
import util.CatalogServiceUtils;
import util.MessagesUtils;
import util.ModelExtensions;
import util.OrderUtils;
import util.StringOption;
import util.TagUtils;
import util.api.ApiMapperUtils;
import util.datatable.DataTableParams;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class Orders extends OrderExecution {
    private static final int SHORT_DELAY = 1000;
    private static final int NORMAL_DELAY = 3000;
    private static final int LONG_DELAY = 15000;
    private static final int DEFAULT_DELAY = 60000;
    private static final int RECEIPT_UPDATE_ATTEMPTS = 5;

    public static final String RECENT_ACTIVITIES = "VIPRUI_RECENT_ACTIVITIES";
    public static final int MAX_RECENT_SERVICES = 4;

    private static void addMaxDaysRenderArgs() {
        Integer maxDays = params.get("maxDays", Integer.class);
        if (maxDays == null) {
            if (StringUtils.isNotEmpty(params.get("startDate"))
                    && StringUtils.isNotEmpty(params.get("endDate"))) {
                renderArgs.put("startDate", params.get("startDate"));
                renderArgs.put("endDate", params.get("endDate"));
                maxDays = 0;
            } else {
                maxDays = 1;
            }
        }
        int[] days = { 1, 7, 14, 30, 90, 0 };
        List<StringOption> options = Lists.newArrayList();
        options.add(new StringOption(String.valueOf(maxDays), MessagesUtils.get("orders.nDays", maxDays)));
        for (int day : days) {
            if (day == maxDays) {
                options.remove(0);
            }
            options.add(new StringOption(String.valueOf(day), MessagesUtils.get("orders." + day + "days")));
        }

        renderArgs.put("offsetInMinutes", params.get("offsetInMinutes"));
        renderArgs.put("maxDays", maxDays);
        renderArgs.put("dateDaysAgo", OrderDataTable.getDateDaysAgo(maxDays));
        renderArgs.put("maxDaysOptions", options);
    }

    @Restrictions({ @Restrict("TENANT_ADMIN") })
    public static void allOrders() {
        //TODO should get client time zone from request, currently we get it from javascript method
        RecentUserOrdersDataTable dataTable = new RecentUserOrdersDataTable();
        TenantSelector.addRenderArgs();

        dataTable.setByStartEndDateOrMaxDays(params.get("startDate"), params.get("endDate"),
                params.get("maxDays", Integer.class));
        Long orderCount = dataTable.fetchCount().getCounts().get(Models.currentAdminTenant());
        renderArgs.put("orderCount", orderCount);
        if (orderCount > OrderDataTable.ORDER_MAX_COUNT) {
            flash.put("warning", MessagesUtils.get("orders.warning", orderCount, OrderDataTable.ORDER_MAX_COUNT));
        }
        String deleteStatus = dataTable.getDeleteJobStatus();
        if (deleteStatus != null) {
            flash.put("info", deleteStatus);
            renderArgs.put("disableDeleteAllAndDownload", 1);
        } else {
            String downloadStatus = dataTable.getDownloadJobStatus();
            if (downloadStatus != null) {
                flash.put("info", downloadStatus);
                renderArgs.put("disableDeleteAllAndDownload", 1);
            }
        }
        renderArgs.put("canBeDeletedStatuses", RecentOrdersDataTable.getCanBeDeletedOrderStatuses());

        addMaxDaysRenderArgs();
        Common.copyRenderArgsToAngular();
        render(dataTable);
    }

    @Restrictions({ @Restrict("TENANT_ADMIN") })
    public static void allOrdersJson(Integer maxDays) {
        DataTableParams dataTableParams = DataTablesSupport.createParams(params);
        RecentUserOrdersDataTable dataTable = new RecentUserOrdersDataTable();
        dataTable.setByStartEndDateOrMaxDays(params.get("startDate"), params.get("endDate"), maxDays);
        renderJSON(DataTablesSupport.createJSON(dataTable.fetchData(dataTableParams), params));
    }

    public static void list() {
        OrderDataTable dataTable = new OrderDataTable(Models.currentTenant(), NumberUtils.toInt(params.get("offsetInMinutes"), 0));
        dataTable.setUserInfo(Security.getUserInfo());
        dataTable.setByStartEndDateOrMaxDays(params.get("startDate"), params.get("endDate"),
                params.get("maxDays", Integer.class));
        Long orderCount = dataTable.fetchCount().getCounts().entrySet().iterator().next().getValue();
        if (orderCount > OrderDataTable.ORDER_MAX_COUNT) {
            flash.put("warning", MessagesUtils.get("orders.warning", orderCount, OrderDataTable.ORDER_MAX_COUNT));
        }
        addMaxDaysRenderArgs();
        Common.copyRenderArgsToAngular();
        render(dataTable);
    }

    public static void listJson() {
        OrderDataTable dataTable = new OrderDataTable(Models.currentTenant(), NumberUtils.toInt(params.get("offsetInMinutes"), 0));
        dataTable.setUserInfo(Security.getUserInfo());
        dataTable.setByStartEndDateOrMaxDays(params.get("startDate"), params.get("endDate"),
                params.get("maxDays", Integer.class));

        renderJSON(DataTablesSupport.createJSON(dataTable.fetchAll(), params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<OrderInfo> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    OrderRestRep order = OrderUtils.getOrder(uri(id));
                    if (order != null) {
                        Models.checkAccess(order.getTenant());
                        results.add(new OrderInfo(order));
                    }
                }
            }
        }
        renderJSON(results);
    }

    @FlashException(value = "allOrders")
    @Restrictions({ @Restrict("TENANT_ADMIN") })
    public static void deleteOrders(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            List<URI> uris = Lists.newArrayList();
            for (String id : ids) {
                uris.add(uri(id));
            }
            OrderUtils.deactivateOrders(uris);
        } else {
            RecentUserOrdersDataTable dataTable = new RecentUserOrdersDataTable();
            dataTable.setByStartEndDateOrMaxDays(params.get("startDate"), params.get("endDate"), params.get("maxDays", Integer.class));
            dataTable.deleteOrders();
        }
        flash.success(MessagesUtils.get("orders.delete.submitted"));
        redirect(Common.toSafeRedirectURL("/catalog.orders/allorders?"+request.querystring));
    }

    @FlashException(value = "allOrders")
    @Restrictions({ @Restrict("TENANT_ADMIN") })
    public static void downloadOrders(String ids) {
        RecentUserOrdersDataTable dataTable = new RecentUserOrdersDataTable();
        dataTable.downloadOrders(params.get("startDate"), params.get("endDate"), params.get("maxDays", Integer.class), ids);
    }

    @FlashException(referrer = { "receiptContent" })
    public static void rollbackTask(String orderId, String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.tasks().rollback(uri(taskId));
            flash.put("info", MessagesUtils.get("resources.tasks.rollbackMessage", taskId));
        }
        receipt(orderId);
    }

    @FlashException(referrer = { "receiptContent" })
    public static void retryTask(String orderId, String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.tasks().resume(uri(taskId));
            flash.put("info", MessagesUtils.get("resources.tasks.retryMessage", taskId));
        }
        receipt(orderId);
    }

    @FlashException(referrer = { "receiptContent" })
    public static void resumeTask(String orderId, String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.tasks().resume(uri(taskId));
            flash.put("info", MessagesUtils.get("resources.tasks.resumeMessage", taskId));
        }
        receipt(orderId);
    }

    /**
     * Resubmits an order, creating a new copy with the same parameters.
     * 
     * @param orderId
     *            the order ID.
     */
    public static void resubmitOrder(@Required String orderId) {
        checkAuthenticity();
        OrderRestRep order = OrderUtils.getOrder(uri(orderId));
        try {
            addParametersToFlash(order);
        } catch (Exception e) {
            Logger.error(e, MessagesUtils.get("order.submitFailedWithDetail", e.getMessage()));
            flash.error(MessagesUtils.get("order.submitFailedWithDetail", e.getMessage()));
            Common.handleError();
        }

        Services.showForm(order.getCatalogService().getId().toString(), null, null);
    }

    @Util
    private static void addParametersToFlash(OrderRestRep order) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(order.getCatalogService().getId().toString()));
        HashMap<String, String> tableParams = new HashMap<String, String>();

        if (service==null || service.getServiceDescriptor()==null){
            flash.error("order.submitFailedWithDetail", " The Workflow or Service Descriptor is deleted");
            Logger.error("Service Descriptor not found");
            throw new IllegalStateException("No Service Descriptor found. Might be Customservices Workflow  is deleted ");
        }

        for (ServiceItemRestRep item : service.getServiceDescriptor().getItems()) {
            if (item.isTable()) {
                for (ServiceFieldRestRep tableItem : ((ServiceFieldTableRestRep) item).getItems()) {
                    tableParams.put(tableItem.getName(), item.getName());
                }
            }
        }

        for (Parameter parameter : order.getParameters()) {
            // Do not add encrypted values to the flash scope
            if (parameter.isEncrypted()) {
                continue;
            }
            List<String> values = TextUtils.parseCSV(parameter.getValue());
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                String name = parameter.getLabel();
                if (tableParams.containsKey(name)) {
                    name = tableParams.get(name) + "[" + i + "]." + name;
                }
                flash.put(name, value);
            }
        }
    }

    public static void submitOrder(String serviceId) {
        checkAuthenticity();

        OrderCreateParam order = createAndValidateOrder(serviceId);
        String status = null;
        String orderId = null;
        try {
            if (isSchedulerEnabled()) {
                ScheduledEventCreateParam event = createScheduledOrder(order);
                if (Validation.hasErrors()) {
                    Validation.keep();
                    Common.flashParamsExcept("json", "body");
                    Services.showForm(serviceId, null, null);
                }
                ScheduledEventRestRep submittedEvent = getCatalogClient().orders().submitScheduledEvent(event);
                status = submittedEvent.getEventStatus();
                orderId = submittedEvent.getLatestOrderId().toString();
            } else {
                OrderRestRep submittedOrder = getCatalogClient().orders().submit(order);
                status = submittedOrder.getOrderStatus();
                orderId = submittedOrder.getId().toString();
            }
        } catch (Exception e) {
            Logger.error(e, MessagesUtils.get("order.submitFailedWithDetail", e.getMessage()));
            flash.error(MessagesUtils.get("order.submitFailedWithDetail", e.getMessage()));
            Common.handleError();
        }

        if (OrderRestRep.ERROR.equalsIgnoreCase(status)) {
            flash.error(MessagesUtils.get("order.submitFailed"));
        } else {
            flash.success(MessagesUtils.get("order.submitSuccess"));
        }

        Http.Cookie cookie = request.cookies.get(RECENT_ACTIVITIES);
        response.setCookie(RECENT_ACTIVITIES, updateRecentActivitiesCookie(cookie, serviceId));

        receipt(orderId);
    }

    @Util
    public static OrderCreateParam createAndValidateOrder(String serviceId) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
        ServiceDescriptorRestRep descriptor = service.getServiceDescriptor();

        if (descriptor == null){
            flash.error("order.submitFailedWithDetail", " The Workflow or Service Descriptor is deleted");
            Logger.error("Service Descriptor not found");
            throw new IllegalStateException("No Service Descriptor found. Might be Customservices Workflow  is deleted ");
        }
        // Filter out actual Service Parameters
        Map<String, String> parameters = parseParameters(service, descriptor);
        if (Validation.hasErrors()) {
            Validation.keep();
            Common.flashParamsExcept("json", "body");
            Services.showForm(serviceId, null, null);
        }

        return createOrder(service, descriptor, parameters);
    }

    public static void receipt(String orderId) {
        OrderDetails details = new OrderDetails(orderId);
        Models.checkAccess(details.order.getTenant());
        fetchData(details);
        ServiceDescriptorRestRep descriptor = details.catalogService.getServiceDescriptor();
        addBreadCrumbToRenderArgs(id(details.order.getTenant()), details.catalogService);
        render(orderId, details, descriptor);
    }

    private static void addBreadCrumbToRenderArgs(URI tenant, CatalogServiceRestRep service) {
        List<BreadCrumb> breadcrumbs = ServiceCatalog.createBreadCrumbs(tenant.toString(), service);
        renderArgs.put("breadcrumbs", breadcrumbs);
    }

    public static void receiptContent(String orderId, Long lastUpdated) {
        OrderDetails details = waitForUpdatedOrder(orderId, lastUpdated);
        Models.checkAccess(details.order.getTenant());
        fetchData(details);
        render(orderId, details);
    }

    /**
     * Waits for an update to the order. The lastUpdated value is specified by the receipt page and only once the
     * order has been updated more recently than that are the detail returned.
     * 
     * @param orderId the order ID.
     * @param lastUpdated the last updated time.
     * @return the order details.
     */
    private static OrderDetails waitForUpdatedOrder(String orderId, Long lastUpdated) {
        if (lastUpdated == null) {
            Logger.debug("No last updated value");
            return new OrderDetails(orderId);
        }

        Map<URI, String> oldTasksStateMap = null;
        int updateAttempts = 0;

        // Wait for an update to the order
        while (true) {
            OrderDetails details = new OrderDetails(orderId);

            if (details.isNewer(lastUpdated)) {
                Logger.debug("Found update for order %s newer than: %s", details.order.getOrderNumber(), lastUpdated);
                return details;
            }
            if (details.isFinished()) {
                Logger.debug("Found finished order %s", details.order.getOrderNumber());
                return details;
            }

            if (oldTasksStateMap != null && details.viprTasks != null) {
                if (isTaskStateChanged(oldTasksStateMap, details.viprTasks)) {
                    Logger.debug("Found task state change for order %s", details.order.getOrderNumber());
                    return details;
                }
            } else {
                oldTasksStateMap = createTaskStateMap(details.viprTasks);
            }

            if (++updateAttempts >= RECEIPT_UPDATE_ATTEMPTS) {
                Logger.debug("Updating order %s after %d attempts to find order change", details.order.getOrderNumber(),
                        RECEIPT_UPDATE_ATTEMPTS);
                return details;
            }

            // Pause and check again, delay is based on order state
            int delay = getWaitDelay(details);
            Logger.debug("No update for order %s, waiting for %s ms", details.order.getOrderNumber(), delay);
            await(delay);
        }
    }

    private static Map<URI, String> createTaskStateMap(List<TaskResourceRep> tasks) {
        Map<URI, String> taskMap = Maps.newHashMap();
        for (TaskResourceRep task : tasks) {
            taskMap.put(task.getId(), task.getState());
        }
        return taskMap;
    }

    private static boolean isTaskStateChanged(Map<URI, String> oldTasksStateMap, List<TaskResourceRep> viprTasks) {
        Map<URI, String> currentTaskStateMap = createTaskStateMap(viprTasks);
        return !Maps.difference(oldTasksStateMap, currentTaskStateMap).areEqual();
    }

    private static int getWaitDelay(OrderDetails details) {
        OrderStatus status = OrderStatus.valueOf(details.order.getOrderStatus());
        switch (status) {
            case PENDING:
            case APPROVED:
                // Pending and approved will be quick transitions
                return SHORT_DELAY;
            case EXECUTING:
                // Order is executing, normal delay
                return NORMAL_DELAY;
            case SCHEDULED:
            case APPROVAL:
                // Order is waiting, long delay
                return LONG_DELAY;
            default:
                return DEFAULT_DELAY;
        }
    }

    private static String updateRecentActivitiesCookie(Http.Cookie cookie, String serviceId) {
        List<String> ids = Lists.newArrayList();
        if (cookie != null && cookie.value != null) {
            ids.addAll(Arrays.asList(cookie.value.split(",")));
            if (ids.contains(serviceId)) {
                ids.remove(serviceId);
            }
        }
        ids.add(0, serviceId);
        while (ids.size() > MAX_RECENT_SERVICES) {
            ids.remove(ids.size() - 1);
        }
        return StringUtils.join(ids, ",");
    }

    /**
     * Fetches the remaining data for the order.
     */
    protected static void fetchData(OrderDetails details) {
        details.catalogService = CatalogServiceUtils.getCatalogService(details.order.getCatalogService());

        if (details.executionState != null) {
            details.affectedResources = Lists.newArrayList();
            for (String affectedResourceId : details.executionState.getAffectedResources()) {
                ResourceDetails resourceDetails = AffectedResources.resourceDetails(affectedResourceId);
                if (resourceDetails != null) {
                    details.affectedResources.add(resourceDetails);
                }
            }
            Collections.sort(details.affectedResources, RESOURCE_COMPARATOR);
        }
    }

    public static class OrderDetails {
        public Long lastUpdated;
        public OrderRestRep order;
        public ApprovalRestRep approval;
        public CatalogServiceRestRep catalogService;
        public List<Parameter> orderParameters;
        public ExecutionStateRestRep executionState;
        public List<OrderLogRestRep> logs;
        public List<ExecutionLogRestRep> precheckTaskLogs;
        public List<ExecutionLogRestRep> executeTaskLogs;
        public List<ExecutionLogRestRep> rollbackTaskLogs;
        public List<ResourceDetails> affectedResources;
        public Tags tags;
        public List<TaskResourceRep> viprTasks;
        public ScheduledEventRestRep scheduledEvent;
        public Date scheduleStartDateTime;

        public Map<URI, String> viprTaskStepMessages;

        public Set<String> viprTaskWarningMessages;

        public OrderDetails(String orderId) {
            order = OrderUtils.getOrder(uri(orderId));
            orderParameters = order.getParameters();
            if (order == null) {
                return;
            }
            checkLastUpdated(order);

            approval = getCatalogClient().approvals().search().byOrderId(uri(orderId)).first();
            checkLastUpdated(approval);
            tags = ApiMapperUtils.getTags(order);
            ViPRCoreClient client = getViprClient();
            List<SearchResultResourceRep> searchResults = client.tasks().performSearchBy("tag", TagUtils.createOrderIdTag(orderId));
            viprTasks = client.tasks().getByRefs(searchResults);
            setTaskStepMessages();
            setTaskWarningMessages();

            checkLastUpdated(viprTasks);

            executionState = OrderUtils.getExecutionState(order.getId());
            if (executionState != null) {
                checkLastUpdated(executionState);

                logs = OrderUtils.getOrderLogs(order.getId());
                List<ExecutionLogRestRep> taskLogs = OrderUtils.getExecutionLogs(order.getId());

                precheckTaskLogs = Lists.newArrayList();
                executeTaskLogs = Lists.newArrayList();
                rollbackTaskLogs = Lists.newArrayList();
                for (ExecutionLogRestRep log : taskLogs) {
                    if (ModelExtensions.isPrecheck(log)) {
                        precheckTaskLogs.add(log);
                    } else if (ModelExtensions.isExecute(log)) {
                        executeTaskLogs.add(log);
                    } else if (ModelExtensions.isRollback(log)) {
                        rollbackTaskLogs.add(log);
                    }
                    checkLastUpdated(log);
                }
            }
            URI scheduledEventId = order.getScheduledEventId();
            if (scheduledEventId != null) {
                scheduledEvent = getCatalogClient().orders().getScheduledEvent(scheduledEventId);
                String isoDateTimeStr = String.format("%sT%02d:%02d:00Z",
                        scheduledEvent.getScheduleInfo().getStartDate(),
                        scheduledEvent.getScheduleInfo().getHourOfDay(),
                        scheduledEvent.getScheduleInfo().getMinuteOfHour());
                DateTime startDateTime = DateTime.parse(isoDateTimeStr);
                scheduleStartDateTime = startDateTime.toDate();
            }
        }

        private void setTaskWarningMessages() {
            viprTaskWarningMessages = Sets.newHashSet();
            for (TaskResourceRep task : viprTasks) {
                if (task != null && task.getWarningMessages() != null && !task.getWarningMessages().isEmpty()) {
                    viprTaskWarningMessages.addAll(task.getWarningMessages());
                }
            }
        }

        private void setTaskStepMessages() {
            viprTaskStepMessages = Maps.newHashMap();
            for (TaskResourceRep task : viprTasks) {
                if (task.getWorkflow() != null && TaskUtil.isSuspended(task)) {
                    List<WorkflowStep> steps = Tasks.getWorkflowSteps(task.getWorkflow().getId());
                    String message = "";
                    for (WorkflowStep step : steps) {
                        if (TaskUtil.isSuspended(task) && step.isSuspended()) {
                            message += step.message;
                        }
                    }
                    viprTaskStepMessages.put(task.getId(), message);
                }
            }
        }

        private void checkLastUpdated(OrderRestRep obj) {
            if ((obj != null) && (obj.getLastUpdated() != null)) {
                long updated = obj.getLastUpdated().getTime();
                if ((lastUpdated == null) || (lastUpdated < updated)) {
                    lastUpdated = updated;
                }
            }
        }

        private void checkLastUpdated(ExecutionStateRestRep obj) {
            if ((obj != null) && (obj.getLastUpdated() != null)) {
                long updated = obj.getLastUpdated().getTime();
                if ((lastUpdated == null) || (lastUpdated < updated)) {
                    lastUpdated = updated;
                }
            }
        }

        private void checkLastUpdated(ExecutionLogRestRep obj) {
            if ((obj != null) && (obj.getLastUpdated() != null)) {
                long updated = obj.getLastUpdated().getTime();
                if ((lastUpdated == null) || (lastUpdated < updated)) {
                    lastUpdated = updated;
                }
            }
        }

        private void checkLastUpdated(ApprovalRestRep approval) {
            if ((approval != null) && (approval.getDateActioned() != null)) {
                long updated = approval.getDateActioned().getTime();
                if ((lastUpdated == null) || (lastUpdated < updated)) {
                    lastUpdated = updated;
                }
            }
        }

        private void checkLastUpdated(List<TaskResourceRep> tasks) {
            for (TaskResourceRep task : tasks) {
                if ((task != null) && (task.getStartTime() != null)) {
                    long updated = task.getStartTime().getTimeInMillis();
                    if ((lastUpdated == null) || (lastUpdated < updated)) {
                        lastUpdated = updated;
                    }
                }

                if ((task != null) && (task.getEndTime() != null)) {
                    long updated = task.getEndTime().getTimeInMillis();
                    if ((lastUpdated == null) || (lastUpdated < updated)) {
                        lastUpdated = updated;
                    }
                }

            }
        }

        /**
         * Determines if this has been updated more recently than the given time.
         * 
         * @param time
         *            the time.
         * @return true if this has been updated more recently.
         */
        public boolean isNewer(long time) {
            return (lastUpdated != null) && (lastUpdated > time);
        }

        /**
         * Determines if the order is in a finished state (whether successful or not).
         * 
         * @return true if the order is finished.
         */
        public boolean isFinished() {
            try {
                OrderStatus status = OrderStatus.valueOf(order.getOrderStatus());
                switch (status) {
                    case CANCELLED:
                    case PARTIAL_SUCCESS:
                    case REJECTED:
                    case SUCCESS:
                    case ERROR:
                        return true;
                    default:
                        return false;
                }
            } catch (RuntimeException e) {
                return false;
            }
        }

        public ScheduledEventRestRep getScheduledEvent() {
            return scheduledEvent;
        }
    }

    public static final Comparator<ResourceDetails> RESOURCE_COMPARATOR = new Comparator<ResourceDetails>() {
        @Override
        public int compare(ResourceDetails o1, ResourceDetails o2) {
            return o1.resourceId.compareTo(o2.resourceId);
        }
    };

    protected static class RecentUserOrdersDataTable extends RecentOrdersDataTable {
        public RecentUserOrdersDataTable() {
            super(Models.currentAdminTenant(), NumberUtils.toInt(params.get("offsetInMinutes"), 0));
            alterColumn("submittedBy").setVisible(true);
        }
    }
}
