package com.emc.storageos.model.rdfgroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;

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
     * Supported copy mode. 
     * Valid values: 
     * SYNCHRONOUS
     * ASYNCHRONOUS
     * UNKNOWN
     * ADAPTIVECOPY
     * ALL
     *
     */
	@XmlElement(name = "supported_copy_mode")
	public String getSupportedCopyMode() {
		return supportedCopyMode;
	}

	public void setSupportedCopyMode(String supportedCopyMode) {
		this.supportedCopyMode = supportedCopyMode;
	}
	
	/**
     * The connectivity status. 
     * Valid values: 
     *  UP
     *  DOWN
     *  PARTITIONED
     *  UNKNOWN
     *
     */
    @XmlElement(name = "connectivity_status")
	public String getConnectivityStatus() {
		return connectivityStatus;
	}

	public void setConnectivityStatus(String connectivityStatus) {
		this.connectivityStatus = connectivityStatus;
	}
	
	/**
     * The Copy state. 
     * Valid values:
     *  CONSISTENT
     *  IN_CONSISTENT
     *
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

    /**
     * Given a single RDF Group REST object, create a single String for the drop-down list
     * or display field that represents the key information the user needs to know
     * 
     * @return String
     */
    public String forDisplay(Logger log) {
        StringBuffer sb = new StringBuffer();
        final String token = "+";
        
        // Example:
        // VMAX 1612 -> 5321 : G#-199 : BillRAGroup [5 Vols, SYNC/ASYNC/ANYMODE, Status: UP]
        // 
        // Format of NativeGUID
        //     1           2          3            4        5       6        7
        // SYMMETRIX+000196701343+REMOTEGROUP+000196701343+190+000196701405+190
        //                                           [1343|190]       [1405]
        StringTokenizer st = new StringTokenizer(getNativeGuid(), token);
        sb.append("VMAX ");
        try {
            st.nextToken(); // 1
            st.nextToken(); // 2
            st.nextToken(); // 3

            String srcSerial = st.nextToken(); // 4
            sb.append(srcSerial.substring(Math.max(0, srcSerial.length() - 4))); // 4

            sb.append(" -> ");
            st.nextToken(); // 5
            
            String tgtSerial = st.nextToken(); // 6
            sb.append(tgtSerial.substring(Math.max(0, tgtSerial.length() - 4))); // 6
            
            sb.append(": G#-" + getSourceGroupId());
            sb.append(": " + getName());
            // Using pipes "|" instead of commas because the UI order page treats the commas as newlines
            sb.append(String.format(" [%d Vols | ", (getVolumes() != null) ? getVolumes().size() : 0));
            
            // "ALL" doesn't mean anything to the end user, change it to ANYMODE
            if (getSupportedCopyMode().equalsIgnoreCase("ALL")) {
                sb.append("ANYMODE");
            } else if (getSupportedCopyMode().equalsIgnoreCase("SYNCHRONOUS")) {
                sb.append("SYNC"); // Brief versions of the word, since space is at a premium
            } else if (getSupportedCopyMode().equalsIgnoreCase("ASYNCHRONOUS")) {
                sb.append("ASYNC");
            } else {
                sb.append(getSupportedCopyMode());
            }
            
            sb.append(" | Status: " + getConnectivityStatus() + "]");
            
        } catch (Exception e) {
            // Native GUID is missing some fields, or the format changed.  Log and swallow.
            if (log != null) { 
                log.error("Missing native GUID fields not in format: SYMMETRIX+000196701343+REMOTEGROUP+000196701343+190+000196701405+190");
                if (getNativeGuid() != null) {
                    log.error("Native GUID for RDF Group: " + getNativeGuid());                    
                }
            }
            sb = new StringBuffer();
            sb.append(this.getName());
        }
        return sb.toString();
        
    }
}
