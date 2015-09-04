/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.model;

import java.util.Locale;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public interface ServiceCoded {

    public ServiceCode getServiceCode();

    public String getMessage();

    public String getMessage(Locale locale);

    public boolean isRetryable();
}
