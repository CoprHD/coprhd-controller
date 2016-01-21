/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

/**
 * Tenant org (configuration) data object
 */
@Cf("TenantOrg")
@DbKeyspace(Keyspaces.GLOBAL)
public class TenantOrg extends DataObject {
    // The default provider tenant URN.
    public static final String PROVIDER_TENANT_ORG = URIUtil.createInternalID(TenantOrg.class, "provider").toString();

    // Constant for root's parent - no parent
    public static final String NO_PARENT = URIUtil.createInternalID(TenantOrg.class, "NONE").toString();

    public static final URI SYSTEM_TENANT = URIUtil.createInternalID(TenantOrg.class, "system");

    private NamedURI _parentTenant;
    private String _description;
    private StringSetMap _userMappings;
    private StringSetMap _roleAssignments;

    // current Quota for the tenant in GB
    private Long _quotaGB;

    private Boolean _quotaEnabled;

    // _namespace the tenant is one to one mapped.
    private String _namespace;
    
    // object storage systm to which the namespace belongs
    private URI _namespaceStorage;

    @NamedRelationIndex(cf = "NamedRelation", type = TenantOrg.class)
    @Name("parentTenant")
    public NamedURI getParentTenant() {
        return _parentTenant;
    }

    public void setParentTenant(NamedURI parentTenant) {
        _parentTenant = parentTenant;
        setChanged("parentTenant");
    }

    static public boolean isRootTenant(TenantOrg tenant) {
        return NO_PARENT.equalsIgnoreCase(tenant.getParentTenant().getURI().toString());
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
        setChanged("description");
    }

    @Name("userMappings")
    @PermissionsIndex("PermissionsIndex")
    public StringSetMap getUserMappings() {
        return _userMappings;
    }

    public void setUserMappings(StringSetMap userMappings) {
        _userMappings = userMappings;
        setChanged("userMappings");
    }

    public void addUserMapping(String domain, String userMapping) {
        if (null == _userMappings) {
            _userMappings = new StringSetMap();
        }
        _userMappings.put(domain, userMapping);
    }

    public void removeUserMapping(String domain, String userMapping) {
        if (null != _userMappings) {
            _userMappings.remove(domain, userMapping);
        }
    }

    @PermissionsIndex("PermissionsIndex")
    @Name("role-assignment")
    public StringSetMap getRoleAssignments() {
        if (_roleAssignments == null) {
            _roleAssignments = new StringSetMap();
        }
        return _roleAssignments;
    }

    public void setRoleAssignments(StringSetMap roleAssignments) {
        _roleAssignments = roleAssignments;
    }

    @Name("quota")
    public Long getQuota() {
        return (_quotaGB == null) ? 0L : _quotaGB;
    }

    public void setQuota(Long quota) {
        _quotaGB = quota;
        setChanged("quota");
    }

    @Name("quotaEnabled")
    public Boolean getQuotaEnabled() {
        return (_quotaEnabled == null) ? false : _quotaEnabled;
    }

    public void setQuotaEnabled(Boolean enable) {
        _quotaEnabled = enable;
        setChanged("quotaEnabled");
    }

    public Set<String> getRoleSet(String key) {
        if (null != _roleAssignments) {
            return _roleAssignments.get(key);
        }
        return null;
    }

    public void addRole(String key, String role) {
        if (_roleAssignments == null) {
            _roleAssignments = new StringSetMap();
        }
        _roleAssignments.put(key, role);
    }

    public void removeRole(String key, String role) {
        if (_roleAssignments != null) {
            _roleAssignments.remove(key, role);
        }
    }

    @Name("namespace")
    public String getNamespace() {
        return _namespace;
    }

    public void setNamespace(String namespace) {
        _namespace = namespace;
        setChanged("namespace");
    }
    
    @Name("namespaceStorage")
    public URI getNamespaceStorage() {
        return _namespaceStorage;
    }

    public void setNamespaceStorage(URI namespaceStorage) {
        _namespaceStorage = namespaceStorage;
        setChanged("namespaceStorage");
    }

}
