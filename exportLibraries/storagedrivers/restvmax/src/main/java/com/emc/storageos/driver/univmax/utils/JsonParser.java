/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.utils;

import static com.google.json.JsonSanitizer.sanitize;

import java.lang.reflect.Type;

import com.google.gson.Gson;

/**
 * @author fengs5
 *
 */
public class JsonParser {

    /**
     * Parse Json string to bean
     * 
     * @param jsonString
     * @param T
     * @return bean
     */
    public static <T> T parseJson2Bean(String jsonString, Class T) {
        return (T) new Gson().fromJson(jsonString, T);
    }

    public static <T> T parseJson2Bean(String jsonString, Type responseClazzType) {
        return new Gson().fromJson(sanitize(jsonString), responseClazzType);
    }

}
