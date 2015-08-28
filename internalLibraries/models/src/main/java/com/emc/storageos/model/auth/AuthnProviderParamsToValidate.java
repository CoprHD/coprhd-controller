/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Container class to store parameters to validate an authnprovider.
 * This is a subset of what can be found in AuthnBaseParam, CreateParam, UpdateParam
 */
@XmlRootElement(name = "authnprovider_validate")
public class AuthnProviderParamsToValidate {

    private String managerDN;
    private String managerPwd;
    private List<String> urls;
    private String searchBase;
    private String mode;
    private String groupAttr;
    private List<String> groupObjectClasses;
    private List<String> groupMemberAttributes;

    public AuthnProviderParamsToValidate() {
    }

    public AuthnProviderParamsToValidate(String managerDN, String managerPwd,
            List<String> urls, String searchBase) {
        this.managerDN = managerDN;
        this.managerPwd = managerPwd;
        this.urls = urls;
        this.searchBase = searchBase;
    }

    public AuthnProviderParamsToValidate(String managerDN, String managerPwd,
            String searchBase) {
        this.managerDN = managerDN;
        this.managerPwd = managerPwd;
        this.searchBase = searchBase;
    }

    @XmlElement(name = "manager_dn_validate")
    public String getManagerDN() {
        return managerDN;
    }

    public void setManagerDN(String managerDN) {
        this.managerDN = managerDN;
    }

    @XmlElement(name = "manager_pwd_validate")
    public String getManagerPwd() {
        return managerPwd;
    }

    public void setManagerPwd(String managerPwd) {
        this.managerPwd = managerPwd;
    }

    @XmlElementWrapper(name = "server_urls_validate")
    public List<String> getUrls() {
        if (urls == null) {
            urls = new ArrayList<String>();
        }
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    @XmlElement(name = "search_base_validate")
    public String getSearchBase() {
        return searchBase;
    }

    public void setSearchBase(String searchBase) {
        this.searchBase = searchBase;
    }

    @XmlElement(name = "mode_validate")
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    @XmlElement(name = "group_attr_validate")
    public String getGroupAttr() {
        return groupAttr;
    }

    public void setGroupAttr(String groupAttr) {
        this.groupAttr = groupAttr;
    }

    @XmlElementWrapper(name = "group_object_classes_validate")
    @XmlElement(name = "group_object_class_validate")
    public List<String> getGroupObjectClasses() {
        if (groupObjectClasses == null) {
            groupObjectClasses = new ArrayList<String>();
        }
        return groupObjectClasses;
    }

    public void setGroupObjectClasses(List<String> groupObjectClasses) {
        this.groupObjectClasses = groupObjectClasses;
    }

    @XmlElementWrapper(name = "group_member_attributes_validate")
    @XmlElement(name = "group_member_attribute_validate")
    public List<String> getGroupMemberAttributes() {
        if (groupMemberAttributes == null) {
            groupMemberAttributes = new ArrayList<String>();
        }
        return groupMemberAttributes;
    }

    public void setGroupMemberAttributes(List<String> groupMemberAttributes) {
        this.groupMemberAttributes = groupMemberAttributes;
    }
}
