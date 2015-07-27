/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

/**
 * An Atomic sequence that provides the next unique OrderNumber throughout the ViPR cluster
 *
 * @author dmaddison
 */
public interface OrderNumberSequence {

    /** Provides the next unique order number */
    public long nextOrderNumber();
}
