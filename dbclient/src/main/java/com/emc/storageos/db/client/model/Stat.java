/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import org.codehaus.jackson.annotate.JsonProperty;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.net.URI;

/**
 * Stat time series data object
 */
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
@XmlRootElement(name = "stat")
public class Stat extends TimeSeriesSerializer.DataPoint implements
        Serializable {
    /**
     * Resource URN ID of a Volume/FileShare.
     */
    private URI _resourceId;
    /**
     * The amount of storage capacity provisioned for the volume/fileshare
     */
    private long _provisionedCapacity = -1;
    /**
     * The amount of storage capacity physically allocated to the volume/fileshare
     */
    private long _allocatedCapacity = -1;
    /**
     * The amount of storage capacity allocated for this volume's snapshots.
     */
    private long _snapshotCapacity = -1;
    /**
     * Number of Snapshots of a volume/fileshare.
     */
    private int _snapshotCount = -1;
    /**
     * The amount of data written to the volume/fileshare.
     */
    private long _bandwidthIn = -1;
    /**
     * The amount of data read from the volume/fileshare.
     */
    private long _bandwidthOut = -1;

    /**
     * The cumulative count of I/O's of Volume alone.
     */
    private long _totalIOs = -1;
    /**
     * The cumulative count of all reads of Volume alone.
     */
    private long _readIOs = -1;
    /**
     * The cumulative count of all writes of Volume alone.
     */
    private long _writeIOs = -1;
    /**
     * The cumulative count of data transferred in Kbytes for Volume alone.
     */
    private long _kbytesTransferred = -1;

    /**
     * Counter representing all samples finding the IO processing
     * idleing for the respective volume.
     */
    private long _idleTimeCounter = -1;
    /**
     * Counter representing all samples representing the cumulative IO service times of the
     * components engaged in a IO for the respective volume.
     */
    private long _ioTimeCounter = -1;

    /**
     * The cumulative count of total queue length
     */
    private long _queueLength = -1;
    /**
     * The cumulative count of all read cache hits.
     */
    private long _readHitIOs = -1;
    /**
     * The cumulative count of Write Cache Hits.
     */
    private long _writeHitIOs = -1;

    /**
     * Number of Objects(Object Service)
     */
    private long _objCount = -1;
    /**
     * User Facing Size. Disk usage for objects without replicas; the actual.
     * Object Service user data, which would be reported in the customer bill.
     */
    private long _userSize = -1;
    /**
     * Total disk usage for objects + replicas after system savings; the Object
     * Service capacity used to store userSize with the necessary redundancy.
     */
    private long _realSize = -1;
    /**
     * User Metadata Size (Object Service)
     */
    private long _umdSize = -1;
    /**
     * System Metadata Size (Object Service)
     */
    private long _smdSize = -1;
    
    /**
     * Virtual Pool
     */
    private URI _virtualPool;
    /**
     * Project in which the resource belongs.
     */
    private URI _project;
    /**
     * tenant
     */
    private URI _tenant;
    /**
     * user Id
     */
    private URI _user;
    /**
     * serviceType - block or file
     */
    private String _serviceType;
    /**
     * Unique identifier of a Volume/Fileshare.
     */
    private String _nativeGuid;

    /**
     * Timestamp captures the Bourne collection time
     * when collected plugins.
     */
    private long _timeCollected;

    @XmlElement(nillable=true, name = "resource_id")
    @JsonProperty("resource_id")
    public URI getResourceId() {
        return _resourceId;
    }

    public void setResourceId(URI resourceId) {
        this._resourceId = resourceId;
    }

    @XmlElement(nillable=true, name = "provisioned_capacity")
    @JsonProperty("provisioned_capacity")
    public Long getProvisionedCapacity() {
        if (_provisionedCapacity < 0) {
            return null;
        } else {
            return _provisionedCapacity;
        }
    }

    public void setProvisionedCapacity(long provisionedCapacity) {
        this._provisionedCapacity = provisionedCapacity;
    }

    @XmlElement(nillable=true, name = "allocated_capacity")
    @JsonProperty("allocated_capacity")
    public Long getAllocatedCapacity() {
        if (_allocatedCapacity < 0) {
            return null;
        } else {
            return _allocatedCapacity;
        }
    }

    public void setAllocatedCapacity(long allocatedCapacity) {
        this._allocatedCapacity = allocatedCapacity;
    }

    @XmlElement(nillable=true, name = "snapshot_capacity")
    @JsonProperty("snapshot_capacity")
    public Long getSnapshotCapacity() {
        if (_snapshotCapacity < 0) {
            return null;
        } else {
            return _snapshotCapacity;
        }
    }

    public void setSnapshotCapacity(long snapshotCapacity) {
        this._snapshotCapacity = snapshotCapacity;
    }

    @XmlElement(nillable=true, name = "snapshot_count")
    @JsonProperty("snapshot_count")
    public Integer getSnapshotCount() {
        if (_snapshotCount < 0) {
            return null;
        } else {
            return _snapshotCount;
        }
    }

    public void setSnapshotCount(int snapshotCount) {
        this._snapshotCount = snapshotCount;
    }

    @XmlElement(nillable=true, name = "bandwidth_in")
    @JsonProperty("bandwidth_in")
    public Long getBandwidthIn() {
        if (_bandwidthIn < 0) {
            return null;
        } else {
            return _bandwidthIn;
        }
    }

    public void setBandwidthIn(long bandwidthIn) {
        this._bandwidthIn = bandwidthIn;
    }

    @XmlElement(nillable=true, name = "bandwidth_out")
    @JsonProperty("bandwidth_out")
    public Long getBandwidthOut() {
        if (_bandwidthOut < 0) {
            return null;
        } else {
            return _bandwidthOut;
        }
    }

    public void setBandwidthOut(long bandwidthOut) {
        this._bandwidthOut = bandwidthOut;
    }


    @XmlElement(nillable=true, name = "object_count")
    @JsonProperty("object_count")
    public Long getObjCount() {
        if (_objCount < 0) {
            return null;
        } else {
            return _objCount;
        }
    }

    public void setObjCount(long objCount) {
        this._objCount = objCount;
    }

    @XmlElement(nillable=true, name = "user_size")
    @JsonProperty("user_size")
    public Long getUserSize() {
        if (_userSize < 0) {
            return null;
        } else {
            return _userSize;
        }
    }

    public void setUserSize(long userSize) {
        this._userSize = userSize;
    }

    @XmlElement(nillable=true, name = "real_size")
    @JsonProperty("real_size")
    public Long getRealSize() {
        if (_realSize < 0) {
            return null;
        } else {
            return _realSize;
        }
    }

    public void setRealSize(long realSize) {
        this._realSize = realSize;
    }

    @XmlElement(nillable=true, name = "user_metadata_size")
    @JsonProperty("user_metadata_size")
    public Long getUmdSize() {
        if (_umdSize < 0) {
            return null;
        } else {
            return _umdSize;
        }
    }

    public void setUmdSize(long umdSize) {
        this._umdSize = umdSize;
    }

    @XmlElement(nillable=true, name = "system_metadata_size")
    @JsonProperty("system_metadata_size")
    public Long getSmdSize() {
        if (_smdSize < 0) {
            return null;
        } else {
            return _smdSize;
        }
    }

    public void setSmdSize(long smdSize) {
        this._smdSize = smdSize;
    }

    @XmlElement(nillable=true, name = "virtual_pool_id")
    @JsonProperty("virtual_pool_id")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
    }

    @XmlElement(nillable=true, name = "project_id")
    @JsonProperty("project_id")
    public URI getProject() {
        return _project;
    }

    public void setProject(URI project) {
        this._project = project;
    }

    @XmlElement(nillable=true, name = "tenant_id")
    @JsonProperty("tenant_id")
    public URI getTenant() {
        return _tenant;
    }

    public void setTenant(URI tenant) {
        this._tenant = tenant;
    }

    @XmlElement(nillable=true, name = "user_id")
    @JsonProperty("user_id")
    public URI getUser() {
        return _user;
    }

    public void setUser(URI user) {
        this._user = user;
    }

    @XmlElement(nillable=true, name = "service_type")
    @JsonProperty("service_type")
    public String getServiceType() {
        return _serviceType;
    }

    public void setServiceType(String serviceType) {
        this._serviceType = serviceType;
    }

    /**
     * Returns the Bourne collection time when statistics
     * are collected by plugins from provider.
     * represents <timeCollected>1346397864466</timeCollected> in output.
     * @return the timeCollected in Milli Sec.
     *          block/vnxfile : Bourne collection time.
     */
    @XmlElement(name = "time_collected")
    @JsonProperty("time_collected")
    public long getTimeCollected() {
        return _timeCollected;
    }

    /**
     * Set the Bourne collection time when statistics
     * are collected by plugins from provider.
     * If provider is not returning timestamp,
     * Plugin inserts Bourne collection time.
     * @param timeCollected:
     *           block/file : Bourne collection time.
     */
    public void setTimeCollected(long timeCollected) {
        _timeCollected = timeCollected;
    }

    /**
     * Returns the time measurement taken by provider for
     * the collected statistics.
     * <timeMeasured>1346397864466</timeMeasured>
     * @return the timeMeasured in Milli Seconds.
     *           block : Bourne collection time.
     *           vnxfile  : Provider's collection time.
     */
    @Override
    @XmlElement(name = "time_measured")
    @JsonProperty("time_measured")
    public long getTimeInMillis() {
        return super.getTimeInMillis();
    }

    /**
     * Set the time measurement taken by provider when
     * statistics are collected in Bourne.
     *
     * @param timeMeasured in Milli seconds.
     *           for block   : It is always the Bourne collection time.
     *           for vnxfile : It is the Provider collection time.
     */
    @Override
    public void setTimeInMillis(long timeMeasured) {
        super.setTimeInMillis(timeMeasured);
    }

    /**
     * Returns the nativeGuid of FileShare/Volume.
     * @return the nativeGuid
     */
    @XmlElement(name = "native_guid")
    @JsonProperty("native_guid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    /**
     * Set the nativeGuid of the fileShare/Volume
     * @param nativeGuid the nativeGuid to set
     */
    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
    }

    @XmlElement(nillable=true, name = "total_ios")
    @JsonProperty("total_ios")
    public Long getTotalIOs() {
        return (_totalIOs < 0) ? null : _totalIOs;
    }

    public void setTotalIOs(long totalIOs) {
        _totalIOs = totalIOs;
    }

    @XmlElement(nillable=true, name = "read_ios")
    @JsonProperty("read_ios")
    public Long getReadIOs() {
        return (_readIOs < 0) ? null : _readIOs;
    }

    public void setReadIOs(long readIOs) {
        _readIOs = readIOs;
    }

    @XmlElement(nillable=true, name = "write_ios")
    @JsonProperty("write_ios")
    public Long getWriteIOs() {
        return (_writeIOs < 0) ? null : _writeIOs;
    }

    public void setWriteIOs(long writeIOs) {
        _writeIOs = writeIOs;
    }

    @XmlElement(nillable=true, name = "kbytes_transferred")
    @JsonProperty("kbytes_transferred")
    public Long getKbytesTransferred() {
        return (_kbytesTransferred < 0) ? null : _kbytesTransferred;
    }

    public void setKbytesTransferred(long kbytesTransferred) {
        _kbytesTransferred = kbytesTransferred;
    }

    @XmlElement(nillable=true, name = "idle_time_counter")
    @JsonProperty("idle_time_counter")
    public Long getIdleTimeCounter() {
        return (_idleTimeCounter < 0) ? null : _idleTimeCounter;
    }

    public void setIdleTimeCounter(long idleTimeCounter) {
        _idleTimeCounter = idleTimeCounter;
    }

    @XmlElement(nillable=true, name = "io_time_counter")
    @JsonProperty("io_time_counter")
    public Long getIoTimeCounter() {
        return (_ioTimeCounter < 0) ? null : _ioTimeCounter;
    }

    public void setIoTimeCounter(long ioTimeCounter) {
        _ioTimeCounter = ioTimeCounter;
    }

    @XmlElement(nillable=true, name = "queue_length")
    @JsonProperty("queue_length")
    public Long getQueueLength() {
        return (_queueLength < 0) ? null : _queueLength;
    }

    public void setQueueLength(long queueLength) {
        _queueLength = queueLength;
    }

    @XmlElement(nillable=true, name = "read_hit_ios")
    @JsonProperty("read_hit_ios")
    public Long getReadHitIOs() {
        return (_readHitIOs < 0) ? null : _readHitIOs;
    }

    public void setReadHitIOs(long readHitIOs) {
        _readHitIOs = readHitIOs;
    }

    @XmlElement(nillable=true, name = "write_hit_ios")
    @JsonProperty("write_hit_ios")
    public Long getWriteHitIOs() {
        return (_writeHitIOs < 0) ? null : _writeHitIOs;
    }

    public void setWriteHitIOs(long writeHitIOs) {
        _writeHitIOs = writeHitIOs;
    }
}
