/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.net.URI;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.vipr.model.sys.SysSvcTask;
import com.emc.vipr.model.sys.SysSvcTaskList;
import com.emc.vipr.model.sys.logging.LogSeverity;

import com.emc.storageos.model.event.EventParameters;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.CallHomeConstants;
import com.emc.storageos.systemservices.impl.logsvc.LogRequestParam;

@Path("/callhome/")
public interface CallHomeService {

    /**
     * Internal API for sending call home alerts event. (Same as /callhome/alert)
     */
    @POST
    @Path("internal/alert/")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SysSvcTask sendInternalAlert(@QueryParam("source") String source,
                                        @DefaultValue(CallHomeConstants
                                                .SYMPTOM_CODE_REQUEST_LOGS)
                                        @QueryParam("event_id") int eventId,
                                        @QueryParam(LogRequestParam.NODE_ID) List<String>
                                                nodeIds,
                                        @QueryParam(LogRequestParam.NODE_NAME) List<String> 
                                                nodeNames,
                                        @QueryParam(LogRequestParam.LOG_NAME)
                                        List<String> logNames,
                                        @DefaultValue(LogSeverity.DEFAULT_VALUE_AS_STR)
                                        @QueryParam(LogRequestParam.SEVERITY) int severity,
                                        @QueryParam(LogRequestParam.START_TIME) String
                                                start,
                                        @QueryParam(LogRequestParam.END_TIME) String end,
                                        @QueryParam(LogRequestParam.MSG_REGEX) String
                                                msgRegex,
                                        @QueryParam(LogRequestParam.MAX_COUNT) int maxCount,
                                        EventParameters eventParameters) throws Exception;

    /**
     * Create an alert event with error logs attached, which aid in
     * troubleshooting customer issues and sends it to ConnectEMC
     * @brief Create an alert event
     *
     * @param source   The service from which this API is invoked.
     *                 Allowed values: CONTROLLER, OBJECT
     *                 Default: CONTROLLER
     * @param eventId  Event id for these alerts
     *                 Allowed values: 999, 599
     *                 Default: 999
     * @param nodeIds  The ids of the nodes for which log data is collected.
     *                 Allowed values: standalone,syssvc-node1,syssvc-node2 etc
     * @param nodeNames    The names of the nodes for which log data is collected.
     *                     Allowed values: Current values of node_x_name properties
     * @param logNames The names of the log files to process.
     * @param severity The minimum severity level for a logged message.
     *            Allowed values:0-9. Default value: 7
     * @param start The start datetime of the desired time window. Value is
     *            inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or
     *            datetime in ms.
     *            If not specified, it will start with the oldest messages
     *            first.
     * @param end The end datetime of the desired time window. Value is
     *            inclusive.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or
     *            datetime in ms.
     *            If not specified, will continue until the latest log is
     *            retrieved, or the maxCount limit is met.
     * @param msgRegex A regular expression to which the log message conforms.
     * @param maxCount Maximum number of log messages to retrieve. This may return
     *            more than max count, if there are more messages with same
     *            timestamp as of the latest message.
     *            Value should be greater than 0.
     * @param force If 1, will run multiple requests at same time.
     * @param forceAttachLogs If true, the connectemc alert is always sent out with logs
     *            attached. If the log size exceeds the max attachment size
     *            specified in logsvc.properties, the connectemc alert will
     *            not be sent.
     *            If false, the connectemc alert is sent regardless of the
     *            log size. If the log size exceeds the max attachment size,
     *            the alert is sent out without the log attachment.
     *            By default, this parameter is false.
     * @prereq ConnectEMC should be configured and system should be licensed
     */
    @POST
    @Path("alert/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SysSvcTask sendAlert(
            @Deprecated @QueryParam("source") String source,
            @DefaultValue(CallHomeConstants.SYMPTOM_CODE_REQUEST_LOGS) @QueryParam("event_id") int eventId,
            @QueryParam(LogRequestParam.NODE_ID) List<String> nodeIds,
            @QueryParam(LogRequestParam.NODE_NAME) List<String> nodeNames,
            @QueryParam(LogRequestParam.LOG_NAME) List<String> logNames,
            @DefaultValue(LogSeverity.DEFAULT_VALUE_AS_STR) @QueryParam(LogRequestParam.SEVERITY) int severity,
            @QueryParam(LogRequestParam.START_TIME) String start,
            @QueryParam(LogRequestParam.END_TIME) String end,
            @QueryParam(LogRequestParam.MSG_REGEX) String msgRegex,
            @QueryParam(LogRequestParam.MAX_COUNT) int maxCount,
            @DefaultValue("false") @QueryParam("forceAttachLogs") boolean forceAttachLogs,
            @QueryParam("force") int force,
            EventParameters eventParameters) throws Exception;

    /**
     * Get all tasks for an alert event.
     * 
     * @brief List tasks for an alert event
     * @param id Alert event id
     * @prereq none
     * @return List of tasks.
     */
    @GET
    @Path("/alert/{id}/tasks/")
    public SysSvcTaskList getTasks(@PathParam("id") URI id);

    /**
     * Get a task for an alert event.
     * 
     * @brief Get alert event task
     * @param id Alert event id
     * @param opId Alert event task id
     * @prereq none
     * @return Task details
     */
    @GET
    @Path("/alert/{id}/tasks/{op_id}/")
    public SysSvcTask getTask(@PathParam("id") URI id, @PathParam("op_id") String opId);

    /**
     * Send a registration event to ConnectEMC with configuration properties
     * 
     * @brief Send a registration event
     * @prereq ConnectEMC should be configured and system should be licensed
     * @return
     */
    @POST
    @Path("registration/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response sendRegistrationEvent();

    /**
     * Send a heartbeat event to ConnectEMC with configuration properties
     * 
     * @brief Send a heartbeat event
     * @prereq ConnectEMC should be configured and system should be licensed
     *         information
     * 
     * @return
     */
    @POST
    @Path("heartbeat/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response sendHeartbeatEvent();

    /**
     * Retrieve virtual machine information required for ESRS setup.
     * 
     * @brief Show virtual machine information required for ESRS setup
     * @prereq none
     * @return Node data information for ESRS
     */
    @GET
    @Path("esrs-device/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response getNodeDataForEsrs();
}
