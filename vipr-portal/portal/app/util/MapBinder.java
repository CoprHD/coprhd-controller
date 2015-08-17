/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import play.data.binding.TypeBinder;
import play.templates.JavaExtensions;

public class MapBinder implements TypeBinder<Map<String, String>> {

    private Map<String, String> getMapFromParams(String name) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        String prefix = name + ".";
        String[] values;
        String key, value;

        for (Map.Entry<String, String[]> entry : play.mvc.Scope.Params.current().all().entrySet()) {
            key = entry.getKey().toString();
            if (key.startsWith(prefix)) {
                values = entry.getValue();
                value = JavaExtensions.join(Arrays.asList(entry.getValue()), ", ");
                map.put(key, value);
            }
        }
        return map;
    }

    @Override
    public Object bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType)
            throws Exception {
        return getMapFromParams(name);
    }
}
