/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.ArrayList;

public class NamespaceDetailsCommandResult {
    private String name;
    private String id;
    private String default_data_services_vpool;
    private ArrayList<String> allowed_vpools_list;
    private ArrayList<String> disallowed_vpools_list;
    private String is_encryption_enabled;
    private boolean is_stale_allowed;
    private boolean is_compliance_enabled;
    
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
    public String getDefault_data_services_vpool() {
        return default_data_services_vpool;
    }
    public void setDefault_data_services_vpool(String default_data_services_vpool) {
        this.default_data_services_vpool = default_data_services_vpool;
    }
    public ArrayList<String> getAllowed_vpools_list() {
        return allowed_vpools_list;
    }
    public void setAllowed_vpools_list(ArrayList<String> allowed_vpools_list) {
        this.allowed_vpools_list = allowed_vpools_list;
    }
    public ArrayList<String> getDisallowed_vpools_list() {
        return disallowed_vpools_list;
    }
    public void setDisallowed_vpools_list(ArrayList<String> disallowed_vpools_list) {
        this.disallowed_vpools_list = disallowed_vpools_list;
    }
    public String getIs_encryption_enabled() {
        return is_encryption_enabled;
    }
    public void setIs_encryption_enabled(String is_encryption_enabled) {
        this.is_encryption_enabled = is_encryption_enabled;
    }
    public boolean isIs_stale_allowed() {
        return is_stale_allowed;
    }
    public void setIs_stale_allowed(boolean is_stale_allowed) {
        this.is_stale_allowed = is_stale_allowed;
    }
    public boolean isIs_compliance_enabled() {
        return is_compliance_enabled;
    }
    public void setIs_compliance_enabled(boolean is_compliance_enabled) {
        this.is_compliance_enabled = is_compliance_enabled;
    }   
}