/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static controllers.Common.angularRenderArgs;
import static render.RenderProxy.renderViprProxy;
import static render.RenderSupportPackage.renderSupportPackage;
import static render.RenderSupportDiagutilPackage.renderSupportDiagutilPackage;
import static util.BourneUtil.getSysClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.ExternalServerType;
import com.emc.storageos.management.backup.util.BackupClient;
import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.management.backup.util.SFtpClient;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.services.ServicesMetadata;
import com.emc.vipr.model.sys.diagutil.UploadParam;
import com.emc.vipr.model.sys.diagutil.UploadParam.UploadType;
import com.emc.vipr.model.sys.diagutil.LogParam;
import com.emc.vipr.model.sys.diagutil.DiagutilParam;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import com.emc.vipr.model.sys.diagutil.UploadFtpParam;
import com.emc.vipr.model.sys.recovery.RecoveryPrecheckStatus;
import jobs.CollectDiagutilDataJob;
import jobs.MinorityNodeRecoveryJob;
import jobs.RebootNodeJob;
import jobs.RestartServiceJob;
import models.datatable.NodeServicesDataTable;
import models.datatable.NodesDataTable;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.http.auth.AuthenticationException;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.With;
import util.*;
import util.datatable.DataTablesSupport;
import util.support.SupportDiagutilCreator;
import util.support.SupportPackageCreator;
import util.support.SupportPackageCreator.OrderTypes;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.systemservices.impl.healthmonitor.models.Status;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.healthmonitor.DataDiskStats;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.NodeStats;
import com.emc.vipr.model.sys.healthmonitor.ProcModels.MemoryStats;
import com.emc.vipr.model.sys.healthmonitor.ServiceHealth;
import com.emc.vipr.model.sys.healthmonitor.ServiceStats;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN") })
public class SystemHealth extends Controller {

    public static final String PARAM_NODE_ID = "nodeId";
    public static final String PARAM_SERVICE = "service";
    public static final String PARAM_SEVERITY = "severity";
    public static final String PARAM_SEARCH_MESSAGE = "searchMessage";
    public static final String PARAM_START_TIME = "startTime";
    public static final String PARAM_END_TIME = "endTime";
    public static final String PARAM_CONFIG_PROP = "redeploy";
    public static final String DBHEALTH_STATUS_SUCCESS = "SUCCESS";
    public static final String DBHEALTH_STATUS_FAIL = "FAILED";
    public static final String DBHEALTH_STATUS_FINISH = "FINISHED";

    public static final String DEFAULT_SEVERITY = "7";
    public static final String[] SEVERITIES = { "4", "5", "7", "8" };
    public static final String[] ORDER_TYPES = { OrderTypes.ERROR.name(), OrderTypes.ALL.name() };

    public static void systemHealth() {
        final ViPRSystemClient client = BourneUtil.getSysClient();

        List<NodeHealth> nodeHealthList = MonitorUtils.getNodeHealth(client);
        Map<String, Integer> statusCount = Maps.newHashMap();
        // Initialize Map so with a "Good" status to have 0 services so when we display, if no other service is "Good" it will still display
        // that in UI.
        statusCount.put(Status.GOOD.toString(), 0);
        for (NodeHealth nodeHealth : nodeHealthList) {
            Integer count = statusCount.get(nodeHealth.getStatus());
            statusCount.put(nodeHealth.getStatus(), (count == null) ? 1 : ++count);
        }

        renderArgs.put("allServices", getAllServiceNames(nodeHealthList));
        angularRenderArgs().put("clusterInfo", AdminDashboardUtils.getClusterInfo());
        renderArgs.put("dataTable", new NodesDataTable());
        angularRenderArgs().put("nodeCount", nodeHealthList.size());
        angularRenderArgs().put("statusCount", statusCount);
        render();
    }

    public static void clusterHealth() {
        Map<String, Promise<?>> promises = Maps.newHashMap();
        promises.put("nodeHealthList", AdminDashboardUtils.nodeHealthList());
        promises.put("clusterInfo", AdminDashboardUtils.clusterInfo());
        trySetRenderArgs(promises);
        render();
    }

    public static void dbHealth() {
        DbRepairStatus dbstatus = AdminDashboardUtils.gethealthdb();
        int progress = dbstatus.getProgress();
        String health = dbstatus.getStatus().toString();
        angularRenderArgs().put("progress", progress + "%");
        if (health == DBHEALTH_STATUS_SUCCESS || health == DBHEALTH_STATUS_FAIL) {
        	health = DBHEALTH_STATUS_FINISH;
        }
        renderArgs.put("health", health);
        if (dbstatus.getStartTime() != null) {
            DateTime startTime = new DateTime(dbstatus.getStartTime().getTime());
            renderArgs.put("startTime", startTime);
        }
        if (dbstatus.getLastCompletionTime() != null) {
            DateTime endTime = new DateTime(dbstatus.getLastCompletionTime().getTime());
            renderArgs.put("endTime", endTime);
        }
        render(dbstatus);
    }

    public static void nodeRecoveryVapp() {
        ViPRSystemClient client = BourneUtil.getSysClient();
        RecoveryStatus recoveryStatus = client.control().getRecoveryStatus();
        if (recoveryStatus.getStartTime()!= null ) {
            DateTime startTime = new DateTime(recoveryStatus.getStartTime().getTime());
            renderArgs.put("startTime", startTime);
        }
        if (recoveryStatus.getEndTime() != null) {
            DateTime endTime = new DateTime(recoveryStatus.getEndTime().getTime());
            renderArgs.put("endTime", endTime);
        }
        RecoveryPrecheckStatus recoveryPrecheckStatus = client.control().getRecoveryPrecheckStatus();
        String precheckMsg = "";
        switch (recoveryPrecheckStatus.getStatus()) {
            case RECOVERY_NEEDED:
                precheckMsg = Messages.get("nodeRecovery.precheck.success", recoveryPrecheckStatus.getRecoverables().toString());
                break;
            case ALL_GOOD:
                precheckMsg = Messages.get("nodeRecovery.precheck.fail.allgood");
                break;
            case VAPP_IN_DR_OR_GEO:
                precheckMsg = Messages.get("nodeRecovery.precheck.fail.drorgeo");
                break;
            case NODE_UNREACHABLE:
                precheckMsg = Messages.get("nodeRecovery.precheck.fail.unreachable");
                break;
            case CORRUPTED_NODE_COUNT_MORE_THAN_QUORUM:
                precheckMsg = Messages.get("nodeRecovery.precheck.fail.quorum");
                break;
            case CORRUPTED_NODE_FOR_OTHER_REASON:
                precheckMsg = Messages.get("nodeRecovery.precheck.fail.other");
                break;
        }
        renderArgs.put("precheckMsg", precheckMsg);
        renderArgs.put("precheckStatus", recoveryPrecheckStatus.getStatus().name());
        String recoveringMsg = Messages.get("nodeRecovery.recovering.status", recoveryPrecheckStatus.getRecoverables().toString());
        renderArgs.put("recoveringMsg", recoveringMsg);
        ClusterInfo clusterInfo = AdminDashboardUtils.getClusterInfo();
        render(recoveryStatus, clusterInfo);
    }

    public static void nodeRecovery() {
        ViPRSystemClient client = BourneUtil.getSysClient();
        RecoveryStatus recoveryStatus = client.control().getRecoveryStatus();
        if (recoveryStatus.getStartTime()!= null ) {
        	DateTime startTime = new DateTime(recoveryStatus.getStartTime().getTime());
        	renderArgs.put("startTime", startTime);
        }
        if (recoveryStatus.getEndTime() != null) {
        	DateTime endTime = new DateTime(recoveryStatus.getEndTime().getTime());
        	renderArgs.put("endTime", endTime);
        }
        ClusterInfo clusterInfo = AdminDashboardUtils.getClusterInfo();

        render(recoveryStatus, clusterInfo);
    }

    public static void nodeRecoveryReady() {
        render();
    }

    public static void startNodeRecovery() {
        minorityNodeRecovery();
    }

    /**
     * Tries to set a number of render arguments.
     * 
     * @param promises
     *            the map or key to promise.
     */
    private static void trySetRenderArgs(Map<String, Promise<?>> promises) {
        for (Map.Entry<String, Promise<?>> entry : promises.entrySet()) {
            trySetRenderArg(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Tries to set a render argument, ignoring any errors that may occur.
     * 
     * @param name
     *            the name of the render argument.
     * @param promise
     *            the promise to retrieve the value of the promise.
     */
    private static void trySetRenderArg(String name, Promise<?> promise) {
        try {
            Object value = await(promise);
            renderArgs.put(name, value);
        } catch (Exception e) {
            Throwable cause = Common.unwrap(e);
            String message = Common.getUserMessage(cause);
            renderArgs.put(name + "_error", message);
            Logger.warn(cause, "Could not set renderArg '%s'", name);
        }
    }

    public static void logs() {
        List<NodeHealth> nodeHealthList = MonitorUtils.getNodeHealth();
        ClusterInfo clusterInfo = AdminDashboardUtils.getClusterInfo();

        renderArgs.put("severities", SEVERITIES);
        if(DisasterRecoveryUtils.isActiveSite()) {
            renderArgs.put("orderTypes", ORDER_TYPES);
        }

        Set<String> controlServiceNames = getControlServiceNames(nodeHealthList, clusterInfo);
        renderArgs.put("controlServices", controlServiceNames);

        Set<String> allServiceNames = getAllServiceNames(nodeHealthList);
        renderArgs.put("allServices", allServiceNames);

        List<NodeHealth> controlNodes = getControlNodes(nodeHealthList, clusterInfo);
        renderArgs.put("controlNodes", controlNodes);

        DateTime defaultStartTime = new DateTime().minusMinutes(15);
        // Remove some logs from the default list
        Set<String> defaultServiceNames = Sets.newHashSet(allServiceNames);
        defaultServiceNames.remove(SystemLogUtils.MESSAGES_LOG);
        defaultServiceNames.remove(SystemLogUtils.NGINX_ACCESS_LOG);
        defaultServiceNames.remove(SystemLogUtils.NGINX_ERROR_LOG);
        
        renderArgs.put("allDiagnosticOptions", getDiagnosticOptions());
        renderArgs.put("defaultDiagnosticOptions", getDefaultDiagnosticOptions().values());

        loadSystemLogArgument(PARAM_NODE_ID, null);
        loadSystemLogArgument(PARAM_SERVICE, defaultServiceNames, String[].class);
        loadSystemLogArgument(PARAM_SEVERITY, DEFAULT_SEVERITY);
        loadSystemLogArgument(PARAM_SEARCH_MESSAGE, null);
        loadSystemLogArgument(PARAM_START_TIME, defaultStartTime.getMillis(), Long.class);

        Common.copyRenderArgsToAngular();
        render();
    }

    public static void services(String nodeId) {
        NodeHealth nodeHealth = MonitorUtils.getNodeHealth(nodeId);

        if (nodeHealth != null) {
            List<ServiceHealth> serviceHealthList = nodeHealth.getServiceHealthList();
            if (!serviceHealthList.isEmpty()) {
                renderArgs.put("dataTable", new NodeServicesDataTable());
                angularRenderArgs().put("nodeStatus", nodeHealth.getStatus());
                angularRenderArgs().put("serviceCount", serviceHealthList.size());
                angularRenderArgs().put("statusCount", getStatusCount(serviceHealthList));
                angularRenderArgs().put("nodeId", nodeId);
                angularRenderArgs().put("nodeName", nodeHealth.getNodeName());
                render(nodeId);
            }
            else {
                flash.error(Messages.get("system.node.services.error", nodeHealth.getNodeName()));
            }
        } else {
            Logger.warn("Could not determine node name.");
            flash.error(Messages.get("system.node.error", nodeId));
        }
        systemHealth();
    }

    public static void details(String nodeId) {
        NodeStats nodeStats = MonitorUtils.getNodeStats(nodeId);
        NodeHealth nodeHealth = MonitorUtils.getNodeHealth(nodeId);

        if (nodeStats != null && nodeHealth != null) {
            angularRenderArgs().put("nodeType", getNodeType(nodeId));
            angularRenderArgs().put("diskStats", nodeStats.getDiskStatsList());
            angularRenderArgs().put("nodeStatus", nodeHealth.getStatus());
            angularRenderArgs().put("nodeIp", nodeStats.getIp());
            renderArgs.put("healthDetails", healthDetails(nodeStats, nodeHealth));
            angularRenderArgs().put("nodeId", nodeId);
            angularRenderArgs().put("nodeName", nodeHealth.getNodeName());
            render(nodeId);
        }
        else {
            flash.error(Messages.get("system.node.error", nodeId));
            String nodeError= nodeId;
            try {
                nodeError = nodeHealth.getNodeName();
            }catch (NullPointerException e){
                Logger.warn("Could not determine node name.");
            }
            flash.error(Messages.get("system.node.error", nodeError));
            systemHealth();
        }
    }

    public static void renderNodeDetailsJson(String nodeId) {
        NodeStats nodeStats = MonitorUtils.getNodeStats(nodeId);
        NodeHealth nodeHealth = MonitorUtils.getNodeHealth(nodeId);
        Map<String, Object> healthDetails = Maps.newHashMap();

        if (nodeStats != null && nodeHealth != null) {
            healthDetails = healthDetails(nodeStats, nodeHealth);
        }
        renderJSON(healthDetails);
    }

    private static Map<String, Object> healthDetails(NodeStats nodeStats, NodeHealth nodeHealth) {
        MemoryStats memoryStats = nodeStats.getMemoryStats();
        DataDiskStats dataDiskStats = nodeStats.getDataDiskStats();
        Capacity rootCapacity = new Capacity();
        Capacity dataCapacity = new Capacity();
        Capacity memoryCapacity = new Capacity();
        if (memoryStats != null) {
            long freeMem = memoryStats.getMemFree() + memoryStats.getMemBuffers() + memoryStats.getMemCached();
            memoryCapacity = new Capacity((memoryStats.getMemTotal() - freeMem), memoryStats.getMemTotal());
        }
        if (dataDiskStats != null) {
            rootCapacity = new Capacity(dataDiskStats.getRootUsedKB(), dataDiskStats.getRootUsedKB() + dataDiskStats.getRootAvailKB());
            dataCapacity = new Capacity(dataDiskStats.getDataUsedKB(), dataDiskStats.getDataUsedKB() + dataDiskStats.getDataAvailKB());
        }
        List<ServiceHealth> serviceHealthList = nodeHealth.getServiceHealthList();
        Map<String, Integer> statusCount = getStatusCount(serviceHealthList);

        Map<String, Object> nodeDetails = Maps.newHashMap();
        nodeDetails.put("serviceCount", serviceHealthList.size());
        nodeDetails.put("cpuLoad", nodeStats.getLoadAvgStats());
        nodeDetails.put("memoryCapacity", memoryCapacity);
        nodeDetails.put("rootCapacity", rootCapacity);
        nodeDetails.put("dataCapacity", dataCapacity);
        nodeDetails.put("statusCount", statusCount);
        return nodeDetails;
    }

    public static void nodeDetails(String nodeId) {
        NodeStats nodeStats = MonitorUtils.getNodeStats(nodeId);
        NodeHealth nodeHealth = MonitorUtils.getNodeHealth(nodeId);
        if (nodeStats != null && nodeHealth != null) {
            renderArgs.put("healthDetails", healthDetails(nodeStats, nodeHealth));
        }
        render(nodeId);
    }

    public static void listDiagnosticsJson(String nodeId) {
        renderJSON(MonitorUtils.getNodeDiagnostics(nodeId));
    }

    public static void logsJson(String uri) {
        String url = BourneUtil.getSysApiUrl() + StringEscapeUtils.unescapeHtml(uri);
        renderViprProxy(url, Security.getAuthToken(), null);
    }

    public static void listNodesJson() {
        List<NodesDataTable.Nodes> dataTableNodes = Lists.newArrayList();
        for (NodeHealth node : MonitorUtils.getNodeHealth()) {
            String type = getNodeType(node.getNodeId());
            dataTableNodes.add(new NodesDataTable.Nodes(node, type));
        }
        renderJSON(DataTablesSupport.createSource(dataTableNodes, params));
    }

    private static String getNodeType(String nodeId) {
        ClusterInfo cluster = AdminDashboardUtils.getClusterInfo();
        if (isControlNode(nodeId, cluster)) {
            return Messages.get("system.node.controller");
        }
        else if (isExtraNode(nodeId, cluster)) {
            return Messages.get("system.node.dataservice");
        }
        else {
            return null;
        }
    }

    public static void listServicesJson(String nodeId) {
        List<ServiceStats> serviceStatsList = MonitorUtils.getNodeStats(nodeId).getServiceStatsList();
        List<ServiceHealth> serviceHealthList = MonitorUtils.getNodeHealth(nodeId).getServiceHealthList();

        List<NodeServicesDataTable.Services> servicesList = Lists.newArrayList();
        for (ServiceStats service : serviceStatsList) {
            for (ServiceHealth health : serviceHealthList) {
                if (service.getServiceName().equals(health.getServiceName())) {
                    servicesList.add(new NodeServicesDataTable.Services(nodeId, health, service));
                }
            }
        }
        renderJSON(DataTablesSupport.createSource(servicesList, params));
    }

    public static void diskStatsJson(String nodeId) {
        renderJSON(MonitorUtils.getNodeStats(nodeId).getDiskStatsList());
    }

    public static void proxyJson(String uri) {
        String url = BourneUtil.getSysApiUrl() + uri;
        renderViprProxy(url, Security.getAuthToken(), null);
    }

    private static Map<String, Integer> getStatusCount(List<ServiceHealth> serviceList) {
        Map<String, Integer> statusCount = Maps.newHashMap();
        // Initialize Map so with a "Good" status to have 0 services so when we display, if no other service is "Good" it will still display
        // that in UI.
        statusCount.put(Status.GOOD.toString(), 0);
        for (ServiceHealth service : serviceList) {
            Integer count = statusCount.get(service.getStatus());
            statusCount.put(service.getStatus(), (count == null) ? 1 : ++count);
        }
        return statusCount;
    }

    private static Set<String> getControlServiceNames(List<NodeHealth> nodeHealthList, ClusterInfo clusterInfo) {
        List<NodeHealth> controlNodeHealthList = extractControlNodeHealth(nodeHealthList, clusterInfo);
        Set<String> names = extractServiceNames(controlNodeHealthList);
        names.addAll(Arrays.asList(SystemLogUtils.NON_SERVICE_LOGS));
        return names;
    }

    private static Set<String> getAllServiceNames(List<NodeHealth> nodeHealthList) {
        Set<String> names = extractServiceNames(nodeHealthList);
        names.addAll(Arrays.asList(SystemLogUtils.NON_SERVICE_LOGS));
        return names;
    }

    private static Set<String> extractServiceNames(NodeHealth nodeHealth) {
        Set<String> services = Sets.newHashSet();
        if (nodeHealth != null) {
            for (ServiceHealth serviceHealth : nodeHealth.getServiceHealthList()) {
                services.add(serviceHealth.getServiceName());
            }
        }
        return services;
    }

    private static Set<String> extractServiceNames(List<NodeHealth> nodeHealthList) {
        Set<String> services = Sets.newTreeSet();
        if (nodeHealthList != null) {
            for (NodeHealth nodeHealth : nodeHealthList) {
                services.addAll(extractServiceNames(nodeHealth));
            }
        }
        return services;
    }

    private static List<NodeHealth> extractControlNodeHealth(List<NodeHealth> nodeHealthList, ClusterInfo clusterInfo) {
        List<NodeHealth> controlNodeHealths = Lists.newArrayList();
        for (NodeHealth nodeHealth : nodeHealthList) {
            if (isControlNode(nodeHealth.getNodeId(), clusterInfo)) {
                controlNodeHealths.add(nodeHealth);
            }
        }
        return controlNodeHealths;
    }

    private static List<NodeHealth> getControlNodes(List<NodeHealth> nodeHealthList, ClusterInfo clusterInfo) {
        List<NodeHealth> controlNodes = Lists.newArrayList();
        for (NodeHealth node : nodeHealthList) {
            if (isControlNode(node.getNodeId(), clusterInfo)) {
                controlNodes.add(node);
            }
        }
        return controlNodes;
    }

    private static boolean isControlNode(String nodeId, ClusterInfo clusterInfo) {
        if (clusterInfo != null && clusterInfo.getControlNodes() != null) {
            return clusterInfo.getControlNodes().keySet().contains(nodeId);
        }
        return false;
    }

    private static boolean isExtraNode(String nodeId, ClusterInfo clusterInfo) {
        if (clusterInfo != null && clusterInfo.getExtraNodes() != null) {
            return clusterInfo.getExtraNodes().keySet().contains(nodeId);
        }
        return false;
    }
    
    /**
     * Keep the same with script/diagutils
     * -min_cfs|-all_cfs|-zk|-backup|-logs|-properties|-health
     * @return
     */
    private static Map getDiagnosticOptions() {
        Map<String, String> options = getDefaultDiagnosticOptions();
        options.put("all CFs", "all_cfs");
        options.put("backup data", "backup");
        //options.put("including all CFs(-logs)", "-logs"); including -logs by default
        return options;
    }

    private static Map getDefaultDiagnosticOptions() {
        Map<String, String> options = Maps.newLinkedHashMap();
        options.put("minimum CFs", "min_cfs");
        options.put("zookeeper data", "zk");
        options.put("properties", "properties");
        options.put("health data", "health");

        return options;

    }

    private static void loadSystemLogArgument(String name, Object defaultValue) {
        loadSystemLogArgument(name, defaultValue, String.class);
    }

    private static Object loadSystemLogArgument(String name, Object defaultValue, Class type) {
        Object returnValue = null;

        String[] values = params.getAll(name);
        if (values != null && values.length > 0) {
            if (values.length == 1) {
                String value = values[0];
                returnValue = convert(value, type);
            } else {
                returnValue = convert(values, type);
            }
        } else {
            returnValue = defaultValue;
        }

        renderArgs.put(name, returnValue);

        return returnValue;
    }

    private static <T> T convert(String value, Class<T> targetType) {
        if (targetType.equals(String[].class)) {
            return (T) new String[] { value };
        }
        else {
            return (T) ConvertUtils.convert(value, targetType);
        }
    }

    private static <T> T[] convert(String[] values, Class<T> targetType) {
        if (targetType.equals(String[].class)) {
            return (T[]) values;
        }
        else {
            return (T[]) ConvertUtils.convert(values, targetType);
        }
    }

    /**
     * Collects diagutil data.
     * 
     * @param options
     *         the options for collecting diagutils data.
     * @param nodeId
     *         ViPR node ID.
     * @param services
     *         ViPR services
     * @param severity
     *         the severity of logs
     * @param searchMessage
     *         denotes a specific string to search for.
     * @param startTime
     *         denotes the starting time.
     * @param endTime
     *         denotes the ending time.
     * @param orderType
     *         denotes the type of order.
     * @param ftpType
     *         ftp transport type.
     * @param ftpAddr
     *         ftp url.
     * @param userName
     *         username for ftp server
     * @param password
     *         password for authenticating with ftp 
     */
    public static void collectDiagutilData(String[] options, String nodeId, String[] services, Integer severity, String searchMessage,
                                           String startTime, String endTime, String orderType, String ftpType, String ftpAddr,
                                           String userName, String password ) {
        List<String> optionList = null;
        if (options != null) {
            optionList = Arrays.asList(options);
        }
        UploadType uploadType = UploadType.valueOf(ftpType);
        String msgRex = null;
        if(StringUtils.isNotEmpty(searchMessage)) {
            msgRex = "(?i).*" + searchMessage + ".*";
        }
        LogParam logParam = null;
        if (services != null) {
            logParam = new LogParam(Lists.newArrayList(nodeId), Lists.newArrayList(nodeId), Arrays.asList(services),
                    severity, startTime, endTime, msgRex);//to be polished
        }
        String url = ftpAddr;
        String user = userName;
        DiagutilParam diagutilParam;
        if (logParam == null) {
            diagutilParam = new DiagutilParam(false, null, new UploadParam(uploadType, new UploadFtpParam(url, user, password)));
        }else {
            diagutilParam = new DiagutilParam(true, logParam, new UploadParam(uploadType, new UploadFtpParam(url, user, password)));
        }
        new CollectDiagutilDataJob(getSysClient(), optionList, diagutilParam).in(1);
    }

    /**
     * Collect the diagutil status.
     */
    public static void getDiagutilsStatus() {
        DiagutilInfo diagutilInfo = BourneUtil.getSysClient().diagutil().getStatus();
        renderJSON(diagutilInfo);
    }

    /**
     * Cancel the diagutil job.
     */
    public static void cancelDiagutilJob() {
        BourneUtil.getSysClient().diagutil().cancel();
    }

    /**
     * Creates a zip package for the diagutil data.
     * 
     * @param nodeId
     *         The ViPR node ID.
     * @param fileName
     *         filename to be used for zip backup.
     */
    public static void downloadDiagutilData(String nodeId, String fileName) {
        String[] file = fileName.split(File.separator);
        String zipName = file[3] + BackupConstants.COMPRESS_SUFFIX;
        SupportDiagutilCreator creator = new SupportDiagutilCreator(BourneUtil.getSysClient(), nodeId, zipName);
        renderSupportDiagutilPackage(creator, zipName);
    }

    /**
     * Validates external server settings.
     * 
     * @param serverType
     *          The server type.
     * @param serverUrl
     *          URL of the server.
     * @param user
     *          username of the server.
     * @param password
     *          password for authenticating with the server.
     */
    public static void validateExternalSettings(String serverType, String serverUrl, String user, String password) {
        Logger.info("validateExternalSettings of serverType:'%s', serverUrl:'%s', user:'%s'",serverType, serverUrl, user);
        BackupClient client = null;
       // String passwd = PasswordUtil.decryptedValue(password);
        if (serverType.equalsIgnoreCase(UploadType.sftp.name())) {
            if(serverUrl == null || serverUrl.isEmpty() || password == null){
                renderJSON(ValidationResponse.invalid(Messages.get("configProperties.backup.server.empty")));
            }  
            client = new SFtpClient(serverUrl, user, password);
        } else if (serverType.equalsIgnoreCase(UploadType.ftp.name())) {
            if(serverUrl == null || serverUrl.isEmpty() || user == null || password == null) {
                renderJSON(ValidationResponse.invalid(Messages.get("configProperties.backup.server.empty")));
            }
            if (!(serverUrl.startsWith(BackupConstants.FTP_URL_PREFIX)|| serverUrl.startsWith(BackupConstants.FTPS_URL_PREFIX))) {
                renderJSON(ValidationResponse.invalid(Messages.get("configProperties.backup.serverType.invalid")));
            }
            client = new FtpClient(serverUrl, user, password);
        }
        try {
            client.validate();
        } catch (AuthenticationException e){
            renderJSON(ValidationResponse.invalid(Messages.get("configProperties.backup.credential.invalid")));
        } catch (ConnectException e) {
            renderJSON(ValidationResponse.invalid(Messages.get("configProperties.backup.credential.invalid")));
        }
        renderJSON(ValidationResponse.valid(Messages.get("configProperties.backup.testSuccessful")));
    }

    /**
     * Download logs capability really creates the old support package.
     */
    public static void download(String nodeId, String[] service, Integer severity, String searchMessage,
            String startTime, String endTime, String orderTypes) {
        SupportPackageCreator creator = new SupportPackageCreator(request, BourneUtil.getSysClient(),
                Models.currentTenant(), BourneUtil.getCatalogClient());
        if (StringUtils.isNotEmpty(nodeId)) {
            creator.setNodeIds(Lists.newArrayList(nodeId));
        }

        if (service != null && service.length > 0) {
            List<String> logNames = getLogNames(service);
            creator.setLogNames(logNames);
        }

        if (StringUtils.isNotEmpty(searchMessage)) {
            creator.setMsgRegex("(?i).*" + searchMessage + ".*");
        }
        if (StringUtils.isNotEmpty(startTime)) {
            creator.setStartTimeWithRestriction(startTime);
        }
        if (StringUtils.isNotEmpty(endTime)) {
            creator.setEndTime(endTime);
        }
        if (severity != null && severity > 0) {
            creator.setLogSeverity(severity);
        }
        if(DisasterRecoveryUtils.isActiveSite()) {
            if (StringUtils.equalsIgnoreCase(orderTypes, OrderTypes.ALL.name())) {
                creator.setOrderTypes(OrderTypes.ALL);
            } else if (StringUtils.equals(orderTypes, OrderTypes.ERROR.name())) {
                creator.setOrderTypes(OrderTypes.ERROR);
            }
        }
        if (Security.isSystemAdmin()) {
            List<URI> tenantIds = Lists.newArrayList();
            for (TenantOrgRestRep tenant : TenantUtils.getAllTenants()) {
                tenantIds.add(tenant.getId());
            }
            creator.setTenantIds(tenantIds);
        }
        renderSupportPackage(creator);
    }

    private static List<String> getLogNames(String[] services) {
        List<String> logNames = new ArrayList();
        for (String service : services) {
            if (service.equals("controllersvc")) {
                logNames.addAll(ServicesMetadata.CONTROLLSVC_LOG_NAMES);
            }
            logNames.add(service);
        }
        return logNames;
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void getRecoveryStatus() {
        ViPRSystemClient client = BourneUtil.getSysClient();
        RecoveryStatus recoveryStatus = client.control().getRecoveryStatus();
        JsonElement jsonElement = new Gson().toJsonTree(recoveryStatus);
        JsonObject jsonObj = jsonElement.getAsJsonObject();
        if (recoveryStatus.getStartTime() != null) {
        	DateTime startTime = new DateTime(recoveryStatus.getStartTime().getTime());
        	jsonObj.addProperty("startTime", startTime.toString());
        }
        if (recoveryStatus.getEndTime() != null) {
        	DateTime endTime = new DateTime(recoveryStatus.getEndTime().getTime());
        	jsonObj.addProperty("endTime", endTime.toString());
        }
        renderJSON(jsonObj.toString());
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void nodeReboot(@Required String nodeId) {
        NodeHealth nodeHealth = MonitorUtils.getNodeHealth(nodeId);
        String node= nodeId;
        try {
            node = MonitorUtils.getNodeHealth(nodeId).getNodeName();
        }catch (NullPointerException e){
            Logger.warn("Could not determine node name.");
        }
        if(nodeHealth!=null && nodeHealth.getStatus().equals("Good")){
            new RebootNodeJob(getSysClient(), nodeId).in(3);
            flash.success(Messages.get("adminDashboard.nodeRebooting", node));
            Maintenance.maintenance(Common.reverseRoute(SystemHealth.class, "systemHealth"));
        }else{
            flash.error(Messages.get("systemHealth.message.reboot.unavailable", node));
            systemHealth();
        }
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void minorityNodeRecovery() {
        new MinorityNodeRecoveryJob(getSysClient()).in(3);
        ViPRSystemClient client = BourneUtil.getSysClient();
        List<NodeHealth> nodeHealthList = MonitorUtils.getNodeHealth();
        ClusterInfo clusterInfo = AdminDashboardUtils.getClusterInfo();
        if (PlatformUtils.isVMwareVapp()) {
            RecoveryPrecheckStatus recoveryPrecheckStatus = client.control().getRecoveryPrecheckStatus();
            String recoveringMsg = Messages.get("nodeRecovery.recovering.status", recoveryPrecheckStatus.getRecoverables().toString());
            renderArgs.put("recoveringMsg", recoveringMsg);
        }
        RecoveryStatus recoveryStatus = client.control().getRecoveryStatus();

        renderArgs.put("nodeHealthList", nodeHealthList);
        renderArgs.put("clusterInfo", clusterInfo);
        renderArgs.put("recoveryStatus", recoveryStatus);
        if (PlatformUtils.isVMwareVapp()) {
            render("@nodeRecoveryVapp");
        } else {
            render("@nodeRecovery");
        }
    }

    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void serviceRestart(@Required String nodeId, @Required String serviceName) {
        new RestartServiceJob(getSysClient(), serviceName, nodeId).in(3);
        String node= nodeId;
        try {
            node = MonitorUtils.getNodeHealth(nodeId).getNodeName();
        }catch (NullPointerException e){
            Logger.warn("Could not determine node name.");
        }
        flash.success(Messages.get("adminDashboard.serviceRestarting", serviceName, node));
        Maintenance.maintenance(Common.reverseRoute(SystemHealth.class, "services","nodeId", nodeId));
    }

    public static void downloadConfigParameters() throws UnsupportedEncodingException {
        ViPRSystemClient client = BourneUtil.getSysClient();
        PropertyInfoRestRep propertyInfo = client.config().getProperties(PARAM_CONFIG_PROP);
        Map<String, String> props = propertyInfo.getAllProperties();

        StringBuffer output = new StringBuffer();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            output.append(entry.getKey());
            output.append("=");
            output.append(entry.getValue());
            output.append("\n");
        }

        ByteArrayInputStream is = new ByteArrayInputStream(output.toString().getBytes("UTF-8"));
        renderBinary(is, "configProperties", "text/plain", false);
    }

    /**
     * If this is not an appliance, then node recovery should be available (may be a dev kit).
     * If it is an appliance and it is not a VMware app, then node recovery should be available.
     * 
     * @return Returns true if node recovery should be available.
     */
    public static boolean isNodeRecoveryEnabled() {
        boolean isEnabled = false;
        try {
            if (!PlatformUtils.isAppliance()) {
                isEnabled = true;
            } else if (!PlatformUtils.isVMwareVapp()) {
                isEnabled = true;
            }
        } catch (IllegalStateException ise) {
            // Thrown if method could not determine platform.
            Logger.warn("Could not determine platform.");
        }

        return isEnabled;
    }

    public static class Capacity {
        private long used;
        private long total;

        public Capacity(long usedInKB, long totalInKB) {
            this.used = usedInKB * 1024;
            this.total = totalInKB * 1024;
        }

        public Capacity() {
        }

        public long getUsed() {
            return used;
        }

        public void setUsed(long used) {
            this.used = used;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }
    }
}
