/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

import java.util.List;

/**
 * Java bean class for "sloprovisioning/symmetrix/{}/srp/{}" GET method JSON result deserialization.
 *
 * Created by gang on 6/23/16.
 */
public class Srp {
    private Boolean rdfa_dse;
    private Integer num_of_disk_groups;
    private List<String> SrpSgDemandId;
    private String srpId;
    private Double total_allocated_cap_gb;
    private String emulation;
    private String description;
    private Integer num_of_srp_slo_demands;
    private Double total_usable_cap_gb;
    private Double total_subscribed_cap_gb;
    private Integer num_of_srp_sg_demands;
    private Double total_snapshot_allocated_cap_gb;
    private Double total_srdf_dse_allocated_cap_gb;
    private Integer reserved_cap_percent;
    private List<String> diskGroupId;
    private List<String> srpSloDemandId;

    @Override
    public String toString() {
        return "Srp{" +
            "rdfa_dse=" + rdfa_dse +
            ", num_of_disk_groups=" + num_of_disk_groups +
            ", SrpSgDemandId=" + SrpSgDemandId +
            ", srpId='" + srpId + '\'' +
            ", total_allocated_cap_gb=" + total_allocated_cap_gb +
            ", emulation='" + emulation + '\'' +
            ", description='" + description + '\'' +
            ", num_of_srp_slo_demands=" + num_of_srp_slo_demands +
            ", total_usable_cap_gb=" + total_usable_cap_gb +
            ", total_subscribed_cap_gb=" + total_subscribed_cap_gb +
            ", num_of_srp_sg_demands=" + num_of_srp_sg_demands +
            ", total_snapshot_allocated_cap_gb=" + total_snapshot_allocated_cap_gb +
            ", total_srdf_dse_allocated_cap_gb=" + total_srdf_dse_allocated_cap_gb +
            ", reserved_cap_percent=" + reserved_cap_percent +
            ", diskGroupId=" + diskGroupId +
            ", srpSloDemandId=" + srpSloDemandId +
            '}';
    }

    public Boolean getRdfa_dse() {
        return rdfa_dse;
    }

    public void setRdfa_dse(Boolean rdfa_dse) {
        this.rdfa_dse = rdfa_dse;
    }

    public Integer getNum_of_disk_groups() {
        return num_of_disk_groups;
    }

    public void setNum_of_disk_groups(Integer num_of_disk_groups) {
        this.num_of_disk_groups = num_of_disk_groups;
    }

    public List<String> getSrpSgDemandId() {
        return SrpSgDemandId;
    }

    public void setSrpSgDemandId(List<String> srpSgDemandId) {
        SrpSgDemandId = srpSgDemandId;
    }

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public Double getTotal_allocated_cap_gb() {
        return total_allocated_cap_gb;
    }

    public void setTotal_allocated_cap_gb(Double total_allocated_cap_gb) {
        this.total_allocated_cap_gb = total_allocated_cap_gb;
    }

    public String getEmulation() {
        return emulation;
    }

    public void setEmulation(String emulation) {
        this.emulation = emulation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getNum_of_srp_slo_demands() {
        return num_of_srp_slo_demands;
    }

    public void setNum_of_srp_slo_demands(Integer num_of_srp_slo_demands) {
        this.num_of_srp_slo_demands = num_of_srp_slo_demands;
    }

    public Double getTotal_usable_cap_gb() {
        return total_usable_cap_gb;
    }

    public void setTotal_usable_cap_gb(Double total_usable_cap_gb) {
        this.total_usable_cap_gb = total_usable_cap_gb;
    }

    public Double getTotal_subscribed_cap_gb() {
        return total_subscribed_cap_gb;
    }

    public void setTotal_subscribed_cap_gb(Double total_subscribed_cap_gb) {
        this.total_subscribed_cap_gb = total_subscribed_cap_gb;
    }

    public Integer getNum_of_srp_sg_demands() {
        return num_of_srp_sg_demands;
    }

    public void setNum_of_srp_sg_demands(Integer num_of_srp_sg_demands) {
        this.num_of_srp_sg_demands = num_of_srp_sg_demands;
    }

    public Double getTotal_snapshot_allocated_cap_gb() {
        return total_snapshot_allocated_cap_gb;
    }

    public void setTotal_snapshot_allocated_cap_gb(Double total_snapshot_allocated_cap_gb) {
        this.total_snapshot_allocated_cap_gb = total_snapshot_allocated_cap_gb;
    }

    public Double getTotal_srdf_dse_allocated_cap_gb() {
        return total_srdf_dse_allocated_cap_gb;
    }

    public void setTotal_srdf_dse_allocated_cap_gb(Double total_srdf_dse_allocated_cap_gb) {
        this.total_srdf_dse_allocated_cap_gb = total_srdf_dse_allocated_cap_gb;
    }

    public Integer getReserved_cap_percent() {
        return reserved_cap_percent;
    }

    public void setReserved_cap_percent(Integer reserved_cap_percent) {
        this.reserved_cap_percent = reserved_cap_percent;
    }

    public List<String> getDiskGroupId() {
        return diskGroupId;
    }

    public void setDiskGroupId(List<String> diskGroupId) {
        this.diskGroupId = diskGroupId;
    }

    public List<String> getSrpSloDemandId() {
        return srpSloDemandId;
    }

    public void setSrpSloDemandId(List<String> srpSloDemandId) {
        this.srpSloDemandId = srpSloDemandId;
    }
}
