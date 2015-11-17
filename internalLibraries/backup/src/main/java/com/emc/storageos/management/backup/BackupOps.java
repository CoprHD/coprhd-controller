/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.management.backup.exceptions.BackupException;
import com.emc.storageos.management.backup.exceptions.RetryableBackupException;
import com.emc.storageos.services.util.FileUtils;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.sys.recovery.RecoveryConstants;
import com.google.common.base.Preconditions;

public class BackupOps {
    private static final Logger log = LoggerFactory.getLogger(BackupOps.class);
    private static final String BACKUP_LOCK = "backup";
    private static final String IP_ADDR_DELIMITER = ":";
    private static final String IP_ADDR_FORMAT = "%s" + IP_ADDR_DELIMITER + "%d";
    private static final Format FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final String BACKUP_FILE_PERMISSION = "644";
    private static final int LOCK_TIMEOUT = 1000;
    private String serviceUrl = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    private Map<String, String> hosts;
    private Map<String, String> dualAddrHosts;
    private List<Integer> ports;
    private CoordinatorClient coordinatorClient;
    private int quorumSize;
    private List<String> vdcList;
    private File backupDir;

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
    private Map<String, String> getHosts() {
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
        if (backupTag == null) {
            backupTag = createBackupName();
        } else {
            validateBackupName(backupTag);
        }

        InterProcessLock backupLock = null;
        InterProcessLock recoveryLock = null;
        try {
            recoveryLock = getLock(RecoveryConstants.RECOVERY_LOCK, LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            backupLock = getLock(BACKUP_LOCK, LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            createBackupWithoutLock(backupTag, force);
        } finally {
            releaseLock(backupLock);
            releaseLock(recoveryLock);
        }
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
        try (OutputStream fos = new FileOutputStream(infoFile)) {
            Properties properties = new Properties();
            properties.setProperty(BackupConstants.BACKUP_INFO_VERSION, getCurrentVersion());
            properties.setProperty(BackupConstants.BACKUP_INFO_HOSTS, getHostsWithDualInetAddrs().values().toString());
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

    /**
     * Delete backup file on all nodes
     * 
     * @param backupTag
     *            The tag of the backup
     */
    public void deleteBackup(String backupTag) {
        validateBackupName(backupTag);
        InterProcessLock lock = null;
        try {
            lock = getLock(BACKUP_LOCK, LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
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

    private InterProcessLock getLock(String name, long time, TimeUnit unit) {
        boolean acquired = false;
        InterProcessLock lock = null;
        log.info("Try to acquire lock: {}", name);
        try {
            lock = coordinatorClient.getLock(name);
            acquired = lock.acquire(time, unit);
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", name);
            throw BackupException.fatals.failedToGetLock(name, e);
        }
        if (!acquired) {
            log.error("Unable to acquire lock: {}", name);
            throw BackupException.fatals.unableToGetLock(name);
        }
        log.info("Got lock: {}", name);
        return lock;
    }

    private void releaseLock(InterProcessLock lock) {
        if (lock == null) {
            log.info("The lock is null, no need to release");
            return;
        }
        try {
            lock.release();
            log.info("Release lock successful");
        } catch (Exception ignore) {
            log.error("Release lock failed", ignore);
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
                    log.info("List backup on node({})success",
                            task.getRequest().getHost());
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
            if (file.type == BackupType.zk) {
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
                goodNodes.add("vipr" + svcId.substring(svcId.lastIndexOf("-") + 1));
            }
            log.debug("Available nodes: {}", goodNodes);
        } catch (Exception e) {
            log.warn("Failed to get available nodes by query syssvc beacon", e);
            goodNodes.addAll(hosts.keySet());
        }
        return goodNodes;
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
