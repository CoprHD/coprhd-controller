/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "stats")
public class StatList {

    @XmlElement(name = "stat")
    private List<Stat> statistics;

    public List<Stat> getStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("StatList [statistics=");
        builder.append(statistics);
        builder.append("]");
        return builder.toString();
    }

    @XmlRootElement(name = "stat")
    public static class Stat {

        @XmlElement(name = "allocated_capacity")
        private String allocatedCapacity;

        @XmlElement(name = "bandwidth_in")
        private String bandwidthIn;

        @XmlElement(name = "bandwidth_out")
        private String bandwidthOut;

        @XmlElement(name = "kbytes_transferred")
        private String kbTransferred;

        @XmlElement(name = "native_guid")
        private String nativeGuid;

        @XmlElement(name = "project_id")
        private String projectId;

        @XmlElement(name = "provisioned_capacity")
        private String provisionedCapacity;

        @XmlElement(name = "resource_id")
        private String resourceId;

        @XmlElement(name = "service_type")
        private String serviceType;

        @XmlElement(name = "snapshot_capacity")
        private String snapshotCapacity;

        @XmlElement(name = "snapshot_count")
        private String snapshotCount;

        @XmlElement(name = "tenant_id")
        private String tenantId;

        @XmlElement(name = "time_collected")
        private String timeCollected;

        @XmlElement(name = "time_measured")
        private String timeMeasured;

        @XmlElement(name = "virtual_pool_id")
        private String vpoolId;

        @XmlElement(name = "write_hit_ios")
        private String writeHitIos;

        @XmlElement(name = "write_ios")
        private String writeIos;

        @XmlElement(name = "total_ios")
        private String totalIos;

        @XmlElement(name = "read_hit_ios")
        private String readHitIos;

        @XmlElement(name = "read_ios")
        private String readIos;

        @XmlElement(name = "user_id")
        private String userId;

        @XmlElement(name = "user_size")
        private String userSize;

        @XmlElement(name = "user_metadata_size")
        private String userMetadataSize;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Stat [allocatedCapacity=");
            builder.append(allocatedCapacity);
            builder.append(", bandwidthIn=");
            builder.append(bandwidthIn);
            builder.append(", bandwidthOut=");
            builder.append(bandwidthOut);
            builder.append(", kbTransferred=");
            builder.append(kbTransferred);
            builder.append(", nativeGuid=");
            builder.append(nativeGuid);
            builder.append(", projectId=");
            builder.append(projectId);
            builder.append(", provisionedCapacity=");
            builder.append(provisionedCapacity);
            builder.append(", resourceId=");
            builder.append(resourceId);
            builder.append(", serviceType=");
            builder.append(serviceType);
            builder.append(", snapshotCapacity=");
            builder.append(snapshotCapacity);
            builder.append(", snapshotCount=");
            builder.append(snapshotCount);
            builder.append(", tenantId=");
            builder.append(tenantId);
            builder.append(", timeCollected=");
            builder.append(timeCollected);
            builder.append(", timeMeasured=");
            builder.append(timeMeasured);
            builder.append(", vpoolId=");
            builder.append(vpoolId);
            builder.append(", writeHitIos=");
            builder.append(writeHitIos);
            builder.append(", writeIos=");
            builder.append(writeIos);
            builder.append(", totalIos=");
            builder.append(totalIos);
            builder.append(", readHitIos=");
            builder.append(readHitIos);
            builder.append(", readIos=");
            builder.append(readIos);
            builder.append(", userId=");
            builder.append(userId);
            builder.append(", userSize=");
            builder.append(userSize);
            builder.append(", userMetadataSize=");
            builder.append(userMetadataSize);
            builder.append("]");
            return builder.toString();
        }

        public String getAllocatedCapacity() {
            return allocatedCapacity;
        }

        public String getBandwidthIn() {
            return bandwidthIn;
        }

        public String getBandwidthOut() {
            return bandwidthOut;
        }

        public String getKbTransferred() {
            return kbTransferred;
        }

        public String getNativeGuid() {
            return nativeGuid;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getProvisionedCapacity() {
            return provisionedCapacity;
        }

        public String getResourceId() {
            return resourceId;
        }

        public String getServiceType() {
            return serviceType;
        }

        public String getSnapshotCapacity() {
            return snapshotCapacity;
        }

        public String getSnapshotCount() {
            return snapshotCount;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getTimeCollected() {
            return timeCollected;
        }

        public String getTimeMeasured() {
            return timeMeasured;
        }

        public String getVpoolId() {
            return vpoolId;
        }

        public String getWriteHitIos() {
            return writeHitIos;
        }

        public String getWriteIos() {
            return writeIos;
        }

        public String getTotalIos() {
            return totalIos;
        }

        public String getReadHitIos() {
            return readHitIos;
        }

        public String getReadIos() {
            return readIos;
        }

        public String getUserId() {
            return userId;
        }

        public String getUserSize() {
            return userSize;
        }

        public String getUserMetadataSize() {
            return userMetadataSize;
        }

    }

}
