/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.emc.vipr.client.ViPRCatalogClient2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;

import play.Logger;
import play.jobs.Job;

import static util.OrderUtils.dateToLongStr;

public class SupportOrderPackageCreator {

    private static final String TIMESTAMP = "ddMMyy-HHmm";
    private Date startDate;
    private Date endDate;
    private String tenantIDs;
    private String orderIDs;

    private ViPRCatalogClient2 client;

    public SupportOrderPackageCreator(ViPRCatalogClient2 client) {
        this.client = Objects.requireNonNull(client);
    }

    private ViPRCatalogClient2 api() {
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
            writeOrders(zip);
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

    private void writeOrders(ZipOutputStream zip) throws IOException {
        String path = "order";
        OutputStream stream = nextEntry(zip, path);
        InputStream in;
        if (orderIDs != null) {
            in = api().orders().downloadOrdersAsText(null, null, null, orderIDs, null);
        } else {
            in = api().orders().downloadOrdersAsText(dateToLongStr(startDate), dateToLongStr(endDate), tenantIDs, null, null);
        }

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
        private SupportOrderPackageCreator supportPackage;

        public CreateSupportPackageJob(OutputStream out, SupportOrderPackageCreator supportPackage) {
            this.out = out;
            this.supportPackage = supportPackage;
        }

        @Override
        public void doJob() throws Exception {
            supportPackage.writeTo(out);
        }
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getTenantIDs() {
        return tenantIDs;
    }

    public void setTenantIDs(String tenantIDs) {
        this.tenantIDs = tenantIDs;
    }

    public String getOrderIDs() {
        return orderIDs;
    }

    public void setOrderIDs(String orderIDs) {
        this.orderIDs = orderIDs;
    }
}
