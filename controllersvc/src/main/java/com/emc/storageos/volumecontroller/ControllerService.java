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

package com.emc.storageos.volumecontroller;

import java.io.IOException;

import org.springframework.context.ApplicationContextAware;

/**
 * The main lifecycle API for volume controller service
 */
public interface ControllerService extends ApplicationContextAware{
   /**
    * Starts controller service and registers with coordinator.   If coordinator is
    * not available, controller service will repeatedly attempt to connect until
    * service is registered.
    *
    * @throws Exception todo refine
    */
   public void start() throws Exception;

   /**
    * Unregisters from coordinator and stops controller service.  Service is stopped
    * even if coordinator is not available.   Coordinator cluster will remove
    * controller service registration if services terminates uncleanly after timeout.
    *
    * @throws Exception
    */
   public void stop() throws Exception;
}
