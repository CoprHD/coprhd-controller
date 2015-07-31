/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.emc.vipr.client.impl.DateUtils;
import com.emc.vipr.client.util.ItemProcessor;
import com.emc.vipr.model.sys.logging.LogMessage;

public class LogsSearchBuilder {
    private final Logs logs;
    private Set<String> nodeIds;
    private Set<String> logNames;
    private Integer severity;
    private String start;
    private String end;
    private String regex;
    private Integer maxCount;

    public LogsSearchBuilder(Logs logs) {
        this.logs = logs;
    }

    public LogsSearchBuilder nodeIds(String... values) {
        nodeIds = new LinkedHashSet<String>();
        for (String value : values) {
            nodeIds.add(value);
        }
        return this;
    }

    public LogsSearchBuilder nodeIds(Collection<String> values) {
        nodeIds = new LinkedHashSet<String>();
        if (values != null) {
            nodeIds.addAll(values);
        }
        return this;
    }

    public LogsSearchBuilder logNames(String... values) {
        logNames = new LinkedHashSet<String>();
        for (String value : values) {
            logNames.add(value);
        }
        return this;
    }

    public LogsSearchBuilder logNames(Collection<String> values) {
        logNames = new LinkedHashSet<String>();
        if (values != null) {
            logNames.addAll(values);
        }
        return this;
    }

    private String formatDate(Date date) {
        return date != null ? DateUtils.formatUTC(date, Logs.DATE_FORMAT) : null;
    }

    public LogsSearchBuilder startTime(Date startTime) {
        return startTime(formatDate(startTime));
    }

    public LogsSearchBuilder startTime(String startTime) {
        this.start = startTime;
        return this;
    }

    public LogsSearchBuilder endTime(Date endTime) {
        return endTime(formatDate(endTime));
    }

    public LogsSearchBuilder endTime(String endTime) {
        this.end = endTime;
        return this;
    }

    public LogsSearchBuilder regex(String regex) {
        this.regex = regex;
        return this;
    }

    public LogsSearchBuilder severity(int severity) {
        this.severity = severity;
        return this;
    }

    public LogsSearchBuilder severityFatal() {
        return severity(Logs.LOG_LEVEL_FATAL);
    }

    public LogsSearchBuilder severityError() {
        return severity(Logs.LOG_LEVEL_ERROR);
    }

    public LogsSearchBuilder severityWarn() {
        return severity(Logs.LOG_LEVEL_WARN);
    }

    public LogsSearchBuilder severityInfo() {
        return severity(Logs.LOG_LEVEL_INFO);
    }

    public LogsSearchBuilder severityDebug() {
        return severity(Logs.LOG_LEVEL_DEBUG);
    }

    public LogsSearchBuilder severityTrace() {
        return severity(Logs.LOG_LEVEL_TRACE);
    }

    public LogsSearchBuilder maxCount(int maxCount) {
        this.maxCount = maxCount;
        return this;
    }

    public List<LogMessage> run() {
        return logs.get(nodeIds, logNames, severity, start, end, regex, maxCount);
    }

    public InputStream stream() {
        return logs.getAsStream(nodeIds, logNames, severity, start, end, regex, maxCount);
    }

    public InputStream text() {
        return logs.getAsText(nodeIds, logNames, severity, start, end, regex, maxCount);
    }

    public void items(ItemProcessor<LogMessage> processor) {
        logs.getAsItems(processor, nodeIds, logNames, severity, start, end, regex, maxCount);
    }
}
