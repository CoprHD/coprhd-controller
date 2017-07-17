/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

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

    /**
     * Parse json String into a List<T>
     * 
     * @param jsonString
     * @param T
     * @return
     */
    public static <T> List<T> parseJson2List(String jsonString, final Class T) {
        final List<T> items = new ArrayList<T>();
        JsonArray jsonArray = new com.google.gson.JsonParser().parse(jsonString).getAsJsonArray();
        jsonArray.forEach(new Consumer<JsonElement>() {

            @Override
            public void accept(JsonElement arg0) {
                T item = (T) new Gson().fromJson(arg0.toString(), T);
                items.add(item);
            }

        });
        return items;

    }
}
