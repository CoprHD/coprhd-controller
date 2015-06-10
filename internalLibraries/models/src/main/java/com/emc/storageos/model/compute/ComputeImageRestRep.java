/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "compute_image")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ComputeImageRestRep extends DataObjectRestRep {
	
	private String imageName;
	private String imageUrl;
	private String imageType;
	private String computeImageStatus;
	private String lastImportStatusMessage;
	
	public ComputeImageRestRep() {}

	//TODO remove 2 methods
	@XmlElement(name = "image_id")
	public URI getImageId() {
		return null;
	}

	public void setImageId(URI imageId) {
	}

	@XmlElement(name = "image_name")
	public String getImageName() {
		return imageName;
	}

	public void setImageName(String imageName) {
		this.imageName = imageName;
	}

	@XmlElement(name = "image_url")
	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}	
	
	@XmlElement(name = "image_type")
    public String getImageType() {
		return imageType;
	}

	public void setImageType(String imageType) {
		this.imageType = imageType;
	}

	@XmlElement(name = "compute_image_status")
	public String getComputeImageStatus() {
		return computeImageStatus;
	}

	public void setComputeImageStatus(String computeImageStatus) {
		this.computeImageStatus = computeImageStatus;
	}

	@XmlElement(name = "last_import_status_message")
	public String getLastImportStatusMessage() {
		return lastImportStatusMessage;
	}

	public void setLastImportStatusMessage(String lastImportStatusMessage) {
		this.lastImportStatusMessage = lastImportStatusMessage;
	}

}
