/**
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
