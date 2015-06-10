/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import static com.iwave.ext.windows.winrm.WinRMConstants.ENUMERATE_URI;
import static com.iwave.ext.windows.winrm.WinRMConstants.ENUMERATION_URI;
import static com.iwave.ext.windows.winrm.WinRMConstants.PULL_URI;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.Lists;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

public abstract class WinRMEnumerateOperation<T> extends WinRMOperation<List<T>> {
    private static final String ENUMERATE = "Enumerate";
    private static final String PULL = "Pull";
    private static final String ENUMERATION_CONTEXT = "EnumerationContext";
    private static final String MAX_ELEMENTS = "MaxElements";

    private int maxElements = 10;

    public WinRMEnumerateOperation(WinRMTarget target, String resourceUri) {
        super(target);
        setResourceUri(resourceUri);
    }

    public int getMaxElements() {
        return maxElements;
    }

    public void setMaxElements(int maxElements) {
        this.maxElements = maxElements;
    }

    @Override
    public List<T> execute() throws WinRMException {
        debug("Enumerate %s", getResourceUri());
        WinRMRequest request = createEnumerateRequest();
        Document response = sendRequest(request);
        String enumerationContext = getEnumerationContext(response);
        List<T> results = pullItems(enumerationContext);
        return results;
    }

    protected List<T> pullItems(String enumerationContext) throws WinRMException {
        List<T> results = Lists.newArrayList();
        boolean done = false;
        while (!done) {
            WinRMRequest request = createPullRequest(enumerationContext);
            Document response = sendRequest(request);
            Element soapBody = getSoapBody(response);

            Element items = getItems(soapBody);
            if (items != null) {
                processItems(items, results);
            }

            enumerationContext = getEnumerationContext(soapBody);
            done = isEndOfSequence(response);
        }
        return results;
    }

    protected abstract void processItems(Element items, List<T> results);

    /**
     * Creates an Enumerate request.
     * 
     * @return the request.
     */
    public WinRMRequest createEnumerateRequest() {
        WinRMRequest request = createBaseRequest();
        request.setActionUri(ENUMERATE_URI);
        request.setBody(createEnumerateBody());
        return request;
    }

    /**
     * Creates the body of the enumeration.
     * 
     * @return the enumeration body.
     */
    protected String createEnumerateBody() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start(ENUMERATE).attr("xmlns", ENUMERATION_URI).end();
        return xml.toString();
    }

    /**
     * Creates a Pull request to pulling values from the specified enumeration.
     * 
     * @param enumerationContext the enumeration context value returned from the last enumerate or
     *        pull response.
     * @return the pull request.
     */
    public WinRMRequest createPullRequest(String enumerationContext) {
        WinRMRequest request = createBaseRequest();
        request.setActionUri(PULL_URI);
        request.setBody(createPullBody(enumerationContext));
        return request;
    }

    /**
     * Creates the body for the Pull request.
     * 
     * @param enumerationContext the enumeration context value.
     * @return the Pull body.
     */
    protected String createPullBody(String enumerationContext) {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start(PULL).attr("xmlns", ENUMERATION_URI);
        xml.element(ENUMERATION_CONTEXT, enumerationContext);
        xml.element(MAX_ELEMENTS, String.valueOf(maxElements));
        xml.end();
        return xml.toString();
    }

    protected String getEnumerationContext(Document response) {
        return getEnumerationContext(getSoapBody(response));
    }

    protected boolean isEndOfSequence(Document response) {
        return isEndOfSequence(getSoapBody(response));
    }

    protected Element getItems(Document response) {
        return getItems(getSoapBody(response));
    }

    protected String getEnumerationContext(Element soapBody) {
        return XmlUtils.selectText(WinRMConstants.ENUMERATION_CONTEXT_EXPR, soapBody);
    }

    protected boolean isEndOfSequence(Element soapBody) {
        return XmlUtils.selectElement(WinRMConstants.END_OF_SEQUENCE_EXPR, soapBody) != null;
    }

    protected Element getItems(Element soapBody) {
        return XmlUtils.selectElement(WinRMConstants.ITEMS_EXPR, soapBody);
    }
}
