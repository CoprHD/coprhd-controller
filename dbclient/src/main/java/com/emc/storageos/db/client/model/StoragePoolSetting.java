/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("StoragePoolSetting")
public class StoragePoolSetting extends DiscoveredDataObject {
    private URI _storageSystem;
    
    private URI _storagePool;
    /**
     * StoragePoolSetting unique identifier.
     */
    private String _poolsettingID;
    /**
     * raidLevel.
     */
    private String _raidLevel;
    /**
     * DataRedundancyGoal.
     */
    private String _dataRedundancyGoal;
    /**
     * DataRedundancyMax.
     */
    private String _dataRedundancyMax;
    /**
     * DataRedundancyMin.
     */
    private String _dataRedundancyMin;
    /**
     * ExtentStripeLength.
     */
    private String _extentStripeLength;
    /**
     * ExtentStripeLengthMax.
     */
    private String _extentStripeLengthMax;
    /**
     * ExtentStripeLengthMin.
     */
    private String _extentStripeLengthMin;
    /**
     * PackageRedundancyGoal.
     */
    private String _packageRedundancyGoal;
    /**
     * PackageRedundancyMax.
     */
    private String _packageRedundancyMax;
    /**
     * PackageRedundancyMin.
     */
    private String _packageRedundancyMin;

    /**********************************************
     * AlternateIDIndex - poolSettingID           *
     * RelationIndex - StorageDevice,StoragePool  *
     *                                            *
     **********************************************/

    @AlternateId("AltIdIndex")
    @Name("poolsettingID")
    public String getPoolsettingID() {
        return _poolsettingID;
    }

    public void setPoolsettingID(String poolsettingID) {
        _poolsettingID = poolsettingID;
        setChanged("poolsettingID");
    }


    @Name("raidLevel")
    public String getRaidLevel() {
        return _raidLevel;
    }

    public void setRaidLevel(String raidLevel) {
        _raidLevel = raidLevel;
        setChanged("raidLevel");
    }

    @Name("dataRedundancyGoal")
    public String getDataRedundancyGoal() {
        return _dataRedundancyGoal;
    }

    public void setDataRedundancyGoal(String dataRedundancyGoal) {
        _dataRedundancyGoal = dataRedundancyGoal;
        setChanged("dataRedundancyGoal");
    }

    @Name("dataRedundancyMax")
    public String getDataRedundancyMax() {
        return _dataRedundancyMax;
    }

    public void setDataRedundancyMax(String dataRedundancyMax) {
        _dataRedundancyMax = dataRedundancyMax;
        setChanged("dataRedundancyMax");
    }

    @Name("dataRedundancyMin")
    public String getDataRedundancyMin() {
        return _dataRedundancyMin;
    }

    public void setDataRedundancyMin(String dataRedundancyMin) {
        _dataRedundancyMin = dataRedundancyMin;
        setChanged("dataRedundancyMin");
    }

    @Name("extentStripeLength")
    public String getExtentStripeLength() {
        return _extentStripeLength;
    }

    public void setExtentStripeLength(String extentStripeLength) {
        _extentStripeLength = extentStripeLength;
        setChanged("extentStripeLength");
    }

    @Name("extentStripeLengthMax")
    public String getExtentStripeLengthMax() {
        return _extentStripeLengthMax;
    }

    public void setExtentStripeLengthMax(String extentStripeLengthMax) {
        _extentStripeLengthMax = extentStripeLengthMax;
        setChanged("extentStripeLengthMax");
    }

    @Name("extentStripeLengthMin")
    public String getExtentStripeLengthMin() {
        return _extentStripeLengthMin;
    }

    public void setExtentStripeLengthMin(String extentStripeLengthMin) {
        _extentStripeLengthMin = extentStripeLengthMin;
        setChanged("extentStripeLengthMin");
    }

    @Name("packageRedundancyGoal")
    public String getPackageRedundancyGoal() {
        return _packageRedundancyGoal;
    }

    public void setPackageRedundancyGoal(String packageRedundancyGoal) {
        _packageRedundancyGoal = packageRedundancyGoal;
        setChanged("packageRedundancyGoal");
    }

    @Name("packageRedundancyMax")
    public String getPackageRedundancyMax() {
        return _packageRedundancyMax;
    }

    public void setPackageRedundancyMax(String packageRedundancyMax) {
        _packageRedundancyMax = packageRedundancyMax;
        setChanged("packageRedundancyMax");
    }

    @Name("packageRedundancyMin")
    public String getPackageRedundancyMin() {
        return _packageRedundancyMin;
    }

    public void setPackageRedundancyMin(String packageRedundancyMin) {
        _packageRedundancyMin = packageRedundancyMin;
        setChanged("packageRedundancyMin");
    }

    public void setStoragePool(URI storagePool) {
        _storagePool = storagePool;
        setChanged("storagePool");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePool.class)
    @Name("storagePool")
    public URI getStoragePool() {
        return _storagePool;
    }

    public void setStorageSystem(URI storageSystem) {
        _storageSystem = storageSystem;
        setChanged("storageSystem");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageSystem")
    public URI getStorageSystem() {
        return _storageSystem;
    }
}
