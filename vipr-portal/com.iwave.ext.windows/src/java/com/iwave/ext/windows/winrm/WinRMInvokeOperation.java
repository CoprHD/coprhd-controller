/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.iwave.ext.xml.XmlUtils;

public abstract class WinRMInvokeOperation<T> extends WinRMOperation<T> {
    private String actionUri;

    public WinRMInvokeOperation(WinRMTarget target) {
        super(target);
    }

    public WinRMInvokeOperation(WinRMTarget target, String resourceUri, String actionUri) {
        super(target);
        setResourceUri(resourceUri);
        setActionUri(actionUri);
    }

    public String getActionUri() {
        return actionUri;
    }

    public void setActionUri(String actionUri) {
        this.actionUri = actionUri;
    }

    @Override
    public T execute() throws WinRMException {
        WinRMRequest request = createRequest();
        Document response = sendRequest(request);
        Element output = getOutput(response);
        T result = processOutput(output);
        return result;
    }

    protected WinRMRequest createRequest() {
        WinRMRequest request = createBaseRequest();
        request.setActionUri(actionUri);
        request.setBody(createInput());
        return request;
    }

    protected Element getOutput(Document response) {
        Element soapBody = getSoapBody(response);
        Element output = XmlUtils.getFirstChildElement(soapBody);
        return output;
    }

    protected abstract String createInput();

    protected abstract T processOutput(Element output);
}
