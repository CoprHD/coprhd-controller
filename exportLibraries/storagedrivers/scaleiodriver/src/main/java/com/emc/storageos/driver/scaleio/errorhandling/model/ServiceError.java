/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.model;

import com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode;

import java.util.Locale;

import static com.emc.storageos.driver.scaleio.errorhandling.utils.Messages.localize;

public class ServiceError implements ServiceCoded {

    private int _code;

    private String _codeDescription;

    private String _detailedMessage;

    private Boolean _retryable;

    private ServiceError() {
    }

    public ServiceError(final ServiceCoded sce) {
        this(sce, Locale.ENGLISH);
    }

    public ServiceError(final ServiceCoded sce, final Locale locale) {
        this(sce.getServiceCode(), sce.isRetryable(), sce.getServiceCode().getSummary(locale), sce
                .getMessage(locale));
    }

    private ServiceError(ServiceCode serviceCode, boolean retryable, String description,
            String message) {
        _code = serviceCode.getCode();
        _retryable = retryable;
        _codeDescription = description;
        _detailedMessage = message;
    }

    /**
     * This constructor should only be called from {@link ExceptionMessagesProxy}
     * 
     * @param serviceCode
     * @param detailBase
     * @param detailKey
     * @param detailParams
     */
    @SuppressWarnings("unused")
    private ServiceError(final ServiceCode serviceCode, final String detailBase,
            final String detailKey, final Object[] detailParams) {
        // assume not retryable
        // assume english locale
        this(serviceCode, false, serviceCode.getSummary(), localize(detailBase, Locale.ENGLISH,
                detailKey, detailParams));
    }

    public static ServiceError buildServiceError(final ServiceCode code, final String details) {
        ServiceError serviceError = null;

        if (code != null) {
            serviceError = new ServiceError();
            serviceError.setCode(code.getCode());
            serviceError.setCodeDescription(code.getSummary());
            serviceError.setRetryable(null);
            serviceError.setMessage(details);
        }

        return serviceError;
    }

    public int getCode() {
        return _code;
    }

    public void setCode(final int code) {
        _code = code;
    }

    public String getCodeDescription() {
        return _codeDescription;
    }

    public void setCodeDescription(final String codeDescription) {
        _codeDescription = codeDescription;
    }

    @Override
    public String getMessage() {
        return _detailedMessage;
    }

    public void setMessage(final String detailedMessage) {
        _detailedMessage = detailedMessage;
    }

    @Override
    public boolean isRetryable() {
        return _retryable != null ? _retryable : false;
    }

    public Boolean getRetryable() {
        return _retryable;
    }

    public void setRetryable(final Boolean retryable) {
        _retryable = retryable;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Service Code: ");
        buffer.append(this._code);
        buffer.append(", Description: ");
        buffer.append(this._codeDescription);
        buffer.append(", Details: ");
        buffer.append(this._detailedMessage);
        return buffer.toString();
    }

    @Override
    public String getMessage(final Locale locale) {
        return getMessage();
    }

    @Override
    public com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode getServiceCode() {
        return ServiceCode.toServiceCode(_code);
    }
}
