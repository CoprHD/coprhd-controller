/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

/**
 * QueueTooBusyException is thrown by DistributedQueue#put if maximum
 * capacity is reached
 */
public class QueueTooBusyException extends RuntimeException {
}
