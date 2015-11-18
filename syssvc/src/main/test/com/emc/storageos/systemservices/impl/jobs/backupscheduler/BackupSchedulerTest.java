/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.management.backup.BackupFile;
import com.emc.storageos.management.backup.BackupFileSet;
import com.emc.storageos.management.backup.BackupSetInfo;
import com.emc.storageos.management.backup.BackupType;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.systemservices.TestProductName;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Unit test class for Backup Scheduler
 */
// Suppress Sonar warning that created objects are never used. TestProductName constructor is called to set static fields
@SuppressWarnings("squid:S1848")
public class BackupSchedulerTest {
    private static final String[] aliveBackupsAt20141231 = new String[] {
            // DAY: 1
            "vipr-2.2.0.0.123-1-20141231011000",
            "vipr-2.2.0.0.123-1-20141230011000",
            "vipr-2.2.0.0.123-1-20141229011000",
            "vipr-2.2.0.0.123-1-20141228011000",
            "vipr-2.2.0.0.123-1-20141227011000",
    };

    @Test
    public void testScheduling() throws Exception {
        new TestProductName();

        FakeConfiguration cfg = new FakeConfiguration();
        cfg.setSoftwareVersion("vipr-2.2.0.0.123");
        cfg.nodeCount = 1;
        cfg.copiesToKeep = 5;
        cfg.startOffsetMinutes = 60;
        cfg.interval = ScheduleTimeRange.ScheduleInterval.DAY;
        cfg.intervalMultiple = 1;
        cfg.schedulerEnabled = true;
        cfg.currentTime = Calendar.getInstance();
        cfg.currentTime.set(2014, Calendar.JANUARY, 1, 1, 10, 0);

        FakeBackupClient cli = new FakeBackupClient();

        BackupExecutor bakExec = new BackupExecutor(cfg, cli);

        for (int i = 0; i < 365; i++) {
            bakExec.runOnce();

            // Advance time
            cfg.currentTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        for (int i = 0; i < aliveBackupsAt20141231.length; i++) {
            Assert.assertTrue(
                    String.format("Missing backup: %s in %s", aliveBackupsAt20141231[i],
                            Strings.join(",", cli.localBackups.toArray(new String[1]))),
                    cli.localBackups.contains(aliveBackupsAt20141231[i]));
        }

        Set<String> tags = cli.getClusterBackupTags(false);
        Assert.assertEquals(String.format("Incorrect local backup copies: {%s}", Strings.join(",", tags.toArray(new String[tags.size()]))),
                aliveBackupsAt20141231.length, cli.localBackups.size());
    }

    @Test
    public void testUpload() throws Exception {
        new TestProductName();

        FakeConfiguration cfg = new FakeConfiguration();
        cfg.setSoftwareVersion("vipr-2.2.0.0.123");
        cfg.nodeCount = 1;
        cfg.copiesToKeep = 5;
        cfg.startOffsetMinutes = 60;
        cfg.interval = ScheduleTimeRange.ScheduleInterval.DAY;
        cfg.intervalMultiple = 1;
        cfg.schedulerEnabled = false;
        cfg.uploadUrl = "ftps://127.0.0.1/vipr_backup";
        cfg.uploadUserName = "abc";
        // As the worker will anyway retire expired backups, we still need to ensure the virtual date matches
        // the backup tag.
        cfg.currentTime = Calendar.getInstance();
        cfg.currentTime.set(2014, Calendar.DECEMBER, 31, 5, 20, 0);

        FakeBackupClient cli = new FakeBackupClient();

        // Generate fake backups for scheduler to upload to external server
        for (int i = 0; i < aliveBackupsAt20141231.length; i++) {
            cli.localBackups.add(aliveBackupsAt20141231[i]);
            cfg.retainedBackups.add(aliveBackupsAt20141231[i]);
        }

        UploadExecutor upExec = new UploadExecutor(cfg, cli);
        FakeUploader uploader = new FakeUploader(cfg, cli);
        upExec.setUploader(uploader);

        // Drive the worker so it will upload
        // NOTE: Since scheduler is disabled, no new scheduled backup will be generated, hence it will
        // not retire backups in cluster.
        upExec.runOnce();

        // Verify the backups are uploaded
        for (int i = 0; i < aliveBackupsAt20141231.length; i++) {
            Assert.assertTrue(String.format("Backup %s is not uploaded: %s", aliveBackupsAt20141231[i],
                    Strings.join(",", uploader.fileMap.keySet().toArray(new String[uploader.fileMap.size()]))),
                    uploader.fileMap.containsKey(aliveBackupsAt20141231[i] + "-1-1.zip")
                    );
        }
    }

    @Test
    public void testTimeCalculation() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("PRC"));
        now.set(2014, 10, 23, 11, 32, 25);

        Calendar shouldBe = Calendar.getInstance(TimeZone.getTimeZone("PRC"));
        shouldBe.set(Calendar.MILLISECOND, 0);
        shouldBe.set(2014, 10, 23, 0, 0, 0);

        Assert.assertEquals("Calculated interval aligned time is wrong.", shouldBe.getTime().getTime(),
                ScheduleTimeRange.getExpectedMostRecentBackupDateTime(now, ScheduleTimeRange.ScheduleInterval.DAY, 1, 0).getTime());
    }

    @Test
    public void testTagCleanup() throws Exception {
        new TestProductName();

        FakeConfiguration cfg = new FakeConfiguration();
        cfg.setSoftwareVersion("vipr-2.2.0.0.123");
        cfg.nodeCount = 1;
        cfg.copiesToKeep = 5;
        cfg.startOffsetMinutes = 60;
        cfg.interval = ScheduleTimeRange.ScheduleInterval.DAY;
        cfg.intervalMultiple = 1;
        cfg.schedulerEnabled = false;
        cfg.uploadUrl = "ftps://127.0.0.1/vipr_backup";
        cfg.uploadUserName = "abc";
        cfg.uploadedBackups.addAll(Arrays.asList(Arrays.copyOfRange(aliveBackupsAt20141231, 1, aliveBackupsAt20141231.length)));
        // As the worker will anyway retire expired backups, we still need to ensure the virtual date matches
        // the backup tag.
        cfg.currentTime = Calendar.getInstance();
        cfg.currentTime.set(2014, Calendar.DECEMBER, 31, 5, 20, 0);
        cfg.retainedBackups.add(aliveBackupsAt20141231[0]);
        cfg.retainedBackups.add(aliveBackupsAt20141231[1]);

        FakeBackupClient cli = new FakeBackupClient();
        cli.localBackups.add(aliveBackupsAt20141231[0]);
        cli.localBackups.add(aliveBackupsAt20141231[1]);

        UploadExecutor upExec = new UploadExecutor(cfg, cli);
        FakeUploader uploader = new FakeUploader(cfg, cli);
        upExec.setUploader(uploader);

        // Drive the worker so it will upload
        // NOTE: Since scheduler is disabled, no new scheduled backup will be generated, hence it will
        // not retire backups in cluster.
        upExec.runOnce();

        Assert.assertTrue("Missing completed tag", cfg.uploadedBackups.contains(aliveBackupsAt20141231[0]));
        Assert.assertTrue("Missing completed tag", cfg.uploadedBackups.contains(aliveBackupsAt20141231[1]));
        Assert.assertEquals("Tags not cleaned up", 2, cfg.uploadedBackups.size());
    }
}

class FakeUploader extends Uploader {
    public Map<String, Long> fileMap = new HashMap<>();

    public FakeUploader(SchedulerConfig cfg, BackupScheduler cli) {
        super(cfg, cli);
    }

    @Override
    public Long getFileSize(String fileName) throws Exception {
        return this.fileMap.get(fileName);
    }

    @Override
    public OutputStream upload(final String fileName, final long offset) throws Exception {
        // Verify we're uploading to right offset
        Long len = this.fileMap.get(fileName);
        if (len == null) {
            len = 0L;
        }

        Assert.assertEquals("Not resuming uploading at previous break position", len.longValue(), offset);

        return new OutputStream() {
            long written;

            @Override
            public void write(int b) throws IOException {
                this.written++;
            }

            @Override
            public void close() {
                fileMap.put(fileName, offset + this.written);
            }
        };
    }
}

class FakeBackupClient extends BackupScheduler {
    public Set<String> localBackups = new HashSet<>();

    @Override
    public void auditBackup(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {
    }

    @Override
    public void createBackup(String tag) {
        localBackups.add(tag);
    }

    @Override
    public void deleteBackup(String tag) {
        localBackups.remove(tag);
    }

    @Override
    public List<String> getDescParams(final String tag) {
        return new ArrayList<String>() {
            {
                add(tag);
                add("fake");
            }
        };
    }

    @Override
    public Set<String> getClusterBackupTags(boolean ignoreDownNodes) {
        return new HashSet<String>(Arrays.asList(localBackups.toArray(new String[localBackups.size()])));
    }

    @Override
    public Set<String> getNodeBackupTags() {
        return new HashSet<String>(Arrays.asList(localBackups.toArray(new String[localBackups.size()])));
    }

    private static BackupSetInfo createFakeInfo(String tag, BackupType type) {
        BackupSetInfo info = new BackupSetInfo();
        info.setCreateTime(10000);
        info.setName(String.format("%s_%s_vipr1.zip", tag, type));
        info.setSize(1024);
        return info;
    }

    @Override
    public BackupFileSet getDownloadFiles(String tag) {
        BackupFileSet files = new BackupFileSet(1);

        files.add(new BackupFile(createFakeInfo(tag, BackupType.db), "vipr1"));
        files.add(new BackupFile(createFakeInfo(tag, BackupType.geodb), "vipr1"));
        files.add(new BackupFile(createFakeInfo(tag, BackupType.zk), "vipr1"));

        return files;
    }

    @Override
    public String generateZipFileName(String tag, BackupFileSet files) {
        Set<String> availableNodes = files.uniqueNodes();
        return ScheduledBackupTag.toZipFileName(tag, 1, availableNodes.size());
    }

    @Override
    public void uploadTo(BackupFileSet files, long offset, OutputStream uploadStream) throws IOException {
        byte[] buf = new byte[1024];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) (i % 265);
        }

        uploadStream.write(buf, (int) offset, buf.length - (int) offset);
    }
}

class FakeConfiguration extends SchedulerConfig {
    public Calendar currentTime;
    public BackupUploadStatus uploadStatus = new BackupUploadStatus();

    public FakeConfiguration() {
        super(null, null, null);
    }

    @Override
    public String getUploadPassword() {
        return "Passwd";
    }

    // This controls the virtual time, which is running much faster than wall clock
    @Override
    public Calendar now() {
        return (Calendar) this.currentTime.clone();
    }

    @Override
    public void reload() throws ParseException, UnsupportedEncodingException {
    }

    @Override
    public void persist() {
    }

    @Override
    public BackupUploadStatus queryBackupUploadStatus() {
        return uploadStatus;
    }

    @Override
    public void persistBackupUploadStatus(BackupUploadStatus status) {
        uploadStatus.update(status.getBackupName(), status.getStatus(), status.getProgress(), status.getErrorCode());
    }

    @Override
    public boolean isAllowBackup() {
        return true;
    }

    @Override
    public AutoCloseable lock() throws Exception {
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
            }
        };
    }
}
