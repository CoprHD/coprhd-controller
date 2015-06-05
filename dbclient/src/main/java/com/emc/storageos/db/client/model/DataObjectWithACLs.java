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

import java.io.*;
import java.util.Set;

/**
 * Abstract base class for all the objects with ACLs
 */
public abstract class DataObjectWithACLs extends DataObject implements Serializable {
    static final long serialVersionUID = 4733219529069178265L;

    // acls
    private StringSetMap _acls;

    @Name("acls")
    @PermissionsIndex("PermissionsIndex")
    public StringSetMap getAcls() {
        return _acls;
    }

    public void setAcls(StringSetMap acls) {
        _acls = acls;
    }

    public Set<String> getAclSet(String key) {
        if (null != _acls) {
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

    public void removeAcl(String key, String role) {
        if (_acls != null) {
            _acls.remove(key, role);
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
}
