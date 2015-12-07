package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_container_create")
public class StorageContainerRequestParam extends VasaCommonRestRequest{
	
    //protocol endpoint type for storage container
	private String protocolEndPointType;
	
	private Long maxVvolSizeMB;
	
	private String storageSystem;
    private String systemType;

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
