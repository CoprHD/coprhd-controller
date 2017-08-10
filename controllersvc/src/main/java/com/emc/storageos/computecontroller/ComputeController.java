/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

public interface ComputeController extends Controller {

   /**
    * discover the compute systems
    *
    * @param tasks array of AsyncTasks for discovery
    * @return
    */
    public void discoverComputeSystems(AsyncTask[] tasks) throws InternalException;

  /**
   * Create hosts using the specified params 
   * 
   * @param varray URI of the varray
   * @param poolId URI of the compute virtual pool
   * @param sptId URI of the service profile template, optional
   * @param tasks array of AsyncTasks
   *
   * @return
   */
    public void createHosts(URI varray, URI poolId, URI sptId, AsyncTask[] tasks) throws InternalException;
    
   /**
    * Clear device session for the compute system
    *
    * @param computeSystemId URI of the computeSystem
    * @return
    */
    public void clearDeviceSession(URI computeSystemId) throws InternalException;

    
   /**
    * Deactivate host
    *
    * @param tasks array of AsyncTasks for deleting hosts
    * @return 
    */
    public void deactivateHost(AsyncTask[] tasks) throws InternalException;
}
