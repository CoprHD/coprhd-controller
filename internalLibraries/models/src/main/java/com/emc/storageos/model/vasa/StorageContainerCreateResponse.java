package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name="storage_container")
public class StorageContainerCreateResponse extends VasaCommonRestResponse {
    
    //protocol endpoint type for storage container
    private String protocolEndPointType;
    
    private String type;
    
    private List<RelatedResourceRep> varrays;
    
    private List<RelatedResourceRep> vpools;
    
    private Long maxVvolSizeMB;
    
    private RelatedResourceRep storageSystem;

    @XmlElement
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
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
    
    @XmlElementWrapper(name = "varrays")
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public List<RelatedResourceRep> getVirtualArrays() {
        if (varrays == null) {
            return varrays = new ArrayList<RelatedResourceRep>();
        }
        return varrays;
    }

    public void setVirtualArrays(List<RelatedResourceRep> varrays) {
        this.varrays = varrays;
    }

    @XmlElementWrapper(name = "vpools")
    @XmlElement(name = "vpool")
    @JsonProperty("vpool")
    public List<RelatedResourceRep> getVpools() {
        if(vpools == null){
            return vpools = new ArrayList<RelatedResourceRep>();
        }
        return vpools;
    }

    public void setVpools(List<RelatedResourceRep> vpools) {
        this.vpools = vpools;
    }


}
