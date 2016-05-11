/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import com.emc.storageos.model.*;
import static com.emc.vipr.client.system.impl.PathConstants.CALLHOME_REGISTRATION_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CALLHOME_HEARTBEAT_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CALLHOME_ALERT_URL;
import static com.emc.vipr.client.system.impl.PathConstants.CALLHOME_ERS_URL;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;

import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.event.EventParameters;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.eventhandler.Device;

public class CallHome {

    private static final String SOURCE_PARAM = "source";
    private static final String EVENT_ID_PARAM = "event_id";
    private static final String NODE_ID_PARAM = "node_id";
    private static final String NODE_NAME_PARAM = "node_name";
    private static final String LOG_NAME_PARAM = "log_name";
    private static final String SEVERITY_PARAM = "severity";
    private static final String START_TIME_PARAM = "start";
    private static final String END_TIME_PARAM = "end";
    private static final String MSG_REGEX_PARAM = "msg_regex";
    private static final String MAX_COUNT_PARAM = "maxcount";
    private static final String FORCE_PARAM = "force";
    private static final String FORCE = "1";

    private RestClient client;

    public CallHome(RestClient client) {
        this.client = client;
    }

    /**
     * Send a registration event to ConnectEMC with configuration properties
     * <p>
     * API Call: POST /callhome/registration
     */
    public void sendRegistrationEvent() {
        client.post(String.class, CALLHOME_REGISTRATION_URL);
    }

    /**
     * Send a heartbeat event to ConnectEMC with configuration properties.
     * <p>
     * API Call: POST /callhome/registration
     */
    public void sendHeartbeatEvent() {
        client.post(String.class, CALLHOME_HEARTBEAT_URL);
    }

    /**
     * Create an alert event with error logs attached, which aid in troubleshooting
     * customer issues and sends it to ConnectEMC. Convienence method that takes fewer parameters.
     * <p>
     * API Call: POST /callhome/alert
     * 
     * @param start The start datetime of the desired time window. Value is inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime
     *            in ms. Default: Set to yesterday same time
     * @param end The end datetime of the desired time window. Value is inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime in ms.
     * @param eventParameters The event parameters
     * @return The system service task
     */
    public TaskResourceRep sendAlert(String start, String end, EventParameters eventParameters) {
        return sendAlert(null,null,null,null,null,start,end,null,null,false,eventParameters);
    }

    /**
     * Create an alert event with error logs attached, which aid in troubleshooting
     * customer issues and sends it to ConnectEMC.
     * <p>
     * API Call: POST /callhome/alert
     * 
     * @param source The service from which this API is invoked. Allowed values:
     *            CONTROLLER, OBJECT Default: CONTROLLER
     * @param eventId Event id for these alerts Allowed values: 999, 599 Default: 999
     * @param nodeIds The ids of the nodes for which log data is collected.
     *            Allowed values: standalone,syssvc-node1,syssvc-node2 etc
     * @param logNames The names of the log files to process.
     * @param severity The minimum severity level for a logged message. Allowed
     *            values:0-9. Default value: 7
     * @param start The start datetime of the desired time window. Value is inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime
     *            in ms. Default: Set to yesterday same time
     * @param end The end datetime of the desired time window. Value is inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime in ms.
     * @param msgRegex A regular expression to which the log message conforms.
     * @param maxCount Maximum number of log messages to retrieve. This may return more
     *            than max count, if there are more messages with same timestamp as
     *            of the latest message. Value should be greater than 0.
     * @param multipleRequests If true, will run multiple requests at same time.
     * @param eventParameters The event parameters
     * @return The system service task
     */
    public TaskResourceRep sendAlert(String source, Integer eventId, List<String> nodeIds,
            List<String> logNames, Integer severity, String start, String end, String msgRegex,
            Integer maxCount, boolean multipleRequests, EventParameters eventParameters) {

        UriBuilder builder = client.uriBuilder(CALLHOME_ALERT_URL);
        addQueryParam(builder, SOURCE_PARAM, source);
        addQueryParam(builder, EVENT_ID_PARAM, eventId);
        addQueryParam(builder, NODE_ID_PARAM, nodeIds);
        addQueryParam(builder, LOG_NAME_PARAM, logNames);
        addQueryParam(builder, SEVERITY_PARAM, severity);
        addQueryParam(builder, START_TIME_PARAM, start);
        addQueryParam(builder, END_TIME_PARAM, end);
        addQueryParam(builder, MSG_REGEX_PARAM, msgRegex);
        addQueryParam(builder, MAX_COUNT_PARAM, maxCount);

        if (multipleRequests) {
            addQueryParam(builder, FORCE_PARAM, FORCE);
        }

        return client.postURI(TaskResourceRep.class, eventParameters, builder.build());
    }

    /**
     * Create an alert event with error logs attached, which aid in troubleshooting
     * customer issues and sends it to ConnectEMC.
     * <p>
     * API Call: POST /callhome/alert
     *
     * @param source The service from which this API is invoked. Allowed values:
     *            CONTROLLER, OBJECT Default: CONTROLLER
     * @param eventId Event id for these alerts Allowed values: 999, 599 Default: 999
     * @param nodeNames The names of the nodes for which log data is collected.
     *            Allowed values: Current values of node_x_name properties
     * @param logNames The names of the log files to process.
     * @param severity The minimum severity level for a logged message. Allowed
     *            values:0-9. Default value: 7
     * @param start The start datetime of the desired time window. Value is inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime
     *            in ms. Default: Set to yesterday same time
     * @param end The end datetime of the desired time window. Value is inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime in ms.
     * @param msgRegex A regular expression to which the log message conforms.
     * @param maxCount Maximum number of log messages to retrieve. This may return more
     *            than max count, if there are more messages with same timestamp as
     *            of the latest message. Value should be greater than 0.
     * @param multipleRequests If true, will run multiple requests at same time.
     * @param eventParameters The event parameters
     * @return The system service task
     */
    public TaskResourceRep sendAlertByNodeName(String source, Integer eventId, List<String> nodeNames,
                                     List<String> logNames, Integer severity, String start, String end, String msgRegex,
                                     Integer maxCount, boolean multipleRequests, EventParameters eventParameters) {

        UriBuilder builder = client.uriBuilder(CALLHOME_ALERT_URL);
        addQueryParam(builder, SOURCE_PARAM, source);
        addQueryParam(builder, EVENT_ID_PARAM, eventId);
        addQueryParam(builder, NODE_NAME_PARAM, nodeNames);
        addQueryParam(builder, LOG_NAME_PARAM, logNames);
        addQueryParam(builder, SEVERITY_PARAM, severity);
        addQueryParam(builder, START_TIME_PARAM, start);
        addQueryParam(builder, END_TIME_PARAM, end);
        addQueryParam(builder, MSG_REGEX_PARAM, msgRegex);
        addQueryParam(builder, MAX_COUNT_PARAM, maxCount);

        if (multipleRequests) {
            addQueryParam(builder, FORCE_PARAM, FORCE);
        }

        return client.postURI(TaskResourceRep.class, eventParameters, builder.build());
    }

    /**
     * Retrieve virtual machine information required for ESRS setup.
     * <p>
     * API Call: GET /callhome/esrs-device
     * 
     * @return Device information
     */
    public Device getNodeDataForEsrs() {
        return client.get(Device.class, CALLHOME_ERS_URL);
    }

}
