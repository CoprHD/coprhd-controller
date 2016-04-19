/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.tenant;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "tenant")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class TenantOrgRestRep extends DataObjectRestRep {

    private RelatedResourceRep parentTenant;
    private String description;
    private List<UserMappingParam> userMappings;
    private String namespace;
    private URI namespaceStorage;

    public TenantOrgRestRep() {
    }

    public TenantOrgRestRep(RelatedResourceRep parentTenant,
            String description, List<UserMappingParam> userMappings) {
        this.parentTenant = parentTenant;
        this.description = description;
        this.userMappings = userMappings;
    }

    /**
     * 
     * Optional Detailed Description of the Tenant
     * 
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     * Id and hyperlink of the parent tenant
     * 
     */
    @XmlElement(name = "parent_tenant")
    public RelatedResourceRep getParentTenant() {
        return parentTenant;
    }

    public void setParentTenant(RelatedResourceRep parentTenant) {
        this.parentTenant = parentTenant;
    }

    /**
     * 
     * User mappings define how a user is mapped to a tenant. The user can be
     * mapped to a tenant using parameters such as domains, LDAP attributes,
     * and AD group membership.
     * 
     */
    @XmlElementWrapper(name = "user_mappings")
    /**
     * One individual mapping
     * @return 
     */
    @XmlElement(name = "user_mapping")
    public List<UserMappingParam> getUserMappings() {
        if (userMappings == null) {
            userMappings = new ArrayList<UserMappingParam>();
        }
        return userMappings;
    }

    public void setUserMappings(List<UserMappingParam> userMappings) {
        this.userMappings = userMappings;
    }

    /**
     * 
     * Namespace mapped to the Tenant
     * 
     */
    @XmlElement(name = "namespace")
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * 
     * Namespace associated with storage mapped to the Tenant
     * 
     */
    @XmlElement(name = "namespace_storage_system")
    public URI getNamespaceStorage() {
        return namespaceStorage;
    }

    public void setNamespaceStorage(URI namespaceStorage) {
        this.namespaceStorage = namespaceStorage;
    }

}
