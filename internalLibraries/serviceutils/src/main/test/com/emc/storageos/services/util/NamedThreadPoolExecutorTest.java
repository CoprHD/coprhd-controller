/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class NamedThreadPoolExecutorTest {

    class MyCallable implements Callable<String> {

        /**
         * @return the name of the thread that executes this callable
         */
        @Override
        public String call() throws Exception {
            return Thread.currentThread().getName();
        }
    }

    class MyTask implements Runnable {
        private String name = "";

        @Override
        public void run() {
            try {
                setName(Thread.currentThread().getName());
                System.out.println("Name=" + name);
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
            }
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void testNamedExecutorWithoutAppendedTask() throws Exception {
        String poolName = "David Webber's web";
        NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor(poolName, 1);
        Assert.assertTrue("Thread pool appendTaskName flag should have been false.",
                executor.isAppendTaskName() == false);
        Runnable worker = new MyTask();
        executor.execute(worker);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        // task name should not be appended
        String threadName = ((MyTask) worker).getName();
        Assert.assertTrue(String.format("Thread name '%s' does not contain pool name '%s'",
                threadName, poolName), threadName.indexOf(poolName) > -1);
        Assert.assertFalse(String.format("Thread name '%s' should not contain task name '%s'",
                threadName, poolName), threadName.indexOf(MyTask.class.getSimpleName()) > 0);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }

    }

    @Test
    public void testNamedExecutorWithAppendedTask() throws Exception {
        String poolName = "David Webber's web";
        NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor(poolName, 2);
        executor.setAppendTaskName(true);
        Assert.assertTrue("Thread pool appendTaskName flag should have been on.",
                executor.isAppendTaskName() == true);
        MyTask worker = new MyTask();
        executor.execute(worker);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // task name should be appended
        String threadName = ((MyTask) worker).getName();
        Assert.assertTrue(String.format("Thread name '%s' does not contain pool name '%s'",
                threadName, poolName), threadName.indexOf(poolName) > -1);
        Assert.assertTrue(String.format("Thread name '%s' does not contain task name '%s'",
                threadName, poolName), threadName.indexOf(MyTask.class.getSimpleName()) > -1);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNamedExecutorWithAppendedAnonymousTask() throws Exception {
        String poolName = "David Webber's web";
        NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor(poolName, 2);
        executor.setAppendTaskName(true);
        Assert.assertTrue("Thread pool appendTaskName flag should have been on.",
                executor.isAppendTaskName() == true);
        final StringBuilder runtimeThreadName = new StringBuilder();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                runtimeThreadName.append(Thread.currentThread().getName());
            }
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // verify the name has changed
        Assert.assertTrue(
                "Thread name does not reflect anonymous task: " + runtimeThreadName.toString(),
                runtimeThreadName.toString().indexOf(NamedThreadPoolHelper.ANONYMOUS_NAME) > -1);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNamedScheduledExecutorWithAppendedTask() throws Exception {
        String poolName = "David Webber's web";
        ScheduledThreadPoolExecutor executor = new NamedScheduledThreadPoolExecutor(poolName, 1);
        ((NamedScheduledThreadPoolExecutor) executor).setAppendTaskName(true);
        Assert.assertTrue("Thread pool appendTaskName flag should have been on.",
                ((NamedScheduledThreadPoolExecutor) executor).isAppendTaskName() == true);

        for (int i = 0; i < 2; i++) {
            MyCallable worker = new MyCallable();
            String threadName = executor.schedule(worker, 1L, TimeUnit.SECONDS).get();
            Assert.assertTrue(String.format("Thread name '%s' does not contain pool name '%s'",
                    threadName, poolName), threadName.indexOf(poolName) > -1);
            Assert.assertTrue(String.format("Thread name '%s' does not contain task name '%s'",
                    threadName, poolName),
                    threadName.indexOf(MyCallable.class.getSimpleName()) > -1);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNamedScheduledExecutorWithoutAppendedTask() throws Exception {
        String poolName = "David Webber's web";
        ScheduledThreadPoolExecutor executor = new NamedScheduledThreadPoolExecutor(poolName, 1);

        for (int i = 0; i < 2; i++) {
            MyCallable worker = new MyCallable();
            String threadName = executor.schedule(worker, 1L, TimeUnit.SECONDS).get();
            Assert.assertTrue(String.format("Thread name '%s' does not contain pool name '%s'",
                    threadName, poolName), threadName.indexOf(poolName) > -1);
            Assert.assertFalse(String.format("Thread name '%s' should not contain pool name '%s'",
                    threadName, poolName),
                    threadName.indexOf(MyCallable.class.getSimpleName()) > -1);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNamedExecutorThreadNameChangesDuringRun() throws Exception {
        String poolName = "David Webber's web";
        NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor(poolName, 2);
        executor.setAppendTaskName(true);
        Assert.assertTrue("Thread pool appendTaskName flag should have been on.",
                executor.isAppendTaskName() == true);
        Future<String> futureThreadName = executor.submit(new MyCallable());
        String threadName = futureThreadName.get();
        Assert.assertTrue(
                "Thread Name does not match the base + number ::task pattern during execution: "
                        + threadName,
                threadName.matches(poolName + "_\\d+::" + MyCallable.class.getSimpleName()));
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNamedScheduledExecutorThreadNameChangesDuringRun() throws Exception {
        String poolName = "David Webber's web";
        NamedScheduledThreadPoolExecutor executor = new NamedScheduledThreadPoolExecutor(poolName,
                2);
        executor.setAppendTaskName(true);
        Assert.assertTrue("Thread pool appendTaskName flag should have been on.",
                executor.isAppendTaskName() == true);
        Future<String> futureThreadName = executor.submit(new MyCallable());
        String threadName = futureThreadName.get();
        Assert.assertTrue(
                "Thread Name does not match the base + number ::task pattern during execution: "
                        + threadName,
                threadName.matches(poolName + "_\\d+::" + MyCallable.class.getSimpleName()));
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNamedExecutorThreadNameRevertsAfterRun() throws Exception {
        String poolName = "David Webber's web";
        NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor(poolName, 2);
        executor.setAppendTaskName(true);
        Assert.assertTrue("Thread pool appendTaskName flag should have been on.",
                executor.isAppendTaskName() == true);
        Future<Thread> futureThread = executor.submit(new Callable<Thread>() {
            @Override
            public Thread call() {
                return Thread.currentThread();
            }
        });

        Thread t = futureThread.get();
        TimeUnit.SECONDS.sleep(1);
        String nameAfter = t.getName();
        Assert.assertTrue("Thread name does not revert to base name after execution: " + nameAfter,
                nameAfter.matches(poolName + "_\\d+"));

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }
}
