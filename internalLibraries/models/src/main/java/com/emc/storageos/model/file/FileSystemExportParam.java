/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Attributes associated with a file system export.
 *
 */
@XmlRootElement(name = "filesystem_export")
public class FileSystemExportParam {
    
    private String securityType = "sys"; //FileShareExport.SecurityTypes.sys.name();
    private String permissions = "rw"; //FileShareExport.Permissions.rw.name();
    private String rootUserMapping = "nobody";
    private String protocol;
    private String subDirectory;
    private String mountPoint;
    private String comments;
    
    private List<String> endpoints;

    public FileSystemExportParam() {}
    
    public FileSystemExportParam(String securityType, String permissions,
            String rootUserMapping, String protocol, String mountPoint,
            List<String> endpoints) {
        this.securityType = securityType;
        this.permissions = permissions;
        this.rootUserMapping = rootUserMapping;
        this.protocol = protocol;
        this.mountPoint = mountPoint;
        this.endpoints = endpoints;
    }
    
    public FileSystemExportParam(String securityType, String permissions,
            String rootUserMapping, String protocol, String mountPoint,
            List<String> endpoints, String subDirectory) {
        this.securityType = securityType;
        this.permissions = permissions;
        this.rootUserMapping = rootUserMapping;
        this.protocol = protocol;
        this.mountPoint = mountPoint;
        this.endpoints = endpoints;
        this.subDirectory = subDirectory;
    }

    /**
     * Security type for the file export
     * @valid sys = Default UNIX security 
     * @valid krb5 = Kerberos security option
     * @valid krb5i = Kerberos security option
     * @valid krb5p = Kerberos security option
     */
    @XmlElement(name = "type")
    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    /**
     * Description of the operation
     * @return The comments regarding the operation
     */
    
    @XmlElement(name="comments",required = false)
    public String getComments() {
		return comments;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}

	/**
     * Permissions for the file export
     * @valid ro = read only
     * @valid rw = read and write
     * @valid root = full permission
     */
    @XmlElement(name = "permissions")
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    /**
     * User with root permissions
     * @valid root = only root has special access permissions
     * @valid nobody = nobody has special access permissions
     */
    @XmlElement(name = "root_user")
    public String getRootUserMapping() {
        return rootUserMapping;
    }

    public void setRootUserMapping(String rootUserMapping) {
        this.rootUserMapping = rootUserMapping;
    }

    /**
     * File system export protocol.
     * @valid CIFS = Common Internet File System
     * @valid NFS = Network File System
     */
    @XmlElement(name = "protocol", required = true)
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * The path representing the point in the file system where 
     * the file share being exported is mounted.
     * @valid none 
     */
    @XmlElement(name = "mount_point")
    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    @XmlElementWrapper(required = true,name = "endpoints")
    /**
     * List of endpoints the file system is exported to.
     * @valid none 
     */
    @XmlElement(name = "endpoint")
    public List<String> getEndpoints() {
        if (endpoints == null) {
            endpoints = new ArrayList<String>();
        }
        return endpoints;
    }

    public void setEndpoints(List<String> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * The specific sub-directory under the file system that
     * is being exported.  If sub-directory is "null", entire
     * file system is exported.
     * @valid none 
     */
    @XmlElement(name = "sub_directory")
    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }

}
