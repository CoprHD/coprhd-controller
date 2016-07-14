/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;

/**
 * Base data object for file shares and snapshots
 */
public abstract class UnManagedFileObject extends UnManagedDiscoveredObject {
    
    
    // timestamp
    private String _timestamp;

    // file share or volume this snapshot is associated with
    private NamedURI _parent;

    // Project the snapshot was associated to
    private NamedURI _project;

    // native device ID to be indexed - this field is not exposed to client
    private String _nativeId;

    // native device ID to be indexed - this field is not exposed to client
    private String _nativeGuid;

    // RO Checkpoint baseline associated with a created Snapshot from a fileshare
    private String _checkpointBaseline;

    // Generated name
    private String _name;
    
    
    @Name("timestamp")
    public String getTimestamp() {
        return _timestamp;
    }

    public void setTimestamp(String timestamp) {
        this._timestamp = timestamp;
        setChanged("timestamp");
    }
    
    @Name("parent")
    public NamedURI getParent() {
        return _parent;
    }

    public void setParent(NamedURI parent) {
        _parent = parent;
        setChanged("parent");
    }
    
    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
        setChanged("nativeId");
    }
    
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this._nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @Name("name")
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
        setChanged("name");
    }

    @Name("checkpointbaseline")
    public String getCheckpointBaseline() {
        return _checkpointBaseline;
    }

    public void setCheckpointBaseline(String checkpointBaseline) {
        this._checkpointBaseline = checkpointBaseline;
        setChanged("checkpointbaseline");
    }
  
    @Override
    public String canBeDeleted() {
        if (fsUnManagedExportMap != null && !fsUnManagedExportMap.isEmpty()) {
            return UnManagedFSExport.class.getSimpleName();
        }
        if (unManagedSmbShareMap != null && !unManagedSmbShareMap.isEmpty()) {
            return UnManagedSMBFileShare.class.getSimpleName();
        }
        return null;
    }
}
