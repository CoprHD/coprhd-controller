/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.util;

public abstract class AbstractItemProcessor<T> implements ItemProcessor<T> {
    public void startItems() throws Exception {
    }

    public void endItems() throws Exception {
    }
}
