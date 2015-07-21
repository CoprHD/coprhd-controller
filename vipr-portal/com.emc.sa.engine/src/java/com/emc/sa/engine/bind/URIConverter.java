/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.bind;

import java.net.URI;

import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang.StringUtils;

public class URIConverter implements Converter {

    @SuppressWarnings("rawtypes")
    @Override
    public Object convert(Class type, Object value) {
        final String string = value.toString();
        if (StringUtils.isNotBlank(string)) {
            return URI.create(string);
        }
        else {
            return null;
        }
    }
}
