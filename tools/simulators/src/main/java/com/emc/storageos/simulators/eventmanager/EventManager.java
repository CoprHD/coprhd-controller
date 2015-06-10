/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.eventmanager;

import com.emc.storageos.simulators.impl.resource.Events;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event manager class
 */
public class EventManager {
    public class TimedEvent extends Events.CustomEvents {
        private long time = System.currentTimeMillis();

        TimedEvent(String id, String event_type, String start, String devid) {
            super(id, event_type, start, devid);
        }

        public long getTime() {
            return time;
        }
    }

    private ScheduledThreadPoolExecutor _eventExecutor;
    private static final EventManager _eventManager = new EventManager();
    private int _threadPoolSize = 1;
    private long _intervalUnit = 1;
    private long _executeInterval = 3 * _intervalUnit;
    private ConcurrentLinkedQueue<TimedEvent> _eventQueue = new ConcurrentLinkedQueue<TimedEvent>();
    private AtomicLong instance_id = new AtomicLong(1);

    private EventManager() {}

    public static EventManager getInstance() {
        return _eventManager;
    }

    public ConcurrentLinkedQueue<TimedEvent> getEventQueue() {
        return _eventQueue;
    }

    /**
     * Service start
     */
    public void start() {
        _eventExecutor = new ScheduledThreadPoolExecutor(_threadPoolSize);
        _eventExecutor.scheduleWithFixedDelay(new eventHandler(), _executeInterval, _executeInterval, TimeUnit.SECONDS);
    }

    /**
     * Event generation
     */
    private class eventHandler implements Runnable {
        @Override
        public void run() {
            // add new events
            int id = new Random().nextInt(EventList.event_ids.length);
            _eventQueue.add(new TimedEvent("" + EventList.event_ids[id], "type", "1", "1"));

            // delete old events more than 30 mins
            long current = System.currentTimeMillis();
            while (_eventQueue.peek().time < current - 1800000)
                _eventQueue.poll();
        }
    }
}
