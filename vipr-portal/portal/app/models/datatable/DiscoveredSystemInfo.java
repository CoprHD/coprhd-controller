/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.DiscoveryStatusUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;

/**
 * Common base for any discoverable object datatable rows.
 * 
 * @author jonnymiller
 */
public class DiscoveredSystemInfo {
    public static final String LAST_DISCOVERED_DATE = "lastDiscoveredDate";
    public static final String DISCOVERY_STATUS = "discoveryStatus";
    public Long lastDiscoveredDate;
    public String discoveryStatus;
    public String errorSummary;
    public String errorDetails;
    public String statusMessage;
    public String compatibilityStatus;

    public DiscoveredSystemInfo() {
    }

    public DiscoveredSystemInfo(DiscoveredSystemObjectRestRep data) {
        this.lastDiscoveredDate = DiscoveryStatusUtils.getLastDiscoveryDate(data);
        this.discoveryStatus = DiscoveryStatusUtils.getDiscoveryStatus(data);
        this.statusMessage = DiscoveryStatusUtils.getDiscoveryMessage(data);
        this.errorSummary = DiscoveryStatusUtils.getErrorSummary(data);
        this.errorDetails = DiscoveryStatusUtils.getErrorDetails(data);
        this.compatibilityStatus = DiscoveryStatusUtils.getCompatibilityStatus(data);
    }

    public DiscoveredSystemInfo(StorageProviderRestRep data) {
        this.lastDiscoveredDate = DiscoveryStatusUtils.getLastDiscoveryDate(data);
        this.discoveryStatus = DiscoveryStatusUtils.getDiscoveryStatus(data);
        this.statusMessage = DiscoveryStatusUtils.getDiscoveryMessage(data);
        this.errorSummary = DiscoveryStatusUtils.getErrorSummary(data);
        this.errorDetails = DiscoveryStatusUtils.getErrorDetails(data);
        this.compatibilityStatus = DiscoveryStatusUtils.getCompatibilityStatus(data);
    }

    public DiscoveredSystemInfo(ComputeSystemRestRep data) {
        this.lastDiscoveredDate = DiscoveryStatusUtils.getLastDiscoveryDate(data);
        this.discoveryStatus = DiscoveryStatusUtils.getDiscoveryStatus(data);
        this.statusMessage = DiscoveryStatusUtils.getDiscoveryMessage(data);
        this.errorSummary = DiscoveryStatusUtils.getErrorSummary(data);
        this.errorDetails = DiscoveryStatusUtils.getErrorDetails(data);
    }

    public DiscoveredSystemInfo(ComputeElementRestRep data) {
        this.lastDiscoveredDate = DiscoveryStatusUtils.getLastDiscoveryDate(data);
        this.discoveryStatus = DiscoveryStatusUtils.getDiscoveryStatus(data);
        this.statusMessage = DiscoveryStatusUtils.getDiscoveryMessage(data);
        this.errorSummary = DiscoveryStatusUtils.getErrorSummary(data);
        this.errorDetails = DiscoveryStatusUtils.getErrorDetails(data);
    }

    public static void addDiscoveryColumns(DataTable dataTable) {
        dataTable.addColumn(LAST_DISCOVERED_DATE).setRenderFunction("render.relativeDate").hidden();
        dataTable.addColumn(DISCOVERY_STATUS).setRenderFunction("render.discoveryStatusIcon");
    }
}
