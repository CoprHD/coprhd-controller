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

    private String imageServerIp;

    private String imageServerSecondIp;

    private String tftpbootDir;

    private List<RelatedResourceRep> computeImage;

    public ComputeImageServerRestRep() {

    }

    /**
     * @return the imageServerIp
     */
    @XmlElement(name = "imageServerIp")
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
    @XmlElement(name = "imageServerSecondIp")
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

    /**
     * @return the tftpbootDir
     */
    @XmlElement(name = "tftpbootDir")
    public String getTftpbootDir() {
        return tftpbootDir;
    }

    /**
     * @param tftpbootDir
     *            the tftpbootDir to set
     */
    public void setTftpbootDir(String tftpbootDir) {
        this.tftpbootDir = tftpbootDir;
    }
}
