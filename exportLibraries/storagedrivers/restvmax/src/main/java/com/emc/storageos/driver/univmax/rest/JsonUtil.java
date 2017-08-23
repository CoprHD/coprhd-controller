/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest;

import static com.google.json.JsonSanitizer.sanitize;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class JsonUtil {

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String s, Class<T> clazz) {
        if (clazz == String.class) {
            return (T) s;
        }
        return new Gson().fromJson(sanitize(s), clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String s, Type type) {
        if (type.equals(String.class)) {
            return (T) s;
        }
        return new Gson().fromJson(sanitize(s), type);
    }

    @Deprecated
    public static <T> T fromJsonIter(String s, Type type) {
        return new Gson().fromJson(sanitize(s), type);
    }

    @SuppressWarnings("unchecked")
    public static String toJsonString(Object t) {
        if (t instanceof String) {
            return (String) t;
        }
        return new Gson().toJson(t);
    }

    public static String toJsonStringFinePrint(String rstr) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(rstr);
        return gson.toJson(je);
    }

    public static String toJsonStringFinePrint(Object t) {
        String rstr = JsonUtil.toJsonString(t);
        return JsonUtil.toJsonStringFinePrint(rstr);
    }
}
