/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.storagedriver;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "driver")
public class StorageDriverRestRep extends DataObjectRestRep {

    private String driverName;
    private String driverVersion;
    private String driverFileName;
    private String driverStatus;
    private String driverClassName;
    private String metaType;
    private List<String> supportedTypes;
    private boolean defaultSslOn;
    private String sslPort;
    private String nonSslPort;
    private boolean supportAutoTierPolicy;

    @XmlElement(name = "driver_name")
    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    @XmlElement(name = "driver_version")
    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
    }

    @XmlElement(name = "driver_file_name")
    public String getDriverFileName() {
        return driverFileName;
    }

    public void setDriverFileName(String driverFileName) {
        this.driverFileName = driverFileName;
    }

    @XmlElement(name = "driver_status")
    public String getDriverStatus() {
        return driverStatus;
    }

    public void setDriverStatus(String driverStatus) {
        this.driverStatus = driverStatus;
    }

    @XmlElement(name = "driver_class_name")
    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    @XmlElement(name = "meta_type")
    public String getMetaType() {
        return metaType;
    }

    public void setMetaType(String metaType) {
        this.metaType = metaType;
    }

    @XmlElement(name = "supported_types")
    public List<String> getSupportedTypes() {
        if (supportedTypes == null) {
            supportedTypes =   new ArrayList<String>();
        }
        return supportedTypes;
    }

    public void setSupportedTypes(List<String> supportedTypes) {
        this.supportedTypes = supportedTypes;
    }

    @XmlElement(name = "is_ssl_on")
    public boolean isDefaultSslOn() {
        return defaultSslOn;
    }

    public void setDefaultSslOn(boolean defaultSslOn) {
        this.defaultSslOn = defaultSslOn;
    }

    @XmlElement(name = "ssl_port")
    public String getSslPort() {
        return sslPort;
    }

    public void setSslPort(String sslPort) {
        this.sslPort = sslPort;
    }

    @XmlElement(name = "non_ssl_port")
    public String getNonSslPort() {
        return nonSslPort;
    }

    public void setNonSslPort(String nonSslPort) {
        this.nonSslPort = nonSslPort;
    }

    @XmlElement(name = "support_autotier_policy")
    public boolean isSupportAutoTierPolicy() {
        return supportAutoTierPolicy;
    }

    public void setSupportAutoTierPolicy(boolean supportAutoTierPolicy) {
        this.supportAutoTierPolicy = supportAutoTierPolicy;
    }
}
