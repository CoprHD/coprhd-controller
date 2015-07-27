/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import org.apache.curator.framework.recipes.queue.QueueSerializer;

/**
 * DistributedQueue serializer implementation for ControlRequest
 */
public class ControlRequestSerializer implements QueueSerializer<ControlRequest> {
   @Override
   public byte[] serialize(ControlRequest item) {
      return item.serialize();
   }

   @Override
   public ControlRequest deserialize(byte[] bytes) {
      return ControlRequest.deserialize(bytes);
   }
}
