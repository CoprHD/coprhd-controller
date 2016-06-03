/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;

public class StorageProvider extends StorageObject {

    // provider type, ex. vmax, hitachi, etc. . Type: Input.
    private String providerType;

    // provider name name, ex: Hitachi Provider. Type: Input.
    private String providerName;

    // FQDN host or IP address of the provider. Type: Input.
    private String providerHost;

    // flag indicates whether or not to use SSL protocol. Type: Input.
    private Boolean useSSL;

    // management port number. Type: Input.
    private Integer portNumber;

    // management interface user. Type: Input.
    private String username;

    // management interface password. Type: Input.
    private String password;

    // provider Description. Type: Output (optional).
    private String description;

    // provider manufacturer. Type: Output (optional).
    private String manufacturer;

    // provider version. Type: Output (optional).
    private String providerVersion;

    // indicates if driver supports version of the provider. Type: Output.
    private boolean isSupportedVersion;

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderHost() {
        return providerHost;
    }

    public void setProviderHost(String providerHost) {
        this.providerHost = providerHost;
    }

    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }

    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getProviderVersion() {
        return providerVersion;
    }

    public void setProviderVersion(String providerVersion) {
        this.providerVersion = providerVersion;
    }

    public boolean isSupportedVersion() {
        return isSupportedVersion;
    }

    public void setIsSupportedVersion(boolean isSupportedVersion) {
        this.isSupportedVersion = isSupportedVersion;
    }
}
