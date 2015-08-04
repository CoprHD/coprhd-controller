/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.user;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "user")
public class UserInfo {
    private String commonName;
    private String distinguishedName;
    private String tenant;
    private String tenantName;
    private List<String> vdcRoles;
    private List<String> homeTenantRoles;
    private List<SubTenantRoles> subTenantRoles;

    @XmlElement(name = "common_name")
    @JsonProperty("common_name")
    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    @XmlElement(name = "distinguished_name")
    @JsonProperty("distinguished_name")
    public String getDistinguishedName() {
        return distinguishedName;
    }

    public void setDistinguishedName(String distinguishedName) {
        this.distinguishedName = distinguishedName;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    @XmlElementWrapper(name = "vdc_roles")
    /**
     * Virtual data center roles that the user has.
     * @valid SYSTEM_ADMIN
     * @valid SECURITY_ADMIN
     * @valid SYSTEM_MONITOR
     * @valid SYSTEM_AUDITOR
     */
    @XmlElement(name = "vdc_role")
    public List<String> getVdcRoles() {
        if (vdcRoles == null) {
            vdcRoles = new ArrayList<String>();
        }
        return vdcRoles;
    }

    public void setVdcRoles(List<String> roles) {
        this.vdcRoles = roles;
    }

    @XmlElementWrapper(name = "home_tenant_roles")
    /**
     * Provider tenant roles that the user has.
     * @valid TENANT_ADMIN
     * @valid PROJECT_ADMIN
     * @valid TENANT_APPROVER
     */
    @XmlElement(name = "home_tenant_role")
    public List<String> getHomeTenantRoles() {
        if (homeTenantRoles == null) {
            homeTenantRoles = new ArrayList<String>();
        }
        return homeTenantRoles;
    }

    public void setHomeTenantRoles(List<String> roles) {
        this.homeTenantRoles = roles;
    }

    @XmlElementWrapper(name = "subtenant_roles")
    /**
     * Subtenant(s) that this user has roles in.
     * @valid none
     */
    @XmlElement(name = "subtenant")
    public List<SubTenantRoles> getSubTenantRoles() {
        if (subTenantRoles == null) {
            subTenantRoles = new ArrayList<SubTenantRoles>();
        }
        return subTenantRoles;
    }

    public void setSubTenantRoles(List<SubTenantRoles> subTenantRoles) {
        this.subTenantRoles = subTenantRoles;
    }
}
