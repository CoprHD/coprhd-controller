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

import java.util.Locale;

import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.utils.Messages;

public abstract class InternalException extends RuntimeException implements ServiceCoded {
    private static final long serialVersionUID = 2737586407487529024L;

    private final boolean _retryable;
    private final ServiceCode _code;
    private final String _key;
    protected final Object[] _parameters;
    private final String _bundleName;

    protected InternalException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(cause);
        this._retryable = retryable;
        this._code = code;
        this._bundleName = detailBase;
        this._key = detailKey;
        this._parameters = detailParams;
    }

    protected InternalException(final ServiceCode code, final Throwable cause,
            final String pattern, final Object[] params) {
        this(code.isRetryable(), code, cause, null, pattern, params);
    }

    @Override
    public ServiceCode getServiceCode() {
        return _code;
    }

    @Override
    public String getMessage() {
        return getMessage(Locale.ENGLISH);
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage(Locale.getDefault());
    }

    @Override
    public String getMessage(Locale locale) {
        return Messages.localize(_bundleName, locale, _key, _parameters);
    }

    @Override
    public boolean isRetryable() {
        return _retryable;
    }
}
