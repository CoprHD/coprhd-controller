package com.emc.storageos.db.client.util;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionPhase;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.google.common.collect.Lists;

public class OrderTextCreator {
    public static final String ENCRYPTED_FIELD_MASK = "**********";
    private static final String DATE_FORMAT = "dd-MM-yy hh:mm";
    private static final String DETAIL_INDENT = "      \t                            \t";

    private StringBuilder builder = new StringBuilder();
    private OrderRestRep order;
    private CatalogServiceRestRep service;
    private ExecutionStateRestRep state;
    private List<OrderLogRestRep> logs;
    private List<ExecutionLogRestRep> exeLogs;

    public OrderRestRep getOrder() {
        return order;
    }

    public void setOrder(OrderRestRep order) {
        this.order = order;
    }

    public void setOrder(Order order, List<OrderParameter> params) {
        this.order = map(order, params);
    }

    public CatalogServiceRestRep getService() {
        return service;
    }

    public void setService(CatalogServiceRestRep service) {
        this.service = service;
    }

    public ExecutionStateRestRep getState() {
        return state;
    }

    public void setState(ExecutionStateRestRep state) {
        this.state = state;
    }

    public void setState(ExecutionState state) {
        this.state = map(state);
    }

    public List<OrderLogRestRep> getLogs() {
        return logs;
    }

    public void setLogs(List<OrderLogRestRep> logs) {
        this.logs = logs;
    }

    public void setRawLogs(List<ExecutionLog> logs) {
        this.logs = new ArrayList<OrderLogRestRep>();
        for (ExecutionLog log : logs) {
            this.logs.add(map(log));
        }
    }

    public List<ExecutionLogRestRep> getExeLogs() {
        return exeLogs;
    }

    public void setExeLogs(List<ExecutionLogRestRep> exeLogs) {
        this.exeLogs = exeLogs;
    }

    public void setRawExeLogs(List<ExecutionTaskLog> exeLogs) {
        this.exeLogs = new ArrayList<ExecutionLogRestRep>();
        for (ExecutionTaskLog log : exeLogs) {
            this.exeLogs.add(map(log));
        }
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
            to.setExecutionWindow(
                    toRelatedResource(ResourceTypeEnum.EXECUTION_WINDOW, from.getExecutionWindowId().getURI()));
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
                } else {
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

    public String getText() {
        try {
            writeDetails();
            writeRequestParameters();
            writeExecutionState();
        } catch (Exception e) {
            writeHeader("ERROR CREATING ORDER");
            builder.append(ExceptionUtils.getFullStackTrace(e));
        }

        return builder.toString();
    }

    private void writeDetails() {
        writeHeader("ORDER DETAILS");
        writeField("Order ID", order.getId());
        writeField("Order Number", order.getOrderNumber());
        writeField("Submitted By", order.getSubmittedBy());
        writeField("Date Submitted", order.getCreationTime().getTime());
        writeField("Date Completed", order.getDateCompleted());
        writeField("Message", order.getMessage());
        writeField("Status", order.getOrderStatus());
        writeField("Catalog Service", service.getTitle());
        writeField("Catalog ID", service.getId());
        writeField("Base Service", service.getBaseService());
        writeField("Requires Approval?", service.isApprovalRequired());
        writeField("Requires Execution Window?", service.isExecutionWindowRequired());
        if (Boolean.TRUE.equals(service.isExecutionWindowRequired())) {
            writeField("Execution Window?", service.getDefaultExecutionWindow().getId());
        }
    }

    private void writeRequestParameters() {
        writeHeader("Parameters");

        List<Parameter> parameters = order.getParameters();
        for (Parameter parameter : parameters) {
            writeField(parameter.getFriendlyLabel(), parameter.getFriendlyValue());
        }
    }

    private void writeExecutionState() {
        if (state == null) {
            return;
        }
        writeHeader("Execution State");
        writeField("Execution Status", state.getExecutionStatus());
        writeField("Start Date", state.getStartDate());
        writeField("End Date", state.getEndDate());
        writeField("Affected Resources", state.getAffectedResources());
        writeLogs(state);
        writeTaskLogs(state);
    }

    private void writeLogs(ExecutionStateRestRep state) {
        writeHeader("Logs");
        for (OrderLogRestRep log : logs) {
            writeLog(log);
        }
    }

    private void writeTaskLogs(ExecutionStateRestRep state) {
        List<ExecutionLogRestRep> precheckLogs = getTaskLogs(exeLogs, ExecutionPhase.PRECHECK);
        List<ExecutionLogRestRep> executeLogs = getTaskLogs(exeLogs, ExecutionPhase.EXECUTE);
        List<ExecutionLogRestRep> rollbackLogs = getTaskLogs(exeLogs, ExecutionPhase.ROLLBACK);

        if (!precheckLogs.isEmpty()) {
            writeHeader("Precheck Steps");
            for (ExecutionLogRestRep log : precheckLogs) {
                writeLog(log);
            }
        }
        if (!executeLogs.isEmpty()) {
            writeHeader("Execute Steps");
            for (ExecutionLogRestRep log : executeLogs) {
                writeLog(log);
            }
        }
        if (!rollbackLogs.isEmpty()) {
            writeHeader("Rollback Steps");
            for (ExecutionLogRestRep log : rollbackLogs) {
                writeLog(log);
            }
        }
    }

    private List<ExecutionLogRestRep> getTaskLogs(List<ExecutionLogRestRep> logs, ExecutionPhase phase) {
        List<ExecutionLogRestRep> phaseLogs = Lists.newArrayList();
        for (ExecutionLogRestRep log : logs) {
            if (phase.name().equals(log.getPhase())) {
                phaseLogs.add(log);
            }
        }
        return phaseLogs;
    }

    private void writeLog(OrderLogRestRep log) {
        builder.append("[").append(log.getLevel()).append("]");
        builder.append("\t").append(log.getDate());
        if (StringUtils.isNotBlank(log.getMessage())) {
            builder.append("\t").append(log.getMessage());
        }
        if (StringUtils.isNotBlank(log.getStackTrace())) {
            builder.append("\n").append(log.getStackTrace());
        }
        builder.append("\n");
    }

    private void writeLog(ExecutionLogRestRep log) {
        builder.append("[").append(log.getLevel()).append("]");
        builder.append("\t").append(log.getDate());
        if (StringUtils.isNotBlank(log.getMessage())) {
            builder.append("\t").append(log.getMessage());
        }
        if (log.getElapsed() != null) {
            builder.append("\t(").append(log.getElapsed()).append(" ms)");
        }
        if (StringUtils.isNotBlank(log.getDetail())) {
            builder.append("\n").append(DETAIL_INDENT).append(log.getDetail());
        }
        if (StringUtils.isNotBlank(log.getStackTrace())) {
            builder.append("\n").append(log.getStackTrace());
        }
        builder.append("\n");
    }

    private void writeHeader(String header) {
        builder.append("\n");
        builder.append(header);
        builder.append("\n");
        builder.append(StringUtils.repeat("-", header.length()));
        builder.append("\n");
    }

    private void writeField(String label, Date date) {
        final SimpleDateFormat DATE = new SimpleDateFormat(DATE_FORMAT);
        if (date == null) {
            writeField(label, "");
        } else {
            writeField(label, DATE.format((date)));
        }
    }

    private void writeField(String label, Object value) {
        builder.append(label);

        if (!label.endsWith("?") && !label.endsWith(":")) {
            builder.append(":");
        }
        builder.append(" ");
        if (value != null) {
            builder.append(value.toString());
        }
        builder.append("\n");
    }
}
