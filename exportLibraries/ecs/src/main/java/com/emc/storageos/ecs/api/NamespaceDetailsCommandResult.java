/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.ArrayList;

public class NamespaceDetailsCommandResult {
    private String name;
    private String id;
    private ECSLink link;
    private String inactive;
    private String global;
    private String remote;
    private String vdc;
    private ArrayList<String> tags;
    private String default_data_services_vpool;
    private ArrayList<String> allowed_vpools_list;
    private ArrayList<String> disallowed_vpools_list;
    private String namespace_admins;
    private ArrayList<String> user_mapping;

    //get set methods
    String getName() {
        return name;
    }
    
    void setName(String name) {
        this.name = name;
    }
    
    String getId() {
        return id;
    }
    
    void setId(String id) {
        this.id = id;
    }
    
    ECSLink getLink() {
        return link;
    }

    void setLink(ECSLink link) {
        this.link = link;
    }
    
    String getInactive() {
        return inactive;
    }
    
    void setInactive(String inactive) {
        this.inactive = inactive;
    }

    String getGlobal() {
        return global;
    }
    
    void setGlobal(String global) {
        this.global = global;
    }
    
    String getRemote() {
        return remote;
    }
    
    void setRemote(String remote) {
        this.remote = remote;
    }
    
    String getVdc() {
        return vdc;
    }
    
    void setVdc(String vdc) {
        this.vdc = name;
    }
    
    ArrayList<String> getTags() {
        return tags;
    }
    
    void setTags(ArrayList<String> tags) {
        this.tags = tags;
    }
    
    String getDefault_data_services_vpool() {
        return default_data_services_vpool;
    }
    
    void setDefault_data_services_vpool(String default_data_services_vpool) {
        this.default_data_services_vpool = default_data_services_vpool;
    }
    
    ArrayList<String> getAllowed_vpools_list() {
        return allowed_vpools_list;
    }
    
    void setAllowed_vpools_list(ArrayList<String> allowed_vpools_list) {
        this.allowed_vpools_list = allowed_vpools_list;
    }

    ArrayList<String> getDisallowed_vpools_list() {
        return disallowed_vpools_list;
    }
    
    void setDisallowed_vpools_list(ArrayList<String> disallowed_vpools_list) {
        this.disallowed_vpools_list = disallowed_vpools_list;
    }
    
    String getNamespace_admins() {
        return namespace_admins;
    }
    
    void setNamespace_admins(String namespace_admins) {
        this.namespace_admins = namespace_admins;
    }
    
    ArrayList<String> getUser_mapping() {
        return user_mapping;
    }
    
    void setUser_mapping(ArrayList<String> user_mapping) {
        this.user_mapping = user_mapping;
    }
}