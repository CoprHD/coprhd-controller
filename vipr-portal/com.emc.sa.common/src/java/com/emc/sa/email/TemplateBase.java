/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.email;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.text.StrSubstitutor;

import com.emc.sa.util.Messages;
import com.google.common.collect.Maps;

public class TemplateBase {

    protected Messages messages = new Messages("com.emc.sa.email.EmailMessages");
    private Map<String, String> parameters = Maps.newHashMap();
    private StrSubstitutor substitutor = new StrSubstitutor(new TemplateStrLookup(parameters));

    public TemplateBase() {

    }

    protected String getParameter(String name) {
        return parameters.get(name);
    }

    protected void setParameter(String name, String value) {
        parameters.put(name, value);
    }

    protected String evaluate(String value) {
        addEmailMessagesToParameters();
        return substitutor.replace(value);
    }

    private void addEmailMessagesToParameters() {
        for (String key : messages.getKeySet()) {
            if (parameters.containsKey(key) == false) {
                parameters.put(key, messages.get(key));
            }
        }
    }

    protected static String readTemplate(String resource) {
        InputStream in = TemplateBase.class.getResourceAsStream(resource);
        try {
            return IOUtils.toString(in, "UTF-8");
        } catch (IOException e) {
            throw new Error(e);
        }
    }

}
