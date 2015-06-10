/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "vdc_certs")
public class VdcCertListParam {
    public static String CMD_ADD_CERT = "ADD_CERT";
    public static String CMD_UPDATE_CERT = "UPDATE_CERT";

    private String cmd;
    private String targetVdcId;
    private String targetVdcCert;

    // all the connected VDCs' certs for adding cert to target VDC
    private List<VdcCertParam> certs;

    public VdcCertListParam() {}

    @XmlElement(name = "cmd")
    public String getCmd() {
        return cmd;
    }
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    @XmlElement(name = "target_vdc_id")
    public String getTargetVdcId() {
        return targetVdcId;
    }
    public void setTargetVdcId(String targetVdcId) {
        this.targetVdcId = targetVdcId;
    }

    @XmlElement(name = "target_vdc_cert")
    public String getTargetVdcCert() {
        return targetVdcCert;
    }
    public void setTargetVdcCert(String targetVdcCert) {
        this.targetVdcCert = targetVdcCert;
    }

    @XmlElement(name = "vdc_cert")
    public List<VdcCertParam> getVdcCerts() {
        if (certs == null) {
            certs = new ArrayList<VdcCertParam>();
        }
        return certs;
    }
    public void setVdcCerts(List<VdcCertParam> certs) {
        this.certs = certs;
    }


}
