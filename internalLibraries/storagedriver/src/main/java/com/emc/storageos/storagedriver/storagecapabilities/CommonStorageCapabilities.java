/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import java.util.List;

/**
 * This class describes set of standard service options of storage.
 */

public class CommonStorageCapabilities {
    private List<CapabilityInstance> dataStorageServiceOptions;
    private List<DataConnectivityServiceOption> dataConnectivity;
    private List<DataPerformanceServiceOption> dataPerformance;
    private List<DataProtectionServiceOption> dataProtection;
    private List<DataSecurityServiceOption> dataSecurity;
    private List<ExportPathsServiceOption> exportPathParams;

    public List<CapabilityInstance> getDataStorage() {
        return dataStorageServiceOptions;
    }

    public void setDataStorage(List<CapabilityInstance> dataStorage) {
        this.dataStorageServiceOptions = dataStorage;
    }

    public List<DataConnectivityServiceOption> getDataConnectivity() {
        return dataConnectivity;
    }

    public void setDataConnectivity(List<DataConnectivityServiceOption> dataConnectivity) {
        this.dataConnectivity = dataConnectivity;
    }

    public List<DataPerformanceServiceOption> getDataPerformance() {
        return dataPerformance;
    }

    public void setDataPerformance(List<DataPerformanceServiceOption> dataPerformance) {
        this.dataPerformance = dataPerformance;
    }

    public List<DataProtectionServiceOption> getDataProtection() {
        return dataProtection;
    }

    public void setDataProtection(List<DataProtectionServiceOption> dataProtection) {
        this.dataProtection = dataProtection;
    }

    public List<DataSecurityServiceOption> getDataSecurity() {
        return dataSecurity;
    }

    public void setDataSecurity(List<DataSecurityServiceOption> dataSecurity) {
        this.dataSecurity = dataSecurity;
    }

    public List<ExportPathsServiceOption> getExportPathParams() {
        return exportPathParams;
    }

    public void setExportPathParams(List<ExportPathsServiceOption> exportPathParams) {
        this.exportPathParams = exportPathParams;
    }
}
