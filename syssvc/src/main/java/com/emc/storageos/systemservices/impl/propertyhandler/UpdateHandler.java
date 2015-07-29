/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.PropertyInfoRestRep;

/**
 * works like filter to inject in system properties update process.
 * 
 * classes implemented this interface need be registered in PropertyHandlers in sys-config.xml.
 * 
 * before - called before updating system properties
 * after - called after update system properties
 * 
 */
public interface UpdateHandler {

    public void before(PropertyInfoRestRep oldValue, PropertyInfoRestRep newValue);

    public void after(PropertyInfoRestRep oldValue, PropertyInfoRestRep newValue);

}
