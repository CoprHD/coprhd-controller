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
            final String strURI = value.toString();
            if (StringUtils.isNotBlank(strURI)) {
                // the uri can be wrapped in quotes when coming from an order due to how we prepare the
                // orderparameters, so we need to handle this case.
                String substrURI = StringUtils.substringBetween(strURI, "\"");
                if (URIUtil.isValid(strURI)) {
                    return URI.create(strURI);
                } else if (substrURI != null && URIUtil.isValid(substrURI)) {
                    return URI.create(substrURI);
                } else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
    }

}
