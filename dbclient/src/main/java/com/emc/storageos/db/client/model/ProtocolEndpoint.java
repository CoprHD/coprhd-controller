package com.emc.storageos.db.client.model;

import java.net.URI;


@Cf("ProtocolEndpoint")
public class ProtocolEndpoint extends DataObject{

    private String protocolEndpointType;
    
    private String lunId;
    
    private String ipAddress;
    
    private URI storageSystem;
    
    private String serverMount;
    
    private String transportIpAddress;
    
    private String authType;
    
    private String inBandCapability;
    
    private String serverScope;
    
    private String serverMajor;
    
    private String serverMinor;
    
    
    @Name("protocolEndpointType")
    public String getProtocolEndpointType() {
        return protocolEndpointType;
    }

    public void setProtocolEndpointType(String protocolEndpointType) {
        this.protocolEndpointType = protocolEndpointType;
        setChanged("protocolEndpointType");
    }

    @Name("lunId")
    public String getLunId() {
        return lunId;
    }

    public void setLunId(String lunId) {
        this.lunId = lunId;
        setChanged("lunId");
    }

    @Name("ipAddress")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    @Name("serverMount")
    public String getServerMount() {
        return serverMount;
    }

    public void setServerMount(String serverMount) {
        this.serverMount = serverMount;
        setChanged("serverMount");
    }

    @Name("transportIpAddress")
    public String getTransportIpAddress() {
        return transportIpAddress;
    }

    public void setTransportIpAddress(String transportIpAddress) {
        this.transportIpAddress = transportIpAddress;
        setChanged("transportIpAddress");
    }

    @Name("authType")
    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
        setChanged("authType");
    }

    @Name("inBandCapability")
    public String getInBandCapability() {
        return inBandCapability;
    }

    public void setInBandCapability(String inBandCapability) {
        this.inBandCapability = inBandCapability;
        setChanged("inBandCapability");
    }

    @Name("serverScope")
    public String getServerScope() {
        return serverScope;
    }

    public void setServerScope(String serverScope) {
        this.serverScope = serverScope;
        setChanged("serverScope");
    }

    @Name("serverMajor")
    public String getServerMajor() {
        return serverMajor;
    }

    public void setServerMajor(String serverMajor) {
        this.serverMajor = serverMajor;
        setChanged("serverMajor");
    }

    @Name("serverMinor")
    public String getServerMinor() {
        return serverMinor;
    }

    public void setServerMinor(String serverMinor) {
        this.serverMinor = serverMinor;
        setChanged("serverMinor");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @IndexByKey
    @Name("storageSystem")
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
        setChanged("storageSystem");
    }

    public static enum ProtocolEndpointTypeEnum{
        SCSI, NFS, NFS4x;
        private static final ProtocolEndpointTypeEnum protocolEndpointTypes[] = values();
        
        public static ProtocolEndpointTypeEnum lookup(final String type) {
            for (ProtocolEndpointTypeEnum protocolEndpointType : protocolEndpointTypes) {
                 if(protocolEndpointType.name().equals(type)){
                     return protocolEndpointType;
                 }
            }
            return null;
        }
    }
    
    public static enum ProtocolEndpointAuthEnum{
        System, Kerberos5;
        private static final ProtocolEndpointAuthEnum protocolEndpointAuthTypes[] = values();
        
        public static ProtocolEndpointAuthEnum lookup(final String type) {
            for (ProtocolEndpointAuthEnum protocolEndpointAuthType : protocolEndpointAuthTypes) {
                 if(protocolEndpointAuthType.name().equals(type)){
                     return protocolEndpointAuthType;
                 }
            }
            return null;
        }
    }
    
    public static enum InBandBindCapabilityEnum{
        LocalBind, GlobalBind;
        private static final InBandBindCapabilityEnum InBandBindCapabilityTypes[] = values();
        
        public static InBandBindCapabilityEnum lookup(final String type) {
            for (InBandBindCapabilityEnum InBandBindCapabilityType : InBandBindCapabilityTypes) {
                 if(InBandBindCapabilityType.name().equals(type)){
                     return InBandBindCapabilityType;
                 }
            }
            return null;
        }
    }
    
    
}
