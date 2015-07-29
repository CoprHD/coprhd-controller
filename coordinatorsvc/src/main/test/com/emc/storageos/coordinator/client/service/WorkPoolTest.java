/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Work pool unit test
 */
public class WorkPoolTest extends CoordinatorTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(WorkPoolTest.class);
    private static final String _poolName = "testPool";

    /**
     * Test assignment listener
     */
    private class AssignmentListener implements WorkPool.WorkAssignmentListener {
        private Set<WorkPool.Work> _assigned = new HashSet<WorkPool.Work>();
        private CountDownLatch _assignmentCount;

        public synchronized void setAssignmentLatch(CountDownLatch latch) {
            _assignmentCount = latch;
        }

        @Override
        public synchronized void assigned(Set<WorkPool.Work> work) {
            Set<WorkPool.Work> newAssignment = new HashSet<WorkPool.Work>(work);
            newAssignment.removeAll(_assigned);

            Iterator<WorkPool.Work> newWorkIt = newAssignment.iterator();
            while (newWorkIt.hasNext()) {
                WorkPool.Work next = newWorkIt.next();
                _logger.info("New work {}", next.getId());
                _assignmentCount.countDown();
            }

            Set<WorkPool.Work> removedAssignment = new HashSet<WorkPool.Work>(_assigned);
            removedAssignment.removeAll(work);
            Iterator<WorkPool.Work> removedAssignmentIt = removedAssignment.iterator();
            while (removedAssignmentIt.hasNext()) {
                WorkPool.Work next = removedAssignmentIt.next();
                _logger.info("Removed assignment {}", next.getId());
                _assignmentCount.countDown();

            }
            _assigned = work;
        }

        public synchronized Set<WorkPool.Work> getWorkItems() {
            return new HashSet<WorkPool.Work>(_assigned);
        }

    }

    @Test
    public void testWorkPool() throws Exception {
        final int totalWorkItemCount = 100;
        final int totalWorkerCount = 5;
        CountDownLatch assignmentLatch = new CountDownLatch(totalWorkItemCount);
        List<WorkPool> workerList = new ArrayList<WorkPool>();
        List<AssignmentListener> listeners = new ArrayList<AssignmentListener>();
        for (int index = 0; index < totalWorkerCount; index++) {
            AssignmentListener listener = new AssignmentListener();
            listener.setAssignmentLatch(assignmentLatch);
            listeners.add(listener);
            workerList.add(connectClient().getWorkPool(_poolName, listener));
        }
        for (int index = 0; index < totalWorkItemCount; index++) {
            workerList.get(index % totalWorkerCount).addWork(Integer.toString(index));
        }
        Assert.assertTrue(assignmentLatch.await(30, TimeUnit.SECONDS));

        assignmentLatch = new CountDownLatch(totalWorkItemCount);
        for (int index = 0; index < totalWorkerCount; index++) {
            listeners.get(index).setAssignmentLatch(assignmentLatch);
        }
        for (int index = 0; index < totalWorkItemCount; index++) {
            workerList.get(index % totalWorkerCount).removeWork(Integer.toString(index));
        }

        Assert.assertTrue(assignmentLatch.await(30, TimeUnit.SECONDS));
    }
}
