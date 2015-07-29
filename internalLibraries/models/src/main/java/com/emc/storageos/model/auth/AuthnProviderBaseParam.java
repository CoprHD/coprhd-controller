/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import com.emc.storageos.model.valid.Length;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;

/**
 * Authentication provider configuration base object
 */
public abstract class AuthnProviderBaseParam {

    /**
     * The kind of provider. Active Directory(ad) or generic LDAPv3 (ldap)
     * 
     * @valid ad
     * @valid ldap
     */
    private String mode;

    /**
     * Name of the provider
     * 
     * @valid any string.
     * @valid provider names must be unique within a virtual data center
     */
    private String label;

    /**
     * Description of the provider
     * 
     * @valid any string
     */
    private String description;

    /**
     * Specifies if a provider is disabled or enabled.
     * During provider creation or update, if disable is set to false,
     * a basic connectivity test will be performed against the LDAP/AD server.
     * If the disable parameter is set to true, no validation will be done and
     * the provider will be added/updated as long as the parameters are
     * syntactically correct.
     * During the operation of the system, a disabled provider will exist but
     * not be considered when authenticating principals.
     * 
     * @valid true to disable
     * @valid false to enable
     */
    private Boolean disable;

    /**
     * Distinguished Name for the bind user.
     * 
     * @valid Example: CN=Administrator,CN=Users,DC=domain,DC=com
     * @valid Example: domain\Administrator
     */
    private String managerDn;

    /**
     * Password for the manager DN "bind" user.
     * 
     * @valid none
     */
    private String managerPassword;

    /**
     * Search base from which the LDAP search will start when authenticating
     * users. See also: search_scope
     * 
     * @valid Example: CN=Users,DC=domain,DC=com
     */
    private String searchBase;

    /**
     * Key value pair representing the search filter criteria.
     * 
     * @valid %u or %U needs to be present on the right side of the equal sign (Example: filterKey=%u).
     * @valid %u stands for the whole username string as typed in by the user.
     * @valid %U stands for the username portion only of the string containing the domain
     * @valid Example: in user@company.com, %U is user. %u is user@company.com
     */
    private String searchFilter;

    /**
     * In conjunction with the search_base, the search_scope indicates how many
     * levels below the base the search can continue.
     * 
     * @valid ONELEVEL = The search will start at the search_base location and continue up to one level deep
     * @valid SUBTREE = The search will start at the search_base location and continue through the entire tree
     */
    private String searchScope;

    /**
     * Attribute for group search. This is the attribute name that will be used to represent group membership.
     * Once set during creation of the provider, the value for this parameter cannot be changed.
     * 
     * @valid Example: "CN"
     */
    private String groupAttribute;

    /**
     * Maximum number of results that the LDAP server will return on a single page.
     * 
     * @valid If provided, the value must be greater than 0
     * @valid The value cannot be higher than the max page size configured on the LDAP server.
     */
    private Integer maxPageSize;

    /**
     * Whether or not to validate certificates when ldaps is used.
     * 
     * @valid true
     * @valid false
     */
    private Boolean validateCertificates;

    public AuthnProviderBaseParam() {
    }

    public AuthnProviderBaseParam(String mode, String label,
            String description, Boolean disable, String serverCert,
            String managerDn, String managerPassword, String searchBase,
            String searchFilter, String searchScope, String searchAttributeKey,
            String groupAttribute, Integer maxPageSize,
            Boolean validateCertificates) {
        this.mode = mode;
        this.label = label;
        this.description = description;
        this.disable = disable;
        this.managerDn = managerDn;
        this.managerPassword = managerPassword;
        this.searchBase = searchBase;
        this.searchFilter = searchFilter;
        this.searchScope = searchScope;
        this.groupAttribute = groupAttribute;
        this.maxPageSize = maxPageSize;
        this.validateCertificates = validateCertificates;
    }

    @XmlElement
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    @Length(min = 2, max = 128)
    @XmlElement(required = false, name = "name")
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @XmlElement(required = false, name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "disable", required = false, defaultValue = "false")
    public Boolean getDisable() {
        return disable;
    }

    public void setDisable(Boolean disable) {
        this.disable = disable;
    }

    @XmlElement(name = "manager_dn")
    @JsonProperty("manager_dn")
    public String getManagerDn() {
        return managerDn;
    }

    public void setManagerDn(String managerDn) {
        this.managerDn = managerDn;
    }

    @XmlElement(name = "manager_password")
    @JsonProperty("manager_password")
    public String getManagerPassword() {
        return managerPassword;
    }

    public void setManagerPassword(String managerPassword) {
        this.managerPassword = managerPassword;
    }

    @XmlElement(name = "search_base")
    @JsonProperty("search_base")
    public String getSearchBase() {
        return searchBase;
    }

    public void setSearchBase(String searchBase) {
        this.searchBase = searchBase;
    }

    @XmlElement(name = "search_filter")
    @JsonProperty("search_filter")
    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter;
    }

    @XmlElement(name = "search_scope", defaultValue = "ONELEVEL")
    @JsonProperty("search_scope")
    public String getSearchScope() {
        return searchScope;
    }

    public void setSearchScope(String searchScope) {
        this.searchScope = searchScope;
    }

    @XmlElement(name = "group_attribute")
    @JsonProperty("group_attribute")
    public String getGroupAttribute() {
        return groupAttribute;
    }

    public void setGroupAttribute(String groupAttribute) {
        this.groupAttribute = groupAttribute;
    }

    @XmlElement(name = "max_page_size", defaultValue = "1000")
    @JsonProperty("max_page_size")
    public Integer getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(Integer maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    @Deprecated
    @XmlElement(name = "validate_certificates", required = false, defaultValue = "false")
    @JsonProperty("validate_certificates")
    public Boolean getValidateCertificates() {
        return validateCertificates;
    }

    @Deprecated
    public void setValidateCertificates(Boolean validateCertificates) {
        this.validateCertificates = validateCertificates;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AuthBaseParam [mode=");
        sb.append(mode);
        sb.append(", description=");
        sb.append(description);
        sb.append(", disable=");
        sb.append(disable);
        sb.append(", manager_dn=");
        sb.append(managerDn);
        sb.append(", manager_password=");
        sb.append("***PASSWORD MASKED***");
        sb.append(", search_base=");
        sb.append(searchBase);
        sb.append(", search_filter=");
        sb.append(searchFilter);
        sb.append(", search_scope=");
        sb.append(searchScope);
        sb.append(", group_attribute=");
        sb.append(groupAttribute);
        sb.append(", max_page_size=");
        sb.append(maxPageSize);

        sb.append("]");
        return sb.toString();
    }
}
