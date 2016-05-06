/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import com.emc.vipr.client.ViPRCoreClient;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import play.Logger;
import play.jobs.Job;
import play.mvc.Http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SupportAuditPackageCreator {

    private static final String TIMESTAMP = "ddMMyy-HHmm";
    private String timeBucket = null;
    private Date startTime = null;
    private Date endTime = null;
    private String svcType = null;
    private String user = null;
    private String result = null;
    private String keyword = null;
    private String language = "en_US";

    private Http.Request request;
    private ViPRCoreClient client;

    public SupportAuditPackageCreator(Http.Request request, ViPRCoreClient client) {
        this.request = request;
        this.client = Objects.requireNonNull(client);
    }

    public String getTimeBucket() {
        return timeBucket;
    }

    public void setTimeBucket(String timeBucket) {
        this.timeBucket = timeBucket;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getSvcType() {
        return svcType;
    }

    public void setSvcType(String svcType) {
        this.svcType = svcType;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    private ViPRCoreClient api() {
        return client;
    }

    public static String formatTimestamp(Calendar cal) {
        final SimpleDateFormat TIME1 = new SimpleDateFormat(TIMESTAMP);
        return cal != null ? TIME1.format(cal.getTime()) : "UNKNOWN";
    }

    public CreateSupportPackageJob createJob(OutputStream out) {
        return new CreateSupportPackageJob(out, this);
    }

    public void writeTo(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
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

    private void writeLogs(ZipOutputStream zip) throws IOException {
        String path = "audit.log";
        OutputStream stream = nextEntry(zip, path);
        InputStream in = api().audit().getAsText(startTime, endTime, svcType, user, result, keyword, language);
        try {
            IOUtils.copy(in, stream);
        } finally {
            in.close();
            stream.close();
        }
    }

    /**
     * Job that runs to generate a support package.
     */
    public static class CreateSupportPackageJob extends Job {
        private OutputStream out;
        private SupportAuditPackageCreator supportPackage;

        public CreateSupportPackageJob(OutputStream out, SupportAuditPackageCreator supportPackage) {
            this.out = out;
            this.supportPackage = supportPackage;
        }

        @Override
        public void doJob() throws Exception {
            supportPackage.writeTo(out);
        }
    }
}
