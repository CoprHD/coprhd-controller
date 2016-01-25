/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ComputeImageServerMigration;
import com.emc.storageos.model.valid.EnumType;
/**
 * Model/ColumnFamily to represent ComputeImageServer
 *
 * @author kumara4
 *
 */
@Cf("ComputeImageServer")
public class ComputeImageServer extends DataObject {
	public static enum ComputeImageServerStatus {
        AVAILABLE, NOT_AVAILABLE
    }
	
    private String imageServerIp;
    private String imageServerUser;
    private String imageServerPassword;
    private String tftpBootDir;
    private String imageDir;
    private String imageServerSecondIp;
    private String imageServerHttpPort="44491";
    private Integer sshPort = 22;
    private Integer sshTimeoutMs = 20000;
    private Integer imageImportTimeoutMs = 1800000;
    private Integer osInstallTimeoutMs = 3600000;
    private Integer jobPollingIntervalMs = 60000;
    private StringSet computeImages;
    private StringSet failedComputeImages;
    private String computeImageServerStatus = ComputeImageServerStatus.NOT_AVAILABLE.name();

    
	@CustomMigrationCallback(callback = ComputeImageServerMigration.class)
    @Name("imageServerIp")
    public String getImageServerIp() {
        return imageServerIp;
    }

    public void setImageServerIp(String imageServerIp) {
        this.imageServerIp = imageServerIp;
        setChanged("imageServerIp");
    }

    @Name("imageServerUser")
    public String getImageServerUser() {
        return imageServerUser;
    }

    public void setImageServerUser(String imageServerUser) {
        this.imageServerUser = imageServerUser;
        setChanged("imageServerUser");
    }

    @Encrypt
    @Name("imageServerPassword")
    public String getImageServerPassword() {
        return imageServerPassword;
    }

    public void setImageServerPassword(String imageServerPassword) {
        this.imageServerPassword = imageServerPassword;
        setChanged("imageServerPassword");

    }

    @Name("tftpBootDir")
    public String getTftpBootDir() {
        return tftpBootDir;
    }

    public void setTftpBootDir(String tftpBootDir) {
        String s = tftpBootDir.trim();
        if (!s.endsWith("/")) {
            this.tftpBootDir = s + "/";
        } else {
            this.tftpBootDir = s;
        }
        setChanged("tftpBootDir");
    }

    @Name("imageDir")
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
        setChanged("imageDir");
    }

    @Name("imageServerSecondIp")
    public String getImageServerSecondIp() {
        return imageServerSecondIp;
    }

    public void setImageServerSecondIp(String imageServerSecondIp) {
        this.imageServerSecondIp = imageServerSecondIp;
        setChanged("imageServerSecondIp");
    }

    @Name("imageServerHttpPort")
    public String getImageServerHttpPort() {
        return imageServerHttpPort;
    }

    public void setImageServerHttpPort(String imageServerHttpPort) {
        this.imageServerHttpPort = imageServerHttpPort;
        setChanged("imageServerHttpPort");
    }

    @Name("sshTimeoutMs")
    public Integer getSshTimeoutMs() {
        return sshTimeoutMs;
    }

    public void setSshTimeoutMs(Integer sshTimeoutMs) {
        this.sshTimeoutMs = sshTimeoutMs;
        setChanged("sshTimeoutMs");
    }

    @Name("imageImportTimeoutMs")
    public Integer getImageImportTimeoutMs() {
        return imageImportTimeoutMs;
    }

    public void setImageImportTimeoutMs(Integer imageImportTimeoutMs) {
        this.imageImportTimeoutMs = imageImportTimeoutMs;
        setChanged("imageImportTimeoutMs");
    }

    @Name("osInstallTimeoutMs")
    public Integer getOsInstallTimeoutMs() {
        return osInstallTimeoutMs;
    }

    public void setOsInstallTimeoutMs(Integer osInstallTimeoutMs) {
        this.osInstallTimeoutMs = osInstallTimeoutMs;
        setChanged("osInstallTimeoutMs");
    }

    @Name("jobPollingIntervalMs")
    public Integer getJobPollingIntervalMs() {
        return jobPollingIntervalMs;
    }

    public void setJobPollingIntervalMs(Integer jobPollingIntervalMs) {
        this.jobPollingIntervalMs = jobPollingIntervalMs;
        setChanged("jobPollingIntervalMs");
    }

    @Name("sshPort")
    public Integer getSshPort() {
        return sshPort;
    }

    public void setSshPort(Integer sshPort) {
        this.sshPort = sshPort;
        setChanged("sshPort");
    }

    /**
     * @return the computeImage
     */
    @RelationIndex(cf = "RelationIndex", type = ComputeImage.class)
    @IndexByKey
    @Name("computeImages")
    public StringSet getComputeImages() {
        return computeImages;
    }

    /**
     * @param computeImages
     *            the computeImages to set
     */
    public void setComputeImages(StringSet computeImages) {
        this.computeImages = computeImages;
        setChanged("computeImages");
    }

    @EnumType(ComputeImageServerStatus.class)
    @Name("computeImageServerStatus")
    public String getComputeImageServerStatus() {
        return computeImageServerStatus;
    }

    public void setComputeImageServerStatus(String computeImageServerStatus) {
        this.computeImageServerStatus = computeImageServerStatus;
        setChanged("computeImageServerStatus");
    }

    /**
     * @return the failedComputeImages
     */
    @Name("failedComputeImages")
    public StringSet getFailedComputeImages() {
        return failedComputeImages;
    }

    /**
     * @param failedComputeImages the failedComputeImages to set
     */
    public void setFailedComputeImages(StringSet failedComputeImages) {
        this.failedComputeImages = failedComputeImages;
    }
}
