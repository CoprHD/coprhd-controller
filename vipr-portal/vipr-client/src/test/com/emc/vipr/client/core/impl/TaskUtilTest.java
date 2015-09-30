/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TaskUtilTest {

    @Test
    public void testErrorOnNullTask() {
        assertEquals(true, TaskUtil.isError(null));
    }

}
