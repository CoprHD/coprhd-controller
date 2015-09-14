/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.beans.Transient;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Set;

public abstract class DiscoveredComputeSystemWithAcls extends AbstractDiscoveredTenantResource implements Serializable {
    private static final long serialVersionUID = 2131442061913781875L;

    protected static final Logger _log = LoggerFactory.getLogger(DiscoveredComputeSystemWithAcls.class);

    @Deprecated
    private URI _tenant;

    // acls
    private StringSetMap _acls;

    @Name("acls")
    @PermissionsIndex("PermissionsIndex")
    public StringSetMap getAcls() {
        return _acls;
    }

    public void setAcls(StringSetMap acls) {
        _acls = acls;
        setChanged("acls");
    }

    public Set<String> getAclSet(String key) {
        if (_acls != null) {
            return _acls.get(key);
        }
        return null;
    }

    public void addAcl(String key, String role) {
        if (_acls == null) {
            _acls = new StringSetMap();
        }
        _acls.put(key, role);
    }

    public void addAcl(URI tenantId) {
        //Doing the PermissionsKey functionality here as making the
        //DBClient to be a dependent on security library creates a
        //circular dependency, hence just added this formatting alone
        //here.
        String key = String.format("%s,%s", "TENANT", tenantId.toString());
        String role = "USE";

        addAcl(key, role);
    }

    public void removeAcl(String key) {
        if (_acls != null) {
            _acls.remove(key);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(_acls);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        _acls = (StringSetMap) in.readObject();
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#getTenant()
     */
    @Override
    @Name("tenant")
    @RelationIndex(cf = "RelationIndex", type = TenantOrg.class)
    public URI getTenant() {
        return _tenant;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#setTenant(java.net.URI)
     */
    @Override
    public void setTenant(URI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#auditParameters()
     */
    @Override
    public abstract Object[] auditParameters();

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#getDataObject()
     */
    @Override
    public DataObject findDataObject() {
        return (DataObject) this;
    }

    /**
     * Finds the vCenter tenant based on the first acls configured.
     * This tenant information is used to populate the vCenter
     * GET request response in case of upgrade. The same is also
     * used for any vCenter tasks.
     *
     * @return
     */
    public URI findVcenterTenant() {
        if (CollectionUtils.isEmpty(_acls)) {
            _log.debug("Returning null uri");
            return NullColumnValueGetter.getNullURI();
        }

        String permissionKey = null;
        for (Map.Entry<String, ? extends AbstractChangeTrackingSet> aclEntry : _acls.entrySet()) {
            if (aclEntry != null &&
                    StringUtils.isNotBlank(aclEntry.getKey()) &&
                    !CollectionUtils.isEmpty(aclEntry.getValue())) {
                permissionKey = aclEntry.getKey();
                break;
            }
        }

        URI tenant = NullColumnValueGetter.getNullURI();
        if (StringUtils.isNotBlank(permissionKey)) {
            tenant = URI.create(permissionKey.split(",")[1]);
        }

        _log.debug("Vcenter {} tenant {}", this.getLabel(), tenant);
        return tenant;
    }
}
