/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs.model;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import org.apache.commons.lang.StringUtils;

public class ApiErrorCode {
    private final ServiceCode serviceCode;

    public String code;
    public boolean deprecated = false;

    public ApiErrorCode(ServiceCode serviceCode) {
        this.serviceCode = serviceCode;
    }

    public int getCode() {
        return serviceCode.getCode();
    }

    public String getMessage() {
        return StringUtils.remove(serviceCode.getSummary(), "\"");
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
}
