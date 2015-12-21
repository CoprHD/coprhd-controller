package com.emc.storageos.model.vasa;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "storage_container_create")
public class StorageContainerRequestParam extends VasaCommonRestRequest{
	
    //protocol endpoint type for storage container
	private String protocolEndPointType;
	
	private Long maxVvolSizeMB;
	
	private String storageSystem;
	
	private Set<String> varrays;
	
	private Set<String> vPools;
	
	private Set<String> physicalStorageContainers;
	
	private String type;
    
	@XmlElement(name = "protocolEndPointType")
	public String getProtocolEndPointType() {
		return protocolEndPointType;
	}

	public void setProtocolEndPointType(String protocolEndPointType) {
		this.protocolEndPointType = protocolEndPointType;
	}

	@XmlElement(name="maxVvolSizeMB")
    public Long getMaxVvolSizeMB() {
        return maxVvolSizeMB;
    }

    public void setMaxVvolSizeMB(Long maxVvolSizeMB) {
        this.maxVvolSizeMB = maxVvolSizeMB;
    }
    
    @XmlElement(name="storageSystem")
    public String getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(String storageSystem) {
        this.storageSystem = storageSystem;
    }
    
    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays for the Storage Container
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public Set<String> getVarrays() {
        return varrays;
    }

    public void setVarrays(Set<String> varrays) {
        this.varrays = varrays;
    }

    @XmlElementWrapper(name = "vpools")
    /**
     * The virtual pools for the Storage Container
     * 
     * @valid none
     */
    @XmlElement(name = "vpool")
    @JsonProperty("vpool")
    public Set<String> getvPools() {
        return vPools;
    }

    public void setvPools(Set<String> vPools) {
        this.vPools = vPools;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElementWrapper(name = "physical_storagecontainers")
    /**
     * The virtual pools for the Storage Container
     * 
     * @valid none
     */
    @XmlElement(name = "physical_storagecontainer")
    @JsonProperty("physical_storagecontainer")
    public Set<String> getPhysicalStorageContainers() {
        return physicalStorageContainers;
    }

    public void setPhysicalStorageContainers(Set<String> physicalStorageContainers) {
        this.physicalStorageContainers = physicalStorageContainers;
    }

	
	
   
	}
