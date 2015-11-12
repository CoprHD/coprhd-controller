/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.impl;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.emc.storageos.model.TaskResourceRep;

public class TaskUtilTest {

    @Test
    public void testErrorOnNullTask() {
        assertEquals(true, TaskUtil.isError(null));
    }

    @Test
    public void testNullTaskError() {

        List<TaskResourceRep> tasks = new ArrayList<TaskResourceRep>();
        tasks.add(null);

        TaskUtil.checkForErrors(tasks);
    }
}
