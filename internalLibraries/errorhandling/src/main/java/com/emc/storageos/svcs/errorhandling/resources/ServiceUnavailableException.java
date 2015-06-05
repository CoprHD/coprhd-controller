/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.resources;

import javax.ws.rs.core.Response.Status;

public class ServiceUnavailableException extends APIException {
    private static final long serialVersionUID = -2011349372777314716L;

    private ServiceUnavailableException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.SERVICE_UNAVAILABLE, code, cause, detailBase, detailKey, detailParams);
    }
}
