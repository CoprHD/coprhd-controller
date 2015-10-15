package com.emc.storageos.storagedriver.model;

import com.emc.storageos.storagedriver.CapabilityInstance;

import java.util.List;

public class StorageSystem extends StorageObject {

    // system type, ex. isilon, netapp, etc. . Type: Input.
    private String systemType;

    // system name, ex: los123.lss.emc.com . Type: Input.
    private String systemName;

    // serial number.
    private String serialNumber;

    // device OS/firmware major version
    private String majorVersion;

    // device OS/firmware minor version
    private String minorVersion;

    // management port number. Type: Input.
    private Integer portNumber;

    // management interface user. Type: Input.
    private String username;

    // management interface password. Type: Input.
    private String password;

    // management interface IP address. Type: Input.
    private String ipAddress;

    private List<String> protocols;

    // Array Firmware Version
    private String firmwareVersion;

    // Indicates if driver supports firmware version of the system.
    private boolean isSupportedVersion;

    // Model Number of storage system
    private String model;


    // Supported provisioning type. Set by driver. Type: Output.
    private SupportedProvisioningType provisioningType;

    public static enum SupportedProvisioningType {
        THICK, THIN, THIN_AND_THICK
    }

    private List<CapabilityInstance> capabilities;

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(String majorVersion) {
        this.majorVersion = majorVersion;
    }

    public String getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(String minorVersion) {
        this.minorVersion = minorVersion;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public List<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(List<String> protocols) {
        this.protocols = protocols;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public boolean isSupportedVersion() {
        return isSupportedVersion;
    }

    public void setIsSupportedVersion(boolean isSupportedVersion) {
        this.isSupportedVersion = isSupportedVersion;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public SupportedProvisioningType getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(SupportedProvisioningType provisioningType) {
        this.provisioningType = provisioningType;
    }

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
