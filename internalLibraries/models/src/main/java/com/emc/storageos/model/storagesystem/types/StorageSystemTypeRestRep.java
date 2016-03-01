package com.emc.storageos.model.storagesystem.types;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storagesystem_types")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StorageSystemTypeRestRep extends DataObjectRestRep {

    private String storageSystemTypeName;
    private String storageType;
    private boolean isSmiProvider = false;
    
    private List<NamedRelatedResourceRep> availableImageServers = new ArrayList<NamedRelatedResourceRep>();

    public StorageSystemTypeRestRep() {
    }

    // TODO remove 2 methods
    @XmlElement(name = "storageSystemType_id")
    public URI getStorageSystemTypeId() {
        return null;
    }

    public void setImageId(URI imageId) {
    }

    @XmlElement(name = "storagesystemtype_name")
    public String getStorageSystemTypeName() {
        return storageSystemTypeName;
    }

    public void setStorageSystemTypeName(String storageSystemTypeName) {
        this.storageSystemTypeName = storageSystemTypeName;
    }

    @XmlElement(name = "storage_type")
    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    @XmlElement(name = "isSmiProvider")
    public boolean getIsSmiProvider() {
        return isSmiProvider;
    }

    public void setIsSmiProvider(boolean isSmiProvider) {
        this.isSmiProvider = isSmiProvider;
    }

     @XmlElementWrapper(name = "available_image_servers", nillable = true, required = false)
    @XmlElement(name = "available_image_server")
    public List<NamedRelatedResourceRep> getAvailableImageServers() {
        return availableImageServers;
    }

    public void setAvailableImageServers(
            List<NamedRelatedResourceRep> availableImageServers) {
        this.availableImageServers = availableImageServers;
    }

}
