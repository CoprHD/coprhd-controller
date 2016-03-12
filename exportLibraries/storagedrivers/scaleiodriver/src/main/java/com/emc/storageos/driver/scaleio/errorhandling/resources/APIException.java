/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import java.util.Arrays;
import java.util.Locale;

import javax.ws.rs.core.Response.StatusType;

import com.emc.storageos.driver.scaleio.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.driver.scaleio.errorhandling.model.StatusCoded;
import com.emc.storageos.driver.scaleio.errorhandling.utils.Messages;

public abstract class APIException extends RuntimeException implements StatusCoded {

    private static final long serialVersionUID = 5137591375806302511L;

    public static final BadRequestExceptions badRequests = ExceptionMessagesProxy
            .create(BadRequestExceptions.class);
    public static final ForbiddenExceptions forbidden = ExceptionMessagesProxy
            .create(ForbiddenExceptions.class);
    public static final InternalServerErrorExceptions internalServerErrors = ExceptionMessagesProxy
            .create(InternalServerErrorExceptions.class);
    public static final MethodNotAllowedExceptions methodNotAllowed = ExceptionMessagesProxy
            .create(MethodNotAllowedExceptions.class);
    public static final NotFoundExceptions notFound = ExceptionMessagesProxy
            .create(NotFoundExceptions.class);
    public static final ServiceUnavailableExceptions serviceUnavailable = ExceptionMessagesProxy
            .create(ServiceUnavailableExceptions.class);
    public static final UnauthorizedExceptions unauthorized = ExceptionMessagesProxy
            .create(UnauthorizedExceptions.class);

    private final StatusType _status;

    private final ServiceCode _code;

    private final String _key;

    private final Object[] _parameters;

    private final String _bundleName;

    private final boolean retryable = false;

    protected APIException(final StatusType status, final ServiceCode code, final Throwable cause,
                           final String detailBase, final String detailKey, final Object[] detailParams) {
        super(cause);
        this._status = status;
        this._code = code;
        this._bundleName = detailBase;
        this._key = detailKey;
        this._parameters = (detailParams != null) ? Arrays.copyOf(detailParams, detailParams.length) : null;
    }

    @Override
    public StatusType getStatus() {
        return _status;
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
    public String getMessage(final Locale locale) {
        return Messages.localize(_bundleName, locale, _key, _parameters);
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage(Locale.getDefault());
    }

    @Override
    public boolean isRetryable() {
        return retryable;
    }
}
