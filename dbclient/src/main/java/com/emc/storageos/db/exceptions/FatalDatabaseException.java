/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.exceptions;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class FatalDatabaseException extends DatabaseException {
    private static final long serialVersionUID = 8508132363498513927L;

    protected FatalDatabaseException(ServiceCode code, Throwable cause, String detailBase,
            String detailKey, Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }
}
