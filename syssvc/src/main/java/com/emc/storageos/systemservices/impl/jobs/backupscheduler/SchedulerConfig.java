/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.mail.MailHelper;
import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.services.util.Strings;
import com.emc.vipr.model.sys.ClusterInfo.ClusterState;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.emc.vipr.model.sys.recovery.RecoveryConstants;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * This class holds the configuration for scheduled backup & upload
 */
public class SchedulerConfig {
    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);
    
    private static final String BACKUP_SCHEDULER_LOCK = "scheduled_backup";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final int MAX_VERSION_RETRY_TIMES = 5;
    private static final int MAX_VERSION_RETRY_INTERVAL = 1000*30;

    private CoordinatorClient coordinatorClient;
    private EncryptionProvider encryptionProvider;
    private DbClient dbClient;

    private MailHelper mailHelper;

    public int nodeCount;

    // Configurations mirrored from system properties
    public boolean schedulerEnabled;
    public ScheduleTimeRange.ScheduleInterval interval;
    public int intervalMultiple;
    public Integer startOffsetMinutes;
    public int copiesToKeep;
    public String uploadUrl;
    public String uploadUserName;
    private byte[] uploadPassword;
    private String softwareVersion;

    // Internal state shared between nodes and across restart
    public TreeSet<String> retainedBackups = new TreeSet<>(new ScheduledBackupTag.TagComparator());
    public Set<String> uploadedBackups = new HashSet<>();

    public SchedulerConfig(CoordinatorClient coordinatorClient, EncryptionProvider encryptionProvider, DbClient dbClient) {
        this.coordinatorClient = coordinatorClient;
        this.encryptionProvider = encryptionProvider;
        this.dbClient = dbClient;
        this.mailHelper = new MailHelper(coordinatorClient);
    }

    public String getUploadPassword() {
        if (this.uploadPassword == null) {
            return "";
        }

        return this.encryptionProvider.decrypt(Base64.decodeBase64(this.uploadPassword));
    }

    public Calendar now() {
        return Calendar.getInstance(UTC);
    }

    public void reload() throws Exception {
        log.info("Loading configuration");
        
        getSofttwareWithRetry();

        PropertyInfo propInfo = this.coordinatorClient.getPropertyInfo();

        this.nodeCount = Integer.parseInt(propInfo.getProperty("node_count"));

        String startTimeStr = propInfo.getProperty(BackupConstants.SCHEDULE_TIME);
        String intervalStr = propInfo.getProperty(BackupConstants.SCHEDULE_INTERVAL);
        String copiesStr = propInfo.getProperty(BackupConstants.COPIES_TO_KEEP);
        String urlStr = propInfo.getProperty(BackupConstants.UPLOAD_URL);
        String usernameStr = propInfo.getProperty(BackupConstants.UPLOAD_USERNAME);
        String passwordStr = propInfo.getProperty(BackupConstants.UPLOAD_PASSWD);
        String enableStr = propInfo.getProperty(BackupConstants.SCHEDULER_ENABLED);

        this.interval = ScheduleTimeRange.ScheduleInterval.DAY;
        this.intervalMultiple = 1;
        if (intervalStr != null && !intervalStr.isEmpty()) {
            // Format is ###$$$, where $$$ is interval unit, and ### represents times of the interval unit
            // E.g. "5day", ###=5, $$$=day.
            int digitLen = 0;
            while (Character.isDigit(intervalStr.charAt(digitLen))) {
                digitLen++;
            }

            this.intervalMultiple = digitLen > 0 ? Integer.parseInt(intervalStr.substring(0, digitLen)) : 1;
            if (this.intervalMultiple <= 0) {
                log.warn("The interval string {} parse to non-positive ({}) multiple of intervals", intervalStr, this.intervalMultiple);
                this.intervalMultiple = 1;
            }
            this.interval = ScheduleTimeRange.parseInterval(intervalStr.substring(digitLen));
        } else {
            log.warn("The interval string is absent or empty, daily backup (\"1day\") is used as default.");
        }

        this.startOffsetMinutes = 0;
        if (startTimeStr != null && startTimeStr.length() > 0) {
            // Format is ...dddHHmm
            int raw = Integer.parseInt(startTimeStr);
            int minute = raw % 100;
            raw /= 100;
            int hour = raw % 100;
            int day = raw / 100;

            this.startOffsetMinutes = (day * 24 + hour) * 60 + minute;
        }

        this.copiesToKeep = BackupConstants.DEFAULT_BACKUP_COPIES_TO_KEEP;
        if (copiesStr != null && copiesStr.length() > 0) {
            this.copiesToKeep = Integer.parseInt(copiesStr);
        }

        if (urlStr == null || urlStr.length() == 0) {
            this.uploadUrl = null;
        } else if (urlStr.endsWith("/")) {
            this.uploadUrl = urlStr;
        } else {
            this.uploadUrl = urlStr + "/";
        }

        this.uploadUserName = usernameStr;
        this.uploadPassword = null;
        if (passwordStr != null && passwordStr.length() > 0) {
            this.uploadPassword = passwordStr.getBytes("UTF-8");
        }

        this.schedulerEnabled = enableStr == null || enableStr.length() == 0 ? false : Boolean.parseBoolean(enableStr);
        this.retainedBackups.clear();
        this.uploadedBackups.clear();
        Configuration cfg = this.coordinatorClient.queryConfiguration(Constants.BACKUP_SCHEDULER_CONFIG, Constants.GLOBAL_ID);
        if (cfg != null) {
            String succBackupStr = cfg.getConfig(BackupConstants.BACKUP_TAGS_RETAINED);
            if (succBackupStr != null && succBackupStr.length() > 0) {
                splitAndRemoveEmpty(succBackupStr, ",", this.retainedBackups);
            }

            String completedTagsStr = cfg.getConfig(BackupConstants.BACKUP_TAGS_UPLOADED);
            if (completedTagsStr != null && completedTagsStr.length() > 0) {
                splitAndRemoveEmpty(completedTagsStr, ",", this.uploadedBackups);
            }
        }
    }

    private static void splitAndRemoveEmpty(String str, String regex, Set<String> toList) {
        for (String seg : str.split(regex)) {
            String normalized = seg.trim();
            if (normalized.length() > 0) {
                toList.add(normalized);
            }
        }
    }

    public void persist() {
        ConfigurationImpl cfg = new ConfigurationImpl();
        cfg.setKind(Constants.BACKUP_SCHEDULER_CONFIG);
        cfg.setId(Constants.GLOBAL_ID);
        cfg.setConfig(BackupConstants.BACKUP_TAGS_RETAINED,
                Strings.join(",", this.retainedBackups.toArray(new String[this.retainedBackups.size()])));
        cfg.setConfig(BackupConstants.BACKUP_TAGS_UPLOADED,
                Strings.join(",", this.uploadedBackups.toArray(new String[this.uploadedBackups.size()])));
        this.coordinatorClient.persistServiceConfiguration(cfg);
    }

    public AutoCloseable lock() throws Exception {
        return new InterProcessLockHolder(this.coordinatorClient, BACKUP_SCHEDULER_LOCK, this.log);
    }

    public void sendBackupFailureToRoot(String tag, String errMsg) {
        Map<String, String> params = new HashMap<>();
        params.put("tag", tag);
        params.put("errorMessage", errMsg);

        String subject = getEmailSubject("Failed to Create Backup: ", tag);
        sendEmailToRoot(subject, "BackupFailedEmail.html", params);
    }

    public void sendUploadFailureToRoot(String tags, String errMsg) {
        Map<String, String> params = new HashMap<>();
        params.put("tags", tags);
        params.put("url", this.uploadUrl);
        params.put("errorMessage", errMsg);

        String subject = getEmailSubject("Failed to Upload Backups: ", tags);
        log.info("Error message: {}", subject);
        sendEmailToRoot(subject, "UploadFailedEmail.html", params);
    }

    private String getEmailSubject(String preSubject, String tags) {
        if (VdcUtil.isLocalVdcSingleSite()) {
            return preSubject + tags;
        } else {
            String vdcId = VdcUtil.getLocalShortVdcId();
            return String.format("%s %s in %s", preSubject, tags, vdcId);
        }

    }

    private void sendEmailToRoot(String subject, String templateFile, Map<String, String> params) {
        try {
            String htmlTemplate;
            try (InputStream in = SchedulerConfig.class.getResourceAsStream(templateFile)) {
                htmlTemplate = IOUtils.toString(in, "UTF-8");
            }

            String html = MailHelper.parseTemplate(params, htmlTemplate);

            String to = getMailAddressOfUser("root");
            if (to == null) {
                log.warn("Cannot find email configuration for user root, no alert email can be sent.");
                return;
            } else {
                log.info("The mail address of user root is: {}", to);
            }

            this.mailHelper.sendMailMessage(to, subject, html);
            log.info("Send email to root user done");
        } catch (Exception e) {
            log.error("Failed to send email to root", e);
        }
    }

    /**
     * get user's mail address from UserPreference CF
     * 
     * @param userName
     * @return
     */
    private String getMailAddressOfUser(String userName) {

        DataObjectType doType = TypeMap.getDoType(UserPreferences.class);
        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(
                doType.getColumnField(UserPreferences.USER_ID), userName);
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();
        this.dbClient.queryByConstraint(constraint, queryResults);

        List<URI> userPrefsIds = new ArrayList<>();
        for (NamedElementQueryResultList.NamedElement namedElement : queryResults) {
            userPrefsIds.add(namedElement.getId());
        }
        if (userPrefsIds.isEmpty()) {
            return null;
        }

        final List<UserPreferences> userPrefs = new ArrayList<>();
        Iterator<UserPreferences> iter = this.dbClient.queryIterativeObjects(UserPreferences.class, userPrefsIds);
        while (iter.hasNext()) {
            userPrefs.add(iter.next());
        }

        if (userPrefs.size() > 1) {
            throw new IllegalStateException("There should only be 1 user preferences object for a user");
        }
        if (userPrefs.isEmpty()) {
            // if there isn't a user prefs object in the DB yet then we haven't saved one for this user yet.
            return null;
        }

        return userPrefs.get(0).getEmail();
    }

    public boolean isAllowBackup() {
        if (isClusterUpgrading()) {
            log.info("Cluster is upgrading, not allowed to do backup");
            return false;
        }
        if (isClusterNodeRecovering()) {
            log.info("Cluster is node recovering, not allowed to do backup");
            return false;
        }
        return true;
    }

    private boolean isClusterUpgrading() {
        String currentVersion = coordinatorClient.getCurrentDbSchemaVersion();
        String targetVersion = coordinatorClient.getTargetDbSchemaVersion();
        log.info("Current version: {}, target version: {}.", currentVersion,
                targetVersion);
        if (!currentVersion.equalsIgnoreCase(targetVersion)) {
            log.warn("Current version is not equal to the target version");
            return true;
        }

        ClusterState state = coordinatorClient.getControlNodesState();
        log.info("Current control nodes' state: {}", state);
        if (state == ClusterState.STABLE || state == ClusterState.SYNCING
                || state == ClusterState.DEGRADED) {
            return false;
        }
        return true;
    }

    private boolean isClusterNodeRecovering() {
        RecoveryStatus.Status status = null;
        Configuration cfg = coordinatorClient.queryConfiguration(Constants.NODE_RECOVERY_STATUS, Constants.GLOBAL_ID);
        if (cfg != null) {
            String statusStr = cfg.getConfig(RecoveryConstants.RECOVERY_STATUS);
            if (statusStr != null && statusStr.length() > 0) {
                status = RecoveryStatus.Status.valueOf(statusStr);
            }
        }
        log.info("Recovery status is: {}", status);
        if (status == RecoveryStatus.Status.INIT || status == RecoveryStatus.Status.PREPARING
                || status == RecoveryStatus.Status.REPAIRING || status == RecoveryStatus.Status.SYNCING) {
            return true;
        }
        return false;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

	public void setSoftwareVersion(String softwareVersion) {
		this.softwareVersion = softwareVersion;
	}
	
	private void getSofttwareWithRetry() throws Exception, InterruptedException {
        int retryTimes = 0;
        RepositoryInfo targetInfo = null;
        while (retryTimes <= MAX_VERSION_RETRY_TIMES) {
            retryTimes++;
            targetInfo = this.coordinatorClient.getTargetInfo(RepositoryInfo.class);
            if (targetInfo == null){
                log.info("can't get version, try {} seconds later", MAX_VERSION_RETRY_INTERVAL/1000);
                Thread.sleep(MAX_VERSION_RETRY_INTERVAL);
                continue;
            }
            this.softwareVersion = targetInfo.getCurrentVersion().toString();
            log.info("Version: {}", softwareVersion);
            break;
        }
        
        if (targetInfo == null) {
            throw new Exception("Can't get version information from coordinator client");
        }
    }

    /**
     * Query upload status from ZK
     */
    public BackupUploadStatus queryBackupUploadStatus() {
        Configuration cfg = coordinatorClient.queryConfiguration(
                BackupConstants.BACKUP_UPLOAD_STATUS, Constants.GLOBAL_ID);
        Map<String, String> allItems = (cfg == null) ? new HashMap<String, String>() : cfg.getAllConfigs(false);
        BackupUploadStatus uploadStatus = new BackupUploadStatus(allItems);
        log.info("Upload status is: {}", uploadStatus);
        return uploadStatus;
    }

    /**
     * Persist upload status to ZK
     */
    public void persistBackupUploadStatus(BackupUploadStatus status) {
        Map<String, String> allItems = (status != null) ? status.getAllItems(): null;
        if (allItems == null || allItems.size() == 0){
            return;
        }
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(BackupConstants.BACKUP_UPLOAD_STATUS);
        config.setId(Constants.GLOBAL_ID);

        log.info("Setting upload status: {}", status);
        for (Map.Entry<String, String> entry : allItems.entrySet()) {
            config.setConfig(entry.getKey(), entry.getValue());
        }
        coordinatorClient.persistServiceConfiguration(config);
        log.info("Persist backup upload status to zk successfully");
    }
}
