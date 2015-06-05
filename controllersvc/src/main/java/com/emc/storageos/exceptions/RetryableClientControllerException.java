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

package com.emc.storageos.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class RetryableClientControllerException extends ClientControllerException {
    private static final long serialVersionUID = -7442262822497235733L;

    protected RetryableClientControllerException(ServiceCode code, Throwable cause,
            String detailBase, String detailKey, Object[] detailParams) {
        super(true, code, cause, detailBase, detailKey, detailParams);
    }
}
