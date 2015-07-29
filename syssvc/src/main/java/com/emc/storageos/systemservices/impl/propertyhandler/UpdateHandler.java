/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
