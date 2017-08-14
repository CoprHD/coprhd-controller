/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.SymmetrixPortKeyType;

public class InitiatorType {

    private String initiatorId;
    private List<SymmetrixPortKeyType> symmetrixPortKey;
    private String alias;
    private String type;
    private String fcid;
    private String fcid_value;
    private String fcid_lockdown;
    private String ip_address;
    private boolean logged_in;
    private boolean on_fabric;
    private boolean port_flags_override;
    private String enabled_flags;
    private String disabled_flags;
    private String flags_in_effect;
    private int num_of_vols;
    private String host;
    private int num_of_host_groups;
    private List<String> hostGroup;
    private int num_of_masking_views;
    private List<String> maskingview;

    /**
     * @return the initiatorId
     */
    public String getInitiatorId() {
        return initiatorId;
    }

    /**
     * @param initiatorId the initiatorId to set
     */
    public void setInitiatorId(String initiatorId) {
        this.initiatorId = initiatorId;
    }

    /**
     * @return the symmetrixPortKey
     */
    public List<SymmetrixPortKeyType> getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    /**
     * @param symmetrixPortKey the symmetrixPortKey to set
     */
    public void setSymmetrixPortKey(List<SymmetrixPortKeyType> symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    /**
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * @param alias the alias to set
     */
    public void setAlias(String alias) {
        this.alias = alias;
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
     * @return the fcid
     */
    public String getFcid() {
        return fcid;
    }

    /**
     * @param fcid the fcid to set
     */
    public void setFcid(String fcid) {
        this.fcid = fcid;
    }

    /**
     * @return the fcid_value
     */
    public String getFcid_value() {
        return fcid_value;
    }

    /**
     * @param fcid_value the fcid_value to set
     */
    public void setFcid_value(String fcid_value) {
        this.fcid_value = fcid_value;
    }

    /**
     * @return the fcid_lockdown
     */
    public String getFcid_lockdown() {
        return fcid_lockdown;
    }

    /**
     * @param fcid_lockdown the fcid_lockdown to set
     */
    public void setFcid_lockdown(String fcid_lockdown) {
        this.fcid_lockdown = fcid_lockdown;
    }

    /**
     * @return the ip_address
     */
    public String getIp_address() {
        return ip_address;
    }

    /**
     * @param ip_address the ip_address to set
     */
    public void setIp_address(String ip_address) {
        this.ip_address = ip_address;
    }

    /**
     * @return the logged_in
     */
    public boolean isLogged_in() {
        return logged_in;
    }

    /**
     * @param logged_in the logged_in to set
     */
    public void setLogged_in(boolean logged_in) {
        this.logged_in = logged_in;
    }

    /**
     * @return the on_fabric
     */
    public boolean isOn_fabric() {
        return on_fabric;
    }

    /**
     * @param on_fabric the on_fabric to set
     */
    public void setOn_fabric(boolean on_fabric) {
        this.on_fabric = on_fabric;
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
     * @return the flags_in_effect
     */
    public String getFlags_in_effect() {
        return flags_in_effect;
    }

    /**
     * @param flags_in_effect the flags_in_effect to set
     */
    public void setFlags_in_effect(String flags_in_effect) {
        this.flags_in_effect = flags_in_effect;
    }

    /**
     * @return the num_of_vols
     */
    public int getNum_of_vols() {
        return num_of_vols;
    }

    /**
     * @param num_of_vols the num_of_vols to set
     */
    public void setNum_of_vols(int num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
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
     * @return the hostGroup
     */
    public List<String> getHostGroup() {
        return hostGroup;
    }

    /**
     * @param hostGroup the hostGroup to set
     */
    public void setHostGroup(List<String> hostGroup) {
        this.hostGroup = hostGroup;
    }

    /**
     * @return the num_of_masking_views
     */
    public int getNum_of_masking_views() {
        return num_of_masking_views;
    }

    /**
     * @param num_of_masking_views the num_of_masking_views to set
     */
    public void setNum_of_masking_views(int num_of_masking_views) {
        this.num_of_masking_views = num_of_masking_views;
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
        return "InitiatorType [initiatorId=" + initiatorId + ", symmetrixPortKey=" + symmetrixPortKey + ", alias=" + alias + ", type="
                + type + ", fcid=" + fcid + ", fcid_value=" + fcid_value + ", fcid_lockdown=" + fcid_lockdown + ", ip_address="
                + ip_address + ", logged_in=" + logged_in + ", on_fabric=" + on_fabric + ", port_flags_override=" + port_flags_override
                + ", enabled_flags=" + enabled_flags + ", disabled_flags=" + disabled_flags + ", flags_in_effect=" + flags_in_effect
                + ", num_of_vols=" + num_of_vols + ", host=" + host + ", num_of_host_groups=" + num_of_host_groups + ", hostGroup="
                + hostGroup + ", num_of_masking_views=" + num_of_masking_views + ", maskingview=" + maskingview + "]";
    }

}
