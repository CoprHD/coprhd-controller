/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "upload_param")
public class UploadParam {
    private UploadFtpParam uploadFtpParam;

    public UploadParam() {}

    @XmlElement(name = "ftp_param")
    public UploadFtpParam getUploadFtpParam() {
        return uploadFtpParam;
    }

    public void setUploadFtpParam(UploadFtpParam uploadFtpParam) {
        this.uploadFtpParam = uploadFtpParam;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("ftp param {");
        sb.append(uploadFtpParam.toString());
        sb.append("}");

        return sb.toString();
    }

}
