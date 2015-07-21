/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.io.Serializable;
import java.util.*;

/**
 *  Represents a user record in the database.  
 */
@NoInactiveIndex
@Cf("StorageOSUserDAO")
public class StorageOSUserDAO extends DataObject implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String _userName;
    protected String _tenantId;
    private String _distinguishedName;
    protected StringSet _attributes = new StringSet();
    protected StringSet _groups = new StringSet();
    protected Boolean _local;

    /**
     * Returns the value of the field called '_userName'.
     * @return Returns the _userName.
     */
    @Name("username")
    @AlternateId("AltIdIndex")
    public String getUserName() {
        return _userName;
    }

    /**
     * Sets the field called '_userName' to the given value.
     * @param userName The _userName to set.
     */
    public void setUserName(String userName) {
        _userName = userName;
        setChanged("username");
    }
    /**
     * Returns the value of the field called '_tenantId'.
     * @return Returns the _tenantId.
     */
    @Name("tenantid")
    public String getTenantId() {
        return _tenantId;
    }

    /**
     * Sets the field called '_tenantId' to the given value.
     * @param tenantId The _tenantId to set.
     */
    public void setTenantId(String tenantId) {
        _tenantId = tenantId;    
        setChanged("tenantid");
    }

    /**
     * Sets distinguished name for the user
     * @param distinguishedName
     */
    public void setDistinguishedName( String distinguishedName ) {
        _distinguishedName = distinguishedName;
        setChanged("distinguishedName");
    }

    /**
     * Returns distinguished name of the user
     * @return
     */
    @Name("distinguishedName")
    public String getDistinguishedName() {
        return _distinguishedName;
    }

    /**
     * Returns the value of the field called '_attributes'.
     * @return Returns the _attributes.
     */
    @Name("attributes")
    public StringSet getAttributes() {
        return _attributes;
    }

    /**
     * Sets the field called '_attributes' to the given value.
     * @param attributes The _attributes to set.
     */
    public void setAttributes(StringSet attributes) {
        if (_attributes == null) {
            _attributes = new StringSet();
        }
        _attributes.replace(attributes);
    }

    /**
     * add an attribute to the set of attributes
     * @param attr
     */
    public void addAttribute(String attr) {
        if (_attributes == null) {
            _attributes = new StringSet();
        }
        _attributes.add(attr);
    }

    /**
     * Returns the value of the field called '_groups'.
     * @return Returns the _groups.
     */
    @Name("groups")
    public StringSet getGroups() {
        return _groups;
    }

    /**
     * Sets groups to the given value.
     * @param groups The _groups to set.
     */
    public void setGroups(StringSet groups) {
        if (_groups == null) {
            _groups = new StringSet();
        }
        _groups.replace(groups);
    }

    /**
     *
     * @param group
     */
    public void addGroup(String group) {
        if (_groups == null) {
            _groups = new StringSet();
        }
        _groups.add(group);
    }

    /**
     * Returns the value of the field called '_local'.
     * @return Returns the _local.
     */
    @Name("islocal")
    public Boolean getIsLocal() {
        return (_local != null) ? _local : Boolean.FALSE;
    }

    /**
     * Sets the field called '_local' to the given value.
     * @param local The _local to set.
     */
    public void setIsLocal(Boolean local) {
        _local = local;
        setChanged("islocal");
    }

    /**
     * Update fields from the new record
     * @param newDao
     */
    public void updateFrom(StorageOSUserDAO newDao) {
        if (_tenantId == null || !_tenantId.equals(newDao.getTenantId())) {
            setTenantId(newDao.getTenantId());
        }
        // update group info
        if (newDao.getGroups() == null || newDao.getGroups().size() == 0) {
            if (_groups != null) {
                _groups.clear();
            }
        } else {
            StringSet newGroups = newDao.getGroups();
            if (_groups == null) {
                _groups = new StringSet();
            } else {
                // remove what is not there in the new set
                Set<String> remove = new HashSet<String>();
                for (String group: _groups) {
                    if (!newGroups.contains(group)) {
                        remove.add(group);    
                    }
                }
                _groups.removeAll(remove);
            }
            for (String group: newGroups) {
                if (!_groups.contains(group)) {
                    _groups.add(group);
                }
            }
        }
        // update attributes
        if (newDao.getAttributes() == null || newDao.getAttributes().size() == 0) {
            if (_attributes != null) {
                _attributes.clear();
            }
        } else {
            StringSet newAttributes = newDao.getAttributes();
            if (_attributes == null) {
                _attributes = new StringSet();
            } else {
                Set<String> remove = new HashSet<String>();
                for (String attr: new StringSet(_attributes)) {
                    if (!newAttributes.contains(attr)) {
                        remove.add(attr);
                    }
                }
                _attributes.removeAll(remove);
            }
            for (String attr: newAttributes) {
                if (!_attributes.contains(attr)) {
                    _attributes.add(attr);
                }
            }
        }
    }
}
