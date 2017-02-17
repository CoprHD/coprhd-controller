/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeElement")
public class ComputeElement extends DiscoveredSystemObject {

    private URI _computeSystem;
    private Long _ram = 0L;
    private Integer _numOfCores = 0;
    private String _uuid;
    private String _originalUuid;
    private String _dn;
    private Short _numberOfProcessors = 0;
    private Integer _numberOfThreads = 0;
    private String _processorSpeed;
    private Boolean _available;
    private String _sptId;
    private String _chassisId;
    private Long _slotId;
    private String _model;
    private String _bios;

    @RelationIndex(cf = "ComputeRelationIndex", type = ComputeSystem.class)
    @Name("computeSystem")
    public URI getComputeSystem() {
        return _computeSystem;
    }

    public void setComputeSystem(URI computeSystem) {
        this._computeSystem = computeSystem;
        setChanged("computeSystem");
    }

    @Name("ram")
    public Long getRam() {
        return _ram;
    }

    public void setRam(Long ram) {
        this._ram = ram;
        setChanged("ram");
    }

    @Name("numOfCores")
    public Integer getNumOfCores() {
        return _numOfCores;
    }

    public void setNumOfCores(Integer numOfCores) {
        this._numOfCores = numOfCores;
        setChanged("numOfCores");
    }

    @AlternateId("AltIdIndex")
    @Name("uuid")
    public String getUuid() {
        return _uuid;
    }

    public void setUuid(String uuid) {
        this._uuid = uuid;
        setChanged("uuid");
    }

    @Name("bios")
    public String getBios() {
        return _bios;
    }

    public void setBios(String bios) {
        this._bios = bios;
        setChanged("bios");
    }

    @Name("originalUuid")
    public String getOriginalUuid() {
        return _originalUuid;
    }

    public void setOriginalUuid(String originalUuid) {
        this._originalUuid = originalUuid;
        setChanged("originalUuid");
    }

    /**
     * This is currently the dn of the Service Profile that's bound If this is
     * not set, that means that there isn't a SP bound to he blade
     * 
     * @return
     */
    @Name("dn")
    public String getDn() {
        return _dn;
    }

    public void setDn(String dn) {
        this._dn = dn;
        setChanged("dn");
    }

    @Name("numberOfProcessors")
    public Short getNumberOfProcessors() {
        return _numberOfProcessors;
    }

    public void setNumberOfProcessors(Short numberOfProcessors) {
        this._numberOfProcessors = numberOfProcessors;
        setChanged("numberOfProcessors");
    }

    @Name("numberOfThreads")
    public Integer getNumberOfThreads() {
        return _numberOfThreads;
    }

    public void setNumberOfThreads(Integer numberOfThreads) {
        this._numberOfThreads = numberOfThreads;
        setChanged("numberOfThreads");
    }

    @Name("processorSpeed")
    public String getProcessorSpeed() {
        return _processorSpeed;
    }

    public void setProcessorSpeed(String processorSpeed) {
        this._processorSpeed = processorSpeed;
        setChanged("processorSpeed");
    }

    @Name("available")
    public Boolean getAvailable() {
        return (_available != null) && _available;
    }

    /**
     * set available
     * 
     * @param available
     */
    public void setAvailable(Boolean available) {
        _available = available;
        setChanged("available");
    }

    @Name("sptId")
    public String getSptId() {
        return _sptId;
    }

    /**
     * set sptId
     * 
     * @param sptId
     */
    public void setSptId(String sptId) {
        _sptId = sptId;
        setChanged("sptId");
    }

    @Name("chassisId")
    public String getChassisId() {
        return _chassisId;
    }

    public void setChassisId(String _chassisId) {
        this._chassisId = _chassisId;
        setChanged("chassisId");
    }

    @Name("slotId")
    public Long getSlotId() {
        return _slotId;
    }

    public void setSlotId(Long _slotId) {
        this._slotId = _slotId;
        setChanged("slotId");
    }

    @Name("model")
    public String getModel() {
        return _model;
    }

    public void setModel(String _model) {
        this._model = _model;
        setChanged("model");
    }
}
