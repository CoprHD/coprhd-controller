/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

/**
 * Project data object
 */
@Cf("Project")
@DbKeyspace(Keyspaces.GLOBAL)
public class Project extends DataObjectWithACLs {
    private NamedURI _tenantOrg;
    private String _owner;
    private Long _quotaGB;
    private Boolean _quotaEnabled;
    private StringSet assignedVNasServers;

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

}
