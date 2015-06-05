/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.text.StrSubstitutor;

import com.google.common.collect.Maps;
import com.iwave.ext.xml.XmlStringBuilder;

public class WinRMRequest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private static final String REQUEST_TEMPLATE = readRequestTemplate();
    private static final String URL = "url";
    private static final String RESOURCE_URI = "resourceUri";
    private static final String ACTION_URI = "actionUri";
    private static final String MESSAGE_ID = "messageId";
    private static final String SELECTOR_SET = "selectorSet";
    private static final String OPTION_SET = "optionSet";
    private static final String TIMEOUT_SECONDS = "timeoutSeconds";
    private static final String BODY = "body";

    private Map<String, String> parameters = Maps.newHashMap();
    private StrSubstitutor substitutor = new StrSubstitutor(parameters);

    public WinRMRequest() {
        setBody("");
        setSelectorSet("");
        setOptionSet("");
        setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        setMessageId(UUID.randomUUID().toString());
    }

    public void setUrl(String url) {
        setParameter(URL, url);
    }

    public void setResourceUri(String resourceUri) {
        setParameter(RESOURCE_URI, resourceUri);
    }

    public void setActionUri(String actionUri) {
        setParameter(ACTION_URI, actionUri);
    }

    public void setMessageId(String messageId) {
        setParameter(MESSAGE_ID, messageId);
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        setParameter(TIMEOUT_SECONDS, ""+timeoutSeconds);
    }

    public void setSelectorSet(String selectorSet) {
        setParameter(SELECTOR_SET, selectorSet);
    }

    public void setSelector(String name, String value) {
        Map<String, String> selectorSet = Maps.newHashMap();
        selectorSet.put(name, value);
        setSelectorSet(selectorSet);
    }

    public void setSelectorSet(Map<String, String> selectorSet) {
        XmlStringBuilder sb = new XmlStringBuilder();
        if (selectorSet.size() > 0) {
            sb.start("w:SelectorSet");
            for (Map.Entry<String, String> selector : selectorSet.entrySet()) {
                sb.start("w:Selector").attr("Name", selector.getKey());
                if (selector.getValue() != null) {
                    sb.text(selector.getValue());
                }
                else {
                    sb.attr("xsi:nil", "true");
                }
                sb.end();
            }
            sb.end();
        }
        setSelectorSet(sb.toString());
    }

    public void setOptionSet(String optionSet) {
        setParameter(OPTION_SET, optionSet);
    }

    public void setOptionSet(Map<String, String> optionSet) {
        XmlStringBuilder sb = new XmlStringBuilder();
        if (optionSet.size() > 0) {
            sb.start("w:OptionSet");
            for (Map.Entry<String, String> option : optionSet.entrySet()) {
                sb.start("w:Option").attr("Name", option.getKey());
                if (option.getValue() != null) {
                    sb.text(option.getValue());
                }
                else {
                    sb.attr("xsi:nil", "true");
                }
                sb.end();
            }
            sb.end();
        }
        setOptionSet(sb.toString());
    }

    public void setBody(String body) {
        setParameter(BODY, body);
    }

    public void setParameter(String name, String value) {
        parameters.put(name, value);
    }

    public String evaluate(String value) {
        return substitutor.replace(value);
    }

    public String getContent() {
        return evaluate(REQUEST_TEMPLATE);
    }

    /**
     * Reads the WinRM request template.
     * 
     * @return the WinRM request template.
     */
    private static String readRequestTemplate() {
        InputStream in = WinRMRequest.class.getResourceAsStream("WinRMRequest.xml");
        try {
            return IOUtils.toString(in, "UTF-8");
        }
        catch (IOException e) {
            throw new Error(e);
        }
    }
}
