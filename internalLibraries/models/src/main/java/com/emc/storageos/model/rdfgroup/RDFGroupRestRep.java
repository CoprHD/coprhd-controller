package com.emc.storageos.model.rdfgroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;

@XmlRootElement(name = "rdf_group")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class RDFGroupRestRep extends DiscoveredDataObjectRestRep{
	
	private String sourceGroupId;
    
    private String remoteGroupId;
    
    private URI sourcePort;
    
    private URI remotePort;
    
    private List<URI> volumes;
    
    private Boolean active;
    
    private String supportedCopyMode;
    
    private String connectivityStatus;
    
    private String copyState;
    
    private Boolean supported = true;
       
    private String sourceReplicationGroupName;
    
    private String targetReplicationGroupName;
    
    private URI sourceStorageSystemUri;
    
    private URI remoteStorageSystemUri;
    
    /**
     * The source group id
     *
     * @valid none
     */
    @XmlElement(name = "source_group_id")
	public String getSourceGroupId() {
		return sourceGroupId;
	}

	public void setSourceGroupId(String sourceGroupId) {
		this.sourceGroupId = sourceGroupId;
	}
	/**
     * The remote group id
     *
     * @valid none
     */
    @XmlElement(name = "remote_group_id")
	public String getRemoteGroupId() {
		return remoteGroupId;
	}

	public void setRemoteGroupId(String remoteGroupId) {
		this.remoteGroupId = remoteGroupId;
	}
	/**
	 * Source port URI
	 */
	@XmlElement(name = "source_port")
	public URI getSourcePort() {
		return sourcePort;
	}

	public void setSourcePort(URI sourcePort) {
		this.sourcePort = sourcePort;
	}
	/**
	 * Remote port URI
	 */
	@XmlElement(name = "remote_port")
	public URI getRemotePort() {
		return remotePort;
	}

	public void setRemotePort(URI remotePort) {
		this.remotePort = remotePort;
	}
	
	@XmlElementWrapper(name = "volumes")
    /**
     * The list of optional parameters
     * 
     * @valid none
     */
    @XmlElement(name = "volume")
	public List<URI> getVolumes() {
		if(volumes == null){
			volumes = new ArrayList<>();
		}
		return volumes;
	}

	public void setVolumes(List<URI> volumes) {
		this.volumes = volumes;
	}
	/**
	 * active
	 */
	@XmlElement(name = "active")
	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}
	
	
	/**
     * Supported copy mode
     *
     * @valid SYNCHRONOUS 
     * @valid ASYNCHRONOUS 
     * @valid UNKNOWN
     * @valid ADAPTIVECOPY
     * @valid ALL
     */
	@XmlElement(name = "supported_copy_mode")
	public String getSupportedCopyMode() {
		return supportedCopyMode;
	}

	public void setSupportedCopyMode(String supportedCopyMode) {
		this.supportedCopyMode = supportedCopyMode;
	}
	
	/**
     * The connectivity status
     *
     * @valid UP 
     * @valid DOWN 
     * @valid PARTITIONED
     * @valid UNKNOWN
     */
    @XmlElement(name = "connectivity_status")
	public String getConnectivityStatus() {
		return connectivityStatus;
	}

	public void setConnectivityStatus(String connectivityStatus) {
		this.connectivityStatus = connectivityStatus;
	}
	
	/**
     * The Copy state
     *
     * @valid CONSISTENT 
     * @valid IN_CONSISTENT 
     */
    @XmlElement(name = "copy_state")
	public String getCopyState() {
		return copyState;
	}

	public void setCopyState(String copyState) {
		this.copyState = copyState;
	}
	@XmlElement(name = "supported")
	public Boolean getSupported() {
		return supported;
	}

	public void setSupported(Boolean supported) {
		this.supported = supported;
	}
	@XmlElement(name = "source_replication_group_name")
	public String getSourceReplicationGroupName() {
		return sourceReplicationGroupName;
	}

	public void setSourceReplicationGroupName(String sourceReplicationGroupName) {
		this.sourceReplicationGroupName = sourceReplicationGroupName;
	}
	@XmlElement(name = "target_replication_group_name")
	public String getTargetReplicationGroupName() {
		return targetReplicationGroupName;
	}

	public void setTargetReplicationGroupName(String targetReplicationGroupName) {
		this.targetReplicationGroupName = targetReplicationGroupName;
	}
	@XmlElement(name = "source_system_uri")
	public URI getSourceStorageSystemUri() {
		return sourceStorageSystemUri;
	}

	public void setSourceStorageSystemUri(URI sourceStorageSystemUri) {
		this.sourceStorageSystemUri = sourceStorageSystemUri;
	}
	@XmlElement(name = "remote_system_uri")
	public URI getRemoteStorageSystemUri() {
		return remoteStorageSystemUri;
	}

	public void setRemoteStorageSystemUri(URI remoteStorageSystemUri) {
		this.remoteStorageSystemUri = remoteStorageSystemUri;
	} 

}
