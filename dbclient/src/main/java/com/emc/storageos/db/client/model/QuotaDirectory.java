/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * QuotaDirectory data object
 */
@SuppressWarnings("serial")
@Cf("QuotaDirectory")
public class QuotaDirectory extends FileObject implements ProjectResource {

    // enumeration of quota directory security styles
    public enum SecurityStyles {
        parent,
        unix,
        ntfs,
        mixed
    };

    // file share or volume this quota directory is associated with
    private NamedURI _parent;

    // Project the quota-directory was associated to
    private NamedURI _project;

    // tenant this quota-directory is associated with
    private NamedURI _tenant;

    // native device ID to be indexed - this field is not exposed to client
    private String _nativeId;

    // native device ID to be indexed - this field is not exposed to client
    private String _nativeGuid;

    // Generated name
    private String _name;

    private Boolean _oplock;

    private Long _size; // Quota size in bytes- hard limit.
    
    private Integer _softLimit; //Soft limit in percentage of hard limit
    
    private Integer _notificationLimit; // notification limit in percentage of hardl limit
    
    private Integer _softGrace; //soft grace period in days

    // UNIX, NTFS, Mixed
    private String _securityStyle = SecurityStyles.parent.name();

    public Class<? extends DataObject> parentClass() {
        return FileShare.class;
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

    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return _tenant;
    }

    public void setTenant(NamedURI tenant) {
        _tenant = tenant;
        setChanged("tenant");
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

    @Name("oplock")
    public Boolean getOpLock() {
        return _oplock;
    }

    public void setOpLock(Boolean oplock) {
        this._oplock = oplock;
        setChanged("oplock");
    }

    @Name("size")
    public Long getSize() {
        return _size;
    }

    public void setSize(Long size) {
        this._size = size;
        setChanged("size");
    }

    @Name("softLimit")
    public Integer getSoftLimit() {
        return _softLimit;
    }

    public void setSoftLimit(Integer softLimit) {
        this._softLimit = softLimit;
        setChanged("softLimit");
    }

    @Name("notificationLimit")
    public Integer getNotificationLimit() {
        return _notificationLimit;
    }

    public void setNotificationLimit(Integer notificationLimit) {
        this._notificationLimit = notificationLimit;
        setChanged("notificationLimit");
    }

    @Name("softGrace")
    public Integer getSoftGrace() {
        return _softGrace;
    }

    public void setSoftGrace(Integer softGrace) {
        this._softGrace = softGrace;
        setChanged("softGrace");
    }

    @Name("security_style")
    public String getSecurityStyle() {
        return _securityStyle;
    }

    public void setSecurityStyle(String securityStyle) {
        this._securityStyle = securityStyle;
        setChanged("security_style");
    }

    @Name("name")
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
        setChanged("name");
    }
}
