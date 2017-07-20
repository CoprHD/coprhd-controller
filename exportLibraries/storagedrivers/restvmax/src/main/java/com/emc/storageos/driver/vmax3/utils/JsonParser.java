/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.utils;

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

}
