/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Snapshot data object
 */
@Cf("Snapshot")
public class Snapshot extends FileObject implements ProjectResourceSnapshot {
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

    @NamedRelationIndex(cf = "NamedRelationIndex", type = FileShare.class)
    @Name("parent")
    public NamedURI getParent() {
        return _parent;
    }

    public void setParent(NamedURI parent) {
        _parent = parent;
        setChanged("parent");
    }

    public Class<? extends DataObject> parentClass() {
        return FileShare.class;
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
        setChanged("nativeId");
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this._nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @NamedRelationIndex(cf = "NamedRelationIndex", type = Project.class)
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

}
