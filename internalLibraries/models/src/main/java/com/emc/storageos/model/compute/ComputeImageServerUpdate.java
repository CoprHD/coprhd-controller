/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "compute_imageserver_update")
public class ComputeImageServerUpdate {
    private String name;
    private String imageServerIp;
    private String imageServerSecondIp;
    private String imageServerUser;
    private String imageServerPassword;
    private String tftpBootDir;
    private Integer osInstallTimeout;
    private Integer sshTimeout;
    private Integer imageImportTimeout;

    public ComputeImageServerUpdate() {

    }

    public ComputeImageServerUpdate(String name, String imageServerAddress,
            String imageServerSecondIp, String imageServerUser,
            String imageServerPassword, String tftpBootDir,
            Integer osInstallTimeout, Integer sshTimeout, Integer imageImportTimeout) {
        super();
        this.setName(name);
        this.imageServerIp = imageServerAddress;
        this.imageServerSecondIp = imageServerSecondIp;
        this.imageServerUser = imageServerUser;
        this.imageServerPassword = imageServerPassword;
        this.tftpBootDir = tftpBootDir;
        this.osInstallTimeout = osInstallTimeout;
        this.sshTimeout = sshTimeout;
        this.imageImportTimeout = imageImportTimeout;
    }

    /**
     * @return the imageServerIp
     */
    @XmlElement(name = "imageserver_ip")
    @JsonProperty("imageserver_ip")
    public String getImageServerIp() {
        return imageServerIp;
    }

    /**
     * @param imageServerIp
     *            the imageServerIp to set
     */
    public void setImageServerIp(String imageServerIp) {
        this.imageServerIp = imageServerIp;
    }

    /**
     * @return the imageServerSecondIp
     */
    @XmlElement(name = "imageserver_secondip")
    @JsonProperty("imageserver_secondip")
    public String getImageServerSecondIp() {
        return imageServerSecondIp;
    }

    /**
     * @param imageServerSecondIp
     *            the imageServerSecondIp to set
     */
    public void setImageServerSecondIp(String imageServerSecondIp) {
        this.imageServerSecondIp = imageServerSecondIp;
    }

    /**
     * @return the imageServerUser
     */
    @XmlElement(name = "imageserver_user")
    @JsonProperty("imageserver_user")
    public String getImageServerUser() {
        return imageServerUser;
    }

    /**
     * @param imageServerUser
     *            the imageServerUser to set
     */
    public void setImageServerUser(String imageServerUser) {
        this.imageServerUser = imageServerUser;
    }

    /**
     * @return the password
     */
    @XmlElement(name  = "imageserver_password")
    @JsonProperty("imageserver_password")
    public String getImageServerPassword() {
        return imageServerPassword;
    }

    /**
     * @param imageServerPassword
     *            the password to set
     */
    public void setImageServerPassword(String imageServerPassword) {
        this.imageServerPassword = imageServerPassword;
    }

    /**
     * @return the tftpBootDir
     */
    @XmlElement(name = "tftpBootDir")
    @JsonProperty("tftpBootDir")
    public String getTftpBootDir() {
        return tftpBootDir;
    }

    /**
     * @param tftpBootdir
     *            the tftpBootdir to set
     */
    public void setTftpBootDir(String tftpBootdir) {
        this.tftpBootDir = tftpBootdir;
    }

    /**
     * @return the osInstallTimeout
     */
    @XmlElement(name = "osinstall_timeout")
    @JsonProperty("osinstall_timeout")
    public Integer getOsInstallTimeout() {
        return osInstallTimeout;
    }

    /**
     * @param osInstallTimeout
     *            the osInstallTimeout to set
     */
    public void setOsInstallTimeout(Integer osInstallTimeout) {
        this.osInstallTimeout = osInstallTimeout;
    }

    /**
     * @return the name
     */
    @XmlElement
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the sshTimeout
     */
    @XmlElement(name = "ssh_timeout")
    @JsonProperty("ssh_timeout")
    public Integer getSshTimeout() {
        return sshTimeout;
    }

    /**
     * @param sshTimeout the sshTimeout to set
     */
    public void setSshTimeout(Integer sshTimeout) {
        this.sshTimeout = sshTimeout;
    }

    /**
     * @return the imageImportTimeout
     */
    @XmlElement(name = "imageimport_timeout")
    @JsonProperty("imageimport_timeout")
    public Integer getImageImportTimeout() {
        return imageImportTimeout;
    }

    /**
     * @param imageImportTimeout the imageImportTimeout to set
     */
    public void setImageImportTimeout(Integer imageImportTimeout) {
        this.imageImportTimeout = imageImportTimeout;
    }
}
