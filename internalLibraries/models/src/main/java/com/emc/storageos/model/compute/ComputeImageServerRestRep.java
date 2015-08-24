/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "compute_imageserver")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ComputeImageServerRestRep extends DataObjectRestRep {

	private String imageServerAddress;

	private String osInstallAddress;

	private String bootDir;

	private List<RelatedResourceRep> computeImage;

	public ComputeImageServerRestRep() {

	}

	/**
	 * @return the imageServerAddress
	 */
	@XmlElement(name = "imageServerAddress")
	public String getImageServerAddress() {
		return imageServerAddress;
	}

	/**
	 * @param imageServerAddress
	 *            the imageServerAddress to set
	 */
	public void setImageServerAddress(String imageServerAddress) {
		this.imageServerAddress = imageServerAddress;
	}

	/**
	 * @return the osInstallAddress
	 */
	@XmlElement(name = "osInstallAddress")
	public String getOsInstallAddress() {
		return osInstallAddress;
	}

	/**
	 * @param osInstallAddress
	 *            the osInstallAddress to set
	 */
	public void setOsInstallAddress(String osInstallAddress) {
		this.osInstallAddress = osInstallAddress;
	}

	/**
	 * @return the bootDir
	 */
	@XmlElement(name = "bootDir")
	public String getBootDir() {
		return bootDir;
	}

	/**
	 * @param bootDir
	 *            the bootDir to set
	 */
	public void setBootDir(String bootDir) {
		this.bootDir = bootDir;
	}

	/**
	 * @return the computeImage
	 */
	@XmlElementWrapper(name = "compute_image")
	@XmlElement(name = "compute_image")
	public List<RelatedResourceRep> getComputeImage() {
		if (null == computeImage) {
			computeImage = new ArrayList<RelatedResourceRep>();
		}
		return computeImage;
	}

	/**
	 * @param computeImage
	 *            the computeImage to set
	 */
	public void setComputeImage(List<RelatedResourceRep> computeImage) {
		this.computeImage = computeImage;
	}

}
