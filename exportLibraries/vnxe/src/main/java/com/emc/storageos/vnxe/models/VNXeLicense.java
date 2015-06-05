/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class VNXeLicense extends VNXeBase{
    private boolean isValid;
    private String issued;
    private String name;
    private String version;
    private String expires;
    private boolean isPermanent;
    private NameId feature;
    
    public boolean getIsValid() {
        return isValid;
    }
    public void setIsValid(boolean isValid) {
        this.isValid = isValid;
    }
    public String getIssued() {
        return issued;
    }
    public void setIssued(String issued) {
        this.issued = issued;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }
    public String getExpires() {
        return expires;
    }
    public void setExpires(String expires) {
        this.expires = expires;
    }
    public boolean isPermanent() {
        return isPermanent;
    }
    public void setPermanent(boolean isPermanent) {
        this.isPermanent = isPermanent;
    }
    public NameId getFeature() {
        return feature;
    }
    public void setFeature(NameId feature) {
        this.feature = feature;
    }
    
    public static enum FeatureEnum {
        ANTIVIRUS,
        CIFS,
        DEDUPE,
        EMC_SUPPORT_ECOSYSTEM,
        FAST_CACHE,
        FAST_VP,
        FC,
        FLR,
        ISCSI,
        MONITORING_REPORTING,
        NFS,
        SNAP,
        UNISPHERE,
        UNISPHERE_CENTRAL,
        VNXE_PROVISION;   
    }

}
