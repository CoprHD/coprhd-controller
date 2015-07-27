/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;


import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import com.emc.storageos.db.client.model.AbstractSerializableNestedObject;


/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

@XmlRootElement(name = "unmanaged_smb_share")
public class UnManagedSMBFileShare extends AbstractSerializableNestedObject {

    private static final String NAME = "name";
    private static final String MOUNTPOINT = "mountPoint";
    private static final String DESCRIPTION = "description";
    private static final String PERMISSION_TYPE = "permissionType";
    private static final String PERMISSION = "permission";
    private static final String MAX_USERS = "maxUsers";
    private static final String NATIVE_ID = "nativeId";
    private static final String PORT_GROUP = "portGroup";
    private static final String PATH = "path";

    /**
     * JAXB requirement
     */
    public UnManagedSMBFileShare() {
    }

    public UnManagedSMBFileShare(String name, String description, String permissionType, String permission, int maxUsers, String mountPoint) {
        setName(name);
        setDescription(description);
        setPermissionType(permissionType);
        setPermission(permission);
        setMaxUsers(maxUsers);
        setMountPoint(mountPoint);
    }

    public UnManagedSMBFileShare(String name, String description, String permissionType, String permission, int maxUsers) {
        setName(name);
        setDescription(description);
        setPermissionType(permissionType);
        setPermission(permission);
        setMaxUsers(maxUsers);
    }

    @XmlElement
    public String getName() {
        return getStringField(NAME);
    }

    public void setName(String name) {
        if(name == null) name = "";
        setField(NAME, name);
    }

    @XmlElement
    public String getDescription() {
        return getStringField(DESCRIPTION);
    }

    public void setDescription(String description) {
        if(description == null) description = "";
        setField(DESCRIPTION, description);
    }

    @XmlElement(name = "permission_type")
    public String getPermissionType() {
        return getStringField(PERMISSION_TYPE);
    }

    public void setPermissionType(String permissionType) {
        if(permissionType == null) permissionType = "";
        setField(PERMISSION_TYPE, permissionType);
    }

    @XmlElement(name = "permission")
    public String getPermission() {
        return getStringField(PERMISSION);
    }

    public void setPermission(String permission) {
        if(permission == null) permission = "";
        setField(PERMISSION, permission);
    }

    //@XmlElement(name = "max_users")
    @XmlTransient
    public int getMaxUsers() {
        return getIntField(MAX_USERS);
    }

    public void setMaxUsers(int maxUsers) {
        setField(MAX_USERS, maxUsers);
    }

    @XmlElement
    public String getMountPoint() {
        return getStringField(MOUNTPOINT);
    }

    public void setMountPoint(String mountPoint) {
        if(mountPoint == null) mountPoint = "";
        setField(MOUNTPOINT, mountPoint);
    }

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return getStringField(NATIVE_ID);
    }

    public void setNativeId(String nativeId) {
        if(nativeId == null) nativeId = "";
        setField(NATIVE_ID, nativeId);
    }
    
    @XmlElement
    public String getPortGroup() {
    	return getStringField(PORT_GROUP);
    }
    
    public void setPortGroup(String portGroup) {
    	if (portGroup == null) portGroup = "";
    	setField(PORT_GROUP, portGroup);
    }
    
    public void setPath(String path) {
    	if(path == null) {
    		path = "";
    	}
    	setField(PATH, path);
    }
    
    @XmlElement
    public String getPath() {
    	return getStringField(PATH);
    }
}
