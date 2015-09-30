/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.emc.vipr.client.exceptions.ServiceErrorException;

public class TaskUtilTest {

    @Test
    public void testErrorOnNullTask() {
        assertEquals(true, TaskUtil.isError(null));
    }

    @Test
    public void testCheckForError() {
        final String ERROR_CODE_DESCRIPTION = "Task object is null. Unable to determine success of task";
        try {
            TaskUtil.checkForError(null);
        } catch (ServiceErrorException e) {
            assertEquals(ERROR_CODE_DESCRIPTION, e.getCodeDescription());
        }

    }
}
