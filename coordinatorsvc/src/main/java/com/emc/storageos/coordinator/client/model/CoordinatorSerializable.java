/**
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
