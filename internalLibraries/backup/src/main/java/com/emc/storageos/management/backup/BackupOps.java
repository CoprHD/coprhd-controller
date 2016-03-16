/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.vipr.model.sys.backup.BackupInfo;
import org.apache.commons.io.FilenameUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.base.Preconditions;

import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.Service;

import com.emc.storageos.services.util.FileUtils;
import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.management.backup.exceptions.BackupException;
import com.emc.storageos.management.backup.exceptions.RetryableBackupException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.vipr.model.sys.recovery.RecoveryConstants;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.ClusterInfo;

public class BackupOps {
    private static final Logger log = LoggerFactory.getLogger(BackupOps.class);
    private static final String IP_ADDR_DELIMITER = ":";
    private static final String IP_ADDR_FORMAT = "%s" + IP_ADDR_DELIMITER + "%d";
    private static final Format FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final String BACKUP_FILE_PERMISSION = "644";
    private String serviceUrl = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    private Map<String, String> hosts;
    private Map<String, String> dualAddrHosts;
    private List<Integer> ports;
    private CoordinatorClient coordinatorClient;
    private int quorumSize;
    private List<String> vdcList;
    private File backupDir;

    private DrUtil drUtil;

    public DrUtil getDrUtil() {
        return drUtil;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    private void checkOnStandby() {
        if (drUtil.isStandby()) {
            log.error("Backup and restore operations on standby site are forbidden");
            throw BackupException.fatals.forbidBackupOnStandbySite();
        }
    }

    /**
     * Default constructor.
     */
    BackupOps() {
    }

    /**
     * Sets jmx service url
     * 
     * @param serviceUrl
     *            The string format of jmx service url
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * Sets jmx service hosts
     * 
     * @param hosts
     *            The list of jmx service hosts
     */
    void setHosts(Map<String, String> hosts) {
        this.hosts = hosts;
        this.quorumSize = hosts.size() / 2 + 1;
    }

    /**
     * Normalize DualInetAddress so to persist String into _info.properties file
     * 
     * @param host
     * @return return ipv4 if host only contains ipv4 return [ipv6] if host only
     *         contains ipv6 return ipv4/[ipv6] if both ipv4 and ipv6 are
     *         configured
     */
    private String normalizeDualInetAddress(DualInetAddress host) {
        if (!host.hasInet4() && !host.hasInet6()) {
            return null;
        }
        if (host.hasInet4() && host.hasInet6()) {
            StringBuilder sb = new StringBuilder();
            sb.append(host.getInet4()).append(BackupConstants.HOSTS_IP_DELIMITER)
                    .append("[").append(host.getInet6()).append("]");
            return sb.toString();
        } else if (host.hasInet4()) {
            return host.getInet4();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(host.getInet6()).append("]");
            return sb.toString();
        }
    }

    /**
     * Get InetAddressLookupMap from coordinatorClient
     */
    private CoordinatorClientInetAddressMap getInetAddressLookupMap() {
        Preconditions.checkNotNull(coordinatorClient,
                "Please initialize coordinator client before any operations");
        return coordinatorClient.getInetAddessLookupMap();
    }

    /**
     * Gets ViPR hosts from coordinator client, update it if necessary
     * 
     * @return map of node id to IP address for each ViPR host
     */
    public Map<String, String> getHosts() {
        if (hosts == null || hosts.isEmpty()) {
            hosts = initHosts();
        }
        return hosts;
    }

    private synchronized Map<String, String> initHosts() {
        if (hosts != null && !hosts.isEmpty()) {
            return hosts;
        }
        CoordinatorClientInetAddressMap addressMap = getInetAddressLookupMap();
        hosts = new TreeMap<>();
        for (String nodeId : addressMap.getControllerNodeIPLookupMap().keySet()) {
            try {
                String ipAddr = addressMap.getConnectableInternalAddress(nodeId);
                DualInetAddress inetAddress = DualInetAddress.fromAddress(ipAddr);
                String host = normalizeDualInetAddress(inetAddress);
                hosts.put(nodeId, host);
            } catch (Exception ex) {
                throw BackupException.fatals.failedToGetHost(nodeId, ex);
            }
        }
        this.quorumSize = hosts.size() / 2 + 1;
        return hosts;
    }

    /**
     * Gets a map of node id and IP addresses, update it if necessary
     * 
     * @return map of node id to IP address(both IPv4 and IPv6 if configured)
     *         for each ViPR host
     */
    // Suppress Sonar violation of Multithreaded correctness
    // This is a get method, it's thread safe
    @SuppressWarnings("findbugs:IS2_INCONSISTENT_SYNC")
    private Map<String, String> getHostsWithDualInetAddrs() {
        if (dualAddrHosts == null || dualAddrHosts.isEmpty()) {
            dualAddrHosts = initDualAddrHosts();
        }
        return dualAddrHosts;
    }

    private synchronized Map<String, String> initDualAddrHosts() {
        if (dualAddrHosts != null && !dualAddrHosts.isEmpty()) {
            return dualAddrHosts;
        }
        dualAddrHosts = new TreeMap<>();
        CoordinatorClientInetAddressMap addressMap = getInetAddressLookupMap();
        for (String nodeId : addressMap.getControllerNodeIPLookupMap().keySet()) {
            String normalizedHost = normalizeDualInetAddress(addressMap.get(nodeId));
            if (normalizedHost == null) {
                throw BackupException.fatals
                        .failedToGetValidDualInetAddress("Neither IPv4 or IPv6 address is configured");
            }
            dualAddrHosts.put(nodeId, normalizedHost);
        }
        return dualAddrHosts;
    }

    public int getQuorumSize() {
        return this.quorumSize;
    }

    /**
     * Sets jmx service ports
     * 
     * @param ports
     *            The list of jmx service ports
     */
    public void setPorts(List<Integer> ports) {
        this.ports = ports;
    }

    /**
     * Sets coordinator client
     * 
     * @param coordinatorClient
     *            The instance of coordinator client
     */
    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    /**
     * Gets vdc list
     */
    public List<String> getVdcList() {
        return vdcList;
    }

    /**
     * Sets vdc list
     * 
     * @param vdcList
     *            The list of vdcs
     */
    public void setVdcList(List<String> vdcList) {
        this.vdcList = vdcList;
    }

    public File getBackupDir() {
        return backupDir;
    }

    public void setBackupDir(File backupDir) {
        this.backupDir = backupDir;
    }

    public File getDownloadDirectory(String remoteBackupFilename) {
        int dotIndex=remoteBackupFilename.lastIndexOf(".");
        String backupFolder=remoteBackupFilename.substring(0, dotIndex);

        return new File(BackupConstants.RESTORE_DIR, backupFolder);
    }

    public Map<String, Long> getInternalDownloadSize(String backupName) {
        Map<String, Long> downloadSize = new HashMap();

        File folder = getDownloadDirectory(backupName);
        File[] files = getBackupFiles(folder);

        try {
            String localHostName = InetAddress.getLocalHost().getHostName();
            Map<String, URI> nodes = getNodesInfo();
            for (Map.Entry<String, URI> node : nodes.entrySet()) {
                String hostname = toHostName(node.getKey());
                if (hostname.equals(localHostName)) {
                    continue; // zip file has already been downloaded
                }
                long size = 0;
                for (File f : files) {
                    if (belongToNode(f, hostname)) {
                        size += f.length();
                    }
                }
                downloadSize.put(hostname, size);
            }
        }catch(URISyntaxException | UnknownHostException e) {
            log.error("Failed to set download size e=", e.getMessage());
        }

        return downloadSize;
    }
    /**
     * Create backup file on all nodes
     * 
     * @param backupTag
     *            The tag of this backup
     */
    public void createBackup(String backupTag) {
        createBackup(backupTag, false);
    }

    /**
     * Create backup file on all nodes
     * 
     * @param backupTag
     *            The tag of this backup
     * @param force
     *            Ignore the errors during the creation
     */
    public void createBackup(String backupTag, boolean force) {
        checkOnStandby();
        if (backupTag == null) {
            backupTag = createBackupName();
        } else {
            validateBackupName(backupTag);
        }
        precheckForCreation(backupTag);

        InterProcessLock backupLock = null;
        InterProcessLock recoveryLock = null;
        try {
            backupLock = getLock(BackupConstants.BACKUP_LOCK, BackupConstants.LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            recoveryLock = getLock(RecoveryConstants.RECOVERY_LOCK, BackupConstants.LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            createBackupWithoutLock(backupTag, force);
        } finally {
            releaseLock(recoveryLock);
            releaseLock(backupLock);
        }
    }

    public void checkBackup(File backupFolder) throws Exception {
        File[] backupFiles = getBackupFiles(backupFolder);

        if (backupFiles == null) {
            String errMsg = String.format("The %s contains no backup files", backupFolder.getAbsolutePath());
            throw new RuntimeException(errMsg);
        }

        File infoPropertyFile = null;
        boolean isGeo = false;
        boolean found_db_file=false;
        boolean found_geodb_file=false;
        String fullFileName = null;

        for (File file : backupFiles) {
            fullFileName = file.getAbsolutePath();

            if (fullFileName.endsWith(BackupConstants.BACKUP_INFO_SUFFIX)) {
                // it's a property info file
                infoPropertyFile = file;
                continue;
            }

            String filename = file.getName();

            if (isGeoBackup(filename)) {
                isGeo = true;
            }

            if (filename.contains("_db_")) {
                found_db_file = true;
            }else if (filename.contains(BackupType.geodb.toString()) || filename.contains(BackupType.geodbmultivdc.toString())) {
                found_geodb_file = true;
            }

            checkMD5(file);
        }

        log.info("found db {} geodb {}", found_db_file, found_geodb_file);
        if (!found_db_file) {
            String errMsg = String.format("%s does not contain db files", backupFolder.getAbsolutePath());
            throw new RuntimeException(errMsg);
        }

        if (!found_geodb_file) {
            String errMsg = String.format("%s does not contain geodb files", backupFolder.getAbsolutePath());
            throw new RuntimeException(errMsg);
        }

        if (infoPropertyFile == null) {
            String errMsg = String.format("%s does not contain property file", backupFolder.getAbsolutePath());
            throw new RuntimeException(errMsg);
        }

        checkBackup(infoPropertyFile, isGeo);
    }

    public List<URI> getOtherNodes() throws URISyntaxException, UnknownHostException {
        Map<String, URI> nodes = getNodesInfo();
        String localHostName = InetAddress.getLocalHost().getHostName();
        List<URI> uris = new ArrayList();
        for (Map.Entry<String, URI> node : nodes.entrySet()) {
            String hostname = toHostName(node.getKey());
            if (hostname.equals(localHostName)) {
                continue;
            }

            uris.add(node.getValue());
        }
        return uris;
    }

    public URI getMyURI() throws URISyntaxException, UnknownHostException {
        Map<String, URI> nodes = getNodesInfo();
        String localHostName = InetAddress.getLocalHost().getHostName();
        for (Map.Entry<String, URI> node : nodes.entrySet()) {
            String hostname = toHostName(node.getKey());
            if (hostname.equals(localHostName)) {
                return node.getValue();
            }
        }

        log.error("Can't find my URI localhost={}", localHostName);
        
        return null;
    }

    public List<String> getBackupFileNames(File backupFolder) throws UnknownHostException, URISyntaxException {
        File[] backupFiles = getBackupFiles(backupFolder);

        if (backupFiles == null) {
            String errMsg = String.format("The %s contains no backup files", backupFolder.getAbsolutePath());
            throw new RuntimeException(errMsg);
        }

        List<String> filenames = new ArrayList();

        for (File f : backupFiles) {
            filenames.add(f.getName());
        }

        return filenames;
    }

    private String toHostName(String nodeName) {
        return nodeName.replace("node", "vipr");
    }

    private boolean belongToNode(File file, String nodeName) {
        String filename = file.getName();
        return filename.contains(nodeName) ||
               filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
               filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private File[] getBackupFiles(File backupFolder) {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(BackupConstants.COMPRESS_SUFFIX) || name.endsWith(BackupConstants.BACKUP_INFO_SUFFIX);
            }
        };

        return backupFolder.listFiles(filter);
    }

    private void checkBackup(File propertyInfoFile, boolean isGeo) throws Exception {
        RestoreManager manager = new RestoreManager();
        CoordinatorClientImpl client = (CoordinatorClientImpl) coordinatorClient;
        manager.setNodeCount(client.getNodeCount());

        DualInetAddress addresses = coordinatorClient.getInetAddessLookupMap().getDualInetAddress();
        String ipaddress4 = addresses.getInet4();
        String ipaddress6 = addresses.getInet6();
        manager.setIpAddress4(ipaddress4);
        manager.setIpAddress6(ipaddress6);
        manager.setEnableChangeVersion(false);

        manager.checkBackupInfo(propertyInfoFile, isGeo);
    }

    /* We support 3-nodes-to-5-nodes restore, so
     * there can be no data on vipr4 and vipr5, but there should
     * have data on vipr1, vipr2 and vipr3.
     */
    public boolean shouldHaveBackupData() {
        String myNodeId = getCurrentNodeId();
        log.info("myNodeId={}", myNodeId);

        return myNodeId.equals("vipr1") || myNodeId.equals("vipr2") || myNodeId.equals("vipr3");
    }

    public void addRestoreListener(NodeListener listener) throws Exception {
        coordinatorClient.addNodeListener(listener);
    }

    public void removeRestoreListener(NodeListener listener) throws Exception {
        coordinatorClient.removeNodeListener(listener);
    }

    /**
     * Query restore status from ZK
    */
    public BackupRestoreStatus queryBackupRestoreStatus(String backupName, boolean isLocal) {
        Configuration cfg = coordinatorClient.queryConfiguration(coordinatorClient.getSiteId(),
                getBackupConfigKind(isLocal), backupName);
        Map<String, String> allItems = (cfg == null) ? new HashMap<String, String>() : cfg.getAllConfigs(false);

        BackupRestoreStatus restoreStatus = new BackupRestoreStatus(allItems);
        return restoreStatus;
    }

    public String getBackupConfigKind(boolean isLocal) {
        return isLocal ? BackupConstants.LOCAL_RESTORE_KIND_PREFIX : BackupConstants.REMOTE_RESTORE_KIND_PREFIX;
    }

    public String getBackupConfigPrefix() {
        String path = getBackupConfigKind(false);
        StringBuilder builder = new StringBuilder("/sites/");
        builder.append(coordinatorClient.getSiteId());
        builder.append("/config/");
        builder.append(path);

        return builder.toString();
    }

    public void clearCurrentBackupInfo() {
        log.info("clear current backup info");
        persistCurrentBackupInfo("", false);
    }

    public void persistCurrentBackupInfo(String backupName, boolean isLocal) {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(BackupConstants.PULL_RESTORE_STATUS);
        config.setId(Constants.GLOBAL_ID);
        config.setConfig(BackupConstants.CURRENT_DOWNLOADING_BACKUP_NAME_KEY, backupName);
        config.setConfig(BackupConstants.CURRENT_DOWNLOADING_BACKUP_ISLOCAL_KEY, Boolean.toString(isLocal));

        coordinatorClient.persistServiceConfiguration(coordinatorClient.getSiteId(), config);
        log.info("Persist current backup info to zk successfully");
    }

    public String getCurrentBackupName() {
        Map<String, String> currentBackupInfo = getCurrentBackupInfo();
        return currentBackupInfo.get(BackupConstants.CURRENT_DOWNLOADING_BACKUP_NAME_KEY);
    }

    private Map<String, String> getCurrentBackupInfo() {
        Configuration cfg = coordinatorClient.queryConfiguration(coordinatorClient.getSiteId(),
                BackupConstants.PULL_RESTORE_STATUS, Constants.GLOBAL_ID);

        Map<String, String> allItems = (cfg == null) ? new HashMap<String, String>() : cfg.getAllConfigs(false);

        // The map should has only 4 entries: _kind, _id, backupname and isLocal
        if (!allItems.isEmpty() && allItems.size() != 4) {
            log.error("Invalid current backup info from zk: {}", allItems);
            throw new RuntimeException("invalid current backup info from zk");
        }

        return allItems;
    }

    /**
     * Persist download status to ZK
     */
    public void setBackupFileNames(String backupName, List<String> filenames) {
        updateRestoreStatus(backupName, null, null, null, 0, false, filenames, true, false);
    }

    public void setRestoreStatus(String backupName, BackupRestoreStatus.Status s, String details,
                                 boolean increaseCompleteNumber, boolean doLock) {
        updateRestoreStatus(backupName, s, details, null, 0, increaseCompleteNumber, null, true, doLock);
    }

    public void updateDownloadedSize(String backupName, long size, boolean doLock) {
        updateRestoreStatus(backupName, null, null, null, size, false, null, false, doLock);
    }

    public long getSizeToDownload(String backupName) throws UnknownHostException {
        BackupRestoreStatus s = queryBackupRestoreStatus(backupName, false);
        Map<String, Long> map = s.getSizeToDownload();

        String localHostName = InetAddress.getLocalHost().getHostName();

        return map.get(localHostName);
    }

    public void updateRestoreStatus(String backupName, BackupRestoreStatus.Status status, String details,
                                     Map<String, Long>downloadSize, long increasedSize, boolean increaseCompletedNodeNumber,
                                     List<String> backupfileNames, boolean doLog, boolean doLock) {
        InterProcessLock lock = null;
        try {
            if (doLock) {
                lock = getLock(BackupConstants.RESTORE_STATUS_UPDATE_LOCK,
                        -1, TimeUnit.MILLISECONDS); // -1= no timeout
            }

            if (doLog) {
                log.info("get lock {}", BackupConstants.RESTORE_STATUS_UPDATE_LOCK);
            }

            BackupRestoreStatus s = queryBackupRestoreStatus(backupName, false);

            if (!canBeUpdated(status, s)) {
                return;
            }

            s.setBackupName(backupName);

            if (status != null) {
                s.setStatusWithDetails(status, details);
            }

            if (downloadSize != null) {
                s.setSizeToDownload(downloadSize);
            }

            if (increasedSize > 0) {
                try {
                    String localHostName = InetAddress.getLocalHost().getHostName();
                    s.increaseDownloadedSize(localHostName, increasedSize);
                }catch (UnknownHostException e) {
                    log.error("Failed to set downloaded size e=", e);
                }
            }

            if (increaseCompletedNodeNumber) {
                s.increaseNodeCompleted();
            }

            if (backupfileNames != null) {
                s.setBackupFileNames(backupfileNames);
            }

            updateBackupRestoreStatus(s);

            persistBackupRestoreStatus(s, false, doLog);

            if (doLog) {
                log.info("Persist backup restore status {} to zk successfully", s);
            }
        }finally {
            if (doLog) {
                log.info("To release lock {}", BackupConstants.RESTORE_STATUS_UPDATE_LOCK);
            }

            if (doLock) {
                releaseLock(lock);
            }
        }

    }

    private void updateBackupRestoreStatus(BackupRestoreStatus s) {
        BackupRestoreStatus.Status restoreStatus = s.getStatus();
        if ( restoreStatus == BackupRestoreStatus.Status.DOWNLOADING) {
            long nodeNumber = getHosts().size();
            if (s.getNodeCompleted() == nodeNumber ) {
                s.setStatusWithDetails(BackupRestoreStatus.Status.DOWNLOAD_SUCCESS, null);
                restoreStatus = BackupRestoreStatus.Status.DOWNLOAD_SUCCESS;
            }
        }

        if (restoreStatus == BackupRestoreStatus.Status.DOWNLOAD_SUCCESS ||
                restoreStatus == BackupRestoreStatus.Status.DOWNLOAD_CANCELLED ||
                restoreStatus  == BackupRestoreStatus.Status.DOWNLOAD_FAILED ) {
            clearCurrentBackupInfo();
        }
    }

    private boolean canBeUpdated(BackupRestoreStatus.Status status, BackupRestoreStatus s) {
        if (status == BackupRestoreStatus.Status.RESTORE_FAILED
                || status == BackupRestoreStatus.Status.RESTORING
                || status == BackupRestoreStatus.Status.RESTORE_SUCCESS) {
            return true;
        }

        if (s.getStatus() == BackupRestoreStatus.Status.DOWNLOAD_SUCCESS) {
            return false;
        }

        if ( (status == BackupRestoreStatus.Status.DOWNLOAD_CANCELLED) && (!s.getStatus().canBeCanceled())) {
            log.info("current status {} can't be canceled", s);
            return false;
        }

        return true;
    }

    public void persistBackupRestoreStatus(BackupRestoreStatus status, boolean isLocal, boolean doLog) {
        if (doLog) {
            log.info("Persist backup restore status {}", status);
        }

        Map<String, String> allItems = status.toMap();

        ConfigurationImpl config = new ConfigurationImpl();
        String backupName = status.getBackupName();
        config.setKind(getBackupConfigKind(isLocal));
        config.setId(backupName);

        for (Map.Entry<String, String> entry : allItems.entrySet()) {
            config.setConfig(entry.getKey(), entry.getValue());
        }

        coordinatorClient.persistServiceConfiguration(coordinatorClient.getSiteId(), config);

    }

    public synchronized  void setGeoFlag(String backupName, boolean isLocal) {
        BackupRestoreStatus state = queryBackupRestoreStatus(backupName, isLocal);
        state.setIsGeo(true);
        log.info("Persist backup restore status {} stack=", state, new Throwable());
        Map<String, String> allItems = state.toMap();

        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(getBackupConfigKind(isLocal));
        config.setId(backupName);

        for (Map.Entry<String, String> entry : allItems.entrySet()) {
            config.setConfig(entry.getKey(), entry.getValue());
        }

        coordinatorClient.persistServiceConfiguration(coordinatorClient.getSiteId(), config);
        log.info("Persist backup restore status to zk successfully");
    }

    /**
     * To check if the MD5 of the file equals to MD5 in ${file}.md5
     * The MD5 file is one-line file and should have following formate:
     * MD5  FILE-SIZE  FILE
     *
     * @param file the file to be checked
     */
    private void checkMD5(File file) {
        log.info("To check {}", file.getAbsolutePath());

        try {
            String generatedMD5 = Files.hash(file, Hashing.md5()).toString();

            String md5Filename = file.getAbsolutePath() + BackupConstants.MD5_SUFFIX;

            File md5File = new File(md5Filename);

            if (!md5File.exists()) {
                String errMsg = String.format("The MD5 file %s not exist", md5Filename);
                throw new RuntimeException(errMsg);
            }

            List<String> lines = Files.readLines(md5File, Charset.defaultCharset());
            if (lines.size() != 1) {
                String errMsg = String.format("Invalid md5 file %s: more than 1 line", md5Filename);
                throw new RuntimeException(errMsg);
            }

            String[] tokens = lines.get(0).split("\\s");

            if (tokens.length != 3) {
                String errMsg = String.format("Invalid md5 file %s : only 3 fields allowed in a line", md5Filename);
                throw new RuntimeException(errMsg);
            }

            if (!generatedMD5.equals(tokens[0])) {
                String errMsg = String.format("%s: MD5 doesn't match ", md5Filename);
                throw new RuntimeException(errMsg);
            }
        } catch (IOException e) {
            String errMsg = String.format("Failed to check MD5 of %s: %s ", file.getAbsolutePath(), e.getMessage());
            throw new RuntimeException(errMsg);
        }
    }

    public boolean isGeoBackup(String backupFileName) {
        return backupFileName.contains("multivdc");
    }

    public void cancelDownload() {
        Map<String, String> map = getCurrentBackupInfo();
        log.info("To cancel current download {}", map);
        String backupName = map.get(BackupConstants.CURRENT_DOWNLOADING_BACKUP_NAME_KEY);
        boolean isLocal = Boolean.parseBoolean(map.get(BackupConstants.CURRENT_DOWNLOADING_BACKUP_ISLOCAL_KEY));

        if (backupName.isEmpty()) {
            log.info("No backup is downloading, so ignore cancel");
            return;
        }

        if (!isLocal) {
            setRestoreStatus(backupName, BackupRestoreStatus.Status.DOWNLOAD_CANCELLED, null, false, true);
            log.info("Persist the cancel flag into ZK");
        }
    }

    public File getBackupDir(String backupName, boolean isLocal) {
        String name = backupName;
        if (backupName.endsWith(BackupConstants.COMPRESS_SUFFIX)) {
            name = FilenameUtils.removeExtension(backupName);
        }

        return isLocal ? new File(getBackupDir(), name) : new File(BackupConstants.RESTORE_DIR, name);
    }

    class CreateBackupCallable extends BackupCallable<Void> {
        @Override
        public Void sendRequest() throws Exception {
            createBackupFromNode(this.backupTag, this.host, this.port);
            return null;
        }
    }

    private void createBackupWithoutLock(String backupTag, boolean force) {
        for (int retryCnt = 0; retryCnt < BackupConstants.RETRY_MAX_CNT; retryCnt++) {
            List<String> errorList = new ArrayList<String>();
            Throwable result = null;
            try {
                List<BackupProcessor.BackupTask<Void>> backupTasks =
                        (new BackupProcessor(getHosts(), ports, backupTag))
                                .process(new CreateBackupCallable(), true);
                for (BackupProcessor.BackupTask task : backupTasks) {
                    try {
                        task.getResponse().getFuture().get();
                    } catch (CancellationException e) {
                        log.warn("The task of create backup was canceled", e);
                    } catch (InterruptedException e) {
                        errorList.add(String.format(IP_ADDR_FORMAT,
                                task.getRequest().getHost(), task.getRequest().getPort()));
                        log.error(String.format("Create backup on node(%s:%d) failed.",
                                task.getRequest().getHost(), task.getRequest().getPort()), e);
                        result = ((result == null) ? e : result);
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (ignoreError(cause)) {
                            errorList.add(String.format(IP_ADDR_FORMAT,
                                    "follower", task.getRequest().getPort()));
                        } else {
                            errorList.add(String.format(IP_ADDR_FORMAT,
                                    task.getRequest().getHost(), task.getRequest().getPort()));
                            log.error(String.format("Create backup on node(%s:%d) failed.",
                                    task.getRequest().getHost(), task.getRequest().getPort()), cause);
                        }
                        boolean retry = (cause instanceof RetryableBackupException);
                        boolean exist = (cause instanceof BackupException) &&
                                (((BackupException) cause).getServiceCode()
                                    == ServiceCode.BACKUP_CREATE_EXSIT);
                        result = (result == null || retry || exist || ignoreError(result))
                                ? cause : result;
                    }
                }
                if (result != null) {
                    if (result instanceof Exception) {
                        throw (Exception) result;
                    } else {
                        throw new Exception(result);
                    }
                }
                log.info("Create backup({}) success", backupTag);
                persistBackupInfo(backupTag);
                return;
            } catch (Exception e) {
                boolean retry = (e instanceof RetryableBackupException) &&
                        (retryCnt < BackupConstants.RETRY_MAX_CNT - 1);
                if (retry) {
                    deleteBackupWithoutLock(backupTag, true);
                    log.info("Retry to create backup...");
                    continue;
                }
                boolean exist = (e instanceof BackupException) &&
                        (((BackupException) e).getServiceCode() == ServiceCode.BACKUP_CREATE_EXSIT);
                if (exist) {
                    throw BackupException.fatals.failedToCreateBackup(backupTag, errorList.toString(), e);
                }
                if (!checkCreateResult(backupTag, errorList, force)) {
                    deleteBackupWithoutLock(backupTag, true);
                    Throwable cause = (e.getCause() == null ? e : e.getCause());
                    throw BackupException.fatals.failedToCreateBackup(backupTag, errorList.toString(), cause);
                }
                break;
            }
        }
    }

    private boolean checkCreateResult(String backupTag, List<String> errorList, boolean force) {
        int dbFailedCnt = 0;
        int geodbFailedCnt = 0;
        int zkFailedCnt = 0;
        List<String> newErrList = (List<String>) ((ArrayList<String>) errorList).clone();
        for (String ip : newErrList) {
            int port = Integer.parseInt(ip.split(IP_ADDR_DELIMITER)[1]);
            switch (port) {
                case 7199:
                    dbFailedCnt++;
                    break;
                case 7299:
                    geodbFailedCnt++;
                    break;
                case 7399:
                    zkFailedCnt++;
                    if ((ip.split(IP_ADDR_DELIMITER)[0]).equals("follower")) {
                        errorList.remove(ip);
                    }
                    break;
                default:
                    log.error("Invalid port({}) during backup", port);
            }
        }
        if (dbFailedCnt == 0 && geodbFailedCnt == 0 && zkFailedCnt < hosts.size()) {
            log.info("Create backup({}) success", backupTag);
            persistBackupInfo(backupTag);
            return true;
        } else if (force == true
                && dbFailedCnt <= (hosts.size() - quorumSize)
                && geodbFailedCnt <= hosts.size() - quorumSize
                && zkFailedCnt < hosts.size()) {
            log.warn("Create backup({}) on nodes({}) failed, but force ignore the errors",
                    backupTag, errorList.toString());
            persistBackupInfo(backupTag);
            return true;
        } else {
            log.error("Create backup({}) on nodes({}) failed", backupTag, errorList.toString());
            return false;
        }
    }

    public static synchronized String createBackupName() {
        return FORMAT.format(new Date(System.currentTimeMillis()));
    }

    private void validateBackupName(String backupTag) {
        Preconditions.checkArgument(isValidLinuxFileName(backupTag)
                        && !backupTag.contains(BackupConstants.BACKUP_NAME_DELIMITER),
                "Invalid backup name: %s", backupTag);
    }

    private boolean isValidLinuxFileName(String fileName) {
        // the original Linux file name length limitation is 256
        // 200 is our more restricted limitation as described above BackupService.createBackup method.
        if (fileName == null || fileName.trim().isEmpty() || fileName.contains("/") || fileName.length() > 200) {
            return false;
        }
        return true;
    }

    private void createBackupFromNode(String backupTag, String host, int port)
            throws IOException {
        JMXConnector conn = connect(host, port);
        try {
            BackupManagerMBean backupMBean = getBackupManagerMBean(conn);
            backupMBean.create(backupTag);
            log.info(String.format("Node(%s:%d) - Create backup(name=%s) success",
                    host, port, backupTag));
        } catch (BackupException e) {
            if (ignoreError(e)) {
                log.info(String.format("Node(%s:%d) - Create backup(name=%s) finished",
                        host, port, backupTag));
            } else {
                log.error(String.format("Node(%s:%d) - Create backup(name=%s) failed",
                        host, port, backupTag));
            }
            throw e;
        } finally {
            close(conn);
        }
    }

    private boolean ignoreError(Throwable error) {
        boolean noNeedBackup = (error != null)
                && (error instanceof BackupException)
                && (((BackupException) error).getServiceCode()
                    == ServiceCode.BACKUP_INTERNAL_NOT_LEADER);
        return noNeedBackup;
    }

    /**
     * Records backup info
     */
    private void persistBackupInfo(String backupTag) {
        File targetDir = new File(getBackupDir(), backupTag);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return;
        }
        File infoFile = new File(targetDir, backupTag + BackupConstants.BACKUP_INFO_SUFFIX);
        Date now = new Date();
        try (OutputStream fos = new FileOutputStream(infoFile)) {
            Properties properties = new Properties();
            properties.setProperty(BackupConstants.BACKUP_INFO_VERSION, getCurrentVersion());
            properties.setProperty(BackupConstants.BACKUP_INFO_HOSTS, getHostsWithDualInetAddrs().values().toString());
            properties.setProperty(BackupConstants.BACKUP_INFO_CREATE_TIME, Long.toString(now.getTime()));
            properties.store(fos, null);
            // Guarantee ower/group owner/permissions of infoFile is consistent with other backup files
            FileUtils.chown(infoFile, BackupConstants.STORAGEOS_USER, BackupConstants.STORAGEOS_GROUP);
            FileUtils.chmod(infoFile, BACKUP_FILE_PERMISSION);
        } catch (Exception ex) {
            log.error("Failed to record backup info", ex);
        }
    }

    private String getCurrentVersion() throws Exception {
        RepositoryInfo info = coordinatorClient.getTargetInfo(RepositoryInfo.class);
        String version = info.getCurrentVersion().toString();
        log.info("Current ViPR version: {}", version);
        return version;
    }

    private void precheckForCreation(String backupTag) {
        // Check "backup_max_manual_copies"
        if (!isScheduledBackupTag(backupTag)) {
            int currentManualBackupNumber = getCurrentManualBackupNumber();
            int maxManualBackupNumber = getMaxManualBackupNumber();
            if (currentManualBackupNumber >= maxManualBackupNumber) {
                throw BackupException.fatals.manualBackupNumberExceedLimit(
                        currentManualBackupNumber, maxManualBackupNumber);
            }
        }
    }

    private int getCurrentManualBackupNumber() {
        int manualBackupNumber = 0;
        Set<String> backups = listRawBackup(true).uniqueTags();
        for (String backupTag : backups) {
            if (!isScheduledBackupTag(backupTag)) {
                manualBackupNumber++;
                log.info("Backup({}) is manual created", backupTag);
            }
        }
        return manualBackupNumber;
    }

    private int getMaxManualBackupNumber() {
        PropertyInfo propInfo = coordinatorClient.getPropertyInfo();
        return Integer.parseInt(propInfo.getProperty(BackupConstants.BACKUP_MAX_MANUAL_COPIES));
    }

    public static boolean isScheduledBackupTag(String tag) {
        // This pattern need to consider extension, version part could be longer and node count could bigger
        String regex = String.format(BackupConstants.SCHEDULED_BACKUP_TAG_REGEX_PATTERN, ProductName.getName(),
                BackupConstants.SCHEDULED_BACKUP_DATE_PATTERN.length());
        log.info("Scheduler backup name pattern regex is {}", regex);
        Pattern backupNamePattern = Pattern.compile(regex);
        return backupNamePattern.matcher(tag).find();
    }

    /**
     * Delete backup file on all nodes
     * 
     * @param backupTag
     *            The tag of the backup
     */
    public void deleteBackup(String backupTag) {
        checkOnStandby();
        validateBackupName(backupTag);
        InterProcessLock lock = null;
        try {
            lock = getLock(BackupConstants.BACKUP_LOCK, BackupConstants.LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            deleteBackupWithoutLock(backupTag, false);
        } finally {
            releaseLock(lock);
        }
    }

    class DeleteBackupCallable extends BackupCallable<Void> {
        @Override
        public Void sendRequest() throws Exception {
            deleteBackupFromNode(this.backupTag, this.host, this.port);
            return null;
        }
    }

    /**
     * Deletes backup file on all nodes without lock, please be careful to use it.
     * 
     * @param backupTag
     *            The tag of the backup
     * @param ignore
     *            True means ignore error/exception
     */
    private void deleteBackupWithoutLock(final String backupTag, final boolean ignore) {
        List<String> errorList = new ArrayList<String>();
        try {
            List<BackupProcessor.BackupTask<Void>> backupTasks =
                    (new BackupProcessor(getHosts(), Arrays.asList(ports.get(0)), backupTag))
                            .process(new DeleteBackupCallable(), false);
            Throwable result = null;
            for (BackupProcessor.BackupTask task : backupTasks) {
                try {
                    task.getResponse().getFuture().get();
                    log.info("Delete backup(name={}) on node({})success",
                            backupTag, task.getRequest().getHost());
                } catch (CancellationException e) {
                    log.warn(String.format("The task of deleting backup(%s) on node(%s) was canceled",
                            backupTag, task.getRequest().getHost()), e);
                } catch (InterruptedException e) {
                    log.error(String.format("Delete backup on node(%s:%d) failed.",
                            task.getRequest().getHost(), task.getRequest().getPort()), e);
                    result = ((result == null) ? e : result);
                    errorList.add(task.getRequest().getHost());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    log.error(String.format("Delete backup on node(%s:%d) failed.",
                            task.getRequest().getHost(), task.getRequest().getPort()), cause);
                    result = ((result == null) ? cause : result);
                    errorList.add(task.getRequest().getHost());
                }
            }
            if (result != null) {
                if (result instanceof Exception) {
                    throw (Exception) result;
                } else {
                    throw new Exception(result);
                }
            }
            log.info("Delete backup(name={}) success", backupTag);
        } catch (Exception ex) {
            List<String> newErrList = (List<String>) ((ArrayList<String>) errorList).clone();
            for (String host : newErrList) {
                for (int i = 1; i < ports.size(); i++) {
                    try {
                        deleteBackupFromNode(backupTag, host, ports.get(i));
                        errorList.remove(host);
                        log.info(String.format("Retry delete backup(%s) on node(%s:%d) success",
                                backupTag, host, ports.get(i)));
                        break;
                    } catch (Exception e) {
                        log.error(String.format("Retry delete backup on node(%s:%d) failed",
                                host, ports.get(i)), e);
                    }
                }
            }
            if (!errorList.isEmpty()) {
                Throwable cause = (ex.getCause() == null ? ex : ex.getCause());
                if (ignore) {
                    log.warn(String.format(
                            "Delete backup({%s}) on nodes(%s) failed, but ignore ingnore the errors",
                            backupTag, errorList.toString()), cause);
                } else {
                    throw BackupException.fatals.failedToDeleteBackup(backupTag, errorList.toString(), cause);
                }
            } else {
                log.info("Delete backup(name={}) success", backupTag);
            }
        }
    }

    private void deleteBackupFromNode(String backupTag, String host, int port) {
        JMXConnector conn = connect(host, port);
        try {
            BackupManagerMBean backupMBean = getBackupManagerMBean(conn);
            backupMBean.delete(backupTag);
            log.info(String.format(
                    "Node(%s:%d) - Delete backup(name=%s) success", host, port, backupTag));
        } catch (BackupException e) {
            log.error(String.format(
                    "Node(%s:%d) - Delete backup(name=%s) failed", host, port, backupTag));
            throw e;
        } finally {
            close(conn);
        }
    }

    public boolean isDownloadInProgress() {
        try {
            CoordinatorClientImpl client = (CoordinatorClientImpl) coordinatorClient;

            String path = getDownloadOwnerPath();
            log.info("Download zk path={}", path);
            List<String> downloaders = client.getChildren(path);
            return (downloaders != null) && (!downloaders.isEmpty());
        }catch(KeeperException.NoNodeException e) {
            return false; // no downloading is running
        } catch (Exception e) {
            log.error("Failed to check downloading tasks e=",e);
            throw BackupException.fatals.failedToReadZkInfo(e);
        }
    }

    public boolean isDownloadComplete(String backupName) {
        BackupRestoreStatus s = queryBackupRestoreStatus(backupName, false);
        if ( s.getStatus() != BackupRestoreStatus.Status.DOWNLOAD_SUCCESS) {
            return false;
        }

        File downloadFolder = getDownloadDirectory(backupName);

        try {
            checkBackup(downloadFolder);
        }catch (Exception e) {
            return false;
        }
        return true;
    }

    private String getMyDownloadingZKPath() {
        String downloadersPath = getDownloadOwnerPath();
        CoordinatorClientImpl client = (CoordinatorClientImpl)coordinatorClient;
        CoordinatorClientInetAddressMap addrMap = client.getInetAddessLookupMap();
        String myNodeId= addrMap.getNodeId();
        return downloadersPath+"/"+myNodeId;
    }

    public void registerDownloader() throws Exception {
        String path = getMyDownloadingZKPath();
        CoordinatorClientImpl client = (CoordinatorClientImpl)coordinatorClient;
        log.info("register downloader: {}", path);
        client.createEphemeralNode(path, null);
    }

    public void unregisterDownloader() throws Exception {
        String path = getMyDownloadingZKPath();
        log.info("unregister downloader: {}", path);
        CoordinatorClientImpl client = (CoordinatorClientImpl)coordinatorClient;
        client.deleteNode(path);
    }

    private String getDownloadOwnerPath() {
        return getBackupConfigPrefix()+BackupConstants.DOWNLOAD_OWNER_SUFFIX;
    }

    public InterProcessLock getLock(String name, long time, TimeUnit unit) {
        InterProcessLock lock = null;
        log.info("Try to acquire lock: {}", name);
        try {
            lock = coordinatorClient.getLock(name);
            if (time >=0) {
                boolean acquired = lock.acquire(time, unit);
                if (!acquired) {
                    log.error("Unable to acquire lock: {}", name);
                    if (name.equals(RecoveryConstants.RECOVERY_LOCK)) {
                        throw BackupException.fatals.unableToGetRecoveryLock(name);
                    }
                    throw BackupException.fatals.unableToGetLock(name);
                }
            } else {
                lock.acquire(); // no timeout
            }
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", name);
            throw BackupException.fatals.failedToGetLock(name, e);
        }
        log.info("Got lock: {}", name);
        return lock;
    }

    public void releaseLock(InterProcessLock lock) {
        if (lock == null) {
            log.info("The lock is null, no need to release");
            return;
        }
        try {
            lock.release();
            log.info("Release lock successful");
        } catch (Exception ignore) {
            log.warn("Release lock failed", ignore);
        }
    }

    class ListBackupCallable extends BackupCallable<List<BackupSetInfo>> {
        @Override
        public List<BackupSetInfo> sendRequest() throws Exception {
            return listBackupFromNode(this.host, this.port);
        }
    }

    /**
     * Get a list of backup sets that have zk backup files
     * and quorum db/geodb backup files
     * 
     * @return a list of backup sets info
     */
    public List<BackupSetInfo> listBackup() {
        checkOnStandby();
        log.info("Listing backup sets");
        return listBackup(true);
    }

    /**
     * Get a list of backup sets info
     * 
     * @param ignore if true, ignore the errors during the operation
     */
    public BackupFileSet listRawBackup(final boolean ignore) {
        BackupFileSet clusterBackupFiles = new BackupFileSet(this.quorumSize);
        List<String> errorList = new ArrayList<>();
        try {
            List<BackupProcessor.BackupTask<List<BackupSetInfo>>> backupTasks =
                    (new BackupProcessor(getHosts(), Arrays.asList(ports.get(2)), null))
                            .process(new ListBackupCallable(), false);
            Throwable result = null;
            for (BackupProcessor.BackupTask task : backupTasks) {
                try {
                    List<BackupSetInfo> nodeBackupFileList = (List<BackupSetInfo>) task.getResponse().getFuture().get();
                    clusterBackupFiles.addAll(nodeBackupFileList, task.getRequest().getNode());
                    log.info("List backup on node({})success", task.getRequest().getHost());
                } catch (CancellationException e) {
                    log.warn("The task of listing backup on node({}) was canceled",
                            task.getRequest().getHost(), e);
                } catch (InterruptedException e) {
                    log.error(String.format("List backup on node(%s:%d) failed",
                            task.getRequest().getHost(), task.getRequest().getPort()), e);
                    result = ((result == null) ? e : result);
                    errorList.add(task.getRequest().getNode());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    log.error(String.format("List backup on node(%s:%d) failed.",
                            task.getRequest().getHost(), task.getRequest().getPort()), cause);
                    result = ((result == null) ? cause : result);
                    errorList.add(task.getRequest().getNode());
                }
            }
            if (result != null) {
                if (result instanceof Exception) {
                    throw (Exception) result;
                } else {
                    throw new Exception(result);
                }
            }
        } catch (Exception e) {
            log.error("Exception when listing backups", e);
            if (!errorList.isEmpty()) {
                Throwable cause = (e.getCause() == null ? e : e.getCause());
                if (ignore) {
                    log.warn("List backup on nodes({}) failed, but ignore the errors",
                            errorList.toString(), cause);
                } else {
                    throw BackupException.fatals.failedToListBackup(errorList.toString(), cause);
                }
            }
        }

        return clusterBackupFiles;
    }

    /**
     * Get a list of backup sets info
     * 
     * @param ignore if true, ignore the errors during the operation
     */
    public List<BackupSetInfo> listBackup(boolean ignore) {
        BackupFileSet clusterBackupFiles = listRawBackup(ignore);
        List<BackupSetInfo> backupSetList = filterToCreateBackupsetList(clusterBackupFiles);
        if (!backupSetList.isEmpty()) {
            Collections.sort(backupSetList, new Comparator<BackupSetInfo>() {
                @Override
                public int compare(BackupSetInfo o1, BackupSetInfo o2) {
                    return (int) (o2.getCreateTime() - o1.getCreateTime());
                }
            });
        }
        log.info("List backup({}) success", backupSetList.toString());
        return backupSetList;
    }

    private List<BackupSetInfo> listBackupFromNode(String host, int port) {
        JMXConnector conn = connect(host, port);
        try {
            BackupManagerMBean backupMBean = getBackupManagerMBean(conn);
            List<BackupSetInfo> backupFileList = backupMBean.list();
            if (backupFileList == null) {
                throw new IllegalStateException("Get backup list is null");
            }
            log.info(String.format("Node(%s:%d) - List backup success", host, port));
            return backupFileList;
        } catch (BackupException e) {
            log.error(String.format("Node(%s:%d) - List backup failed", host, port));
            throw e;
        } finally {
            close(conn);
        }
    }

    private List<BackupSetInfo> filterToCreateBackupsetList(BackupFileSet clusterBackupFiles) {
        List<BackupSetInfo> backupSetList = new ArrayList<>();
        for (String backupTag : clusterBackupFiles.uniqueTags()) {
            BackupSetInfo backupSetInfo = findValidBackupSet(clusterBackupFiles, backupTag);
            if (backupSetInfo != null) {
                backupSetList.add(backupSetInfo);
            }
        }
        return backupSetList;
    }

    private BackupSetInfo findValidBackupSet(BackupFileSet clusterBackupFiles, String backupTag) {
        BackupFileSet filesForTag = clusterBackupFiles.subsetOf(backupTag, null, null);

        if (!filesForTag.isValid()) {
            return null;
        }

        long size = 0;
        long creationTime = 0;
        for (BackupFile file : filesForTag) {
            size += file.info.getSize();
            if (file.type == BackupType.info) {
                creationTime = file.info.getCreateTime();
            }
        }

        return initBackupSetInfo(backupTag, size, creationTime);
    }

    private BackupSetInfo initBackupSetInfo(String backupTag, Long size, Long createTime) {
        BackupSetInfo backupInfo = new BackupSetInfo();
        if (backupTag != null) {
            backupInfo.setName(backupTag);
        }
        if (size != null) {
            backupInfo.setSize(size);
        }
        if (createTime != null) {
            backupInfo.setCreateTime(createTime);
        }
        return backupInfo;
    }

    /**
     * Gets disk quota for backup files in gigabyte.
     */
    public int getQuotaGb() {
        checkOnStandby();
        int quotaGb;
        JMXConnector conn = connect(getLocalHost(), ports.get(0));
        try {
            BackupManagerMBean backupMBean = getBackupManagerMBean(conn);
            quotaGb = backupMBean.getQuotaGb();
            log.info("Get backup quota(size={} GB) success", quotaGb);
        } catch (Exception e) {
            log.error("Get backup quota failed");
            throw BackupException.fatals.failedToGetQuota(e);
        } finally {
            close(conn);
        }
        return quotaGb;
    }

    private String getLocalHost() {
        Preconditions.checkNotNull(coordinatorClient,
                "Please initialize coordinator client before any operations");
        DualInetAddress inetAddress = coordinatorClient.getInetAddessLookupMap().getDualInetAddress();
        return inetAddress.hasInet4() ? inetAddress.getInet4() : inetAddress.getInet6();
    }

    private boolean isNodeAvailable(String host) {
        for (String nodeId : hosts.keySet())
        if (hosts.get(nodeId).equals(host)) {
            return getAvailableNodes().contains(nodeId);
        }
        return false;
    }

    private List<String> getAvailableNodes() {
        List<String> goodNodes = new ArrayList<String>();
        try {
            List<Service> svcs = coordinatorClient.locateAllServices(
                    ((CoordinatorClientImpl) coordinatorClient).getSysSvcName(),
                    ((CoordinatorClientImpl) coordinatorClient).getSysSvcVersion(),
                    (String) null, null);
            for (Service svc : svcs) {
                String svcId = svc.getId();
                String nodeId = String.format(Constants.NODE_ID_FORMAT, svcId.substring(svcId.lastIndexOf("-") + 1));
                if (svcId.endsWith(Constants.STANDALONE_ID)) {
                    nodeId = Constants.STANDALONE_ID;
                }
                goodNodes.add(nodeId);
            }
            log.debug("Available nodes: {}", goodNodes);
        } catch (Exception e) {
            log.warn("Failed to get available nodes by query syssvc beacon", e);
            goodNodes.addAll(hosts.keySet());
        }
        return goodNodes;
    }

    public Map<String, URI> getNodesInfo() throws URISyntaxException {
        List<Service> services = coordinatorClient.locateAllServices(
                ((CoordinatorClientImpl) coordinatorClient).getSysSvcName(),
                ((CoordinatorClientImpl) coordinatorClient).getSysSvcVersion(),
                null, null);

        //get URL schema and port
        Service svc = services.get(0);
        URI uri = svc.getEndpoint();
        int port = uri.getPort();
        String scheme = uri.getScheme();

        DrUtil util = new DrUtil();
        util.setCoordinator(coordinatorClient);
        Site localSite = util.getLocalSite();
        Map<String, String> addresses = localSite.getHostIPv4AddressMap();
        if (!localSite.isUsingIpv4()) {
            addresses = localSite.getHostIPv6AddressMap();
        }

        Map<String, URI> nodesInfo = new HashMap();

        for (Map.Entry<String, String> addr : addresses.entrySet()) {
            String nodeUri = scheme +"://" + addr.getValue()+ ":" + port + "/";
            nodesInfo.put(addr.getKey(), new URI(nodeUri));
        }

        return nodesInfo;
    }

    /**
     * Query info of a remote backup, if the backup has been downloaded, get info from local downloaded directory
     * @param backupName
     * @param serverUri
     * @param username
     * @param password
     * @return
     * @throws IOException
     */
    public BackupInfo getBackupInfo(String backupName, String serverUri, String username, String password) throws IOException {
        log.info("To get backup info of {} from server={} ", backupName, serverUri);

        File backupFolder= getDownloadDirectory(backupName);
        try {
            checkBackup(backupFolder);
            log.info("The backup {} for this node has already been downloaded", backupName);
            BackupInfo info = getBackupInfo(backupFolder, false);
            info.setRestoreStatus(queryBackupRestoreStatus(backupName, false));
            return info;
        } catch (Exception e) {
            // The backup has not been downloaded yet or is invalid, query from the server
        }

        BackupInfo backupInfo = new BackupInfo();
        backupInfo.setFileName(backupName);

        FtpClient client = new FtpClient(serverUri, username, password);
        InputStream in = client.download(backupName);
        ZipInputStream zin = new ZipInputStream(in);
        ZipEntry zentry = zin.getNextEntry();

        while (zentry != null) {
            if (isPropEntry(zentry)) {
                log.info("Found the property file={}", zentry.getName());
                setBackupInfo(zin, backupInfo);
                break;
            }
            zentry = zin.getNextEntry();
        }

        try {
            zin.closeEntry();
            zin.close();
        }catch (IOException e) {
            log.debug("Failed to close the stream e", e);
            // it's a known issue to use curl
            //it's safe to ignore this exception here.
        }

        return backupInfo;
    }

    private boolean isPropEntry(ZipEntry zentry) {
        return zentry.getName().endsWith(BackupConstants.BACKUP_INFO_SUFFIX);
    }

    private void setBackupInfo(ZipInputStream zin, BackupInfo backupInfo) throws IOException {
        Properties properties = loadProperties(zin);

        backupInfo.setVersion(getBackupVersion(properties));
        backupInfo.setCreateTime(getCreateTime(properties, backupInfo.getFileName()));
        backupInfo.setRestoreStatus(queryBackupRestoreStatus(backupInfo.getFileName(), false));
    }

    /**
     * Get backup info from local disk, it could be the local backup or the downloaded remote backup
     * @param backupFolder the folder of local backup
     * @return
     */
    public BackupInfo getBackupInfo(File backupFolder, boolean isLocal) {
        File[] backupFiles = getBackupFiles(backupFolder);

        File propFile = null;
        long size = 0;

        for (File backupFile : backupFiles) {
            size += backupFile.length();

            if (backupFile.getName().endsWith(BackupConstants.BACKUP_INFO_SUFFIX)) {
                propFile = backupFile;
            }
        }

        BackupInfo backupInfo = new BackupInfo();

        String backupName = backupFolder.getName();
        backupInfo.setFileName(backupName);

        try (FileInputStream in = new FileInputStream(propFile)) {
            Properties properties = loadProperties(in);
            backupInfo.setVersion(getBackupVersion(properties));
            backupInfo.setCreateTime(getCreateTime(properties, backupName));
        }catch (IOException e) {
            log.error("Failed to get backup info from {} e=", propFile.getName(), e);
        }

        backupInfo.setFileSize(size);

        return backupInfo;
    }

    private String getBackupVersion(Properties properties) {
        return properties.getProperty(BackupConstants.BACKUP_INFO_VERSION);
    }

    private long getCreateTime(Properties properties, String backupName) {
        long time = getCreateTimeFromProperties(properties);
        if ( time != 0 )
            return time;

        //This can happen if the backup file is made before Yoda,
        // try to get the create time from the backup name
        time = getCreateTimeFromName(backupName);

        log.info("create time ={}", time);
        return time;
    }

    private long getCreateTimeFromProperties(Properties properties) {
        String stime = properties.getProperty(BackupConstants.BACKUP_INFO_CREATE_TIME);
        return stime == null ? 0 : Long.parseLong(stime);
    }

    public long getCreateTimeFromPropFile(File propFile) {
        long time = 0;

        try (FileInputStream in = new FileInputStream(propFile)) {
            Properties properties = loadProperties(in);
            time = getCreateTimeFromProperties(properties);
        }catch (IOException e) {
            log.error("Failed to get create time from prop file {} e=", propFile.getName(), e);
        }

        return time;
    }

    private Properties loadProperties(InputStream in) {
        Properties properties = new Properties();
        try {
            properties.load(in);
        }catch(IOException e) {
            log.error("Failed to load properties e=", e);
        }

        return properties;
    }

    private long getCreateTimeFromName(String backupName) {
        if (backupName == null) {
            log.error("Backup file name is empty");
            throw new IllegalArgumentException("Backup file name is empty");
        }

        if (!backupName.contains(BackupConstants.COLLECTED_BACKUP_NAME_DELIMITER)) {
            log.error("Backup file name should contain {}", BackupConstants.COLLECTED_BACKUP_NAME_DELIMITER);
            throw new IllegalArgumentException("Invalid backup file name: " + backupName);
        }

        String[] nameSegs = backupName.split(BackupConstants.COLLECTED_BACKUP_NAME_DELIMITER);

        for (String segment : nameSegs) {
            if (isTimeFormat(segment)) {
                log.info("Backup({}) create time is: {}", backupName, segment);
                return convertTime(segment);
            }
        }

        log.info("Could not get create time from backup name");
        return 0;
    }

    private long convertTime(String stime) {
        long time = 0;

        DateFormat df = new SimpleDateFormat(BackupConstants.SCHEDULED_BACKUP_DATE_PATTERN);
        try {
            Date date = df.parse(stime);
            time = date.getTime();
        }catch (Exception e) {
            log.error("Failed to parse time {} e=", stime, e);
            time= 0;
        }

        log.info("time={}", time);
        return time;
    }

    private boolean isTimeFormat(String nameSegment) {
        String regex = String.format(BackupConstants.SCHEDULED_BACKUP_DATE_REGEX_PATTERN,
                BackupConstants.SCHEDULED_BACKUP_DATE_FORMAT.length());

        Pattern backupNamePattern = Pattern.compile(regex);

        return backupNamePattern.matcher(nameSegment).find();
    }

    public URI getFirstNodeURI() throws URISyntaxException {
        Map<String, URI> nodesInfo = getNodesInfo();

        return nodesInfo.get("node1");
    }

    public String getCurrentNodeId() {
        CoordinatorClientInetAddressMap addr = coordinatorClient.getInetAddessLookupMap();
        return addr.getNodeId();
    }

    public boolean isClusterStable() {
        ClusterInfo.ClusterState state = coordinatorClient.getControlNodesState();
        log.info("cluster state={}", state);
        return state == ClusterInfo.ClusterState.STABLE;
    }

    public boolean isActiveSite() {
        DrUtil util = new DrUtil();
        util.setCoordinator(coordinatorClient);
        return util.isActiveSite();
    }
    /**
     * Create a connection to the JMX agent
     */
    private JMXConnector connect(String host, int port) {
        try {
            if (!isNodeAvailable(host)) {
                log.info("Node({}) is unavailable", host);
                throw new IllegalStateException("Node is unavailable");
            }
            log.debug("Connecting to JMX Server {}:{}", host, port);
            String connectorAddress = String.format(serviceUrl, host, port);
            JMXServiceURL jmxUrl = new JMXServiceURL(connectorAddress);
            return JMXConnectorFactory.connect(jmxUrl);
        } catch (MalformedURLException e) {
            log.error(String.format("Failed to construct jmx url for %s:%d", host, port), e);
            throw new IllegalStateException("Failed to construct jmx url");
        } catch (IOException e) {
            log.error(String.format("Failed to connect %s:%d", host, port), e);
            throw new IllegalStateException("Failed to connect " + host);
        }
    }

    /**
     * Setup the MBean proxies
     */
    private BackupManagerMBean getBackupManagerMBean(JMXConnector conn) {
        Preconditions.checkNotNull(conn, "Invalid jmx connector");
        try {
            ObjectName objectName = new ObjectName(BackupManager.MBEAN_NAME);
            MBeanServerConnection mbsc = conn.getMBeanServerConnection();
            return JMX.newMBeanProxy(mbsc, objectName, BackupManagerMBean.class);
        } catch (MalformedObjectNameException e) {
            log.error("Failed to construct object name for mbean({})", BackupManager.MBEAN_NAME, e);
            throw new IllegalStateException("Failed to construct object name");
        } catch (IOException e) {
            log.error("Failed to create backup manager MBean proxy", e);
            throw new IllegalStateException("Failed to create backup manager MBean proxy");
        }
    }

    /**
     * Close the connection to JMX agent
     */
    private void close(JMXConnector conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException e) {
                log.error("Failed to close JMX connector", e);
            }
        }
    }
}
