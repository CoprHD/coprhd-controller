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

    public void discoverComputeSystems(AsyncTask[] tasks) throws InternalException;

    public void createHosts(URI varray, URI poolId, AsyncTask[] tasks) throws InternalException;

    public void clearDeviceSession(URI computeSystemId) throws InternalException;

    public void deactivateHost(AsyncTask[] tasks) throws InternalException;
}
