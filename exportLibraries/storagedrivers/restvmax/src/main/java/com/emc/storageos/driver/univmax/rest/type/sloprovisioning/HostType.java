/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultType;

/**
 * @author fengs5
 *
 */
public class HostType extends GenericResultType {

    String hostId;
    long num_of_masking_views;
    int num_of_initiators;
    int num_of_host_groups;
    boolean port_flags_override;
    boolean consistent_lun;
    String enabled_flags;
    String disabled_flags;
    String type;
    List<String> initiator;
    List<String> hostgroup;
    List<String> maskingview;

    /**
     * @return the hostId
     */
    public String getHostId() {
        return hostId;
    }

    /**
     * @param hostId the hostId to set
     */
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    /**
     * @return the num_of_masking_views
     */
    public long getNum_of_masking_views() {
        return num_of_masking_views;
    }

    /**
     * @param num_of_masking_views the num_of_masking_views to set
     */
    public void setNum_of_masking_views(long num_of_masking_views) {
        this.num_of_masking_views = num_of_masking_views;
    }

    /**
     * @return the num_of_initiators
     */
    public int getNum_of_initiators() {
        return num_of_initiators;
    }

    /**
     * @param num_of_initiators the num_of_initiators to set
     */
    public void setNum_of_initiators(int num_of_initiators) {
        this.num_of_initiators = num_of_initiators;
    }

    /**
     * @return the num_of_host_groups
     */
    public int getNum_of_host_groups() {
        return num_of_host_groups;
    }

    /**
     * @param num_of_host_groups the num_of_host_groups to set
     */
    public void setNum_of_host_groups(int num_of_host_groups) {
        this.num_of_host_groups = num_of_host_groups;
    }

    /**
     * @return the port_flags_override
     */
    public boolean isPort_flags_override() {
        return port_flags_override;
    }

    /**
     * @param port_flags_override the port_flags_override to set
     */
    public void setPort_flags_override(boolean port_flags_override) {
        this.port_flags_override = port_flags_override;
    }

    /**
     * @return the consistent_lun
     */
    public boolean isConsistent_lun() {
        return consistent_lun;
    }

    /**
     * @param consistent_lun the consistent_lun to set
     */
    public void setConsistent_lun(boolean consistent_lun) {
        this.consistent_lun = consistent_lun;
    }

    /**
     * @return the enabled_flags
     */
    public String getEnabled_flags() {
        return enabled_flags;
    }

    /**
     * @param enabled_flags the enabled_flags to set
     */
    public void setEnabled_flags(String enabled_flags) {
        this.enabled_flags = enabled_flags;
    }

    /**
     * @return the disabled_flags
     */
    public String getDisabled_flags() {
        return disabled_flags;
    }

    /**
     * @param disabled_flags the disabled_flags to set
     */
    public void setDisabled_flags(String disabled_flags) {
        this.disabled_flags = disabled_flags;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the initiator
     */
    public List<String> getInitiator() {
        return initiator;
    }

    /**
     * @param initiator the initiator to set
     */
    public void setInitiator(List<String> initiator) {
        this.initiator = initiator;
    }

    /**
     * @return the hostgroup
     */
    public List<String> getHostgroup() {
        return hostgroup;
    }

    /**
     * @param hostgroup the hostgroup to set
     */
    public void setHostgroup(List<String> hostgroup) {
        this.hostgroup = hostgroup;
    }

    /**
     * @return the maskingview
     */
    public List<String> getMaskingview() {
        return maskingview;
    }

    /**
     * @param maskingview the maskingview to set
     */
    public void setMaskingview(List<String> maskingview) {
        this.maskingview = maskingview;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "HostType [hostId=" + hostId + ", num_of_masking_views=" + num_of_masking_views + ", num_of_initiators=" + num_of_initiators
                + ", num_of_host_groups=" + num_of_host_groups + ", port_flags_override=" + port_flags_override + ", consistent_lun="
                + consistent_lun + ", enabled_flags=" + enabled_flags + ", disabled_flags=" + disabled_flags + ", type=" + type
                + ", initiator=" + initiator + ", hostgroup=" + hostgroup + ", maskingview=" + maskingview + ", toString()="
                + super.toString() + "]";
    }

}
