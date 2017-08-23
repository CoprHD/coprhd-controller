/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class VolumeIdentifierType {

    // min/max occurs: 1/1
    private VolumeIdentifierChoiceType volumeIdentifierChoice;
    // min/max occurs: 0/1
	private String identifier_name;
    // min/max occurs: 0/1
    private String append_number;

    public void setVolumeIdentifierChoice(VolumeIdentifierChoiceType volumeIdentifierChoice) {
        this.volumeIdentifierChoice = volumeIdentifierChoice;
    }

    public void setIdentifier_name(String identifier_name) {
        this.identifier_name = identifier_name;
    }

    public void setAppend_number(String append_number) {
        this.append_number = append_number;
    }
}
