/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 imited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage-system")
public class StorageSystem {

    @XmlElement
    private SystemDetail system;

    /**
     * @return the system
     */
    public SystemDetail getSystem() {
        return system;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("StorageSystem [system=");
        builder.append(system);
        builder.append("]");
        return builder.toString();
    }

    @XmlRootElement(name = "storage_system")
    public static class SystemDetail {
        @XmlElement
        private String id;
        @XmlElement
        private boolean inactive;
        @XmlElement(name = "name")
        private String label;
        @XmlElement(name = "system_type")
        private String deviceType;
        @XmlElement(name = "ip_address")
        private String ipAddress;
        @XmlElement(name = "port_number")
        private String portNumber;
        @XmlElement(name = "serial_number")
        private String serialNumber;
        @XmlElement(name = "smis_provider_ip")
        private String smisProviderIP;
        @XmlElement(name = "smis_port_number")
        private String smisPortNumber;
        @XmlElement
        private String smisUserName;
        @XmlElement
        private String smisPassword;
        @XmlElement(name = "smis_use_ssl")
        private Boolean smisUseSSL;
        @XmlElement(name = "job_discovery_status")
        private Boolean jobStatus;
        @XmlElement(name = "native_guid")
        private Boolean nativeGuid;
        @XmlElement(name = "registration_mode")
        private Boolean registrationMode;
        @XmlElement(name = "registration_status")
        private Boolean registrationStatus;

        public String getId() {
            return id;
        }

        public boolean isInactive() {
            return inactive;
        }

        public String getLabel() {
            return label;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public String getPortNumber() {
            return portNumber;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public String getSmisProviderIP() {
            return smisProviderIP;
        }

        public String getSmisPortNumber() {
            return smisPortNumber;
        }

        public String getSmisUserName() {
            return smisUserName;
        }

        public String getSmisPassword() {
            return smisPassword;
        }

        public Boolean getSmisUseSSL() {
            return smisUseSSL;
        }

        public Boolean getJobStatus() {
            return jobStatus;
        }

        public Boolean getNativeGuid() {
            return nativeGuid;
        }

        public Boolean getRegistrationMode() {
            return registrationMode;
        }

        public Boolean getRegistrationStatus() {
            return registrationStatus;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("StorageSystem [id=");
            builder.append(id);
            builder.append(", inactive=");
            builder.append(inactive);
            builder.append(", label=");
            builder.append(label);
            builder.append(", deviceType=");
            builder.append(deviceType);
            builder.append(", ipAddress=");
            builder.append(ipAddress);
            builder.append(", portNumber=");
            builder.append(portNumber);
            builder.append(", serialNumber=");
            builder.append(serialNumber);
            builder.append(", smisProviderIP=");
            builder.append(smisProviderIP);
            builder.append(", smisPortNumber=");
            builder.append(smisPortNumber);
            builder.append(", smisUserName=");
            builder.append(smisUserName);
            builder.append(", smisPassword=");
            builder.append(smisPassword);
            builder.append(", smisUseSSL=");
            builder.append(smisUseSSL);
            builder.append(", jobStatus=");
            builder.append(jobStatus);
            builder.append(", nativeGuid=");
            builder.append(nativeGuid);
            builder.append(", registrationMode=");
            builder.append(registrationMode);
            builder.append(", registrationStatus=");
            builder.append(registrationStatus);
            builder.append("]");
            return builder.toString();

        }
    }

    @XmlRootElement(name = "storage_system")
    public static class StorageSystemInfo {
        @XmlElement
        private String id;
        @XmlElement(name = "name")
        private String label;

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("StorageSystemInfo [id=");
            builder.append(id);
            builder.append(", label=");
            builder.append(label);
            builder.append("]");
            return builder.toString();

        }
    }

    @XmlRootElement(name = "storage_systems")
    public static class StorageSystemList {

        @XmlElement(name = "storage_system")
        private List<StorageSystemInfo> systems;

        public List<StorageSystemInfo> getSystems() {
            return systems;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("StorageSystemList [systems=");
            builder.append(systems);
            builder.append("]");
            return builder.toString();
        }

    }
}
