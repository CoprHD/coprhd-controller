/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

/**
 * Project data object
 */
@Cf("Project")
@DbKeyspace(Keyspaces.GLOBAL)
public class Project extends DataObjectWithACLs {

    private static final String EXPECTED_GEO_VERSION_FOR_ASSIGN_VNAS_SUPPORT = "2.4";
    private NamedURI _tenantOrg;
    private String _owner;
    private Long _quotaGB;
    private Boolean _quotaEnabled;
    private StringSet assignedVNasServers;
    private StringSet filePolicies;

    @NamedRelationIndex(cf = "NamedRelation", type = TenantOrg.class)
    @Name("tenantOrg")
    public NamedURI getTenantOrg() {
        return _tenantOrg;
    }

    public void setTenantOrg(NamedURI tenantOrg) {
        _tenantOrg = tenantOrg;
        setChanged("tenantOrg");
    }

    @Name("owner")
    public String getOwner() {
        return _owner;
    }

    public void setOwner(String owner) {
        _owner = owner;
        setChanged("owner");
    }

    @Name("quota")
    public Long getQuota() {
        return (null == _quotaGB) ? 0L : _quotaGB;
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

    /**
     * @return the assignedVNasServers
     */
    @AllowedGeoVersion(version = EXPECTED_GEO_VERSION_FOR_ASSIGN_VNAS_SUPPORT)
    @Name("assigned_vnas_servers")
    public StringSet getAssignedVNasServers() {
        if (assignedVNasServers == null) {
            assignedVNasServers = new StringSet();
        }
        return assignedVNasServers;
    }

    /**
     * @param assignedVNasServers the assignedVNasServers to set
     */
    public void setAssignedVNasServers(StringSet assignedVNasServers) {
        this.assignedVNasServers = assignedVNasServers;
        setChanged("assigned_vnas_servers");
    }

    @Name("filePolicies")
    public StringSet getFilePolicies() {
        return filePolicies;
    }

    public void setFilePolicies(StringSet filePolicies) {
        this.filePolicies = filePolicies;
        setChanged("filePolicies");
    }

    public void addFilePolicy(URI policy) {
        StringSet policies = filePolicies;
        if (policies == null) {
            policies = new StringSet();
        }
        policies.add(policy.toString());
        this.filePolicies = policies;
    }

    public void removeFilePolicy(Project project, URI policy) {
        StringSet policies = filePolicies;
        if (policies != null) {
            policies.remove(policy.toString());
            this.filePolicies = policies;
        }
    }
}
