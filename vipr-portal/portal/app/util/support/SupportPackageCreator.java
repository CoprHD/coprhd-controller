/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import java.io.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import controllers.security.Security;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.UnhandledException;
import org.apache.commons.lang.text.StrBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import play.Logger;
import play.Play;
import play.jobs.Job;
import play.mvc.Http;
import util.ConfigPropertyUtils;
import util.MonitorUtils;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.db.client.util.OrderTextCreator;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.search.SearchBuilder;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.sys.healthmonitor.HealthRestRep;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.StatsRestRep;
import com.google.common.collect.Sets;
import util.TenantUtils;

public class SupportPackageCreator {
    private static final String TIMESTAMP = "ddMMyy-HHmm";

    private static final String VIPR_LOG_DATE_FORMAT = "yyyy-MM-dd_HH:mm:ss";
    private static final Integer LOG_MINTUES_PREVIOUSLY = 60;
    private static final Integer ORDER_EARLIEST_START_DATE = 30;

    public enum OrderTypes {
        NONE, ERROR, ALL
    }

    // Logging Info
    private List<String> logNames;
    private List<String> nodeIds;
    private String startTime = getDefaultStartTime();
    private String endTime = "";
    private String msgRegex = null;
    private Integer logSeverity = 5; // WARN
    private OrderTypes orderTypes = OrderTypes.NONE;

    private Http.Request request;
    private ViPRSystemClient client;
    private String tenantId;
    private ViPRCatalogClient2 catalogClient;

    private List<URI> tenantIds;//if is set, means need to get orders for these tenants

    public SupportPackageCreator(Http.Request request, ViPRSystemClient client, String tenantId, ViPRCatalogClient2 catalogClient) {
        this.request = request;
        this.client = Objects.requireNonNull(client);
        this.tenantId = tenantId;
        this.catalogClient = Objects.requireNonNull(catalogClient);
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public void setLogNames(List<String> logNames) {
        this.logNames = logNames;
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds;
    }

    public void setLogSeverity(Integer logSeverity) {
        this.logSeverity = logSeverity;
    }

    public void setMsgRegex(String msgRegex) {
        this.msgRegex = msgRegex;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public void setStartTimeWithRestriction(String startTime) {
        long restrictEarliestTimestamp = getTimestampOfDaysAgo(ORDER_EARLIEST_START_DATE);
        this.startTime = restrictEarliestTimestamp > Long.parseLong(startTime) ? String.valueOf(restrictEarliestTimestamp) : startTime;
    }

    public void setOrderTypes(OrderTypes orderTypes) {
        this.orderTypes = orderTypes;
    }

    public void setTenantIds(List<URI> tenantIds) {
        this.tenantIds = tenantIds;
    }

    private String getDefaultStartTime() {
        DateTime currentTimeInUTC = new DateTime(DateTimeZone.UTC);
        DateTime startTimeInUTC = currentTimeInUTC.minusMinutes(LOG_MINTUES_PREVIOUSLY);
        DateTimeFormatter fmt = DateTimeFormat.forPattern(VIPR_LOG_DATE_FORMAT);
        return fmt.print(startTimeInUTC);
    }
    
    private long getTimestampOfDaysAgo(int days) {
        DateTime currentTimeInUTC = new DateTime(DateTimeZone.UTC);
        return currentTimeInUTC.minusDays(days).getMillis();
    }

    public static String formatTimestamp(Calendar cal) {
        final SimpleDateFormat TIME = new SimpleDateFormat(TIMESTAMP);
        return cal != null ? TIME.format(cal.getTime()) : "UNKNOWN";
    }

    private ViPRSystemClient api() {
        return client;
    }

    private ViPRCatalogClient2 catalogApi() {
        return catalogClient;
    }

    private Properties getConfig() {
        Properties props = new Properties();
        props.putAll(ConfigPropertyUtils.getPropertiesFromCoordinator());
        return props;
    }

    private String getMonitorHealthXml() {
        HealthRestRep health = api().health().getHealth();
        return marshall(health);
    }

    private String getMonitorStatsXml() {
        StatsRestRep stats = api().health().getStats();
        return marshall(stats);
    }

    private String marshall(Object obj) {
        try {
            StringWriter writer = new StringWriter();
            JAXBContext context = JAXBContext.newInstance(obj.getClass());
            context.createMarshaller().marshal(obj, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new UnhandledException(e);
        }
    }

    private String getBrowserInfo() {
        StrBuilder sb = new StrBuilder();

        if (request != null) {
            for (Map.Entry<String, Http.Header> header : request.headers.entrySet()) {
                sb.append(header.getKey()).append(": ").append(header.getValue().values).append("\n");
            }
        }
        return sb.toString();
    }

    private List<OrderRestRep> getOrders() {
        if ((orderTypes == OrderTypes.ALL) || (orderTypes == OrderTypes.ERROR)) {
            List<OrderRestRep> orders = Lists.newArrayList();
            if (tenantIds != null) {
                for (URI tenantId : tenantIds) {
                    SearchBuilder<OrderRestRep> search = catalogApi().orders().search().byTimeRange(
                            this.startTime, this.endTime, tenantId);
                    if (orderTypes == OrderTypes.ERROR) {
                        search.filter(new FailedOrderFilter());
                    }
                    List<OrderRestRep> tenantOrders = search.run();
                    Logger.info("Found %s Orders for tenantId %s", tenantOrders.size(), tenantId);
                    orders.addAll(tenantOrders);
                }
            } else {
                SearchBuilder<OrderRestRep> search = catalogApi().orders().search().byTimeRange(
                        this.startTime, this.endTime);
                if (orderTypes == OrderTypes.ERROR) {
                    search.filter(new FailedOrderFilter());
                }
                orders = search.run();
                Logger.info("Found %s Orders", orders.size());
            }
            return orders;
        } else {
            return Collections.emptyList();
        }
    }

    private Map<String,String> getSelectedNodeIds() {
        Map<String,String> activeNodeIds = Maps.newTreeMap();

        for (NodeHealth activeNode : MonitorUtils.getNodeHealth(api())) {
            if (!StringUtils.containsIgnoreCase(activeNode.getStatus(), "unavailable") || Play.mode.isDev()) {
                activeNodeIds.put(activeNode.getNodeId(),activeNode.getNodeName());
            }
        }

        Map<String,String> selectedNodeIds = Maps.newTreeMap();
        if ((nodeIds == null) || nodeIds.isEmpty()) {
            selectedNodeIds.putAll(activeNodeIds);
        }
        else {
            for (String node :nodeIds) {
                if (activeNodeIds.containsKey(node))
                    selectedNodeIds.put(node, activeNodeIds.get(node));
            }
        }
        return selectedNodeIds;
    }

    public CreateSupportPackageJob createJob(OutputStream out) {
        return new CreateSupportPackageJob(out, this);
    }

    public void writeTo(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            writeConfig(zip);
            writeSystemInfo(zip);
            writeOrders(zip);
            writeLogs(zip);
            zip.flush();
        } finally {
            zip.close();
        }
    }

    private OutputStream nextEntry(ZipOutputStream zip, String path) throws IOException {
        Logger.debug("Adding entry: %s", path);
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        return new CloseShieldOutputStream(zip);
    }

    private void addBinaryEntry(ZipOutputStream zip, String path, byte[] data) throws IOException {
        Logger.debug("Adding entry: %s", path);
        ZipEntry entry = new ZipEntry(path);
        entry.setSize(data.length);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
        zip.flush();
    }

    private void addStringEntry(ZipOutputStream zip, String path, String data) throws IOException {
        addBinaryEntry(zip, path, data.getBytes("UTF-8"));
    }

    private void writeConfig(ZipOutputStream zip) throws IOException {
        Properties config = getConfig();
        config.store(nextEntry(zip, "info/config.properties"), "");
    }

    private void writeSystemInfo(ZipOutputStream zip) throws IOException {
        addStringEntry(zip, "info/MonitorHealth.xml", getMonitorHealthXml());
        addStringEntry(zip, "info/MonitorStats.xml", getMonitorStatsXml());
        addStringEntry(zip, "info/BrowserInfo.txt", getBrowserInfo());
    }

    private void writeOrders(ZipOutputStream zip) throws IOException {
        for (OrderRestRep order : getOrders()) {
            writeOrder(zip, order);
        }
    }

    private void writeOrder(ZipOutputStream zip, OrderRestRep order) throws IOException {
        String path = String.format("orders/%s", OrderTextCreator.genereateOrderFileName(order));
        addStringEntry(zip, path, getOrderTextCreator(order).getText());
        Logger.debug("Written Order " + order.getId() + " to archive");
    }

    private OrderTextCreator getOrderTextCreator(OrderRestRep order) {
        ViPRCatalogClient2 client = catalogApi();
        OrderTextCreator creator = new OrderTextCreator();
        creator.setOrder(order);
        creator.setService(client.services().get(order.getCatalogService()));
        creator.setState(client.orders().getExecutionState(order.getId()));
        creator.setLogs(client.orders().getLogs(order.getId()));
        creator.setExeLogs(client.orders().getExecutionLogs(order.getId()));
        return creator;
    }

    private void writeLogs(ZipOutputStream zip) throws IOException {
        if (logNames != null) {
            // Ensure no duplicate log names
            Set<String> selectedLogNames = Sets.newLinkedHashSet(logNames);
            Map<String,String> selectedNodeIds = getSelectedNodeIds();
            for (String nodeId : selectedNodeIds.keySet()) {
                String nodeName = selectedNodeIds.get(nodeId);
                for (String logName : selectedLogNames) {
                    writeLog(zip, nodeId, nodeName, logName);
                }
            }
        }
    }

    private void writeLog(ZipOutputStream zip, String nodeId, String nodeName, String logName) throws IOException {
        Set<String> nodeIds = Collections.singleton(nodeId);
        Set<String> logNames = Collections.singleton(logName);
        OutputStream stream = null;

        InputStream in = api().logs().getAsText(nodeIds, null, logNames, logSeverity, startTime, endTime, msgRegex, null);
        long size = 1024*1024*300L;
        int partId = 2;
        long writeSize = 0L;
        String line = null;
        String path = String.format("logs/%s_%s_%s.log", logName, nodeId, nodeName);
        stream = nextEntry(zip, path);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(stream));
        try {
            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.newLine();
                writeSize = writeSize + line.length();
                if (writeSize > size) {
                    bw.close();
                    path = String.format("logs/%s_%s_%s_%d.log", logName, nodeId, nodeName,partId);
                    stream = nextEntry(zip, path);
                    bw = new BufferedWriter(new OutputStreamWriter(stream));
                    writeSize = 0L;
                    partId++;
                }
            }
        } finally {
            br.close();
            IOUtils.closeQuietly(bw);
        }
    }

    /**
     * Filter for failed orders.
     */
    private static class FailedOrderFilter extends DefaultResourceFilter<OrderRestRep> {
        @Override
        public boolean accept(OrderRestRep item) {
            return StringUtils.equals(OrderStatus.ERROR.name(), item.getOrderStatus());
        }
    }

    /**
     * Job that runs to generate a support package.
     * 
     * @author jonnymiller
     */
    public static class CreateSupportPackageJob extends Job {
        private OutputStream out;
        private SupportPackageCreator supportPackage;

        public CreateSupportPackageJob(OutputStream out, SupportPackageCreator supportPackage) {
            this.out = out;
            this.supportPackage = supportPackage;
        }

        @Override
        public void doJob() throws Exception {
            supportPackage.writeTo(out);
        }
    }
}
