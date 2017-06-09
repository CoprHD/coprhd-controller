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
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.util.OrderTextCreator;
import com.emc.storageos.model.ResourceTypeEnum;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.emc.sa.model.util.ScheduleTimeHelper;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
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
        if (from.getScheduledEventId() != null) {
            to.setScheduledEventId(from.getScheduledEventId());
        }
        if (from.getScheduledTime() != null) {
            to.setScheduledTime(from.getScheduledTime());
        }
        return to;
    }

    public static ExecutionStateRestRep map(ExecutionState from) {
        return OrderTextCreator.map(from);
    }

    public static OrderLogRestRep map(ExecutionLog from) {
        return OrderTextCreator.map(from);
    }

    public static ExecutionLogRestRep map(ExecutionTaskLog from) {
        return OrderTextCreator.map(from);
    }

    public static Order createNewObject(URI tenantId, OrderCreateParam param) {
        Order newObject = new Order();
        newObject.setId(URIUtil.createId(Order.class));
        newObject.setTenant(tenantId.toString());
        newObject.setCatalogServiceId(param.getCatalogService());
        newObject.setWorkflowDocument(param.getWorkflowDocument());
        if (param.getScheduledEventId() != null) {
            newObject.setScheduledEventId(param.getScheduledEventId());
            if (param.getScheduledTime() != null) {
                newObject.setScheduledTime(ScheduleTimeHelper.convertStrToCalendar(param.getScheduledTime()));
            }

            if (param.getExecutionWindow() == null) {
                newObject.setExecutionWindowId(null);
            } else {
                newObject.setExecutionWindowId(new NamedURI(param.getExecutionWindow(), "ExecutionWindow"));
            }
        }

        updateObject(newObject, param);

        return newObject;
    }

    public static List<OrderParameter> createOrderParameters(Order order, OrderCreateParam param,
            EncryptionProvider encryption) {

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
                    // We have to treat this as a CSV value - pull the CSV
                    // apart, encrypt the pieces, re-CSV encode
                    List<String> values = Lists.newArrayList();
                    for (String value : TextUtils.parseCSV(parameter.getValue())) {
                        values.add(Base64.encodeBase64String(encryption.encrypt(value)));
                    }
                    String encryptedValue = TextUtils.formatCSV(values);
                    orderParameter.setFriendlyValue(ENCRYPTED_FIELD_MASK);
                    orderParameter.setValue(encryptedValue);
                } else {
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
