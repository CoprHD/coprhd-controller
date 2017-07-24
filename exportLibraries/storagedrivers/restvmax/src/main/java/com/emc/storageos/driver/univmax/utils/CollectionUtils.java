/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.utils;

import java.util.Map;

/**
 * @author fengs5
 *
 */
public class CollectionUtils {

    public static boolean isNullOrEmptyMap(@SuppressWarnings("rawtypes") Map c) {
        return (c == null || c.isEmpty());
    }
}
