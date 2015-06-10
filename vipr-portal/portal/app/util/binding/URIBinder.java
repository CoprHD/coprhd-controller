/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.binding;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;

import org.apache.commons.lang.StringUtils;

import play.data.binding.Global;
import play.data.binding.TypeBinder;

@Global
public class URIBinder implements TypeBinder<URI> {
    @Override
    public Object bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType)
            throws Exception {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return URI.create(value);
    }
}
