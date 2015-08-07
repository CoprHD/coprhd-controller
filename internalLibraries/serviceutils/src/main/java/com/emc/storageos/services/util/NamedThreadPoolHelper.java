/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.util;

import org.apache.commons.lang.StringUtils;

class NamedThreadPoolHelper {
    private static final String TASK_DELIMITER = "::";
    public static final String ANONYMOUS_NAME = "Anonymous";

    private NamedThreadPoolHelper() {
    }

    /**
     * Add the name of the provided Runnable task to the name of the provided
     * Thread
     */
    public static void changeNameBeforeExecute(Thread t, Runnable r) {
        String addMe = (r instanceof NamedTask) ? ((NamedTask<?>) r).getName() : r.getClass()
                .getSimpleName();
        if (StringUtils.isBlank(addMe)) {
            addMe = ANONYMOUS_NAME;
        }
        t.setName(t.getName() + TASK_DELIMITER + addMe);
    }

    /**
     * Remove the name of the provided Runnable task from the current thread, if
     * it's present
     */
    public static void resetNameAfterExecute(Runnable r, Throwable t) {
        if (r != null) {
            String removeMe = (r instanceof NamedTask) ? ((NamedTask<?>) r).getName() : r
                    .getClass().getSimpleName();
            if (StringUtils.isBlank(removeMe)) {
                removeMe = ANONYMOUS_NAME;
            }
            Thread.currentThread().setName(
                    StringUtils.removeEnd(Thread.currentThread().getName(), TASK_DELIMITER
                            + removeMe));
        }
    }
}
