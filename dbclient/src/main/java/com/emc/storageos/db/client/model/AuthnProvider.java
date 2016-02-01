/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
/**
 * Authentication provider configuration data object
 */
@Cf("AuthnProvider")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
public class AuthnProvider extends DataObject {
    private static final String EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT = "2.3";

    private ProvidersType _mode;
    private String _description;
    private Boolean _disable;
    private Boolean _autoRegisterOpenStackProjects;
    private StringSet _serverUrls;
    private StringSet _domains;
    private String _serverCert;
    private String _managerDN;
    private String _managerPassword;
    private String _searchBase;
    private String _searchFilter;
    private String _searchAttributeKey;
    private StringSet _groupWhitelistValues;
    private String _groupAttribute;
    private Integer _maxPageSize;
    private long _lastModified;
    private String _searchScope;
    private Boolean _validateCertificates;
    private StringMap keys;
    private StringSet _groupObjectClassNames;
    private StringSet _groupMemberAttributeTypeNames;

    // names to be used in the 'mode' element of the Provider
    public static enum ProvidersType {
        ldap, ad, keystone
    }

    // values to be used for the searchScope element
    public static enum SearchScope {
        ONELEVEL, SUBTREE
    }

    @Name("mode")
    public String getMode() {
        if (null != _mode) {
            return _mode.toString();
        } else {
            return null;
        }
    }

    public void setMode(String mode) {
        try {
            _mode = ProvidersType.valueOf(mode);
            setChanged("mode");
        } catch (IllegalArgumentException ex) {
            IllegalArgumentException newex = new IllegalArgumentException("The provided value for <mode> is not correct", ex);
            throw newex;
        }
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
        setChanged("description");
    }

    @Name("disable")
    public Boolean getDisable() {
        if (null != _disable) {
            return _disable;
        } else {
            return false;
        }
    }

    public void setDisable(Boolean disable) {
        _disable = disable;
        setChanged("disable");
    }

    @Name("autoRegisterOpenStackProjects")
    public Boolean getAutoRegisterOpenStackProjects() {
        if (null != _autoRegisterOpenStackProjects) {
            return _autoRegisterOpenStackProjects;
        } else {
            return false;
        }
    }

    public void setAutoRegisterOpenStackProjects(Boolean autoRegisterOpenStackProjects) {
        _autoRegisterOpenStackProjects = autoRegisterOpenStackProjects;
        setChanged("enableOpenstackAuth");
    }

    @Name("serverUrls")
    public StringSet getServerUrls() {
        return _serverUrls;
    }

    public void setServerUrls(
            StringSet server_urls) {
        _serverUrls = server_urls;
        setChanged("serverUrls");
    }

    @Name("domains")
    @AlternateId("AltIdIndex")
    public StringSet getDomains() {
        return _domains;
    }

    public void setDomains(StringSet domains) {
        _domains = domains;
        setChanged("domains");
    }

    @Deprecated
    @Name("serverCert")
    public String getServerCert() {
        return _serverCert;
    }

    @Deprecated
    public void setServerCert(String urls) {
        _serverCert = urls;
        setChanged("serverCert");
    }

    @Name("managerDN")
    public String getManagerDN() {
        return _managerDN;
    }

    public void setManagerDN(String manager_dn) {
        _managerDN = manager_dn;
        setChanged("managerDN");
    }

    @Encrypt
    @Name("managerPassword")
    public String getManagerPassword() {
        return _managerPassword;
    }

    public void setManagerPassword(
            String manager_password) {
        _managerPassword = manager_password;
        setChanged("managerPassword");
    }

    @Name("searchBase")
    public String getSearchBase() {
        return _searchBase;
    }

    public void setSearchBase(
            String search_base) {
        _searchBase = search_base;
        setChanged("searchBase");
    }

    @Name("searchFilter")
    public String getSearchFilter() {
        return _searchFilter;
    }

    public void setSearchFilter(
            String search_filter) {
        _searchFilter = search_filter;
        setChanged("searchFilter");
    }

    @Name("searchScope")
    public String getSearchScope() {
        return _searchScope;
    }

    public void setSearchScope(
            String searchScope) {
        _searchScope = searchScope;
        setChanged("searchScope");
    }

    @Deprecated
    @Name("searchAttributeKey")
    public String getSearchAttributeKey() {
        return _searchAttributeKey;
    }

    @Deprecated
    public void setSearchAttributeKey(
            String search_attribute_key) {
        _searchAttributeKey = search_attribute_key;
        setChanged("searchAttributeKey");
    }

    @Name("groupWhitelistValues")
    public StringSet getGroupWhitelistValues() {
        return _groupWhitelistValues;
    }

    public void setGroupWhitelistValues(StringSet group_whitelist_values) {
        _groupWhitelistValues = group_whitelist_values;
        setChanged("groupWhitelistValues");
    }

    @Name("groupAttribute")
    public String getGroupAttribute() {
        return _groupAttribute;
    }

    public void setGroupAttribute(String group_attribute) {
        _groupAttribute = group_attribute;
        setChanged("groupAttribute");
    }

    @Name("maxPageSize")
    public Integer getMaxPageSize() {
        return _maxPageSize;
    }

    public void setMaxPageSize(Integer maxPageSize) {
        _maxPageSize = maxPageSize;
        setChanged("maxPageSize");
    }

    @Name("lastModified")
    public Long getLastModified() {
        return _lastModified;
    }

    public void setLastModified(Long lastModified) {
        _lastModified = lastModified;
        setChanged("lastModified");
    }
    @Name("keys")
    public StringMap getKeys() {
        return keys;
    }
    public String getKeyValue(String key) {
        String value = null;
        if (keys != null ) {
            value = keys.get(key);
        }
        return (value == null) ? NullColumnValueGetter.getNullStr() : value;
    }
    public void setKeys(StringMap keys) {
        this.keys = keys;
        setChanged("keys");
    }
    public void addKey(String key, String value) {
        if (getKeys() == null) {
            setKeys(new StringMap());
        }
        getKeys().put(key, value);
        setChanged("keys");
    }
    public void removeKey(String key) {
        if (keys != null) {
            getKeys().remove(key);
            setChanged("keys");
        }
    }
    public void removeKeys(String[] keyArray) {
        if (keys != null) {
        	for(String key : keyArray)
        	{
        		getKeys().remove(key);
        	}
        	setChanged("keys");
        }
    }

    @AllowedGeoVersion(version = EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT)
    @Name("groupObjectClassNames")
    public StringSet getGroupObjectClassNames() {
        if (_groupObjectClassNames == null) {
            _groupObjectClassNames = new StringSet();
        }
        return _groupObjectClassNames;
    }

    public void setGroupObjectClassNames(StringSet groupObjectClassNames) {
        this._groupObjectClassNames = groupObjectClassNames;
        setChanged("groupObjectClassNames");
    }

    @AllowedGeoVersion(version = EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT)
    @Name("groupMemberAttributeTypeNames")
    public StringSet getGroupMemberAttributeTypeNames() {
        if (_groupMemberAttributeTypeNames == null) {
            _groupMemberAttributeTypeNames = new StringSet();
        }
        return _groupMemberAttributeTypeNames;
    }

    public void setGroupMemberAttributeTypeNames(StringSet groupMemberAttributeTypeNames) {
        this._groupMemberAttributeTypeNames = groupMemberAttributeTypeNames;
        setChanged("groupMemberAttributeTypeNames");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AuthnConfiguration [mode=");
        sb.append(_mode);
        sb.append(", description=");
        sb.append(_description);
        sb.append(", disable=");
        sb.append(_disable);
        sb.append(", serverUrls=");
        if (null != _serverUrls) {
            for (String s : _serverUrls) {
                sb.append(s).append(",");
            }
        }
        sb.append(", domains=");
        if (null != _domains) {
            for (String s : _domains) {
                sb.append(s).append(",");
            }
        }
        sb.append(", managerDN=");
        sb.append(_managerDN);
        sb.append(", managerPassword=");
        sb.append("***PASSWORD MASKED***");
        sb.append(", searchBase=");
        sb.append(_searchBase);
        sb.append(", searchFilter=");
        sb.append(_searchFilter);
        sb.append(", searchScope=");
        sb.append(_searchScope);
        sb.append(", groupWhitelistValues=");

        if (null != _groupWhitelistValues) {
            for (String s : _groupWhitelistValues) {
                sb.append(s).append(",");
            }
        }

        sb.append(", groupAttribute=");
        sb.append(_groupAttribute);

        sb.append(", maxPageSize=");
        sb.append(_maxPageSize);

        sb.append(", objectClassNames=");
        sb.append(_groupObjectClassNames);

        sb.append(", groupMemberAttributeTypeNames=");
        sb.append(_groupMemberAttributeTypeNames);

        sb.append("]");

        return sb.toString();
    }

    @Deprecated
    @Name("validateCertificates")
    public Boolean getValidateCertificates() {
        return _validateCertificates;
    }

    @Deprecated
    public void setValidateCertificates(Boolean validateCertificates) {
        _validateCertificates = validateCertificates;
        setChanged("validateCertificates");

    }

    /**
     * Returns the minimum expected version for this API to the
     * consumers of the apisvc (portal).
     * 
     * @return minimum expected geo version for this api.
     */
    public static String getExpectedGeoVDCVersionForLDAPGroupSupport() {
        return EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT;
    }
}
