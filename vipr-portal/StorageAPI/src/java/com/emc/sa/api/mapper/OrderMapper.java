/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.ExecutionLogList;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderCommonParam;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.OrderLogList;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.google.common.collect.Lists;

public class OrderMapper {

    private static final String ENCRYPTED_FIELD_MASK = "**********";

    public static OrderRestRep map(Order from, List<OrderParameter> orderParameters) {
        if (from == null) {
            return null;
        }
        OrderRestRep to = new OrderRestRep();
        mapDataObjectFields(from, to);

        if (from.getCatalogServiceId() != null) {
            to.setCatalogService(toRelatedResource(ResourceTypeEnum.CATALOG_SERVICE, from.getCatalogServiceId()));
        }
        if (from.getExecutionWindowId() != null) {
            to.setExecutionWindow(toRelatedResource(ResourceTypeEnum.EXECUTION_WINDOW, from.getExecutionWindowId().getURI()));
        }
        to.setDateCompleted(from.getDateCompleted());
        to.setMessage(from.getMessage());
        to.setOrderNumber(from.getOrderNumber());
        to.setSummary(from.getSummary());
        to.setSubmittedBy(from.getSubmittedByUserId());
        to.setOrderStatus(from.getOrderStatus());
        if (StringUtils.isNotBlank(from.getTenant())) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(from.getTenant())));
        }
        to.setLastUpdated(from.getLastUpdated());

        if (orderParameters != null) {
            for (OrderParameter orderParameter : orderParameters) {
                Parameter parameter = new Parameter();
                parameter.setEncrypted(orderParameter.getEncrypted());
                if (parameter.isEncrypted()) {
                    parameter.setFriendlyValue(ENCRYPTED_FIELD_MASK);
                    parameter.setValue(ENCRYPTED_FIELD_MASK);
                }
                else {
                    parameter.setFriendlyValue(orderParameter.getFriendlyValue());
                    parameter.setValue(orderParameter.getValue());
                }
                parameter.setFriendlyLabel(orderParameter.getFriendlyLabel());
                parameter.setLabel(orderParameter.getLabel());
                to.getParameters().add(parameter);
            }
        }

        return to;
    }

    public static ExecutionStateRestRep map(ExecutionState from) {
        if (from == null) {
            return null;
        }
        ExecutionStateRestRep to = new ExecutionStateRestRep();

        to.setAffectedResources(Lists.newArrayList(from.getAffectedResources()));
        to.setCurrentTask(from.getCurrentTask());
        to.setEndDate(from.getEndDate());
        to.setExecutionStatus(from.getExecutionStatus());
        to.setStartDate(from.getStartDate());
        to.setLastUpdated(from.getLastUpdated());

        return to;
    }

    public static OrderLogRestRep map(ExecutionLog from) {
        if (from == null) {
            return null;
        }
        OrderLogRestRep to = new OrderLogRestRep();

        to.setDate(from.getDate());
        to.setLevel(from.getLevel());
        to.setMessage(from.getMessage());
        to.setPhase(from.getPhase());
        to.setStackTrace(from.getStackTrace());

        return to;
    }

    public static ExecutionLogRestRep map(ExecutionTaskLog from) {
        if (from == null) {
            return null;
        }
        ExecutionLogRestRep to = new ExecutionLogRestRep();

        to.setDate(from.getDate());
        to.setLevel(from.getLevel());
        to.setMessage(from.getMessage());
        to.setPhase(from.getPhase());
        to.setStackTrace(from.getStackTrace());
        to.setDetail(from.getDetail());
        to.setElapsed(from.getElapsed());
        to.setLastUpdated(from.getLastUpdated());

        return to;
    }

    public static Order createNewObject(URI tenantId, OrderCreateParam param) {
        Order newObject = new Order();
        newObject.setId(URIUtil.createId(Order.class));
        newObject.setTenant(tenantId.toString());
        newObject.setCatalogServiceId(param.getCatalogService());

        updateObject(newObject, param);

        return newObject;
    }

    public static List<OrderParameter> createOrderParameters(Order order, OrderCreateParam param, EncryptionProvider encryption) {

        List<OrderParameter> orderParams = new ArrayList<OrderParameter>();

        if (param.getParameters() != null) {
            int parameterIndex = 0;
            for (Parameter parameter : param.getParameters()) {
                OrderParameter orderParameter = new OrderParameter();
                orderParameter.setSortedIndex(parameterIndex++);
                orderParameter.setFriendlyLabel(parameter.getFriendlyLabel());
                orderParameter.setLabel(parameter.getLabel());
                orderParameter.setUserInput(parameter.isUserInput());
                orderParameter.setEncrypted(parameter.isEncrypted());
                if (parameter.isEncrypted()) {
                    // We have to treat this as a CSV value - pull the CSV apart, encrypt the pieces, re-CSV encode
                    List<String> values = Lists.newArrayList();
                    for (String value : TextUtils.parseCSV(parameter.getValue())) {
                        values.add(Base64.encodeBase64String(encryption.encrypt(value)));
                    }
                    String encryptedValue = TextUtils.formatCSV(values);
                    orderParameter.setFriendlyValue(ENCRYPTED_FIELD_MASK);
                    orderParameter.setValue(encryptedValue);
                }
                else {
                    orderParameter.setFriendlyValue(parameter.getFriendlyValue());
                    orderParameter.setValue(parameter.getValue());
                }
                orderParameter.setOrderId(order.getId());
                orderParams.add(orderParameter);
            }
        }

        return orderParams;

    }

    public static void updateObject(Order object, OrderCommonParam param) {

    }

    public static OrderLogList toOrderLogList(List<ExecutionLog> executionLogs) {
        OrderLogList list = new OrderLogList();
        for (ExecutionLog executionLog : executionLogs) {
            OrderLogRestRep orderLogRestRep = map(executionLog);
            list.getOrderLogs().add(orderLogRestRep);
        }
        return list;
    }

    public static ExecutionLogList toExecutionLogList(List<ExecutionTaskLog> executionTaskLogs) {
        ExecutionLogList list = new ExecutionLogList();
        for (ExecutionTaskLog executionTaskLog : executionTaskLogs) {
            ExecutionLogRestRep executionLogRestRep = map(executionTaskLog);
            list.getExecutionLogs().add(executionLogRestRep);
        }
        return list;
    }
}
