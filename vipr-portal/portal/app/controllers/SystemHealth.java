/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static controllers.Common.angularRenderArgs;
import static render.RenderProxy.renderViprProxy;
import static render.RenderSupportPackage.renderSupportPackage;
import static util.BourneUtil.getSysClient;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jobs.MinorityNodeRecoveryJob;
import jobs.RebootNodeJob;
import jobs.RestartServiceJob;
import models.datatable.NodeServicesDataTable;
import models.datatable.NodesDataTable;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import play.Logger;
import play.data.validation.Required;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.With;
import util.AdminDashboardUtils;
import util.BourneUtil;
import util.MonitorUtils;
import util.SystemLogUtils;
import util.datatable.DataTablesSupport;
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
        renderArgs.put("orderTypes", ORDER_TYPES);

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
            memoryCapacity = new Capacity((memoryStats.getMemTotal() - memoryStats.getMemFree()), memoryStats.getMemTotal());
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
            creator.setLogNames(Lists.newArrayList(service));
        }
        if (StringUtils.isNotEmpty(searchMessage)) {
            creator.setMsgRegex("(?i).*" + searchMessage + ".*");
        }
        if (StringUtils.isNotEmpty(startTime)) {
            creator.setStartTime(startTime);
        }
        if (StringUtils.isNotEmpty(endTime)) {
            creator.setEndTime(endTime);
        }
        if (severity != null && severity > 0) {
            creator.setLogSeverity(severity);
        }
        if (StringUtils.equalsIgnoreCase(orderTypes, OrderTypes.ALL.name())) {
            creator.setOrderTypes(OrderTypes.ALL);
        }
        else if (StringUtils.equals(orderTypes, OrderTypes.ERROR.name())) {
            creator.setOrderTypes(OrderTypes.ERROR);
        }
        renderSupportPackage(creator);
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
        RecoveryStatus recoveryStatus = client.control().getRecoveryStatus();

        renderArgs.put("nodeHealthList", nodeHealthList);
        renderArgs.put("clusterInfo", clusterInfo);
        renderArgs.put("recoveryStatus", recoveryStatus);
        render("@nodeRecovery");
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
