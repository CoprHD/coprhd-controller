/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import static controllers.Common.flashException;
import static util.BourneUtil.getSysClient;

import java.io.File;
import java.io.IOException;
import java.util.List;

import models.datatable.LicenseFeatureDataTable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.LicenseUtils;
import util.MessagesUtils;
import util.StorageStatsWrapper;
import util.datatable.DataTablesSupport;

import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.model.sys.healthmonitor.StorageStats;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class Licensing extends Controller {
    public static void index() {
        LicenseFeatureDataTable dataTable = new LicenseFeatureDataTable();
        render(dataTable);
    }

    public static void listJson() {
        License license = LicenseUtils.getLicense();
        StorageStatsWrapper storageStatsWrapper = getStats();

        List<LicenseFeatureDataTable.FeatureInfo> features = LicenseFeatureDataTable.features(license, storageStatsWrapper);
        renderJSON(DataTablesSupport.createJSON(features, params));
    }

    public static void itemDetails(String id) {
        License license = LicenseUtils.getLicense();

        LicenseFeature lf = null;
        for (LicenseFeature feature : license.getLicenseFeatures()) {
            if (StringUtils.equalsIgnoreCase(feature.getModelId(), id)) {
                lf = feature;
                break;
            }
        }
        render(lf);
    }

    private static StorageStatsWrapper getStats() {
        try {
            StorageStats storageStats = getSysClient().health().getStorageStats();
            return new StorageStatsWrapper(storageStats);
        } catch (ViPRException e) {
            // gfl - Call will fail if object service isn't running
            flashException(e);
            Logger.warn("Failed to retrieve /monitor/storage");
        }
        return null;
    }

    public static void uploadLicense(File newLicenseFile) {

        String newLicenseText = null;
        if (newLicenseFile != null) {
            try {
                newLicenseText = FileUtils.readFileToString(newLicenseFile);
            } catch (IOException e) {
                newLicenseText = null;
                Validation.addError("newLicenseFile", MessagesUtils.get("license.invalidLicenseFile"));
                Logger.error(e, "Failed to read license text file");
            }
        }
        else {
            Validation.addError("newLicenseFile", MessagesUtils.get("license.licenseFileIsRequired"));
        }

        if (StringUtils.isNotBlank(newLicenseText)) {
            LicenseUtils.updateLicenseText(newLicenseText);
            flash.success(MessagesUtils.get("license.licenseFileUpdated"));
        }
        else {
            flash.error(MessagesUtils.get("license.uploadFailed"));
        }

        if (Validation.hasErrors()) {
            validation.keep();
        }

        index();
    }
}
