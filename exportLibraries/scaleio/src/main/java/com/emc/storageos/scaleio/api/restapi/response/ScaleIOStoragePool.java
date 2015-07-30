/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi.response;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.scaleio.api.ScaleIOQueryStoragePoolResult;

/**
 * Storage pool attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleIOStoragePool {
    private String protectionDomainId;
    private String name;
    private String id;
    private String capacityAvailableForVolumeAllocationInKb;
    private String maxCapacityInKb;
    private String numOfVolumes;

    public String getProtectionDomainId() {
        return protectionDomainId;
    }

    public void setProtectionDomainId(String protectionDomainId) {
        this.protectionDomainId = protectionDomainId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCapacityAvailableForVolumeAllocationInKb() {
        return capacityAvailableForVolumeAllocationInKb;
    }

    public void setCapacityAvailableForVolumeAllocationInKb(
            String capacityAvailableForVolumeAllocationInKb) {
        this.capacityAvailableForVolumeAllocationInKb = capacityAvailableForVolumeAllocationInKb;
    }

    public String getMaxCapacityInKb() {
        return maxCapacityInKb;
    }

    public void setMaxCapacityInKb(String maxCapacityInKb) {
        this.maxCapacityInKb = maxCapacityInKb;
    }

    public String getNumOfVolumes() {
        return numOfVolumes;
    }

    public void setNumOfVolumes(String numOfVolumes) {
        this.numOfVolumes = numOfVolumes;
    }

    public ScaleIOQueryStoragePoolResult toQueryStoragePoolResult() {
        ScaleIOQueryStoragePoolResult result = new ScaleIOQueryStoragePoolResult();
        result.setAvailableCapacity(capacityAvailableForVolumeAllocationInKb);
        result.setName(name);
        result.setTotalCapacity(maxCapacityInKb);
        result.setVolumeCount(numOfVolumes);
        return result;

    }
}
