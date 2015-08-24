/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Model/ColumnFamily to represent ComputeImageServer
 * 
 * @author kumara4
 *
 */
@Cf("ComputeImageServer")
public class ComputeImageServer extends DataObject {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String imageServerAddress;

	private String osInstallAddress;

	private String username;

	private String password;

	private String bootDir;

	private long installTimeout;

	private StringSet computeImage;

	/**
	 * @return the osInstallAddress
	 */
	@Name("osInstallAddress")
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
	@Name("username")
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
	@Name("password")
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
	@Name("bootDir")
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
	@Name("installTimeout")
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

	/**
	 * @return the imageServerAddress
	 */
	@Name("imageServerAddress")
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
	 * @return the computeImage
	 */
	@RelationIndex(cf = "RelationIndex", type = ComputeImage.class)
	@Name("computeImage")
	public StringSet getComputeImage() {
		return computeImage;
	}

	/**
	 * @param computeImage
	 *            the computeImageUri to set
	 */
	public void setComputeImage(StringSet computeImageUri) {
		this.computeImage = computeImageUri;
	}

}
