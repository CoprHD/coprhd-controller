/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
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
