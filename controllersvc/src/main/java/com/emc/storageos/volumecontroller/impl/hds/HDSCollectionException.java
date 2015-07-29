/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Class for HDS discovery and metering exceptions
 */
public class HDSCollectionException extends BaseCollectionException {

    /**
	 * 
	 */
    private static final long serialVersionUID = -8375392492317521789L;
    public static final int ERROR_CODE_NA_EXCEPTION = -1;

    public HDSCollectionException(final boolean retryable, final ServiceCode code, final Throwable cause, final String detailBase,
            final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    @Override
    public int getErrorCode() {
        return ERROR_CODE_NA_EXCEPTION;
    }

    @Deprecated
    public HDSCollectionException(Throwable e) {
        super(e);
    }

    @Deprecated
    public HDSCollectionException(String message) {
        super(message);
    }

}
