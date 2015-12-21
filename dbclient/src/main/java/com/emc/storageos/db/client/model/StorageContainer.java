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
    
    private StringSet virtualPools;
    
    private StringSet physicalStorageContainers;
    
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

    @RelationIndex(cf = "RelationIndex", type = VirtualPool.class)
    @IndexByKey
    @Name("virtualPools")
    public StringSet getVirtualPools() {
        return virtualPools;
    }

    public void setVirtualPools(StringSet virtualPools) {
        this.virtualPools = virtualPools;
        setChanged("virtualPools");
    }


    @RelationIndex(cf = "RelationIndex", type = StorageContainer.class)
    @IndexByKey
    @Name("physicalStorageContainers")
    public StringSet getPhysicalStorageContainers() {
        return physicalStorageContainers;
    }

    public void setPhysicalStorageContainers(StringSet physicalStorageContainers) {
        this.physicalStorageContainers = physicalStorageContainers;
        setChanged("physicalStorageContainers");
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

    
}
