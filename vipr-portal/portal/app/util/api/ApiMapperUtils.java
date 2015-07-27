/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.api;

import java.util.Collections;
import java.util.List;

import play.data.validation.Validation;
import play.mvc.Router;
import util.CatalogCategoryUtils;
import util.CatalogServiceUtils;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.search.Tags;
import com.emc.vipr.model.catalog.ApprovalInfo;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.CategoryInfo;
import com.emc.vipr.model.catalog.ExecutionInfo;
import com.emc.vipr.model.catalog.ExecutionLogInfo;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.ExecutionTaskInfo;
import com.emc.vipr.model.catalog.Link;
import com.emc.vipr.model.catalog.NamedReference;
import com.emc.vipr.model.catalog.OrderInfo;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.emc.vipr.model.catalog.Reference;
import com.emc.vipr.model.catalog.ServiceInfo;
import com.emc.vipr.model.catalog.ValidationError;
import com.google.common.collect.Lists;

/**
 * @author Chris Dail
 */
public class ApiMapperUtils {

    public static CategoryInfo newCategoryInfo(CatalogCategoryRestRep category) {
        CategoryInfo it = new CategoryInfo();
        it.setId(category.getId().toString());
        it.setLink(Link.newSelfLink(categoryUrl(category.getId().toString())));
        it.setInactive(category.getInactive());
        it.setName(category.getName());
        it.setTitle(category.getTitle());
        it.setDescription(category.getDescription());
        it.setImage(category.getImage());
        List<CatalogCategoryRestRep> subCatalogCategories = CatalogCategoryUtils.getCatalogCategories(category);
        for (CatalogCategoryRestRep subCatalogCategory : subCatalogCategories) {
            it.getSubCategories().add(newNamedReference(subCatalogCategory));
        }
        List<CatalogServiceRestRep> catalogServices = CatalogServiceUtils.getCatalogServices(category);
        for (CatalogServiceRestRep catalogService : catalogServices) {
            it.getServices().add(newServiceInfo(catalogService));
        }
        return it;
    }

    private static NamedReference newNamedReference(CatalogCategoryRestRep category) {
        NamedReference it = new NamedReference();
        it.setId(category.getId().toString());
        it.setHref(categoryUrl(category.getId().toString()));
        it.setName(category.getName());
        return it;
    }

    public static ServiceInfo newServiceInfo(CatalogServiceRestRep service) {
        ServiceInfo it = new ServiceInfo();
        it.setId(service.getId().toString());
        it.setLink(Link.newSelfLink(serviceUrl(service.getId().toString())));
        it.setInactive(service.getInactive());
        it.setName(service.getName());
        it.setTitle(service.getTitle());
        it.setImage(service.getImage());
        it.setDescription(service.getDescription());
        it.setApprovalRequired(service.isApprovalRequired());
        it.setExecutionWindowRequired(service.isExecutionWindowRequired());
        it.setDefaultExecutionWindowId(service.getDefaultExecutionWindow() == null ? null : service.getDefaultExecutionWindow().getId().toString());
        it.setBaseService(service.getBaseService());
        it.setMaxSize(service.getMaxSize());
        return it;
    }

    public static OrderInfo newOrderInfo(OrderRestRep order) {
        OrderInfo it = new OrderInfo();
        it.setId(order.getId().toString());
        it.setOrderNumber(order.getOrderNumber());
        it.setLink(Link.newSelfLink(orderUrl(order.getId().toString())));
        it.setInactive(order.getInactive());
        it.setService(newServiceReference(order.getCatalogService().getId().toString()));
        it.setSummary(order.getSummary());
        it.setMessage(order.getMessage());
        it.setCreatedDate(order.getCreationTime().getTime());
        it.setDateCompleted(order.getDateCompleted());
        it.setSubmittedBy(order.getSubmittedBy());
        it.setStatus(order.getOrderStatus());
        it.setExecutionWindow(order.getExecutionWindow() != null && order.getExecutionWindow().getId() != null ? order.getExecutionWindow().getId().toString() : null);
        it.setExecution(newExecutionReference(order.getId().toString()));
        it.setTags(getTags(order));
        if (order.getParameters() != null) {
            it.setParameters(newParameters(order.getParameters()));
        }
        return it;
    }
    
    public static Tags getTags(DataObjectRestRep object) {
        Tags tags = new Tags();
        if (object.getTags() != null) {
            for (String tag: object.getTags()) {
                tags.getTag().add(tag);
            }
        }
        return tags;
    }    

    public static ExecutionInfo newExecutionInfo(ExecutionStateRestRep state, List<OrderLogRestRep> logs, List<ExecutionLogRestRep> taskLogs) {
        ExecutionInfo it = new ExecutionInfo();
        it.setStartDate(state.getStartDate());
        it.setEndDate(state.getEndDate());
        it.setExecutionStatus(state.getExecutionStatus());
        it.setCurrentTask(state.getCurrentTask());
        it.getAffectedResources().addAll(state.getAffectedResources());
        for (OrderLogRestRep log: logs) {
            ExecutionLogInfo info = new ExecutionLogInfo();
            info.setDate(log.getDate());
            info.setLevel(log.getLevel());
            info.setMessage(log.getMessage());
            info.setPhase(log.getPhase());
            info.setStackTrace(log.getStackTrace());
            it.getExecutionLogs().add(info);
        }
        for (ExecutionLogRestRep log: taskLogs) {
            ExecutionTaskInfo info = new ExecutionTaskInfo();
            info.setDate(log.getDate());
            info.setLevel(log.getLevel());
            info.setMessage(log.getMessage());
            info.setPhase(log.getPhase());
            info.setStackTrace(log.getStackTrace());
            info.setDetail(log.getDetail());
            info.setElapsed(log.getElapsed());
            it.getExecutionTasks().add(info);
        }
        return it;
    }

    public static ApprovalInfo newApprovalInfo(ApprovalRestRep request) {
        ApprovalInfo it = new ApprovalInfo();
        it.setId(request.getId().toString());
        it.setLink(Link.newSelfLink(approvalUrl(request.getId().toString())));
        it.setInactive(request.getInactive());
        it.setApprovedBy(request.getApprovedBy());
        it.setDateActioned(request.getDateActioned());
        it.setMessage(request.getMessage());
        it.setStatus(request.getApprovalStatus());
        it.setOrder(newOrderReference(request.getOrder().getId().toString()));
        it.setTenant(request.getTenant().getId().toString());
        return it;
    }

    public static List<Parameter> newParameters(List<Parameter> parameters) {
        List<Parameter> options = Lists.newArrayList();
        for (Parameter parameter: parameters) {
            options.add(new Parameter(parameter.getFriendlyLabel(), parameter.getValue(), parameter.getFriendlyValue()));
        }
        return options;
    }

	private static Reference newServiceReference(String id) {
        return new Reference(id, serviceUrl(id));
    }

    public static Reference newOrderReference(String id) {
        return new Reference(id, orderUrl(id));
    }

    public static Reference newApprovalReference(String id) {
        return new Reference(id, approvalUrl(id));
    }

    private static Reference newExecutionReference(String id) {
        return new Reference(id, reverse("api.OrdersApi.orderExecution", "orderId", id));
    }

    public static Reference newAssetOptionsReference(String asset) {
        return new Reference(asset, reverse("api.AssetOptionsApi.options", "asset", asset));
    }

    private static String orderUrl(String id) {
        return reverse("api.OrdersApi.order", "orderId", id);
    }

    private static String categoryUrl(String id) {
        return reverse("api.CatalogApi.category", "categoryId", id);
    }

    private static String serviceUrl(String id) {
        return reverse("api.CatalogApi.service", "serviceId", id);
    }

    private static String approvalUrl(String id) {
        return reverse("api.ApprovalsApi.approval", "approvalId", id);
    }
    
    static String reverse(String action, String key, Object value) {
        return Router.reverse(action, Collections.singletonMap(key, value)).url;
    }
    
    public static List<ValidationError> getValidationErrors() {
        List<ValidationError> errors = Lists.newArrayList();
        for (play.data.validation.Error error: Validation.errors()) {
            errors.add(new ValidationError(error.getKey(), error.message()));
        }
        return errors;
    }
    
}
