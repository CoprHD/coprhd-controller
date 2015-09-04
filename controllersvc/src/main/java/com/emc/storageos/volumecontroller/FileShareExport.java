/*
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

package com.emc.storageos.volumecontroller;

import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.FileExport;

import java.io.Serializable;
import java.util.List;

/**
 * Place holder for FS Export information.
 */
public class FileShareExport implements Serializable {

    // enumeration of export security types
    public enum SecurityTypes {
        sys,
        krb5,
        krb5i,
        krb5p
    }

    // enumeration of export permissions
    public enum Permissions {
        ro,
        rw,
        root
    }

    private List<String> _clients;
    private SecurityTypes _securityType;
    private Permissions _permissions;
    private String _rootUserMapping;
    private String _storagePortName;
    private StorageProtocol.File _protocol;
    private String _storagePort;
    private String _path;
    private String _subDirectory;
    private String _mountPath;
    private String _comments;
    private String _isilonId;

    /**
     * Construction of FileShareExport export
     * 
     * @param clients
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     * @param protocol
     * @param storagePortName Storage port name.
     */
    public FileShareExport(List<String> clients, String securityType, String permissions, String rootUserMapping, String protocol,
            String storagePortName, String storagePort) {
        _clients = clients;
        _securityType = Enum.valueOf(SecurityTypes.class, securityType);
        _permissions = Enum.valueOf(Permissions.class, permissions);
        _rootUserMapping = rootUserMapping;
        _storagePortName = storagePortName;
        _protocol = Enum.valueOf(StorageProtocol.File.class, protocol);
        _storagePort = storagePort;

    }

    /**
     * Construction of FileShareExport export
     * 
     * @param clients
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     * @param protocol
     * @param storagePortName Storage port name.
     * @param path
     */
    public FileShareExport(List<String> clients, String securityType, String permissions, String rootUserMapping,
            String protocol, String storagePortName, String storagePort, String path) {
        _clients = clients;
        _securityType = Enum.valueOf(SecurityTypes.class, securityType);
        _permissions = Enum.valueOf(Permissions.class, permissions);
        _rootUserMapping = rootUserMapping;
        _storagePortName = storagePortName;
        _protocol = Enum.valueOf(StorageProtocol.File.class, protocol);
        _storagePort = storagePort;
        _path = path;
    }

    /**
     * Construction of FileShareExport export
     * 
     * @param clients
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     * @param protocol
     * @param storagePortName Storage port name.
     * @param path
     * @param mountPath
     */
    public FileShareExport(List<String> clients, String securityType, String permissions, String rootUserMapping,
            String protocol, String storagePortName, String storagePort, String path, String mountPath, String subDirectory, String comments) {
        _clients = clients;
        _securityType = Enum.valueOf(SecurityTypes.class, securityType);
        _permissions = Enum.valueOf(Permissions.class, permissions);
        _rootUserMapping = rootUserMapping;
        _storagePortName = storagePortName;
        _protocol = Enum.valueOf(StorageProtocol.File.class, protocol);
        _storagePort = storagePort;
        _path = path;
        _mountPath = mountPath;
        _comments = comments;
        _subDirectory = subDirectory;
    }

    public String getComments() {
        if (_comments == null)
            return "";
        return _comments;
    }

    public void setComments(String comments) {
        _comments = comments;
    }

    /**
     * Construction of FileShareExport
     * 
     * @param fileExport FileExport
     */
    public FileShareExport(FileExport fileExport) {
        _clients = fileExport.getClients();
        _permissions = Permissions.valueOf(fileExport.getPermissions());
        _securityType = SecurityTypes.valueOf(fileExport.getSecurityType());
        _rootUserMapping = fileExport.getRootUserMapping();
        _storagePortName = fileExport.getStoragePortName();
        _storagePort = fileExport.getStoragePort();
        _protocol = StorageProtocol.File.valueOf(fileExport.getProtocol());
        _path = fileExport.getPath();
        _mountPath = fileExport.getMountPath();
        _isilonId = fileExport.getIsilonId();
        _subDirectory = fileExport.getSubDirectory();
    }

    public List<String> getClients() {
        return _clients;
    }

    public String getSecurityType() {
        return _securityType.toString();
    }

    public String getPermissions() {
        return _permissions.toString();
    }

    public String getStoragePortName() {
        return _storagePortName;
    }

    public String getRootUserMapping() {
        return _rootUserMapping;
    }

    public String getProtocol() {
        return _protocol.toString();
    }

    public String getStoragePort() {
        return _storagePort;
    }

    public String getPath() {
        return _path;
    }

    public String getMountPath() {
        return _mountPath;
    }

    public String getSubDirectory() {
        return _subDirectory;
    }

    public FileExport getFileExport() {

        FileExport fileExport = new FileExport(_clients, _storagePortName, _mountPath, _securityType.toString(), _permissions.toString(),
                _rootUserMapping,
                _protocol.toString(), _storagePort, _path, _mountPath, _subDirectory, _comments);
        fileExport.setIsilonId(_isilonId);
        return fileExport;
    }

    public String getIsilonId() {
        return _isilonId;
    }

    public void setIsilonId(String isilonId) {
        this._isilonId = isilonId;
    }

}
