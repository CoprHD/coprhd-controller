/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.model;

import java.util.Locale;

public interface ServiceCoded {

    com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode getServiceCode();

    String getMessage();

    String getMessage(Locale locale);

    boolean isRetryable();
}
