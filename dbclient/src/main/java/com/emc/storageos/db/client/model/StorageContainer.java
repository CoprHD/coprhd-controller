package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("StorageContainer")
public class StorageContainer extends DataObjectWithACLs {
    
    private String type;
    
    private StringSet virtualArrays;
    
    private String description;
    
    private URI storageSystem;
    
    private Long maxVvolSizeMB;
    
    private String protocolEndPointType;
    
    private String provisioningType;
    
    private StringSet protocols;
    
    private String systemType;
    
    private String protocolType;
    
    @Name("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        setChanged("type");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @IndexByKey
    @Name("virtualArrays")
    public StringSet getVirtualArrays() {
        return virtualArrays;
    }

    public void setVirtualArrays(StringSet virtualArrays) {
        this.virtualArrays = virtualArrays;
        setChanged("virtualArrays");
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
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

    @Name("maxVvolSizeMB")
    public Long getMaxVvolSizeMB() {
        return maxVvolSizeMB;
    }

    public void setMaxVvolSizeMB(Long maxVvolSizeMB) {
        this.maxVvolSizeMB = maxVvolSizeMB;
        setChanged("maxVvolSizeMB");
    }

    @Name("protocolEndPointType")
    public String getProtocolEndPointType() {
        return protocolEndPointType;
    }

    public void setProtocolEndPointType(String protocolEndPointType) {
        this.protocolEndPointType = protocolEndPointType;
        setChanged("protocolEndPointType");
    }

    @Name("provisioningType")
    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
        setChanged("provisioningType");
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return protocols;
    }

    public void setProtocols(StringSet protocols) {
        this.protocols = protocols;
        setChanged("protocols");
    }


    @Name("systemType")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
        setChanged("systemType");
    }

    
    @Name("protocolType")
    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
        setChanged("protocolType");
    }


    public static enum Type {
        physical, geo;
        private static final Type[] storageContainerTypes = values();
        
        public static Type lookup(final String name){
            for(Type value : storageContainerTypes){
                if(value.name().equals(name)){
                    return value;
                }
            }
            return null;
        }
    }
    
    public static enum ProvisioningType {
        NONE, Thin, Thick;
        public static ProvisioningType lookup(final String name) {
            for (ProvisioningType value : values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
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
    
    public static enum SystemType {
        NONE, isilon, vnxblock, vnxfile, vmax, netapp, netappc, hds, openstack, vnxe, scaleio, datadomain, xtremio, ibmxiv, ecs;
        private static final SystemType[] copyOfValues = values();

        public static SystemType lookup(final String name) {
            for (SystemType value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }
    
    public static enum ProtocolType {
        file, block;
        public static ProtocolType lookup(final String name) {
            for (ProtocolType value : values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }
    
}
