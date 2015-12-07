package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="protocol_endpoint")
public class ProtocolEndpointResponseParam extends VasaCommonRestResponse {

    private String protocolEndpointType;
    
    private String lunId;
    
    private String ipAddress;
    
    private String storageSystem;
    
    private String serverMount;
    
    private String transportIpAddress;
    
    private String authType;
    
    private String inBandCapability;
    
    private String serverScope;
    
    private String serverMajor;
    
    private String serverMinor;
    
    public String getProtocolEndpointType() {
        return protocolEndpointType;
    }

    public void setProtocolEndpointType(String protocolEndpointType) {
        this.protocolEndpointType = protocolEndpointType;
    }

    public String getLunId() {
        return lunId;
    }

    public void setLunId(String lunId) {
        this.lunId = lunId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(String storageSystem) {
        this.storageSystem = storageSystem;
    }

    public String getServerMount() {
        return serverMount;
    }

    public void setServerMount(String serverMount) {
        this.serverMount = serverMount;
    }

    public String getTransportIpAddress() {
        return transportIpAddress;
    }

    public void setTransportIpAddress(String transportIpAddress) {
        this.transportIpAddress = transportIpAddress;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getInBandCapability() {
        return inBandCapability;
    }

    public void setInBandCapability(String inBandCapability) {
        this.inBandCapability = inBandCapability;
    }

    public String getServerScope() {
        return serverScope;
    }

    public void setServerScope(String serverScope) {
        this.serverScope = serverScope;
    }

    public String getServerMajor() {
        return serverMajor;
    }

    public void setServerMajor(String serverMajor) {
        this.serverMajor = serverMajor;
    }

    public String getServerMinor() {
        return serverMinor;
    }

    public void setServerMinor(String serverMinor) {
        this.serverMinor = serverMinor;
    }


}
