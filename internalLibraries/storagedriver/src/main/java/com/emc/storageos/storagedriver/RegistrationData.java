/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;

import java.util.List;
import java.util.Set;

/**
 * Driver registration data
 */
public class RegistrationData {

    public static enum SupportedStorageType {
        BLOCK, FILE, BLOCK_AND_FILE, OBJECT
    }

    // Driver name (fully qualified name of driver class)
    private String driverClassName;

    // Storage system type managed by driver
    private String storageSystemType;

    // Storage type managed by driver
    private SupportedStorageType storageType = SupportedStorageType.BLOCK;

    // Display name for storage system type managed by driver
    private String storageSystemTypeDisplayName;

    // Indicates if storage systems managed by storage provider (true) or directly (false)
    private boolean isManagedByProvider = false;

    // Indicates if storage systems managed by driver can support SSL protocol
    private boolean supportsSsl = false;

    // Management port number for storage system or provider
    private String portNumber;

    // When supports SSL, this is management port number for SSL connections
    private String sslPortNumber;

    // Version of the driver
    private String driverVersion;

    // List of SDK version numbers for which driver is run-time compatible
    private List<String> supportedSdkVersions;

    // Metadata for capabilities provided by storage systems managed by driver
    private Set<CapabilityDefinition> capabilityDefinitions;


    public String getStorageSystemType() {
        return storageSystemType;
    }

    public Set<CapabilityDefinition> getCapabilityDefinitions() {
        return capabilityDefinitions;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }

    public SupportedStorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(SupportedStorageType storageType) {
        this.storageType = storageType;
    }

    public String getStorageSystemTypeDisplayName() {
        return storageSystemTypeDisplayName;
    }

    public void setStorageSystemTypeDisplayName(String storageSystemTypeDisplayName) {
        this.storageSystemTypeDisplayName = storageSystemTypeDisplayName;
    }

    public boolean isManagedByProvider() {
        return isManagedByProvider;
    }

    public void setIsManagedByProvider(boolean isManagedByProvider) {
        this.isManagedByProvider = isManagedByProvider;
    }

    public boolean isSupportsSsl() {
        return supportsSsl;
    }

    public void setSupportsSsl(boolean supportsSsl) {
        this.supportsSsl = supportsSsl;
    }

    public String getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(String portNumber) {
        this.portNumber = portNumber;
    }

    public String getSslPortNumber() {
        return sslPortNumber;
    }

    public void setSslPortNumber(String sslPortNumber) {
        this.sslPortNumber = sslPortNumber;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
    }

    public List<String> getSupportedSdkVersions() {
        return supportedSdkVersions;
    }

    public void setSupportedSdkVersions(List<String> supportedSdkVersions) {
        this.supportedSdkVersions = supportedSdkVersions;
    }

    public void setCapabilityDefinitions(Set<CapabilityDefinition> capabilityDefinitions) {
        this.capabilityDefinitions = capabilityDefinitions;
    }
}
