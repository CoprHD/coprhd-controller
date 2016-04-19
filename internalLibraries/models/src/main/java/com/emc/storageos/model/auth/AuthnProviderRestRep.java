/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import com.emc.storageos.model.DataObjectRestRep;

import javax.xml.bind.annotation.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Class that encapsulates the REST representation of a Authentication
 * provider profile. It also allows conversion from a AuthnConfiguration
 * data model object.
 */

@XmlRootElement(name = "authnprovider")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class AuthnProviderRestRep extends DataObjectRestRep {
    private String mode;
    private Set<String> domains;
    private String searchFilter;
    private String searchScope;
    private String searchBase;
    private String managerDN;
    private String managerPassword;
    private String groupAttribute;
    private Set<String> serverUrls;
    private Set<String> groupWhitelistValues;
    private Boolean disable;
    private Boolean autoRegCoprHDNImportOSProjects;
    private String description;
    private Integer maxPageSize;
    private Set<String> groupObjectClasses;
    private Set<String> groupMemberAttributes;

    /**
     * Description of the provider
     *
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Specifies if a provider is disabled or enabled.
     * During the operation of the system, a disabled provider will exist but
     * not be considered when authenticating principals.
     *
     */
    @XmlElement(name = "disable")
    public Boolean getDisable() {
        return disable;
    }

    public void setDisable(Boolean disable) {
        this.disable = disable;
    }

    /**
     * Specifies if OpenStack Projects will be auto-registered as Tenants in CoprHD.
     *
     */
    @XmlElement(name = "autoreg_coprhd_import_osprojects")
    public Boolean getAutoRegCoprHDNImportOSProjects() {
        return autoRegCoprHDNImportOSProjects;
    }

    public void setAutoRegCoprHDNImportOSProjects(Boolean autoRegCoprHDNImportOSProjects) {
        this.autoRegCoprHDNImportOSProjects = autoRegCoprHDNImportOSProjects;
    }

    @XmlElementWrapper(name = "domains")
    /**
     * Active Directory domain names associated with this
     * provider.  If the server_url points to an Active Directory forest
     * global catalog server, each such element may be one of the many domains from the forest.
     * For non Active Directory servers, domain represents a logical
     * abstraction for this server which may not correspond to a network name.
     */
    @XmlElement(name = "domain")
    public Set<String> getDomains() {
        if (domains == null) {
            domains = new LinkedHashSet<String>();
        }
        return domains;
    }

    public void setDomains(Set<String> domains) {
        this.domains = domains;
    }

    /**
     * Attribute for group search. This is the attribute name that will be used to represent group membership.
     *
     */
    @XmlElement(name = "group_attribute")
    public String getGroupAttribute() {
        return groupAttribute;
    }

    public void setGroupAttribute(String groupAttribute) {
        this.groupAttribute = groupAttribute;
    }

    @XmlElementWrapper(name = "group_whitelist_values")
    /**
     * Names of the groups to be included when querying Active Directory
     * for group membership information about a user or group.  If the White List
     * is set to a value, the provider will only receive group membership information
     * about the groups matched by the value.  If the White List is empty, all group
     * membership information will be retrieved.  (blank == "*").
     * Valid values:
     *  value can describe regular expressions
     *  when empty, all groups are included implicitly
     */
    @XmlElement(name = "group_whitelist_value")
    public Set<String> getGroupWhitelistValues() {
        if (groupWhitelistValues == null) {
            groupWhitelistValues = new LinkedHashSet<String>();
        }
        return groupWhitelistValues;
    }

    public void setGroupWhitelistValues(Set<String> groupWhitelistValues) {
        this.groupWhitelistValues = groupWhitelistValues;
    }

    /**
     * Distinguished Name for the bind user.
     *
     */
    @XmlElement(name = "manager_dn")
    public String getManagerDN() {
        return managerDN;
    }

    public void setManagerDN(String managerDN) {
        this.managerDN = managerDN;
    }

    // // CQ 605181 - for security reasons, do not return password.
    // The following has been commented out to indicate
    // to indicate that this was NOT an oversight
    //
    // @XmlElement(name = "manager_password")
    // public String getManagerPassword() {
    // return "";
    // }

    public void setManagerPassword(String managerPassword) {
        this.managerPassword = managerPassword;
    }

    /**
     * Maximum number of results that the LDAP server will return on a single page.
     * Valid value:
     *  Value must be greater than 0 and cannot be higher than the max page size configured on LDAP server.
     */
    @XmlElement(name = "max_page_size")
    public Integer getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(Integer maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /*
     * The kind of provider. Active Directory(ad) or generic LDAPv3 (ldap)
     * Valid values:
     *  ad
     *  ldap
     * 
     */
    @XmlElement(name = "mode")
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Search base from which the LDAP search will start when authenticating
     * users. See also: search_scope
     *
     */
    @XmlElement(name = "search_base")
    public String getSearchBase() {
        return searchBase;
    }

    public void setSearchBase(String searchBase) {
        this.searchBase = searchBase;
    }

    /**
     * Key value pair representing the search filter criteria.
     *
     *        user. %u is user@company.com)
     */
    @XmlElement(name = "search_filter")
    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter;
    }

    /**
     * In conjunction with the search_base, the search_scope indicates how many
     * levels below the base the search can continue.
     * Valid values:
     *  ONELEVEL = The search will start at the search_base location and continue up to one level deep
     *  SUBTREE = The search will start at the search_base location and continue through the entire tree
     *
     */
    @XmlElement(name = "search_scope")
    public String getSearchScope() {
        return searchScope;
    }

    public void setSearchScope(String searchScope) {
        this.searchScope = searchScope;
    }

    @XmlElementWrapper(name = "server_urls")
    /**
     * Valid ldap or ldaps url strings.
     */
    @XmlElement(name = "server_url")
    public Set<String> getServerUrls() {
        if (serverUrls == null) {
            serverUrls = new LinkedHashSet<String>();
        }
        return serverUrls;
    }

    public void setServerUrls(Set<String> serverUrls) {
        this.serverUrls = serverUrls;
    }

    /**
     * Attribute for group's objectClass search. This is the attribute name that will be used to represent group's name.
     *
     */
    @XmlElementWrapper(name = "group_object_classes")
    @XmlElement(name = "group_object_class")
    public Set<String> getGroupObjectClasses() {
        if (groupObjectClasses == null) {
            groupObjectClasses = new LinkedHashSet<String>();
        }
        return groupObjectClasses;
    }

    public void setGroupObjectClasses(Set<String> groupObjectClasses) {
        this.groupObjectClasses = groupObjectClasses;
    }

    /**
     * Attribute for group's member search. This is the attribute name that will be used to represent group members.
     *
     */
    @XmlElementWrapper(name = "group_member_attributes")
    @XmlElement(name = "group_member_attribute")
    public Set<String> getGroupMemberAttributes() {
        if (groupMemberAttributes == null) {
            groupMemberAttributes = new LinkedHashSet<String>();
        }
        return groupMemberAttributes;
    }

    public void setGroupMemberAttributes(Set<String> groupMemberAttributes) {
        this.groupMemberAttributes = groupMemberAttributes;
    }
}
