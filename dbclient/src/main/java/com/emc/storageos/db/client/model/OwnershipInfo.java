/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * ownership info for a directory
 * Assume entire key hash space is KS = [0, H), which is
 * transformed from key id space: object-id, dir-id, or kp-id.
 * KS is partitioned based on a set of modulo functions w/
 * divisor as powers of 2. Each directory corresponds to a
 * tuple <reminder, divisor>, meaning it represents all h in KS
 * where h % divisor = reminder. For example, the entire space
 * can be partitioned into 4 partitions: <0,4>, <1,2>, <2,8>, <6,8>
 */
@NoInactiveIndex
@ExcludeFromGarbageCollection
@Cf("OwnershipInfo")
@XmlRootElement(name = "ownership_info")
public class OwnershipInfo extends DataObject {

    // The VirtualPool
    private URI _virtualPool;

    // The directory type
    private String _type;

    // Reminder of the modulo function
    private Integer _remainder;

    // Divisor for the modulo function
    private Integer _divisor;

    // The owner ipAddress of the directory
    private String _ownerIpAddress;

    // list of Record files of the associated directory table
    private String _recordFileList;

    // The hosting device where the associated directory table locates
    private URI _device;

    // Directory version #
    private Integer _version;

    // Epoch #
    // Directories may be recreated for the same virtual Pool
    // when a new epoch # will be used.
    private Integer _epoch;

    // the directory table's level
    private Integer _level;

    // the creation of the DT is completed
    private Boolean _creationCompleted;

    // epoch number in ZK ephemeral node
    private String _zkEpoch;

    /**
     * Get virtual pool
     * 
     * @return
     */
    @XmlElement
    @Name("virtualPool")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    /**
     * Set virtual pool
     * 
     * @param virtualPool
     */
    public void setVirtualPool(URI virtualPool)
    {
        _virtualPool = virtualPool;
        setChanged("virtualPool");
    }

    /**
     * Get type
     * 
     * @return
     */
    @XmlElement
    @Name("type")
    public String getType() {
        return _type;
    }

    /**
     * Set type
     * 
     * @param type
     */
    public void setType(String type)
    {
        _type = type;
        setChanged("type");
    }

    /**
     * Get remainder
     * 
     * @return
     */
    @XmlElement
    @Name("remainder")
    public Integer getRemainder() {
        return _remainder;
    }

    /**
     * Set remainder
     * 
     * @param remainder
     */
    public void setRemainder(Integer remainder)
    {
        _remainder = remainder;
        setChanged("remainder");
    }

    /**
     * Get divisor #
     * 
     * @return
     */
    @XmlElement
    @Name("divisor")
    public Integer getDivisor() {
        return _divisor;
    }

    /**
     * Set divisor #
     * 
     * @param divisor
     */
    public void setDivisor(Integer divisor)
    {
        _divisor = divisor;
        setChanged("divisor");
    }

    /**
     * Get IP address and port of the owner
     * WARNING: The name is getOwnerIpAddress but it basically returns ip:port
     * 
     * @return
     */
    @XmlElement
    @Name("ownerIpAddress")
    public String getOwnerIpAddress() {
        return _ownerIpAddress;
    }

    /**
     * Set IP address and port of the owner
     * WARNING: The name is setOwnerIpAddress but the passed in value is of the form ip:port
     * 
     * @param ownerIpAddress
     */
    public void setOwnerIpAddress(String ownerIpAddress)
    {
        _ownerIpAddress = ownerIpAddress;
        setChanged("ownerIpAddress");
    }

    /**
     * Get the path of the record file
     * 
     * @return
     */
    @XmlElement
    @Name("recordFileList")
    public String getRecordFileList() {
        return _recordFileList;
    }

    /**
     * Set the path of the record file
     * 
     * @param recordFileList
     */
    public void setRecordFileList(String recordFileList)
    {
        _recordFileList = recordFileList;
        setChanged("recordFileList");
    }

    /**
     * Get the hosting device of the directory table
     * 
     * @return
     */
    @XmlElement
    @Name("device")
    public URI getDevice() {
        return _device;
    }

    /**
     * Set the hosting device of the directory table
     * 
     * @param device
     */
    public void setDevice(URI device)
    {
        _device = device;
        setChanged("device");
    }

    /**
     * Get the version of the directory table
     * 
     * @return
     */
    @XmlElement
    @Name("version")
    public Integer getVersion() {
        return _version;
    }

    /**
     * Set the version of the directory table
     * 
     * @param version
     */
    public void setVersion(Integer version)
    {
        _version = version;
        setChanged("version");
    }

    /**
     * Get the epoch of the directory table
     * 
     * @return
     */
    @XmlElement
    @Name("epoch")
    public Integer getEpoch() {
        return _epoch;
    }

    /**
     * Set the epoch of the directory table
     * 
     * @param epoch
     */
    public void setEpoch(Integer epoch)
    {
        _epoch = epoch;
        setChanged("epoch");
    }

    /**
     * Get the level of the directory table
     * 
     * @return
     */
    @XmlElement
    @Name("level")
    public Integer getLevel() {
        return _level;
    }

    /**
     * Set the level of the directory table
     * 
     * @param level
     */
    public void setLevel(Integer level)
    {
        _level = level;
        setChanged("level");
    }

    /**
     * Get the creationCompleted of the directory table
     * 
     * @return
     */
    @XmlElement
    @Name("creationCompleted")
    public Boolean getCreationCompleted() {
        return _creationCompleted;
    }

    /**
     * Set the creationCompleted of the directory table
     * 
     * @param creationCompleted
     */
    public void setCreationCompleted(Boolean creationCompleted)
    {
        _creationCompleted = creationCompleted;
        setChanged("creationCompleted");
    }

    @XmlElement
    @Name("zkEpoch")
    /**
     * Get ZK ephemeral node epoch number
     */
    public String getZkEpoch() {
        return _zkEpoch;
    }

    /**
     * Set the ZK ephemeral node epoch number
     * 
     * @param zkEpoch
     */
    public void setZkEpoch(String zkEpoch) {
        _zkEpoch = zkEpoch;
        setChanged("zkEpoch");
    }

    // shallow copy
    public OwnershipInfo copy() {
        OwnershipInfo copy = new OwnershipInfo();
        copy.setId(getId());
        copy.setVirtualPool(getVirtualPool());
        copy.setType(getType());
        copy.setRemainder(getRemainder());
        copy.setDivisor(getDivisor());
        copy.setOwnerIpAddress(getOwnerIpAddress());
        copy.setRecordFileList(getRecordFileList());
        copy.setDevice(getDevice());
        copy.setVersion(getVersion());
        copy.setEpoch(getEpoch());
        copy.setLevel(getLevel());
        copy.setCreationCompleted(getCreationCompleted());
        copy.setZkEpoch(getZkEpoch());
        return copy;
    }

}
