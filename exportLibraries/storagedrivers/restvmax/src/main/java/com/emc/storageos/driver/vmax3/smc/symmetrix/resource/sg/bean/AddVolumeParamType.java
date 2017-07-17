/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.bean;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractParameter;

/**
 * @author fengs5
 *
 */
public class AddVolumeParamType extends AbstractParameter {

    private Long num_of_vols;
    private VolumeAttributeType volumeAttribute;
    private boolean create_new_volumes;
    private CreateStorageEmulationType emulation;
    private VolumeIdentifierType volumeIdentifier;

    /**
     * @return the num_of_vols
     */
    public Long getNum_of_vols() {
        return num_of_vols;
    }

    /**
     * @param num_of_vols the num_of_vols to set
     */
    public void setNum_of_vols(Long num_of_vols) {
        this.num_of_vols = num_of_vols;
    }

    /**
     * @return the volumeAttribute
     */
    public VolumeAttributeType getVolumeAttribute() {
        return volumeAttribute;
    }

    /**
     * @param volumeAttribute the volumeAttribute to set
     */
    public void setVolumeAttribute(VolumeAttributeType volumeAttribute) {
        this.volumeAttribute = volumeAttribute;
    }

    /**
     * @return the create_new_volumes
     */
    public boolean isCreate_new_volumes() {
        return create_new_volumes;
    }

    /**
     * @param create_new_volumes the create_new_volumes to set
     */
    public void setCreate_new_volumes(boolean create_new_volumes) {
        this.create_new_volumes = create_new_volumes;
    }

    /**
     * @return the emulation
     */
    public CreateStorageEmulationType getEmulation() {
        return emulation;
    }

    /**
     * @param emulation the emulation to set
     */
    public void setEmulation(CreateStorageEmulationType emulation) {
        this.emulation = emulation;
    }

    /**
     * @return the volumeIdentifier
     */
    public VolumeIdentifierType getVolumeIdentifier() {
        return volumeIdentifier;
    }

    /**
     * @param volumeIdentifier the volumeIdentifier to set
     */
    public void setVolumeIdentifier(VolumeIdentifierType volumeIdentifier) {
        this.volumeIdentifier = volumeIdentifier;
    }

    /**
     * @param num_of_vols
     * @param volumeAttribute
     */
    public AddVolumeParamType(Long num_of_vols, VolumeAttributeType volumeAttribute) {
        super();
        this.num_of_vols = num_of_vols;
        this.volumeAttribute = volumeAttribute;
    }

}
