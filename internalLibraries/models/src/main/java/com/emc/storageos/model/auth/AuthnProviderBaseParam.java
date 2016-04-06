/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
     * Valid values
     * ad
     * ldap
     */
    private String mode;

    /**
     * Name of the provider.
     * Valid value:
     *  provider names unique within a virtual data center
     *
     */
    private String label;

    /**
     * Description of the provider
     *
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
     */
    private Boolean disable;

    /**
     * Specifies if there is OpenStack registration.
     */
    private Boolean autoRegCoprHDNImportOSProjects;

    /**
     * Distinguished Name for the bind user.
     *
     */
    private String managerDn;

    /**
     * Password for the manager DN "bind" user.
     *
     */
    private String managerPassword;

    /**
     * Search base from which the LDAP search will start when authenticating
     * users. See also: search_scope
     *
     */
    private String searchBase;

    /**
     * Key value pair representing the search filter criteria.
     * Valid value:
     *  %u whole username string
     *  %U username portion only of the string containing the domain
     *
     */
    private String searchFilter;

    /**
     * In conjunction with the search_base, the search_scope indicates how many
     * levels below the base the search can continue.
     * Valid values:
     * ONELEVEL
     * SUBTREE
     */
    private String searchScope;

    /**
     * Attribute for group search. This is the attribute name that will be used to represent group membership.
     * Once set during creation of the provider, the value for this parameter cannot be changed.
     *
     */
    private String groupAttribute;

    /**
     * Maximum number of results that the LDAP server will return on a single page.
     *
     */
    private Integer maxPageSize;

    /**
     * Whether or not to validate certificates when ldaps is used.
     *
     */
    private Boolean validateCertificates;

    public AuthnProviderBaseParam() {
    }

    public AuthnProviderBaseParam(String mode, String label,
            String description, Boolean disable, Boolean autoRegCoprHDNImportOSProjects, String serverCert,
            String managerDn, String managerPassword, String searchBase,
            String searchFilter, String searchScope, String searchAttributeKey,
            String groupAttribute, Integer maxPageSize,
            Boolean validateCertificates) {
        this.mode = mode;
        this.label = label;
        this.description = description;
        this.disable = disable;
        this.autoRegCoprHDNImportOSProjects = autoRegCoprHDNImportOSProjects;
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

    @XmlElement(name = "autoreg_coprhd_import_osprojects", required = false, defaultValue = "false")
    public Boolean getAutoRegCoprHDNImportOSProjects() {
        return autoRegCoprHDNImportOSProjects;
    }

    public void setAutoRegCoprHDNImportOSProjects(Boolean autoRegCoprHDNImportOSProjects) {
        this.autoRegCoprHDNImportOSProjects = autoRegCoprHDNImportOSProjects;
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

    @XmlElement(name = "search_base", required = false, nillable = true)
    @JsonProperty("search_base")
    public String getSearchBase() {
        return searchBase;
    }

    public void setSearchBase(String searchBase) {
        this.searchBase = searchBase;
    }

    @XmlElement(name = "search_filter", required = false, nillable = true)
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
        sb.append(", autoreg_coprhd_import_osprojects=");
        sb.append(autoRegCoprHDNImportOSProjects);
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
