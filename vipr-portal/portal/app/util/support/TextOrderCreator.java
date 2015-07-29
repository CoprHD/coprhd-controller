/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import com.emc.storageos.db.client.model.uimodels.ExecutionPhase;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.Parameter;
import com.google.common.collect.Lists;

/**
 * Converts an Order to a Textual Representation
 */
public class TextOrderCreator {
    private static final String DATE_FORMAT = "dd-MM-yy hh:mm";
    private static final String DETAIL_INDENT = "      \t                            \t";

    private final ViPRCatalogClient2 client;
    private final OrderRestRep order;
    private final StringBuffer buffer;

    public TextOrderCreator(ViPRCatalogClient2 client, OrderRestRep order) {
        this.client = client;
        this.order = order;
        this.buffer = new StringBuffer();
    }

    public String getText() {
        try {
            writeDetails();
            writeRequestParameters();
            writeExecutionState();
        } catch (Exception e) {
            writeHeader("ERROR CREATING ORDER");
            buffer.append(ExceptionUtils.getFullStackTrace(e));
        }

        return buffer.toString();
    }

    private void writeDetails() {
        CatalogServiceRestRep service = client.services().get(order.getCatalogService());
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
        ExecutionStateRestRep state = client.orders().getExecutionState(order.getId());
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
        List<OrderLogRestRep> logs = client.orders().getLogs(order.getId());
        writeHeader("Logs");
        for (OrderLogRestRep log : logs) {
            writeLog(log);
        }
    }

    private void writeTaskLogs(ExecutionStateRestRep state) {
        List<ExecutionLogRestRep> logs = client.orders().getExecutionLogs(order.getId());
        List<ExecutionLogRestRep> precheckLogs = getTaskLogs(logs, ExecutionPhase.PRECHECK);
        List<ExecutionLogRestRep> executeLogs = getTaskLogs(logs, ExecutionPhase.EXECUTE);
        List<ExecutionLogRestRep> rollbackLogs = getTaskLogs(logs, ExecutionPhase.ROLLBACK);

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
        buffer.append("[").append(log.getLevel()).append("]");
        buffer.append("\t").append(log.getDate());
        if (StringUtils.isNotBlank(log.getMessage())) {
            buffer.append("\t").append(log.getMessage());
        }
        if (StringUtils.isNotBlank(log.getStackTrace())) {
            buffer.append("\n").append(log.getStackTrace());
        }
        buffer.append("\n");
    }

    private void writeLog(ExecutionLogRestRep log) {
        buffer.append("[").append(log.getLevel()).append("]");
        buffer.append("\t").append(log.getDate());
        if (StringUtils.isNotBlank(log.getMessage())) {
            buffer.append("\t").append(log.getMessage());
        }
        if (log.getElapsed() != null) {
            buffer.append("\t(").append(log.getElapsed()).append(" ms)");
        }
        if (StringUtils.isNotBlank(log.getDetail())) {
            buffer.append("\n").append(DETAIL_INDENT).append(log.getDetail());
        }
        if (StringUtils.isNotBlank(log.getStackTrace())) {
            buffer.append("\n").append(log.getStackTrace());
        }
        buffer.append("\n");
    }

    private void writeHeader(String header) {
        buffer.append("\n");
        buffer.append(header);
        buffer.append("\n");
        buffer.append(StringUtils.repeat("-", header.length()));
        buffer.append("\n");
    }

    private void writeField(String label, Date date) {
        final SimpleDateFormat DATE = new SimpleDateFormat(DATE_FORMAT);
        if (date == null) {
            writeField(label, "");
        }
        else {
            writeField(label, DATE.format((date)));
        }
    }

    private void writeField(String label, Object value) {
        buffer.append(label);

        if (!label.endsWith("?") && !label.endsWith(":")) {
            buffer.append(":");
        }
        buffer.append(" ");
        if (value != null) {
            buffer.append(value.toString());
        }
        buffer.append("\n");
    }
}
