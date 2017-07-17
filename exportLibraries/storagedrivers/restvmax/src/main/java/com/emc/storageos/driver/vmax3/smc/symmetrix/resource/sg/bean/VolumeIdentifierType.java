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
public class VolumeIdentifierType extends AbstractParameter {

    private VolumeIdentifierChoiceType volumeIdentifierChoice;
    private String identifier_name;
    private String append_number;

    /**
     * @return the volumeIdentifierChoice
     */
    public VolumeIdentifierChoiceType getVolumeIdentifierChoice() {
        return volumeIdentifierChoice;
    }

    /**
     * @param volumeIdentifierChoice the volumeIdentifierChoice to set
     */
    public void setVolumeIdentifierChoice(VolumeIdentifierChoiceType volumeIdentifierChoice) {
        this.volumeIdentifierChoice = volumeIdentifierChoice;
    }

    /**
     * @return the identifier_name
     */
    public String getIdentifier_name() {
        return identifier_name;
    }

    /**
     * @param identifier_name the identifier_name to set
     */
    public void setIdentifier_name(String identifier_name) {
        this.identifier_name = identifier_name;
    }

    /**
     * @return the append_number
     */
    public String getAppend_number() {
        return append_number;
    }

    /**
     * @param append_number the append_number to set
     */
    public void setAppend_number(String append_number) {
        this.append_number = append_number;
    }

    /**
     * @param volumeIdentifierChoice
     */
    public VolumeIdentifierType(VolumeIdentifierChoiceType volumeIdentifierChoice) {
        super();
        this.volumeIdentifierChoice = volumeIdentifierChoice;
    }

}
