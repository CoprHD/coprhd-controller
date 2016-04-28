/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class UserMappingParam {
    private String domain;
    private List<UserMappingAttributeParam> attributes;
    private List<String> groups;

    public UserMappingParam() {
    }

    public UserMappingParam(String domain,
            List<UserMappingAttributeParam> attributes, List<String> groups) {
        this.domain = domain;
        this.attributes = this.removeDuplicate(attributes);
        ;
        this.groups = this.removeDuplicate(groups);
    }

    /**
     * 
     * A single-valued attribute indicating the user's IDP domain
     * 
     */
    @XmlElement(required = true, name = "domain")
    @JsonProperty("domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @XmlElementWrapper(name = "attributes")
    /**
     * The user's LDAP attributes that can be used to further scope tenancy as
     * a set of key-value pairs.
     * 
     */
    @XmlElement(name = "attribute")
    public List<UserMappingAttributeParam> getAttributes() {
        if (attributes == null) {
            attributes = new ArrayList<UserMappingAttributeParam>();
        }
        return attributes;
    }

    public void setAttributes(List<UserMappingAttributeParam> attributes) {
        this.attributes = removeDuplicate(attributes);
    }

    @XmlElementWrapper(name = "groups")
    /**
     * AD Users group memberships to be used for mapping to this tenant
     */
    @XmlElement(name = "group")
    public List<String> getGroups() {
        if (groups == null) {
            groups = new ArrayList<String>();
        }
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = removeDuplicate(groups);
    }

    /**
     * Removes the duplicate entries from the collection (List<T>)
     * and returns the list with unique entries.
     * 
     */
    private <T> List<T> removeDuplicate(List<T> listWithDuplicates) {
        List<T> uniqueList = new ArrayList<T>(new LinkedHashSet<T>(listWithDuplicates));
        return uniqueList;
    }
}
