/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@SuppressWarnings("serial")
public class KeystoneApiException extends InternalException {

    private KeystoneApiException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    /** Holds the methods used to create keystone related exceptions */
    public static final KeystoneExceptions exceptions = ExceptionMessagesProxy.create(KeystoneExceptions.class);

    /** Holds the methods used to create keystone related error conditions */
    public static KeystoneErrors errors = ExceptionMessagesProxy.create(KeystoneErrors.class);

}
