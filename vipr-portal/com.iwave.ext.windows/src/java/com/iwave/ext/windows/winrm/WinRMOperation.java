/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import java.util.Map;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.Maps;
import com.iwave.ext.xml.XmlUtils;

public abstract class WinRMOperation<T> {
    private Logger log = Logger.getLogger(getClass());
    private WinRMTarget target;

    private String resourceUri;
    private Map<String, String> selectorSet = Maps.newHashMap();
    private Map<String, String> optionSet = Maps.newHashMap();

    public WinRMOperation(WinRMTarget target) {
        this.target = target;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public Map<String, String> getSelectorSet() {
        return selectorSet;
    }

    public void setSelectorSet(Map<String, String> selectorSet) {
        this.selectorSet.clear();
        if (selectorSet != null) {
            this.selectorSet.putAll(selectorSet);
        }
    }

    public void setSelector(String name, String value) {
        selectorSet.put(name, value);
    }

    public Map<String, String> getOptionSet() {
        return optionSet;
    }

    public void setOptionSet(Map<String, String> optionSet) {
        this.optionSet.clear();
        if (optionSet != null) {
            this.optionSet.putAll(optionSet);
        }
    }

    public void setOption(String name, String value) {
        optionSet.put(name, value);
    }

    public abstract T execute() throws WinRMException;

    protected WinRMTarget getTarget() {
        return target;
    }

    protected String getTargetUrl() {
        return target.getUrl().toExternalForm();
    }

    protected String sendMessage(String message) throws WinRMException {
        if (log.isDebugEnabled()) {
            debug("Request:\n%s", reformatXml(message));
        }
        try {
            String response = target.sendMessage(message);
            if (log.isDebugEnabled()) {
                debug("Response:\n%s", reformatXml(response));
            }
            return response;
        } catch (WinRMSoapException e) {
            if (!log.isDebugEnabled()) {
                info("Request:\n%s", reformatXml(message));
            }
            error("Error:\n%s", XmlUtils.formatXml(e.getSoapFault()));
            throw e;
        }
    }

    protected WinRMRequest createBaseRequest() {
        WinRMRequest request = new WinRMRequest();
        request.setUrl(getTargetUrl());
        request.setResourceUri(resourceUri);
        request.setSelectorSet(selectorSet);
        request.setOptionSet(optionSet);
        return request;
    }

    protected Document sendRequest(WinRMRequest request) throws WinRMException {
        String response = sendMessage(request.getContent());
        return XmlUtils.parseXml(response);
    }

    protected Element getSoapHeader(Document response) {
        return XmlUtils.selectElement(WinRMConstants.SOAP_HEADER_EXPR, response);
    }

    protected Element getSoapBody(Document response) {
        return XmlUtils.selectElement(WinRMConstants.SOAP_BODY_EXPR, response);
    }

    protected String reformatXml(String xml) {
        try {
            return XmlUtils.formatXml(XmlUtils.parseXml(xml));
        } catch (Exception e) {
            return xml;
        }
    }

    protected boolean isDebug() {
        return log.isDebugEnabled();
    }

    protected void debug(String message, Object... args) {
        if (log.isDebugEnabled()) {
            if (args.length > 0) {
                message = String.format(message, args);
            }
            log.debug(message);
        }
    }

    protected boolean isInfo() {
        return log.isInfoEnabled();
    }

    protected void info(String message, Object... args) {
        if (log.isInfoEnabled()) {
            if (args.length > 0) {
                message = String.format(message, args);
            }
            log.info(message);
        }
    }

    protected void error(String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        log.error(message);
    }

    protected void error(Throwable t, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        log.error(message, t);
    }
}
