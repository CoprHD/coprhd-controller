/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.QuotaDirectory.SecurityStyles;

@Cf("UnManagedFileQuotaDirectory")
public class UnManagedFileQuotaDirectory extends UnManagedFileObject {

    // GUID of parent FS
    protected String _parentFSNativeGuid;

    protected Boolean _opLock;

    protected Long _size;

    protected Integer _softLimit;

    protected Integer _notificationLimit;

    protected Integer _softGrace;

    protected String _nativeId;

    private String _securityStyle = SecurityStyles.parent.name();

    /**
     * Get parent fs native guid
     * 
     * @return
     */
    @AlternateId("parentFsNativeGuid")
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
        setChanged("parentFsNativeGuid");
    }

    @Name("opLock")
    public Boolean getOpLock() {
        return _opLock;
    }

    public void setOpLock(Boolean _opLock) {
        this._opLock = _opLock;
        setChanged("opLock");
    }

    @Name("size")
    public Long getSize() {
        return _size;
    }

    public void setSize(Long _size) {
        this._size = _size;
        setChanged("size");
    }

    @Name("softLimit")
    public Integer getSoftLimit() {
        return _softLimit;
    }

    public void setSoftLimit(Integer _softLimit) {
        this._softLimit = _softLimit;
        setChanged("softLimit");
    }

    @Name("notificationLimit")
    public Integer getNotificationLimit() {
        return _notificationLimit;
    }

    public void setNotificationLimit(Integer _notificationLimit) {
        this._notificationLimit = _notificationLimit;
        setChanged("notificationLimit");
    }

    @Name("softGrace")
    public Integer getSoftGrace() {
        return _softGrace;
    }

    public void setSoftGrace(Integer _softGrace) {
        this._softGrace = _softGrace;
        setChanged("softGrace");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String _nativeId) {
        this._nativeId = _nativeId;
        setChanged("nativeId");
    }

    @Name("securityStyle")
    public String getSecurityStyle() {
        return _securityStyle;
    }

    public void setSecurityStyle(String _securityStyle) {
        this._securityStyle = _securityStyle;
        setChanged("securityStyle");
    }
}