package com.emc.storageos.model.vasa;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "storage_container_create")
public class StorageContainerRequestParam {
	
    private String name;
    private String description;
	
    //protocol endpoint type for storage container
	private String protocolEndPointType;
	
	private Long maxVvolSizeMB;
	
	private String storageSystem;
	
	
    private Set<String> protocols;
    private Set<String> varrays;

    private String provisionType;
    private String systemType;

    /**
     * The name for the Storage Container.
     * 
     * @valid none
     */
    @XmlElement(required = false)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The description for the Storage Container.
     * 
     * @valid none
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "protocol", required = true)
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays for the Storage Container
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public Set<String> getVarrays() {
        return varrays;
    }

    public void setVarrays(Set<String> varrays) {
        this.varrays = varrays;
    }

    /**
     * The provisioning type for the Storage Container
     * 
     * @valid NONE
     * @valid Thin
     * @valid Thick
     */
    @XmlElement(name = "provisioning_type", required = true)
    public String getProvisionType() {
        return provisionType;
    }

    public void setProvisionType(String provisionType) {
        this.provisionType = provisionType;
    }

    /**
     * The supported system type for the Storage Container
     * 
     * @valid NONE
     * @valid vnxblock (Block)
     * @valid vmax (Block)
     * @valid openstack (Block)
     * @valid vnxfile (File)
     * @valid isilon (File)
     * @valid netapp (File)
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }
    
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
	
	
   
	}
