/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringMap;

@Cf("UnManagedFileQuotaDirectory")
public class UnManagedFileQuotaDirectory extends UnManagedFileObject {
    
    // GUID of parent FS
    protected String _parentFSNativeGuid;
    
    /**
     * Get parent fs native guid
     * 
     * @return
     */
    @Name("parentFsNativeGuid")
    public String getParentFSNativeGuid() {
        return _parentFSNativeGuid;
    }

    /**
     * Set parent fs guid
     * 
     * @param mountPath
     */
    public void setParentFSNativeGuid(String _parentFSNativeGuid) {
        this._parentFSNativeGuid = _parentFSNativeGuid;
        setChanged("parentFsGuid");
    }

}
