/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.cache.Cache;
import plugin.StorageOsPlugin;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;

public class LicenseUtils {
    public static final String LICENSE_CACHE_INTERVAL = "2min";

    public static final String CONTROLLER_MODEL = "ViPR_Controller";
    public static final String UNSTRUCTURED_MODEL = "ViPR_Unstructured";

    public static boolean isLicensed(boolean useCache) {
        return isLicensed(LicenseType.CONTROLLER, useCache) || isLicensed(LicenseType.UNSTRUCTURED, useCache);
    }

    public static boolean isControllerLicensed() {
        return isLicensed(LicenseType.CONTROLLER, true);
    }

    public static boolean isObjectLicensed() {
        return isLicensed(LicenseType.UNSTRUCTURED, true);
    }

    public static boolean isCommodityLicensed() {
        return isLicensed(LicenseType.ECS, true) || isLicensed(LicenseType.COMMODITY, true);
    }

    public static boolean isCasLicensed() {
        return isLicensed(LicenseType.CAS, true);
    }

    public static boolean isLicensed(LicenseType type, boolean useCache) {
        if (type == null) {
            return false;
        }

        String licensedCacheKey = getLicensedCacheKey(type);
        Boolean licensed = null;
        if (useCache) {
            licensed = (Boolean) Cache.get(licensedCacheKey);
        }
        if (licensed == null) {
            if (StorageOsPlugin.isEnabled()) {
                CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
                licensed = coordinatorClient != null && coordinatorClient.isStorageProductLicensed(type);
            }
            // In Dev mode if we don't have coordinator, assume always licensed
            else if (Play.mode.isDev()) {
                licensed = Boolean.TRUE;
            }
            else {
                licensed = Boolean.FALSE;
            }

            // We don't really want to hit the license check each time.
            Cache.set(licensedCacheKey, licensed, LICENSE_CACHE_INTERVAL);
        }
        return licensed;
    }

    private static String getLicensedCacheKey(LicenseType type) {
        return "license.isLicensed." + type.toString();
    }

    private static void clearLicenseCache() {
        for (LicenseType type : LicenseType.values()) {
            Cache.delete(getLicensedCacheKey(type));
        }
    }

    /**
     * Gets the license from the API client. If there is any error retrieving the license, this returns null.
     * 
     * @return the license, or null if it could not be retrieved.
     */
    public static License getLicense() {
        try {
            License license = BourneUtil.getSysClient().license().get();

            //TODO: Creating mockup data. Remove me when the backend is ready.
            List<LicenseFeature> licenseFeatures = new ArrayList<LicenseFeature>();
            LicenseFeature licenseFeature = new LicenseFeature();
            licenseFeature.setLicensed(true);
            licenseFeature.setVersion("1.0");
            licenseFeature.setIssuer("EMC");
            licenseFeature.setNotice("Issued to A");
            licenseFeature.setDateExpires("12/30/2099");
            licenseFeature.setExpired(false);
            licenseFeature.setDateIssued("10-Jan-2014");
            licenseFeature.setModelId("ViPR_Controller:TIER1");
            licenseFeature.setSerial("AAAAAAAAA");
            licenseFeature.setStorageCapacity("10000000000");
            licenseFeatures.add(licenseFeature);
            licenseFeature = new LicenseFeature();
            licenseFeature.setLicensed(true);
            licenseFeature.setVersion("1.0");
            licenseFeature.setIssuer("EMC");
            licenseFeature.setNotice("Issued to B");
            licenseFeature.setDateExpires("12/30/2099");
            licenseFeature.setExpired(false);
            licenseFeature.setDateIssued("10-Jan-2014");
            licenseFeature.setModelId("ViPR_Controller:VNX2");
            licenseFeature.setSerial("AAAAAAAAA");
            licenseFeature.setStorageCapacity("20000000000");
            licenseFeatures.add(licenseFeature);
            license.setLicenseFeatures(licenseFeatures);

            return license;
        } catch (RuntimeException e) {
            Logger.error(e, "Could not retrieve license");
            return null;
        }
    }

    public static void updateLicenseText(String newLicenseText) {
        BourneUtil.getSysClient().license().set(newLicenseText);
        clearLicenseCache();
    }

    public static boolean hasCapacity(LicenseFeature feature) {
        return LicenseUtils.getLicensedCapacity(feature.getModelId()) != null;
    }

    public static String getLabel(LicenseFeature feature) {
        String modelKey = "license.model." + feature.getLicenseFeature();
        String label = MessagesUtils.get(modelKey);
        if (modelKey.equals(label)) {
            label = feature.getLicenseFeature();
        }
        return label;
    }

    public static String getType(LicenseFeature feature) {
        String typeKey = "license.type." + feature.getLicenseType().toLowerCase();
        String label = MessagesUtils.get(typeKey);
        if (typeKey.equals(label)) {
            label = feature.getLicenseType();
        }
        return label;
    }

    public static BigDecimal getLicensedCapacity(String model) {
        LicenseFeature licenseFeature = getLicenseFeature(model);
        if (licenseFeature != null && StringUtils.isNotBlank(licenseFeature.getStorageCapacity())) {
            try {

                return new BigDecimal(licenseFeature.getStorageCapacity());
            } catch (NumberFormatException e) {
                // ignore, just return -1
            }
        }
        return null;
    }

    private static LicenseFeature getLicenseFeature(String model) {
        if (StringUtils.isNotBlank(model)) {
            License license = getLicense();
            if (license != null) {
                for (LicenseFeature licenseFeature : license.getLicenseFeatures()) {
                    if (licenseFeature != null && model.equalsIgnoreCase(licenseFeature.getModelId())) {
                        return licenseFeature;
                    }
                }
            }
        }
        return null;
    }

}
