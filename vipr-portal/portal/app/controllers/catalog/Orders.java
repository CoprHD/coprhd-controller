/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static util.BourneUtil.getCatalogClient;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.util.TextUtils;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;

import models.BreadCrumb;
import models.datatable.OrderDataTable;
import models.datatable.OrderDataTable.OrderInfo;
import models.datatable.RecentOrdersDataTable;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Http;
import play.mvc.Util;
import play.mvc.With;
import util.CatalogServiceUtils;
import util.MessagesUtils;
import util.ModelExtensions;
import util.OrderUtils;
import util.StringOption;
import util.TagUtils;
import util.api.ApiMapperUtils;
import util.datatable.DataTableParams;
import util.datatable.DataTablesSupport;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.Tags;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.resources.AffectedResources;
import controllers.resources.AffectedResources.ResourceDetails;
import controllers.security.Security;
import controllers.tenant.TenantSelector;
import controllers.util.Models;

@With(Common.class)
public class Orders extends OrderExecution {
    private static final int SHORT_DELAY = 1000;
    private static final int NORMAL_DELAY = 3000;
    private static final int LONG_DELAY = 15000;
    private static final int DEFAULT_DELAY = 60000;

    public static final String RECENT_ACTIVITIES = "VIPRUI_RECENT_ACTIVITIES";
    public static final int MAX_RECENT_SERVICES = 4;

    private static void addMaxDaysRenderArgs() {
        Integer maxDays = params.get("maxDays", Integer.class);
        if (maxDays == null) {
            maxDays = 1;
        }
        int[] days = { 1, 7, 14, 30, 90 };
        List<StringOption> options = Lists.newArrayList();
        options.add(new StringOption(String.valueOf(maxDays), MessagesUtils.get("orders.nDays", maxDays)));
        for (int day : days) {
            if (day == maxDays) {
                options.remove(0);
            }
            options.add(new StringOption(String.valueOf(day), MessagesUtils.get("orders." + day + "days")));
        }

        renderArgs.put("maxDays", maxDays);
        renderArgs.put("maxDaysOptions", options);
    }

    @Restrictions({ @Restrict("TENANT_ADMIN") })
    public static void allOrders() {
        RecentUserOrdersDataTable dataTable = new RecentUserOrdersDataTable();
        TenantSelector.addRenderArgs();
        addMaxDaysRenderArgs();
        render(dataTable);
    }

    @Restrictions({ @Restrict("TENANT_ADMIN") })
    public static void allOrdersJson(Integer maxDays) {
        DataTableParams dataTableParams = DataTablesSupport.createParams(params);
        RecentUserOrdersDataTable dataTable = new RecentUserOrdersDataTable();
        dataTable.setMaxAgeInDays(maxDays != null ? Math.max(maxDays, 1) : 1);
        renderJSON(DataTablesSupport.createJSON(dataTable.fetchData(dataTableParams), params));
    }

    public static void list() {
        OrderDataTable dataTable = new OrderDataTable(Models.currentTenant());
        dataTable.setUserInfo(Security.getUserInfo());
        render(dataTable);
    }

    public static void listJson() {
        OrderDataTable dataTable = new OrderDataTable(Models.currentTenant());
        dataTable.setUserInfo(Security.getUserInfo());
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

    /**
     * Resubmits an order, creating a new copy with the same parameters.
     * 
     * @param orderId
     *            the order ID.
     */
    public static void resubmitOrder(@Required String orderId) {
        checkAuthenticity();
        OrderRestRep order = OrderUtils.getOrder(uri(orderId));
        addParametersToFlash(order);
        Services.showForm(order.getCatalogService().getId().toString());
    }

    @Util
    private static void addParametersToFlash(OrderRestRep order) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(order.getCatalogService().getId().toString()));
        HashMap<String, String> tableParams = new HashMap<String, String>();

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
        OrderRestRep submittedOrder = null;
        try {
            submittedOrder = getCatalogClient().orders().submit(order);
        } catch (Exception e) {
            Logger.error(e, MessagesUtils.get("order.submitFailed"));
            flash.error(MessagesUtils.get("order.submitFailed"));
            Common.handleError();
        }

        if (OrderRestRep.ERROR.equalsIgnoreCase(submittedOrder.getOrderStatus())) {
            flash.error(MessagesUtils.get("order.submitFailed"));
        }
        else {
            flash.success(MessagesUtils.get("order.submitSuccess"));
        }

        Http.Cookie cookie = request.cookies.get(RECENT_ACTIVITIES);
        response.setCookie(RECENT_ACTIVITIES, updateRecentActivitiesCookie(cookie, serviceId));

        String orderId = submittedOrder.getId().toString();
        receipt(orderId);
    }

    @Util
    public static OrderCreateParam createAndValidateOrder(String serviceId) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
        ServiceDescriptorRestRep descriptor = service.getServiceDescriptor();

        // Filter out actual Service Parameters
        Map<String, String> parameters = parseParameters(service, descriptor);
        if (Validation.hasErrors()) {
            Validation.keep();
            Common.flashParamsExcept("json", "body");
            Services.showForm(serviceId);
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
    
    public static void pauseOrder(String orderId, Long lastUpdated){
        
        try {
            getCatalogClient().orders().pauseOrder(uri(orderId));
        } catch (Exception e) {
            Logger.error(e, MessagesUtils.get("order.pausedFailed"));
            flash.error(MessagesUtils.get("order.pausedFailed"));
            Common.handleError();
        }
        receiptContent(orderId,lastUpdated);       
    }
    
    public static void resumeOrder(String orderId, Long lastUpdated){
        
        try {
            getCatalogClient().orders().resumeOrder(uri(orderId));
        } catch (Exception e) {
            Logger.error(e, MessagesUtils.get("order.resumeFailed"));
            flash.error(MessagesUtils.get("order.resumeFailed"));
            Common.handleError();
        }
        receiptContent(orderId,lastUpdated);       
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

            // Pause and check again, delay is based on order state
            int delay = getWaitDelay(details);
            Logger.debug("No update for order %s, waiting for %s ms", details.order.getOrderNumber(), delay);
            await(delay);
        }
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
                    }
                    else if (ModelExtensions.isExecute(log)) {
                        executeTaskLogs.add(log);
                    }
                    else if (ModelExtensions.isRollback(log)) {
                        rollbackTaskLogs.add(log);
                    }
                    checkLastUpdated(log);
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
    }

    public static final Comparator<ResourceDetails> RESOURCE_COMPARATOR = new Comparator<ResourceDetails>() {
        @Override
        public int compare(ResourceDetails o1, ResourceDetails o2) {
            return o1.resourceId.compareTo(o2.resourceId);
        }
    };

    protected static class RecentUserOrdersDataTable extends RecentOrdersDataTable {
        public RecentUserOrdersDataTable() {
            super(Models.currentAdminTenant());
            alterColumn("submittedBy").setVisible(true);
        }
    }
}
