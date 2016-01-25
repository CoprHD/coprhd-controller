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

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "compute_imageserver")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ComputeImageServerRestRep extends DataObjectRestRep {
    private String imageServerIp;
    private String imageServerSecondIp;
    private String tftpBootDir;
    private List<NamedRelatedResourceRep> computeImages;
    private String computeImageServerStatus;
    private Integer osInstallTimeout;
    private String imageServerUser;
    private List<NamedRelatedResourceRep> failedImages = new ArrayList<NamedRelatedResourceRep>();
    private Integer sshTimeout;
    private Integer imageImportTimeout;

    public ComputeImageServerRestRep() {
    }

    /**
     * @return the imageServerIp
     */
    @XmlElement(name = "imageserver_ip")
    @JsonProperty("imageserverip")
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
    @JsonProperty("imageserversecondip")
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
     * @return the computeImage
     */
    @XmlElementWrapper(name = "compute_images")
    @XmlElement(name = "compute_image")
    @JsonProperty("compute_images")
    public List<NamedRelatedResourceRep> getComputeImages() {
        if (null == computeImages) {
            computeImages = new ArrayList<NamedRelatedResourceRep>();
        }
        return computeImages;
    }

    /**
     * @param computeImages
     *            the computeImages to set
     */
    public void setComputeImages(List<NamedRelatedResourceRep> computeImages) {
        this.computeImages = computeImages;
    }

    /**
     * @return the tftpbootDir
     */
    @XmlElement(name = "tftpBootdir")
    @JsonProperty("tftpBootdir")
    public String getTftpBootDir() {
        return tftpBootDir;
    }

    /**
     * @param tftpBootDir
     *            the tftpBootDir to set
     */
    public void setTftpBootDir(String tftpBootDir) {
        this.tftpBootDir = tftpBootDir;
    }

    @XmlElement(name = "imageserver_status")
    @JsonProperty("imageserver_status")
    public String getComputeImageServerStatus() {
        return computeImageServerStatus;

    }

    public void setComputeImageServerStatus(String computeImageServerStatus) {
        this.computeImageServerStatus = computeImageServerStatus;
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
     * @return the failedImages
     */
    @XmlElementWrapper(name = "failed_compute_images", nillable = true, required = false)
    @XmlElement(name = "failed_compute_image")
    @JsonProperty("failed_compute_images")
    public List<NamedRelatedResourceRep> getFailedImages() {
        return failedImages;
    }

    /**
     * @param failedImages the failedImages to set
     */
    public void setFailedImages(List<NamedRelatedResourceRep> failedImages) {
        this.failedImages = failedImages;
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