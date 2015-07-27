/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.file.FileExportUpdateParams.ExportOperationErrorType;


@XmlRootElement
public class ExportRule implements Serializable {

	/**
	 * Class Name based Hashed version Id.
	 */
	private static final long serialVersionUID = -5928597260144763324L;
	// Part of payload Model Attributes
	private URI fsID;
	private URI snapShotID;
	private String exportPath;
	private String anon;
	private String secFlavor;
	private Set<String> readOnlyHosts;
	private Set<String> readWriteHosts;
	private Set<String> rootHosts;
    private String deviceExportId;
    private String mountPoint;
    private String comments;
    
	// Not a part of payload model attributes - for internal use only.
	private boolean isToProceed = false;
	private ExportOperationErrorType errorTypeIfNotToProceed;

	public boolean isToProceed() {
		return isToProceed;
	}

	public void setIsToProceed (boolean isToProceed, ExportOperationErrorType type) {
		this.isToProceed = isToProceed;
		errorTypeIfNotToProceed = type;
	}

	public ExportOperationErrorType getErrorTypeIfNotToProceed() {
		return errorTypeIfNotToProceed;
	}

	public URI getFsID() {
		return fsID;
	}

	public void setFsID(URI fsID) {
		this.fsID = fsID;
	}

	public URI getSnapShotID() {
		return snapShotID;
	}

	public void setSnapShotID(URI snapShotID) {
		this.snapShotID = snapShotID;
	}

	public String getExportPath() {
		return exportPath;
	}

	public void setExportPath(String exportPath) {
		this.exportPath = exportPath;
	}

	// public void setToProceed(boolean isToProceed) {
	// this.isToProceed = isToProceed;
	// }

	// public void setErrorTypeIfNotToProceed(
	// ExportOperationErrorType errorTypeIfNotToProceed) {
	// this.errorTypeIfNotToProceed = errorTypeIfNotToProceed;
	// }

	@XmlElementWrapper(name = "readOnlyHosts")
	@XmlElement(name = "endPoint")
	public Set<String> getReadOnlyHosts() {
		return readOnlyHosts;
	}

	public void setReadOnlyHosts(Set<String> readOnlyHosts) {
		this.readOnlyHosts = readOnlyHosts;
	}

	@XmlElementWrapper(name = "readWriteHosts")
	@XmlElement(name = "endPoint")
	public Set<String> getReadWriteHosts() {
		return readWriteHosts;
	}

	public void setReadWriteHosts(Set<String> readWriteHosts) {
		this.readWriteHosts = readWriteHosts;
	}

	@XmlElementWrapper(name = "rootHosts")
	@XmlElement(name = "endPoint")
	public Set<String> getRootHosts() {
		return rootHosts;
	}

	public void setRootHosts(Set<String> rootHosts) {
		this.rootHosts = rootHosts;
	}

	/**
	 * Security flavor of an export e.g. sys, krb, krbp or krbi
	 * 
	 * @valid none
	 */
	@XmlElement(name = "secFlavor", required = false)
	public String getSecFlavor() {
		return secFlavor;
	}

	public void setSecFlavor(String secFlavor) {
		this.secFlavor = secFlavor;
	}

	/**
	 * Anonymous root user mapping e.g. "root", "nobody" or "anyUserName"
	 * 
	 * @valid none
	 */
	@XmlElement(name = "anon", required = false)
	public String getAnon() {
		return anon;
	}

	public void setAnon(String anon) {
		this.anon = anon;
	}

	@XmlElement(name = "mountPoint", required = false)
	public String getMountPoint() {
		return mountPoint;
	}

	public void setMountPoint(String mountPoint) {
		this.mountPoint = mountPoint;
	}

    public String getDeviceExportId() {
            return this.deviceExportId;
    }

    public void setDeviceExportId(String deviceExportId){
            this.deviceExportId = deviceExportId;
    }
   
    
    @XmlElement(name="comments" , required = false)
    public String getComments() {
		return comments;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}


    @Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append("[secFlavor : ").append((secFlavor != null) ? secFlavor : "")
				.append("] ");
		sb.append("[exportPath : ").append((exportPath != null) ? exportPath : "")
		.append("] ");
		sb.append("[mountPoint : ").append((mountPoint != null) ? mountPoint : "")
		.append("] ");
		sb.append("[anon : ").append((anon != null) ? anon : "").append("] ");
		sb.append("[Number of readOnlyHosts : ")
				.append((readOnlyHosts != null) ? readOnlyHosts.size() : 0)
				.append("] ").append(getHostsPrintLog(readOnlyHosts));
		sb.append("[Number of readWriteHosts : ")
				.append((readWriteHosts != null) ? readWriteHosts.size() : 0)
				.append("] ").append(getHostsPrintLog(readWriteHosts));
		sb.append("[Number of rootHosts : ")
				.append((rootHosts != null) ? rootHosts.size() : 0)
				.append("] ").append(getHostsPrintLog(rootHosts));
        sb.append("[deviceExportId : ").append((deviceExportId != null) ? deviceExportId : "").append("] ");
		
		return sb.toString();
	}
    
	

	private String getHostsPrintLog(Set<String> hosts) {
		StringBuilder sb = new StringBuilder();
		if (hosts != null && hosts.size() > 0) {
			for (String endPoint : hosts) {
				sb.append("{").append(endPoint).append("}");
			}
		}
		return sb.toString();
	}

	/*private ExportRule(URI fsID, URI snapShotID, String exportPath, String anon,
			String secFlavor, Set<String> readOnlyHosts,
			Set<String> readWriteHosts, Set<String> rootHosts) {
		super();
		this.fsID = fsID;
		this.snapShotID = snapShotID;
		this.exportPath = exportPath;
		this.anon = anon;
		this.secFlavor = secFlavor;
		this.readOnlyHosts = readOnlyHosts;
		this.readWriteHosts = readWriteHosts;
		this.rootHosts = rootHosts;
	}*/

	// Empty constructor used for certain container purposes
	public ExportRule() {

	}
}
