/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import static com.iwave.ext.windows.winrm.WinRMConstants.GET_URI;

import java.util.Map;

import org.w3c.dom.Document;

public abstract class WinRMGetOperation<T> extends WinRMOperation<T> {
    public WinRMGetOperation(WinRMTarget target, String resourceUri) {
        this(target, resourceUri, null);
    }

    public WinRMGetOperation(WinRMTarget target, String resourceUri, Map<String, String> selectorSet) {
        super(target);
        setResourceUri(resourceUri);
        setSelectorSet(selectorSet);
    }

    @Override
    public T execute() throws WinRMException {
        debug("Get %s, SelectorSet: %s", getResourceUri(), getSelectorSet());
        WinRMRequest request = createRequest();
        Document response = sendRequest(request);
        T result = processResponse(response);
        return result;
    }

    protected WinRMRequest createRequest() {
        WinRMRequest request = createBaseRequest();
        request.setActionUri(GET_URI);
        return request;
    }

    protected abstract T processResponse(Document response);
}
