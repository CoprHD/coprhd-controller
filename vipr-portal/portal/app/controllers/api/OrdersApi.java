/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.api;

import static com.emc.vipr.client.catalog.impl.SearchConstants.END_TIME_PARAM;
import static com.emc.vipr.client.catalog.impl.SearchConstants.START_TIME_PARAM;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static render.RenderApiModel.renderApi;
import static util.BourneUtil.getCatalogClient;
import static util.api.ApiMapperUtils.newExecutionInfo;
import static util.api.ApiMapperUtils.newOrderInfo;
import static util.api.ApiMapperUtils.newOrderReference;

import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.emc.storageos.services.util.TimeUtils;
import org.apache.commons.lang.StringUtils;

import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.With;
import util.api.ApiMapperUtils;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TagAssignment;
import com.emc.storageos.model.search.Tags;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderInfo;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Reference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.security.Security;

/**
 * Approvals API
 *
 * @author Chris Dail
 */
@With(Common.class)
public class OrdersApi extends Controller {
    private static final int DEFAULT_MAX_BULK_SIZE = 1000;

    public static void orders() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        List<Reference> orders = Lists.newArrayList();
        for (NamedRelatedResourceRep element : catalog.orders().listByUserTenant()) {
            orders.add(newOrderReference(element.getId().toString()));
        }
        renderApi(orders);
    }

    @Restrict("TENANT_ADMIN")
    public static void allOrders(String startTime, String endTime) {
        List<? extends RelatedResourceRep> elements = queryOrders(startTime, endTime);
        List<Reference> orders = Lists.newArrayList();
        for (RelatedResourceRep element : elements) {
            orders.add(newOrderReference(element.getId().toString()));
        }
        renderApi(orders);
    }

    public static void order(String orderId) {
        OrderRestRep order = getCatalogClient().orders().get(uri(orderId));
        renderApi(newOrderInfo(order));
    }

    public static void updateTags(String orderId, TagAssignment assignment) {
        updateOrderTags(uri(orderId), assignment);
        OrderRestRep order = getOrder(orderId);
        renderApi(newOrderInfo(order));
    }

    public static void retrieveTags(String orderId) {
        OrderRestRep order = getOrder(orderId);
        Tags tags = ApiMapperUtils.getTags(order);
        renderApi(tags);
    }

    public static void bulkGetOrders(String startTime, String endTime) {
        List<? extends RelatedResourceRep> elements = queryOrders(startTime, endTime);
        List<URI> orders = Lists.newArrayList();
        for (RelatedResourceRep element : elements) {
            orders.add(element.getId());
        }
        renderApi(new BulkIdParam(orders));
    }

    public static void bulkOrders(BulkIdParam param) {
        if (param == null || param.getIds().size() > DEFAULT_MAX_BULK_SIZE) {
            badRequest();
        }

        List<OrderInfo> orders = Lists.newArrayList();
        for (OrderRestRep order : getCatalogClient().orders().getByIds(param.getIds())) { // NOSONAR
                                                                                          // ("Suppressing Sonar violation of Possible null pointer dereference of param. In the previous if condition it is already taken care of the case when param is null hence recheck is not required.")
            checkPermissions(order);
            orders.add(newOrderInfo(order));
        }
        renderApi(orders);
    }

    public static void orderExecution(String orderId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        ExecutionStateRestRep executionState = catalog.orders().getExecutionState(uri(orderId));
        List<OrderLogRestRep> logs = catalog.orders().getLogs(uri(orderId));
        List<ExecutionLogRestRep> taskLogs = catalog.orders().getExecutionLogs(uri(orderId));
        renderApi(newExecutionInfo(executionState, logs, taskLogs));
    }

    private static List<? extends RelatedResourceRep> queryOrders(String startTime, String endTime) {
        Date startDate = TimeUtils.getDateTimestamp(startTime);
        Date endDate = TimeUtils.getDateTimestamp(endTime);

        ViPRCatalogClient2 catalog = getCatalogClient();
        List<? extends RelatedResourceRep> elements;
        if (startDate == null && endDate == null) {
            elements = catalog.orders().listByUserTenant();
        }
        else {
            Map<String, Object> params = Maps.newHashMap();
            if (startDate != null) {
                params.put(START_TIME_PARAM, Long.toString(startDate.getTime()));
            }
            if (endDate != null) {
                params.put(END_TIME_PARAM, Long.toString(endDate.getTime()));
            }
            elements = catalog.orders().performSearch(params);

        }
        return elements;
    }

    protected static OrderRestRep getOrder(String orderId) {
        OrderRestRep order = getCatalogClient().orders().get(uri(orderId));
        checkPermissions(order);
        return order;
    }

    private static void checkPermissions(OrderRestRep order) {
        boolean submittedByMe = order.getSubmittedBy().equals(Security.getUserInfo().getIdentifier());
        if (!submittedByMe && !Security.isTenantAdmin() && !Security.isTenantApprover()) {
            forbidden(Messages.get("OrdersApi.noPermission"));
        }
    }

    private static void updateOrderTags(URI orderId, TagAssignment assignment) {
        // shouldn't happen but we'll protect against it anyway.
        if (assignment == null) {
            error(401, Messages.get("OrdersApi.invalidTagChangesElement"));
        }

        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.orders().addTags(orderId, assignment.getAdd());
        catalog.orders().removeTags(orderId, assignment.getRemove());

    }
}
