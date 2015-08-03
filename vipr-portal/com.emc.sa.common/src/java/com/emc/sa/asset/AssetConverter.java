/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset;

import java.net.URI;

import com.emc.storageos.db.client.URIUtil;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang.StringUtils;

/**
 * Simple class for supporting converting string asset values into types objects, mainly for URIs used as IDs.
 * 
 * @author jonnymiller
 */
public class AssetConverter {
    private static ConvertUtilsBean CONVERTER;

    static {
        CONVERTER = new ConvertUtilsBean();
        CONVERTER.register(new URIConverter(), URI.class);
    }

    public static Object convert(String value, Class<?> type) {
        return CONVERTER.convert(value, type);
    }

    private static class URIConverter implements Converter {
        @SuppressWarnings("rawtypes")
        @Override
        public Object convert(Class type, Object value) {
            final String string = value.toString();
            if (StringUtils.isNotBlank(string) && URIUtil.isValid(string)) {
                return URI.create(string);
            }
            else {
                return null;
            }
        }
    }

}
