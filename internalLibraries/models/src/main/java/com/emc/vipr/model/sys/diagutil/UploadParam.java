/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

/**
 * 
 * Model class for UploadParam
 *
 */
@XmlRootElement(name = "upload_param")
public class UploadParam implements Serializable {
	private static final long serialVersionUID = -4425174604960199110L;
	private UploadType uploadType;
    private UploadFtpParam uploadFtpParam;

    @XmlElement(name = "upload_type")
    public UploadType getUploadType() {
        return uploadType;
    }

    public void setUploadType(UploadType uploadType) {
        this.uploadType = uploadType;
    }

    public UploadParam() {
    }

    /**
     * Constructor for UploadParam
     * 
     * @param type
     *         Denotes the upload type
     * @param uploadFtpParam
     *         param for uploading to ftp
     */
    public UploadParam(UploadType type, UploadFtpParam uploadFtpParam) {
        this.uploadType = type;
        this.uploadFtpParam = uploadFtpParam;
    }

    @XmlElement(name = "ftp_param")
    public UploadFtpParam getUploadFtpParam() {
        return uploadFtpParam;
    }

    public void setUploadFtpParam(UploadFtpParam uploadFtpParam) {
        this.uploadFtpParam = uploadFtpParam;
    }

    /**
     * 
     * Enum for the upload type
     *
     */
    public static enum UploadType {
        download,
        ftp,
        sftp
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("upload Type {");
        sb.append(uploadType);
        sb.append("}");
        sb.append("ftp param {");
        sb.append(uploadFtpParam.toString());
        sb.append("}");

        return sb.toString();
    }

}
