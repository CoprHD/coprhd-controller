/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.StorageProtocol;

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
    private Set<SecurityTypes> _securityType;
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

    private static final String SEC_SEPARATOR = ",";

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
        for (String secType : securityType.split(SEC_SEPARATOR)) {
            if (_securityType == null) {
                _securityType = new HashSet<SecurityTypes>();
            }
            _securityType.add(Enum.valueOf(SecurityTypes.class, secType.trim()));

        }
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
        for (String secType : securityType.split(SEC_SEPARATOR)) {
            if (_securityType == null) {
                _securityType = new HashSet<SecurityTypes>();
            }
            _securityType.add(Enum.valueOf(SecurityTypes.class, secType.trim()));

        }
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
        for (String secType : securityType.split(SEC_SEPARATOR)) {
            if (_securityType == null) {
                _securityType = new HashSet<SecurityTypes>();
            }
            _securityType.add(Enum.valueOf(SecurityTypes.class, secType.trim()));

        }
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
        if (_comments == null) {
            return "";
        }
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
        if (fileExport.getSecurityType() != null) {
            for (String secType : fileExport.getSecurityType().split(SEC_SEPARATOR)) {
                if (_securityType == null) {
                    _securityType = new HashSet<SecurityTypes>();
                }
                _securityType.add(Enum.valueOf(SecurityTypes.class, secType.trim()));

            }
        }
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

        // Convert the set of security types to a string separated by comma(,).
        Iterator<SecurityTypes> secIter = _securityType.iterator();
        String securityTypes = secIter.next().toString();
        while (secIter.hasNext()) {
            securityTypes += "," + secIter.next().toString();
        }
        FileExport fileExport = new FileExport(_clients, _storagePortName, _mountPath, securityTypes, _permissions.toString(),
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
