/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Runner {

    Logger log = LoggerFactory.getLogger(Runner.class.getName());

    int nThreads;
    TestCase tc;
    long nCount;

    public Runner(TestCase tc, int nThreads, long nCount) {
        this.nThreads = nThreads;
        this.tc = tc;
        this.nCount = nCount;
    }

    public void registerTestCase(TestCase tc) {
        this.tc = tc;
    }

    public void run() throws Exception {
        List<Callable<PerfStat>> testThreads = new ArrayList();
        for (int i = 0; i < nThreads; i++) {
            testThreads.add(new TestThread(tc, nCount));
        }

        ExecutorService executors = Executors.newFixedThreadPool(nThreads);
        List<Future<PerfStat>> results = executors.invokeAll(testThreads);

        mergeAndShowResult(results);

        System.exit(0);
    }

    private void mergeAndShowResult(List<Future<PerfStat>> results) throws Exception {
        log.info("Computing the results ...");

        // results to be shown
        int totalSucc = 0;
        int totalFail = 0;
        long totalTime = 0;
        long avgOpTime = 0;

        for (Future<PerfStat> r : results) {
            totalSucc += r.get().nSucc;
            totalFail += r.get().nFail;
            totalTime += r.get().totalOpTime;
        }

        System.out.println("ok #: " + totalSucc);
        System.out.println("fail #: " + totalFail);
        avgOpTime = totalTime/totalSucc;
        System.out.println("total OP Time(ms) : " + totalTime);
        System.out.println("avg OP Time(ms) : " + avgOpTime);
    }
}

class TestThread implements Callable<PerfStat> {

    Logger log = LoggerFactory.getLogger(TestThread.class.getName());

    public PerfStat stat = new PerfStat();
    TestCase tc;
    long nCount;

    public TestThread(TestCase tc, long nCount) {
        this.tc = tc;
        this.nCount = nCount;
    }

    @Override
    public PerfStat call() throws Exception {
        int cur = 0;
        log.info("Thread {} get started. Max running count is {}", Thread.currentThread().getName(), nCount);

        while (true) {
            try {
                tc.setup();
                stat.startOp();
                tc.execute();
                stat.succeedOp();
            } catch (Exception e) {
                stat.failOp();
            }

            cur ++;
            if (cur >= nCount) {
                break;
            }
        }

        return stat;
    }
}