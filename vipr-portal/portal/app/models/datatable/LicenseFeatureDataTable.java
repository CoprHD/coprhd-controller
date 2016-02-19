/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;
import com.google.common.collect.Lists;

import util.LicenseUtils;
import util.MessagesUtils;
import util.SetupUtils;
import util.StorageStatsWrapper;
import util.datatable.DataTable;

import java.util.List;

public class LicenseFeatureDataTable extends DataTable {
    public static final String STATUS_OVER_CAPACITY = "OVER_CAPACITY";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_OPEN_SOURCE = "OPEN_SOURCE";

    public LicenseFeatureDataTable() {
        addColumn("name");
        addColumn("type");
        addColumn("status").setRenderFunction("renderLicenseStatus");
        addColumn("expiry").setRenderFunction("render.expiryStatus");
    }

    public static List<FeatureInfo> features(License license, StorageStatsWrapper stats) {
        List<FeatureInfo> features = Lists.newArrayList();
        for (LicenseFeature lf : license.getLicenseFeatures()) {
            features.add(new FeatureInfo(lf, stats));
        }
        return features;
    }

    public static class FeatureInfo {
        private String id;
        private String name;
        private String type;
        private String expiry;
        private String status;

        public FeatureInfo(LicenseFeature lf, StorageStatsWrapper stats) {
            this.id = lf.getModelId();
            this.name = LicenseUtils.getLabel(lf);
            this.type = LicenseUtils.getType(lf);

            if (lf.getDateExpires() == null) {
                this.expiry = MessagesUtils.get("license.permenant.notice", lf.getDateIssued());
            }
            else {
                this.expiry = MessagesUtils.get("license.expires.notice", lf.getDateIssued(), lf.getDateExpires());
            }
            if (lf.isTrialLicense()) {
                this.expiry = this.expiry + " " + MessagesUtils.get("license.trial.notice");
            }

            if (SetupUtils.isOssBuild()) {
                this.status = STATUS_OPEN_SOURCE;
            }
            else if (stats.isOverCapacity(lf)) {
                this.status = STATUS_OVER_CAPACITY;
            }
            else if (lf.isExpired()) {
                this.status = STATUS_EXPIRED;
            }
            else if (lf.isLicensed()) {
                this.status = STATUS_OK;
            }
            else {
                this.status = STATUS_UNKNOWN;
            }
        }
    }
}
