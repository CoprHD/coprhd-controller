/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * File object (file share and file share snapshot) export for Isilon and VNX File storage.
 */
@XmlRootElement(name = "file_export")
public class FileExport extends AbstractSerializableNestedObject {

    private static final String CLIENTS = "clients";
    private static final String SECURITY_TYPE = "securityType";
    private static final String PERMISSIONS = "permissions";
    private static final String ROOT_USER_MAPPING = "rootUserMapping";
    private static final String MOUNTPOINT = "mountPoint";
    private static final String ISILONID = "id";
    private static final String STORAGEPORTNAME = "storagePortName";
    private static final String PROTOCOL = "protocol";
    private static final String STORAGEPORT = "storagePort";
    private static final String PATH = "path";
    private static final String MOUNTPATH = "mountPath";
    private static final String COMMENTS = "comments";
    private static final String NATIVEID = "nativeId";
    private static final String SUBDIRECTORY = "subDirectory";

    /**
     * JAXB requirement
     */
    public FileExport() {
    }

    /**
     * 
     * FileShareObject constructor
     * 
     * @param clients Export clients --- IP, FQN.
     * @param storagePortName Port
     * @param mountPoint Mount point.
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     * @param protocol
     * @param storagePort
     * @param path
     * @param mountPath
     */
    public FileExport(List<String> clients, String storagePortName, String mountPoint,
            String securityType, String permissions, String rootUserMapping,
            String protocol, String storagePort, String path, String mountPath, String subDirectory, String comments) {
        setClients(clients);
        setStoragePortName(storagePortName);
        setMountPoint(mountPoint);
        setSecurityType(securityType);
        setPermissions(permissions);
        setRootUserMapping(rootUserMapping);
        setProtocol(protocol);
        setStoragePort(storagePort);
        setPath(path);
        setMountPath(mountPath);
        setComments(comments);
        setSubDirectory(subDirectory);
    }

    /**
     * 
     * FileShareObject constructor
     * 
     * @param clients Export clients --- IP, FQN.
     * @param storagePortName Port
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     */
    public FileExport(List<String> clients, String storagePortName, String securityType, String permissions,
            String rootUserMapping, String protocol, String storagePort) {
        setClients(clients);
        setStoragePortName(storagePortName);
        setSecurityType(securityType);
        setPermissions(permissions);
        setRootUserMapping(rootUserMapping);
        setProtocol(protocol);
        setStoragePort(storagePort);
    }

    /**
     * 
     * FileShareObject constructor
     * 
     * @param clients Export clients --- IP, FQN.
     * @param storagePortName Port
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     */
    public FileExport(List<String> clients, String storagePortName, String securityType, String permissions,
            String rootUserMapping, String protocol, String storagePort, String path) {
        setClients(clients);
        setStoragePortName(storagePortName);
        setSecurityType(securityType);
        setPermissions(permissions);
        setRootUserMapping(rootUserMapping);
        setProtocol(protocol);
        setStoragePort(storagePort);
        setPath(path);
    }

    /**
     * 
     * FileShareObject constructor
     * 
     * @param clients Export clients --- IP, FQN.
     * @param storagePortName Port
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     */
    public FileExport(List<String> clients, String storagePortName, String securityType, String permissions, String rootUserMapping,
            String protocol) {
        setClients(clients);
        setStoragePortName(storagePortName);
        setSecurityType(securityType);
        setPermissions(permissions);
        setRootUserMapping(rootUserMapping);
        setProtocol(protocol);
    }

    @XmlElement
    public List<String> getClients() {
        return getListOfStringsField(CLIENTS);
    }

    public void setClients(List<String> clients) {
        if (clients == null) {
            clients = new ArrayList<String>();
        }
        setListOfStringsField(CLIENTS, clients);
    }

    @XmlElement(name = "storage_port_name")
    public String getStoragePortName() {
        return getStringField(STORAGEPORTNAME);
    }

    public void setStoragePortName(String port) {
        if (port == null) {
            port = "";
        }
        setField(STORAGEPORTNAME, port);
    }

    @XmlElement(name = "mount_point")
    public String getMountPoint() {
        return getStringField(MOUNTPOINT);
    }

    public void setMountPoint(String mountpoint) {
        if (mountpoint == null) {
            mountpoint = "";
        }
        setField(MOUNTPOINT, mountpoint);
    }

    @XmlElement(name = "isilon_id")
    public String getIsilonId() {
        return getStringField(ISILONID);
    }

    public void setIsilonId(String isilonId) {
        if (isilonId == null) {
            isilonId = "";
        }
        setField(ISILONID, isilonId);
    }

    @XmlElement(name = "security_type")
    public String getSecurityType() {
        return getStringField(SECURITY_TYPE);
    }

    public void setSecurityType(String securityType) {
        if (securityType == null) {
            securityType = "";
        }
        setField(SECURITY_TYPE, securityType);
    }

    @XmlElement
    public String getPermissions() {
        return getStringField(PERMISSIONS);
    }

    public void setPermissions(String permissions) {
        if (permissions == null) {
            permissions = "";
        }
        setField(PERMISSIONS, permissions);
    }

    @XmlElement
    public String getProtocol() {
        return getStringField(PROTOCOL);
    }

    public void setProtocol(String protocol) {
        if (protocol == null) {
            protocol = "";
        }
        setField(PROTOCOL, protocol);
    }

    @XmlElement(name = "root_user_mapping")
    public String getRootUserMapping() {
        return getStringField(ROOT_USER_MAPPING);
    }

    public void setRootUserMapping(String rootUserMapping) {
        if (rootUserMapping == null) {
            rootUserMapping = "";
        }
        setField(ROOT_USER_MAPPING, rootUserMapping);
    }

    @XmlElement(name = "storage_port")
    public String getStoragePort() {
        return getStringField(STORAGEPORT);
    }

    public void setStoragePort(String storagePort) {
        if (storagePort == null) {
            storagePort = "";
        }
        setField(STORAGEPORT, storagePort);
    }

    @XmlElement(name = "path")
    public String getPath() {
        return getStringField(PATH);
    }

    public void setPath(String path) {
        if (path == null) {
            path = "";
        }
        setField(PATH, path);
    }

    @XmlElement(name = "mountPath")
    public String getMountPath() {
        return getStringField(MOUNTPATH);
    }

    public void setMountPath(String mountPath) {
        if (mountPath == null) {
            mountPath = "";
        }
        setField(MOUNTPATH, mountPath);
    }

    @XmlElement(name = "comments")
    public String getComments() {
        return getStringField(COMMENTS);
    }

    public void setComments(String comments) {
        if (comments == null) {
            comments = "";
        }
        setField(COMMENTS, comments);
    }

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return getStringField(NATIVEID);
    }

    public void setNativeId(String nativeId) {
        if (nativeId == null) {
            nativeId = "";
        }
        setField(NATIVEID, nativeId);
    }

    @XmlElement(name = "subDirectory")
    public String getSubDirectory() {
        return getStringField(SUBDIRECTORY);
    }

    public void setSubDirectory(String subDirectory) {
        if (subDirectory == null) {
            subDirectory = "";
        }
        setField(SUBDIRECTORY, subDirectory);
    }

    public String getFileExportKey() {
        return String.format("%1$s.%2$s.%3$s.%4$s.%5$s", getProtocol(), getSecurityType(), getPermissions(), getRootUserMapping(),
                getPath());
    }

    public static String exportLookupKey(String protocol, String securityType, String permissions, String rootMapping, String path) {
        return String.format("%1$s.%2$s.%3$s.%4$s.%5$s", protocol, securityType, permissions, rootMapping, path);
    }

    public static String exportLookupKey(String protocol, String securityType, String permissions, String rootMapping) {
        return String.format("%1$s.%2$s.%3$s.%4$s", protocol, securityType, permissions, rootMapping);
    }

}
