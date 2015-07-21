/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.model.property.PropertyInfo;

public class ImageServerConf {

	private String imageServerIp;
	private String imageServerUser;
	private String imageServerPassword;
	private String tftpbootDir;
	private String imageDir;
	private String imageServerSecondIp;
	private String imageServerHttpPort;
	private Integer sshPort = 22;
	private Integer sshTimeoutMs = 10000;
	private Integer imageImportTimeoutMs = 1800000;
	private Integer osInstallTimeoutMs = 3600000;
	private Integer jobPollingIntervalMs = 60000;

	private boolean valid = false;

	private CoordinatorClient coordinator;
	private EncryptionProvider encryptionProvider;
	private static final Logger log = LoggerFactory.getLogger(ImageServerConf.class);

	public ImageServerConf() {
	}

	public void init() {
		PropertyInfo p = coordinator.getPropertyInfo();
		setImageServerIp(p.getProperty("image_server_address"));
		setImageServerUser(p.getProperty("image_server_username"));
		setTftpbootDir(p.getProperty("image_server_tftpboot_directory"));
		setImageServerSecondIp(p.getProperty("image_server_os_network_ip"));
		setImageServerHttpPort(p.getProperty("image_server_http_port"));
		setImageDir(p.getProperty("image_server_image_directory"));

		String encryptedPassword = p.getProperty("image_server_encpassword");
		try {
			setImageServerPassword(encryptionProvider.decrypt(Base64.decodeBase64(encryptedPassword)));
		} catch (Exception e) {
			log.warn("Can't decrypt image server password, it has to be re-saved");
			return;
		}

		// make sure all required fields are set
		if (!StringUtils.isBlank(imageServerIp) && !StringUtils.isBlank(imageServerUser)
				&& !StringUtils.isBlank(tftpbootDir) && !StringUtils.isBlank(imageServerSecondIp)
				&& !StringUtils.isBlank(imageServerHttpPort) && !StringUtils.isBlank(imageServerPassword)) {
			log.info("ImageServerConf appears valid");
			valid = true;
		}

		try {
			setSshPort(Integer.valueOf(p.getProperty("image_server_ssh_port")));
			setImageImportTimeoutMs(1000 * Integer.valueOf(p.getProperty("image_server_image_import_timeout")));
			setJobPollingIntervalMs(1000 * Integer.valueOf(p.getProperty("image_server_job_polling_interval")));
			setOsInstallTimeoutMs(1000 * Integer.valueOf(p.getProperty("image_server_os_install_timeout")));
			setSshTimeoutMs(1000 * Integer.valueOf(p.getProperty("image_server_ssh_timeout")));
		} catch (NumberFormatException e) {
			// ignoring this, the default values will be used
			log.warn("NumberFormatException when parsing image server values: " + e.getMessage());
		}
	}

	public void setCoordinator(CoordinatorClient coordinator) {
		this.coordinator = coordinator;
	}

	public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
		this.encryptionProvider = encryptionProvider;
	}

	@Override
	public String toString() {
		return String.format("ip: %s, user: %s, sshPort: %s, tftpboot: %s, imageDir: %s, second ip: %s, port: %s, "
				+ "sshTimeoutMs: %s, imageImportTimeoutMs: %s, osInstallTimeoutMs: %s, jobPollingIntervalMs: %s",
				imageServerIp, imageServerUser, sshPort, tftpbootDir, imageDir, imageServerSecondIp,
				imageServerHttpPort, sshTimeoutMs, imageImportTimeoutMs, osInstallTimeoutMs, jobPollingIntervalMs);
	}

	public String getImageServerIp() {
		return imageServerIp;
	}

	public void setImageServerIp(String imageServerIp) {
		this.imageServerIp = imageServerIp;
	}

	public String getImageServerUser() {
		return imageServerUser;
	}

	public void setImageServerUser(String imageServerUser) {
		this.imageServerUser = imageServerUser;
	}

	public String getImageServerPassword() {
		return imageServerPassword;
	}

	public void setImageServerPassword(String imageServerPassword) {
		this.imageServerPassword = imageServerPassword;
	}

	public String getTftpbootDir() {
		return tftpbootDir;
	}

	public void setTftpbootDir(String tftpbootDir) {
		String s = tftpbootDir.trim();
		if (!s.endsWith("/")) {
			this.tftpbootDir = s + "/";
		} else {
			this.tftpbootDir = s;
		}
	}

	public String getImageDir() {
		return imageDir;
	}

	public void setImageDir(String imageDir) {
		String s = imageDir.trim();
		if (s.length() > 0 && !s.endsWith("/")) {
			this.imageDir = s + "/";
		} else {
			this.imageDir = s;
		}
	}

	public String getImageServerSecondIp() {
		return imageServerSecondIp;
	}

	public void setImageServerSecondIp(String imageServerSecondIp) {
		this.imageServerSecondIp = imageServerSecondIp;
	}

	public String getImageServerHttpPort() {
		return imageServerHttpPort;
	}

	public void setImageServerHttpPort(String imageServerHttpPort) {
		this.imageServerHttpPort = imageServerHttpPort;
	}

	public Integer getSshTimeoutMs() {
		return sshTimeoutMs;
	}

	public void setSshTimeoutMs(Integer sshTimeoutMs) {
		this.sshTimeoutMs = sshTimeoutMs;
	}

	public Integer getImageImportTimeoutMs() {
		return imageImportTimeoutMs;
	}

	public void setImageImportTimeoutMs(Integer imageImportTimeoutMs) {
		this.imageImportTimeoutMs = imageImportTimeoutMs;
	}

	public Integer getOsInstallTimeoutMs() {
		return osInstallTimeoutMs;
	}

	public void setOsInstallTimeoutMs(Integer osInstallTimeoutMs) {
		this.osInstallTimeoutMs = osInstallTimeoutMs;
	}

	public Integer getJobPollingIntervalMs() {
		return jobPollingIntervalMs;
	}

	public void setJobPollingIntervalMs(Integer jobPollingIntervalMs) {
		this.jobPollingIntervalMs = jobPollingIntervalMs;
	}

	public Integer getSshPort() {
		return sshPort;
	}

	public void setSshPort(Integer sshPort) {
		this.sshPort = sshPort;
	}

	public boolean isValid() {
		return valid;
	}
}
