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

package com.emc.storageos.coordinator.client.service;

/**
 * ServiceNotFoundException is thrown by CoordinatorClient#locateServie if
 * target service name/version is not found in coordinator
 */
public class ServiceNotFoundException extends RuntimeException {
   public ServiceNotFoundException() {      
   }
   
   public ServiceNotFoundException(String msg) {
      super(msg);
   }
   
   public ServiceNotFoundException(Throwable cause) {
      super(cause);
   }
   
   public ServiceNotFoundException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
