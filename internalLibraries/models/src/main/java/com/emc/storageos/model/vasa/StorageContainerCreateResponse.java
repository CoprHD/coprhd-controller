package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name="storage_container")
public class StorageContainerCreateResponse extends VasaCommonRestResponse {
    
    //protocol endpoint type for storage container
    private String protocolEndPointType;
    
    private Long maxVvolSizeMB;
    
    private RelatedResourceRep storageSystem;

    @XmlElement(name = "protocolEndPointType")
    public String getProtocolEndPointType() {
        return protocolEndPointType;
    }

    public void setProtocolEndPointType(String protocolEndPointType) {
        this.protocolEndPointType = protocolEndPointType;
    }

    @XmlElement
    public Long getMaxVvolSizeMB() {
        return maxVvolSizeMB;
    }

    public void setMaxVvolSizeMB(Long maxVvolSizeMB) {
        this.maxVvolSizeMB = maxVvolSizeMB;
    }

    @XmlElement
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }


}
