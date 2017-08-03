/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

import com.emc.storageos.driver.univmax.rest.type.common.PhysicalCapacityType;

public class SymmetrixType {

    // min/max occurs: 1/1
    private String symmetrixId;
    // min/max occurs: 0/1
    private Long device_count;
    // min/max occurs: 0/1
    private String ucode;
    // min/max occurs: 0/1
    private String targetUcode;
    // min/max occurs: 0/1
    private String model;
    // min/max occurs: 0/1
    private Boolean local;
    // min/max occurs: 0/1
    private String default_fba_srp;
    // min/max occurs: 0/1
    private String default_ckd_srp;
    // min/max occurs: 0/1
    private SloComplianceType sloCompliance;
    // min/max occurs: 0/1
    private PhysicalCapacityType physicalCapacity;
    // min/max occurs: 0/1
    private Long host_visible_device_count;
    // min/max occurs: 0/1
    private Double overall_efficiency_ratio_to_one;
    // min/max occurs: 0/1
    private Double VP_overall_efficiency_ratio_to_one;
    // min/max occurs: 0/1
    private Double VP_saved_percent;
    // min/max occurs: 0/1
    private Double VP_shared_ratio_to_one;
    // min/max occurs: 0/1
    private Double snapshot_overall_efficiency_ratio_to_one;
    // min/max occurs: 0/1
    private Double snapshot_saved_percent;
    // min/max occurs: 0/1
    private Double snapshot_shared_ratio_to_one;
    // min/max occurs: 0/1
    private Double compression_overall_ratio_to_one;
    // min/max occurs: 0/1
    private Double compression_VP_ratio_to_one;
    // min/max occurs: 0/1
    private Double compression_snapshot_ratio_to_one;
    // min/max occurs: 0/1
    private Boolean compression_enabled;
    // min/max occurs: 0/1
    private Double system_meta_data_used_percent;
    // min/max occurs: 0/1
    private Long replication_cache_used_percent;
    // min/max occurs: 0/1
    private Double total_subscribed_cap_gb;
    // min/max occurs: 0/1
    private Double total_allocated_cap_gb;
    // min/max occurs: 0/1
    private Double total_usable_cap_gb;
    // min/max occurs: 0/1
    private Double effective_used_capacity_percent;
    // min/max occurs: 0/1
    private Double external_capacity_gb;

    public String getSymmetrixId() {
        return symmetrixId;
    }

    public Long getDevice_count() {
        return device_count;
    }

    public String getUcode() {
        return ucode;
    }

    public String getTargetUcode() {
        return targetUcode;
    }

    public String getModel() {
        return model;
    }

    public Boolean getLocal() {
        return local;
    }

    public String getDefault_fba_srp() {
        return default_fba_srp;
    }

    public String getDefault_ckd_srp() {
        return default_ckd_srp;
    }

    public SloComplianceType getSloCompliance() {
        return sloCompliance;
    }

    public PhysicalCapacityType getPhysicalCapacity() {
        return physicalCapacity;
    }

    public Long getHost_visible_device_count() {
        return host_visible_device_count;
    }

    public Double getOverall_efficiency_ratio_to_one() {
        return overall_efficiency_ratio_to_one;
    }

    public Double getVP_overall_efficiency_ratio_to_one() {
        return VP_overall_efficiency_ratio_to_one;
    }

    public Double getVP_saved_percent() {
        return VP_saved_percent;
    }

    public Double getVP_shared_ratio_to_one() {
        return VP_shared_ratio_to_one;
    }

    public Double getSnapshot_overall_efficiency_ratio_to_one() {
        return snapshot_overall_efficiency_ratio_to_one;
    }

    public Double getSnapshot_saved_percent() {
        return snapshot_saved_percent;
    }

    public Double getSnapshot_shared_ratio_to_one() {
        return snapshot_shared_ratio_to_one;
    }

    public Double getCompression_overall_ratio_to_one() {
        return compression_overall_ratio_to_one;
    }

    public Double getCompression_VP_ratio_to_one() {
        return compression_VP_ratio_to_one;
    }

    public Double getCompression_snapshot_ratio_to_one() {
        return compression_snapshot_ratio_to_one;
    }

    public Boolean getCompression_enabled() {
        return compression_enabled;
    }

    public Double getSystem_meta_data_used_percent() {
        return system_meta_data_used_percent;
    }

    public Long getReplication_cache_used_percent() {
        return replication_cache_used_percent;
    }

    public Double getTotal_subscribed_cap_gb() {
        return total_subscribed_cap_gb;
    }

    public Double getTotal_allocated_cap_gb() {
        return total_allocated_cap_gb;
    }

    public Double getTotal_usable_cap_gb() {
        return total_usable_cap_gb;
    }

    public Double getEffective_used_capacity_percent() {
        return effective_used_capacity_percent;
    }

    public Double getExternal_capacity_gb() {
        return external_capacity_gb;
    }
}
