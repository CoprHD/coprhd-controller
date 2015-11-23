package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("StorageContainer")
public class StorageContainer extends DataObjectWithACLs {
    
    private String type;
    
    private StringSet virtualArrays;
    
    private String description;
    
    private URI storageSystem;
    
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
    
}
