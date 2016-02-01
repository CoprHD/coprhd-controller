/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a list of principals to be validated in a batch validation request.
 */
@XmlRootElement(name = "principals_to_validate")
public class PrincipalsToValidate {

    private String tenantId;
    private String altTenantId;
    private List<String> users;
    private List<String> altTenantUsers;
    private List<String> groups;

    /**
     * @return the tenantId
     */
    @XmlElement(name = "tenant_id")
    public String getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantId
     *            the tenantId to set
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * @return the altTenantId
     */
    @XmlElement(name = "alt_tenant_id")
    public String getAltTenantId() {
        return altTenantId;
    }

    /**
     * @param altTenantId
     *            the altTenantId to set
     */
    public void setAltTenantId(String altTenantId) {
        this.altTenantId = altTenantId;
    }

    /**
     * @return the users
     */
    @XmlElementWrapper(name = "users")
    @XmlElement(name = "username")
    public List<String> getUsers() {
        return users;
    }

    /**
     * @param users
     *            the users to set
     */
    public void setUsers(List<String> users) {
        this.users = users;
    }

    /**
     * Gets the list of users to be validated against the tenant id, or the alt tenant id
     * 
     * @return the altTenantUsers
     */
    @XmlElementWrapper(name = "alt_tenant_users")
    @XmlElement(name = "username")
    public List<String> getAltTenantUsers() {
        return altTenantUsers;
    }

    /**
     * Users in this groups will be validated against the tenant id, or the alt tenant id
     * 
     * @param altTenantUsers
     *            the altTenantUsers to set
     */
    public void setAltTenantUsers(List<String> altTenantUsers) {
        this.altTenantUsers = altTenantUsers;
    }

    /**
     * @return the groups
     */
    @XmlElementWrapper(name = "groups")
    @XmlElement(name = "group")
    public List<String> getGroups() {
        return groups;
    }

    /**
     * @param groups
     *            the groups to set
     */
    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

}
