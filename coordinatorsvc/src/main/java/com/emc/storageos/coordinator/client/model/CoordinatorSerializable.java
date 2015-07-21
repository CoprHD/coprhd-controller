/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * This interface is designed to be implemented by all classes that can be published into coordinator.
 * Since coordinator requires objects to be serialized before publishing and de-serialized after being read out
 * all subclasses that implements this interface have to implement these two methods
 * encodeAsString & decodeFromString.
 *
 * Also, because coordinator needs subclass's identity,
 * subclasses are obligated to setup these information for coordinator.
 */
public interface CoordinatorSerializable {

    public String encodeAsString();

    public <T extends CoordinatorSerializable> T decodeFromString(String infoStr) throws FatalCoordinatorException;

    public CoordinatorClassInfo getCoordinatorClassInfo();
}
