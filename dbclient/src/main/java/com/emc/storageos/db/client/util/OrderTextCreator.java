/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

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
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.google.common.collect.Lists;

public class OrderTextCreator {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd-MM-yy hh:mm");
    private static final String DETAIL_INDENT = "      \t                            \t";
    private static final SimpleDateFormat TIME = new SimpleDateFormat("ddMMyy-HHmm");

    private StringBuilder builder = new StringBuilder();
    private OrderRestRep order;
    private CatalogServiceRestRep service;
    private ExecutionStateRestRep state;
    private List<OrderLogRestRep> logs;
    private List<ExecutionLogRestRep> exeLogs;

    public void setOrder(OrderRestRep order) {
        this.order = order;
    }

    public void setService(CatalogServiceRestRep service) {
        this.service = service;
    }

    public void setState(ExecutionStateRestRep state) {
        this.state = state;
    }

    public void setState(ExecutionState state) {
        this.state = map(state);
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

    public static String genereateOrderFileName(OrderRestRep order) {
        String timestamp = TIME.format(order.getCreationTime().getTime());
        return String.format("Order-%s-%s-%s.txt", order.getOrderNumber(), order.getOrderStatus(), timestamp);
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
