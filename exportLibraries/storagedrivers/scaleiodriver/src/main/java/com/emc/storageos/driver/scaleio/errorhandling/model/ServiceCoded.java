/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.model;

import com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode;

import java.util.Locale;

public interface ServiceCoded {

    ServiceCode getServiceCode();

    String getMessage();

    String getMessage(Locale locale);

    boolean isRetryable();
}
