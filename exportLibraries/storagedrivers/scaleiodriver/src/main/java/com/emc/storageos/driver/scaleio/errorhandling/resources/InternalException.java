/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.model.ServiceCoded;
import com.emc.storageos.driver.scaleio.errorhandling.utils.Messages;

import java.util.Arrays;
import java.util.Locale;

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
        this._parameters = (detailParams != null) ? Arrays.copyOf(detailParams, detailParams.length) : null;
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
