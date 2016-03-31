/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.client.impl.DateUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.impl.jaxb.ListProcessor;
import com.emc.vipr.client.system.impl.PathConstants;
import com.emc.vipr.client.util.AbstractItemProcessor;
import com.emc.vipr.client.util.ItemProcessor;
import com.emc.vipr.model.sys.logging.LogLevels;
import com.emc.vipr.model.sys.logging.LogLevels.LogLevel;
import com.emc.vipr.model.sys.logging.LogMessage;
import com.emc.vipr.model.sys.logging.SetLogLevelParam;
import com.sun.jersey.api.client.ClientResponse;

public class Logs {
    public static final String DATE_FORMAT = "yyyy-MM-dd_HH:mm:ss";
    public static final int LOG_LEVEL_FATAL = 0;
    public static final int LOG_LEVEL_ERROR = 4;
    public static final int LOG_LEVEL_WARN = 5;
    public static final int LOG_LEVEL_INFO = 7;
    public static final int LOG_LEVEL_DEBUG = 8;
    public static final int LOG_LEVEL_TRACE = 9;

    private static final String NODE_ID = "node_id";
    private static final String NODE_NAME = "node_name";
    private static final String LOG_NAME = "log_name";
    private static final String SEVERITY = "severity";
    private static final String START_TIME = "start";
    private static final String END_TIME = "end";
    private static final String REGEX = "msg_regex";
    private static final String MAX_COUNT = "maxcount";

    private RestClient client;

    public Logs(RestClient client) {
        this.client = client;
    }

    /**
     * Gets the log levels for all nodes and all logs. This is a convenience method for <tt>getLogLevels(null, null)</tt>.
     * 
     * @return the list of log levels.
     * 
     * @see #getLogLevels(Collection, Collection)
     */
    public List<LogLevel> getLogLevels() {
        return getLogLevels(null, null);
    }

    /**
     * Gets the log levels for the given nodes and logs.
     * 
     * @param nodeIds
     *            the IDs of the nodes. If null or empty, all node log levels are retrieved
     * @param logNames
     *            the name of the logs. If null or empty, all logs' levels are returned.
     * @return the list of log levels.
     */
    public List<LogLevel> getLogLevels(Collection<String> nodeIds, Collection<String> logNames) {
        UriBuilder builder = client.uriBuilder(PathConstants.LOG_LEVELS_URL);
        if ((nodeIds != null) && (!nodeIds.isEmpty())) {
            builder.queryParam(NODE_ID, nodeIds.toArray());
        }
        if ((logNames != null) && (!logNames.isEmpty())) {
            builder.queryParam(LOG_NAME, logNames.toArray());
        }
        LogLevels response = client.getURI(LogLevels.class, builder.build());
        return response.getLogLevels();
    }

    /**
     * Gets the log levels for the given nodes and logs by node names.
     *
     * @param nodeNames
     *            the names of the nodes. If null or empty, all node log levels are retrieved
     * @param logNames
     *            the name of the logs. If null or empty, all logs' levels are returned.
     * @return the list of log levels.
     */
    public List<LogLevel> getLogLevelsByNodeName(Collection<String> nodeNames, Collection<String> logNames) {
        UriBuilder builder = client.uriBuilder(PathConstants.LOG_LEVELS_URL);
        if ((nodeNames != null) && (!nodeNames.isEmpty())) {
            builder.queryParam(NODE_NAME, nodeNames.toArray());
        }
        if ((logNames != null) && (!logNames.isEmpty())) {
            builder.queryParam(LOG_NAME, logNames.toArray());
        }
        LogLevels response = client.getURI(LogLevels.class, builder.build());
        return response.getLogLevels();
    }

    /**
     * Sets the log level for all nodes and logs. This is a convenience method for <tt>setLogLevels(severity, null, null)</tt>
     * 
     * @param severity
     *            the log severity.
     * 
     * @see #setLogLevels(int, Collection, Collection)
     */
    public void setLogLevels(int severity) {
        setLogLevels(severity, null, null);
    }

    /**
     * Sets the log level for the given nodes and logs. This constructs a {@link SetLogLevelParam} and calls
     * {@link #setLogLevels(SetLogLevelParam)}.
     * <p>
     * API Call: <tt>POST /logs/log-levels</tt>
     * 
     * @param severity
     *            the log severity.
     * @param nodeIds
     *            the IDs of the nodes. If null all nodes are changed.
     * @param logNames
     *            the names of the logs. If null all logs are changed.
     */
    public void setLogLevels(int severity, Collection<String> nodeIds, Collection<String> logNames) {
        SetLogLevelParam param = new SetLogLevelParam();
        param.setSeverity(severity);
        if ((nodeIds != null) && (!nodeIds.isEmpty())) {
            param.setNodeIds(new ArrayList<String>(nodeIds));
        }
        if ((logNames != null) && (!logNames.isEmpty())) {
            param.setLogNames(new ArrayList<String>(logNames));
        }
        setLogLevels(param);
    }

    /**
     * Sets the log level for the given nodes and logs. This constructs a {@link SetLogLevelParam} and calls
     * {@link #setLogLevels(SetLogLevelParam)}.
     * <p>
     * API Call: <tt>POST /logs/log-levels</tt>
     *
     * @param severity
     *            the log severity.
     * @param nodeNames
     *            the names of the nodes. If null all nodes are changed.
     * @param logNames
     *            the names of the logs. If null all logs are changed.
     */
    public void setLogLevelsByNodeName(int severity, Collection<String> nodeNames, Collection<String> logNames) {
        SetLogLevelParam param = new SetLogLevelParam();
        param.setSeverity(severity);
        if ((nodeNames != null) && (!nodeNames.isEmpty())) {
            param.setNodeNames(new ArrayList<String>(nodeNames));
        }
        if ((logNames != null) && (!logNames.isEmpty())) {
            param.setLogNames(new ArrayList<String>(logNames));
        }
        setLogLevels(param);
    }

    /**
     * Sets the log levels.
     * <p>
     * API Call: <tt>POST /logs/log-levels</tt>
     * 
     * @param param
     *            the log level configuration.
     */
    public void setLogLevels(SetLogLevelParam param) {
        client.post(String.class, param, PathConstants.LOG_LEVELS_URL);
    }

    public List<LogMessage> get(Collection<String> nodeIds, Collection<String> logNames, Integer severity, Date start,
            Date end, String regex, Integer maxCount) {
        return get(nodeIds, logNames, severity, formatDate(start), formatDate(end), regex, maxCount);
    }

    public List<LogMessage> get(Collection<String> nodeIds, Collection<String> logNames, Integer severity,
            String start, String end, String regex, Integer maxCount) {
        final List<LogMessage> results = new ArrayList<LogMessage>();
        AbstractItemProcessor<LogMessage> processor = new AbstractItemProcessor<LogMessage>() {
            @Override
            public void processItem(LogMessage item) throws Exception {
                results.add(item);
            }
        };
        getAsItems(processor, nodeIds, logNames, severity, start, end, regex, maxCount);
        return results;
    }

    public void getAsItems(ItemProcessor<LogMessage> processor, Collection<String> nodeIds,
            Collection<String> logNames, Integer severity, Date start, Date end, String regex, Integer maxCount) {
        getAsItems(processor, nodeIds, logNames, severity, formatDate(start), formatDate(end), regex, maxCount);
    }

    public void getAsItems(ItemProcessor<LogMessage> processor, Collection<String> nodeIds,
            Collection<String> logNames, Integer severity, String start, String end, String regex, Integer maxCount) {
        ListProcessor<LogMessage> listProcessor = new ListProcessor<LogMessage>(LogMessage.class, processor);
        InputStream in = getAsStream(nodeIds, logNames, severity, start, end, regex, maxCount);
        try {
            listProcessor.process(in);
        } catch (Exception e) {
            throw new ViPRException(e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // Silently ignore
            }
        }
    }

    /**
     * Gets the system logs as a stream.
     * <p>
     * API Call: <tt>GET /logs</tt>
     * 
     * @param nodeIds
     *            the IDs of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time, may be null. If specified, this will be formatted in the UTC timezone as <tt>yyyy-MM-dd_HH:mm:ss</tt>.
     * @param end
     *            the end time, may be null. If specified, this will be formatted in the UTC timezone as <tt>yyyy-MM-dd_HH:mm:ss</tt>
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as XML.
     */
    public InputStream getAsStream(Collection<String> nodeIds, Collection<String> logNames, Integer severity,
            Date start, Date end, String regex, Integer maxCount) {
        return getAsStream(nodeIds, logNames, severity, formatDate(start), formatDate(end), regex, maxCount);
    }

    /**
     * Gets the system logs as an XML stream.
     * <p>
     * API Call: <tt>GET /logs</tt>
     * 
     * @param nodeIds
     *            the IDs of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param end
     *            the end time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as XML.
     */
    public InputStream getAsStream(Collection<String> nodeIds, Collection<String> logNames, Integer severity,
            String start, String end, String regex, Integer maxCount) {
        URI uri = getURI(nodeIds, null, logNames, severity, start, end, regex, maxCount);
        ClientResponse response = client.getClient().resource(uri).accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    /**
     * Gets the system logs as a stream by node name.
     * <p>
     * API Call: <tt>GET /logs</tt>
     *
     * @param nodeNames
     *            the names of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time, may be null. If specified, this will be formatted in the UTC timezone as <tt>yyyy-MM-dd_HH:mm:ss</tt>.
     * @param end
     *            the end time, may be null. If specified, this will be formatted in the UTC timezone as <tt>yyyy-MM-dd_HH:mm:ss</tt>
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as XML.
     */
    public InputStream getAsStreamByNodeName(Collection<String> nodeNames, Collection<String> logNames, Integer severity,
                                   Date start, Date end, String regex, Integer maxCount) {
        return getAsStreamByNodeName(nodeNames, logNames, severity, formatDate(start), formatDate(end), regex, maxCount);
    }

    /**
     * Gets the system logs as an XML stream by node name.
     * <p>
     * API Call: <tt>GET /logs</tt>
     *
     * @param nodeNames
     *            the names of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param end
     *            the end time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as XML.
     */
    public InputStream getAsStreamByNodeName(Collection<String> nodeNames, Collection<String> logNames, Integer severity,
                                   String start, String end, String regex, Integer maxCount) {
        URI uri = getURI(null, nodeNames, logNames, severity, start, end, regex, maxCount);
        ClientResponse response = client.getClient().resource(uri).accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    /**
     * Gets the system logs as a text stream.
     * <p>
     * API Call: <tt>GET /logs</tt>
     * 
     * @param nodeIds
     *            the IDs of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param end
     *            the end time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as text.
     */
    public InputStream getAsText(Collection<String> nodeIds, Collection<String> logNames, Integer severity, Date start,
            Date end, String regex, Integer maxCount) {
        return getAsText(nodeIds, logNames, severity, formatDate(start), formatDate(end), regex, maxCount);
    }

    /**
     * Gets the system logs as a text stream by node name.
     * <p>
     * API Call: <tt>GET /logs</tt>
     *
     * @param nodeNames
     *            the names of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param end
     *            the end time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as text.
     */
    public InputStream getAsTextByNodeName(Collection<String> nodeNames, Collection<String> logNames, Integer severity, Date start,
                                 Date end, String regex, Integer maxCount) {
        return getAsTextByNodeName(nodeNames, logNames, severity, formatDate(start), formatDate(end), regex, maxCount);
    }

    /**
     * Gets the system logs as a text stream.
     * <p>
     * API Call: <tt>GET /logs</tt>
     * 
     * @param nodeIds
     *            the IDs of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param end
     *            the end time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as text.
     */
    public InputStream getAsText(Collection<String> nodeIds, Collection<String> logNames, Integer severity,
            String start, String end, String regex, Integer maxCount) {
        URI uri = getURI(nodeIds, null, logNames, severity, start, end, regex, maxCount);
        ClientResponse response = client.getClient().resource(uri).accept(MediaType.TEXT_PLAIN).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    /**
     * Gets the system logs as a text stream by node name.
     * <p>
     * API Call: <tt>GET /logs</tt>
     *
     * @param nodeNames
     *            the names of the nodes on which logs are retrieved, if null or empty logs are retrieved on all nodes.
     * @param logNames
     *            the names of the logs to retrieve, if null or empty all logs are retrieved.
     * @param severity
     *            the severity level, may be null
     * @param start
     *            the start time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param end
     *            the end time (<tt>yyyy-MM-dd_HH:mm:ss</tt>), may be null.
     * @param regex
     *            the regular expression that logs must match, may be null or empty.
     * @param maxCount
     *            the maximum number of log messages to return, may be null. More may be returned if there are multiple logs
     *            at the same instant when max count is reached.
     * @return a stream containing the logs as text.
     */
    public InputStream getAsTextByNodeName(Collection<String> nodeNames, Collection<String> logNames, Integer severity,
                                 String start, String end, String regex, Integer maxCount) {
        URI uri = getURI(null, nodeNames, logNames, severity, start, end, regex, maxCount);
        ClientResponse response = client.getClient().resource(uri).accept(MediaType.TEXT_PLAIN).get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    private URI getURI(Collection<String> nodeIds, Collection<String> nodeNames, Collection<String> logNames, Integer severity, String startTime,
            String endTime, String regex, Integer maxCount) {
        UriBuilder builder = client.uriBuilder(PathConstants.LOGS_URL);
        if ((nodeIds != null) && (!nodeIds.isEmpty())) {
            builder.queryParam(NODE_ID, nodeIds.toArray());
        }
        if ((nodeNames != null) && (!nodeNames.isEmpty())) {
            builder.queryParam(NODE_NAME, nodeNames.toArray());
        }
        if ((logNames != null) && (!logNames.isEmpty())) {
            builder.queryParam(LOG_NAME, logNames.toArray());
        }
        if (severity != null) {
            builder.queryParam(SEVERITY, severity);
        }
        if (startTime != null) {
            builder.queryParam(START_TIME, startTime);
        }
        if (endTime != null) {
            builder.queryParam(END_TIME, endTime);
        }
        if ((regex != null) && (regex.length() > 0)) {
            builder.queryParam(REGEX, regex);
        }
        if (maxCount != null) {
            builder.queryParam(MAX_COUNT, maxCount);
        }
        return builder.build();
    }

    private String formatDate(Date date) {
        return date != null ? DateUtils.formatUTC(date, DATE_FORMAT) : null;
    }

    public LogsSearchBuilder search() {
        return new LogsSearchBuilder(this);
    }
}
