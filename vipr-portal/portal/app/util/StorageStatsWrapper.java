/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.math.BigDecimal;
import com.emc.sa.util.SizeUtils;
import com.emc.vipr.model.sys.healthmonitor.StorageStats;
import com.emc.vipr.model.sys.licensing.LicenseFeature;

public class StorageStatsWrapper {
    private StorageStats storageStats;

    public StorageStatsWrapper(StorageStats stats) {
        if (stats == null) {
            throw new IllegalArgumentException();
        }
        this.storageStats = stats;
    }

    public boolean isOverCapacity(LicenseFeature feature) {
        // TODO: Do not do compliance check in Yoda.
        // Will enable it after Yoda
        // return getUsagePercentage(feature) >= 100;
        return false;
    }

    public String getManagedCapacityLabel(LicenseFeature feature) {
        BigDecimal b = getManagedCapacity(feature);
        if (b != null) {
            return SizeUtils.humanReadableByteCount(b.doubleValue());
        }
        return SizeUtils.humanReadableByteCount(0L);
    }

    public long getUsagePercentage(LicenseFeature feature) {
        BigDecimal totalManaged = getManagedCapacity(feature);
        BigDecimal licenseCap = LicenseUtils.getLicensedCapacity(feature.getModelId());
        return percentage(totalManaged, licenseCap);
    }

    public String getUsageLabel(LicenseFeature feature) {
        String managedCapacityLabel = getManagedCapacityLabel(feature);
        String licensedCapacityLabel = getLicensedCapacityLabel(feature);
        return MessagesUtils.get("license.usage", managedCapacityLabel, licensedCapacityLabel);
    }

    private BigDecimal getManagedCapacity(LicenseFeature feature) {
        if (LicenseUtils.CONTROLLER_MODEL.equalsIgnoreCase(feature.getModelId())) {
            BigDecimal totalController = new BigDecimal(0);
            if (storageStats.getControllerStorageStats() == null) {
                return totalController;
            }
            totalController = totalController.add(new BigDecimal(storageStats.getControllerStorageStats().getFileCapacityKB() * 1024));
            totalController = totalController.add(new BigDecimal(storageStats.getControllerStorageStats().getBlockCapacityKB() * 1024));
            totalController = totalController
                    .add(new BigDecimal(storageStats.getControllerStorageStats().getFreeManagedCapacityKB() * 1024));
            return totalController;
        }
        return BigDecimal.ZERO;
    }

    private String getLicensedCapacityLabel(LicenseFeature feature) {
        BigDecimal b = LicenseUtils.getLicensedCapacity(feature.getModelId());
        if (b != null) {
            return SizeUtils.humanReadableByteCount(b.doubleValue());
        }
        return SizeUtils.humanReadableByteCount(0L);
    }

    private long percentage(BigDecimal managed, BigDecimal cap) {
        if (managed != null && cap != null && cap.doubleValue() > 0) {
            BigDecimal percentage = managed.divide(cap, 4, BigDecimal.ROUND_HALF_EVEN);
            return Math.min(Math.round(percentage.doubleValue() * 100), 100);
        }
        return 0;
    }
}
