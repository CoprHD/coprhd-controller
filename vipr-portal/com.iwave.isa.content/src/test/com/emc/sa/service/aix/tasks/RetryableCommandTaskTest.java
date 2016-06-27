/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;



import org.junit.Test;

import com.iwave.ext.command.CommandException;

import static junit.framework.TestCase.assertEquals;

public class RetryableCommandTaskTest {

    private static final class RetryableCommandTaskWithCounter<T, E> extends RetryableCommandTask<String, CommandException> {

        private int retryCount = 0;

        public int getRetryCount() {
            return retryCount;
        }

        @Override
        protected String tryExecute() {
            // could not find associated hdisk for volume
            throw new HDiskNotFoundException("Could not find the hdisk");
        }

        @Override
        protected boolean canRetry(CommandException e) {
            retryCount++;
            return e instanceof HDiskNotFoundException;
        }

        @Override
        public String getLocalizedName() {
            return "test";
        }

        @Override
        protected void logInfo(String messageKey, Object... args) {
            System.out.println(messageKey);
        }

        @Override
        protected void logError(String messageKey, Object... args) {
            System.err.println(messageKey);
        }

    };

    @Test
    public void test() throws Exception {

        final int MAX_TRIES = 10;

        RetryableCommandTaskWithCounter<String, CommandException> task = new RetryableCommandTaskWithCounter<String, CommandException>();
        task.setDelay(100);
        task.setMaxTries(MAX_TRIES);

        try {
            task.executeTask();
        } catch (Exception e) {
            assertEquals((e instanceof HDiskNotFoundException), true);
        }

        assertEquals(MAX_TRIES, task.getRetryCount());

    }

}
