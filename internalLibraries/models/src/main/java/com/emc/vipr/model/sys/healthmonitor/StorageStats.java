/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents storage usage stats.
 */
@XmlRootElement(name = "storage_stats")
public class StorageStats {

    public StorageStats() {
    }

    public StorageStats(ControllerStorageStats controllerStorageStats) {
        this.controllerStorageStats = controllerStorageStats;
    }

    private ControllerStorageStats controllerStorageStats;

    public static class ControllerStorageStats {
        private double fileCapacityKB;
        private double blockCapacityKB;
        private double freeManagedCapacityKB;

        public ControllerStorageStats() {
        }

        public ControllerStorageStats(double blockCapacityKB, double fileCapacityKB,
                double freeManagedCapacityKB) {
            this.blockCapacityKB = blockCapacityKB;
            this.fileCapacityKB = fileCapacityKB;
            this.freeManagedCapacityKB = freeManagedCapacityKB;
        }

        @XmlElement(name = "file_managed_capacity_kb")
        public double getFileCapacityKB() {
            return fileCapacityKB;
        }

        public void setFileCapacityKB(double fileCapacityKB) {
            this.fileCapacityKB = fileCapacityKB;
        }

        @XmlElement(name = "block_managed_capacity_kb")
        public double getBlockCapacityKB() {
            return blockCapacityKB;
        }

        public void setBlockCapacityKB(double blockCapacityKB) {
            this.blockCapacityKB = blockCapacityKB;
        }

        @XmlElement(name = "free_managed_capacity_kb")
        public double getFreeManagedCapacityKB() {
            return freeManagedCapacityKB;
        }

        public void setFreeManagedCapacityKB(double freeManagedCapacityKB) {
            this.freeManagedCapacityKB = freeManagedCapacityKB;
        }
    }

    public static class DataServiceStorageStats {
        private double capacityKB;

        public DataServiceStorageStats() {
        }

        public DataServiceStorageStats(double capacityKB) {
            this.capacityKB = capacityKB;
        }

        @XmlElement(name = "capacity_kb")
        public double getCapacityKB() {
            return capacityKB;
        }

        public void setCapacityKB(double capacityKB) {
            this.capacityKB = capacityKB;
        }
    }

    @XmlElement(name = "controller")
    public ControllerStorageStats getControllerStorageStats() {
        return controllerStorageStats;
    }

    public void setControllerStorageStats(ControllerStorageStats controllerStorageStats) {
        this.controllerStorageStats = controllerStorageStats;
    }
}
