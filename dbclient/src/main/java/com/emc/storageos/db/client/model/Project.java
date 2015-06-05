/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
    private Long   _quotaGB;
    private Boolean _quotaEnabled;

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
    public Long getQuota(){
        return (null == _quotaGB) ? 0L : _quotaGB;
    }

    public void setQuota(Long quota) {
        _quotaGB = quota;
        setChanged("quota");
    }

    @Name("quotaEnabled")
    public Boolean  getQuotaEnabled(){
        return (_quotaEnabled == null) ? false : _quotaEnabled;
    }

    public void  setQuotaEnabled(Boolean enable){
         _quotaEnabled = enable;
        setChanged("quotaEnabled");
    }

}
