/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.geo.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class FatalGeoException extends GeoException {
    private static final long serialVersionUID = 8508132363498513890L;

    protected FatalGeoException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
