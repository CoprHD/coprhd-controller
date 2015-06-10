/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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

package com.emc.storageos.vnx.xmlapi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VNXFileExport extends VNXBaseClass {

    private String storagePortName;
    private String storagePort;
    private String subDirectory;
    private String dataMover;
    private String mountPoint;
    private String securityType;
    private String permissions;
    private String protocol;
    private String rootUserMapping;
    private String fileId;
    private String exportName;
    private String comment;
    private String maxUsers;
    private List<String> clients;
    private String netBios;
    
    public VNXFileExport() {
    }

    /**
     *
     * FileShareObject constructor
     *
     * @param clients     Export clients --- IP, FQN.
     * @param storagePortName        Port
     * @param mountPoint  Mount point.
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     */
    public VNXFileExport(List<String> clients, String storagePortName, String mountPoint,
                      String securityType, String permissions, String rootUserMapping, String protocol, String storagePort, String subDirectory, String comment) {
        setClients(clients);
        setStoragePortName(storagePortName);
        setMountPoint(mountPoint);
        setSecurityType(securityType);
        setPermissions(permissions);
        setRootUserMapping(rootUserMapping);
        setProtocol(protocol);
        setStoragePort(storagePort);
        setComment(comment);
        setSubDirectory(subDirectory);
    }
    
    /**
    *
    * FileShareObject constructor
    *
    * @param clients     Export clients --- IP, FQN.
    * @param storagePortName        Port
    * @param mountPoint  Mount point.
    * @param securityType
    * @param permissions
    * @param rootUserMapping
    */
//   public VNXFileExport(List<String> clients, String storagePortName, String mountPoint,
//                     String securityType, String permissions, String rootUserMapping, String protocol, String storagePort, String subDirectory) {
//	   this(clients, storagePortName, mountPoint, securityType, permissions, rootUserMapping, protocol, storagePort, subDirectory);
//       setSubDirectory(subDirectory);
//   }

    /**
     *
     * FileShareObject constructor
     *
     * @param clients     Export clients --- IP, FQN.
     * @param storagePortName        Port
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     */
    public VNXFileExport(List<String> clients, String storagePortName, String securityType, String permissions, String rootUserMapping, String protocol, String storagePort) {
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
     * @param clients     Export clients --- IP, FQN.
     * @param storagePortName        Port
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     */
    public VNXFileExport(List<String> clients, String storagePortName, String securityType, String permissions, String rootUserMapping, String protocol) {
        setClients(clients);
        setStoragePortName(storagePortName);
        setSecurityType(securityType);
        setPermissions(permissions);
        setRootUserMapping(rootUserMapping);
        setProtocol(protocol);
    }

    public List<String> getClients() {
        return clients;
    }

    public void setClients(List<String> clients) {
        if(clients == null) clients = new ArrayList<String>();
        this.clients = clients;
    }

    public String getStoragePortName() {
        return this.storagePortName;
    }

    public void setStoragePortName(String port) {
        if(port == null) port = "";
        this.storagePortName = port;
    }

    public String getMountPoint() {
        return this.mountPoint;
    }

    public void setMountPoint(String mountpoint) {
        if(mountpoint == null) mountpoint = "";
        this.mountPoint = mountpoint;
    }

    public String getSecurityType(){
        return this.securityType;
    }

    public void setSecurityType(String securityType){
        if(securityType == null) securityType = "";
        this.securityType = securityType;
    }

    public String getPermissions(){
        return this.permissions;
    }

    public void setPermissions(String permissions){
        if(permissions == null) permissions = "";
        this.permissions = permissions;
    }

    public String getProtocol(){
        return this.protocol;
    }

    public void setProtocol(String protocol){
        if(protocol == null) protocol = "";
        this.protocol = protocol;
    }

    public String getRootUserMapping(){
        return this.rootUserMapping;
    }

    public void setRootUserMapping(String rootUserMapping){
        if(rootUserMapping == null) rootUserMapping = "";
        this.rootUserMapping = rootUserMapping;
    }

    public String getStoragePort(){
        return this.storagePort;
    }

    public void setStoragePort(String storagePort){
        if(storagePort == null) storagePort = "";
        this.storagePort = storagePort;
    }
    
    public String getDataMover(){
        return dataMover;
    }
 
    public void setDataMover(String dataMover){
        this.dataMover = dataMover;
    }

    public String getSubDirectory(){
        return subDirectory;
    }
 
    public void setSubDirectory(String subDirectory){
        this.subDirectory = subDirectory;
    }
    
    public String getFileExportKey() {
        return String.format("%1$s.%2$s.%3$s.%4$s", getProtocol(), getSecurityType(), getPermissions(), getRootUserMapping());
    }

    public static String exportLookupKey(String protocol, String securityType, String permissions, String rootMapping) {
        return String.format("%1$s.%2$s.%3$s.%4$s", protocol, securityType, permissions, rootMapping);
    }
    
    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getExportName() {
    	return exportName;
    }
    
    public void setExportName(String exportName) {
    	this.exportName = exportName;
    }
    
    public String getComment(){
    	return comment;
    }
    
    public void setComment(String comment) {
    	this.comment = comment;
    }
    
    public String getMaxUsers() {
    	return maxUsers;
    }
    
    public void setMaxUsers(String maxUsers) {
    	this.maxUsers = maxUsers;
    }
    
    public String getNetBios() {
    	return netBios;
    }
    
    public void setNetBios(String netBios) {
    	this.netBios = netBios;
    }
}
