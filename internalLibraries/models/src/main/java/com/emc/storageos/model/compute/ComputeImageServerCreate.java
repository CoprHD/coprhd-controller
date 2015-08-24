/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import javax.xml.bind.annotation.XmlElement;

public class ComputeImageServerCreate {

	private String imageServerAddress;

	private String osInstallAddress;

	private String username;

	private String password;

	private String bootDir;

	private long installTimeout;

	public ComputeImageServerCreate() {

	}

	public ComputeImageServerCreate(String imageServerAddress,
			String osInstallAddress, String username, String password,
			String bootDir, long installTimeout) {
		super();
		this.imageServerAddress = imageServerAddress;
		this.osInstallAddress = osInstallAddress;
		this.username = username;
		this.password = password;
		this.bootDir = bootDir;
		this.installTimeout = installTimeout;
	}

	/**
	 * @return the imageServerAddress
	 */
	@XmlElement(required = true, name = "imageServerAddress")
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
	@XmlElement(required = true, name = "osInstallAddress")
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
	 * @return the username
	 */
	@XmlElement(required = true, name = "username")
	public String getUsername() {
		return username;
	}

	/**
	 * @param username
	 *            the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * @return the password
	 */
	@XmlElement(required = true)
	public String getPassword() {
		return password;
	}

	/**
	 * @param password
	 *            the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @return the bootDir
	 */
	@XmlElement(required = true, name = "bootDir")
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
	 * @return the installTimeout
	 */
	@XmlElement(required = true, name = "installTimeout")
	public long getInstallTimeout() {
		return installTimeout;
	}

	/**
	 * @param installTimeout
	 *            the installTimeout to set
	 */
	public void setInstallTimeout(long installTimeout) {
		this.installTimeout = installTimeout;
	}

}
