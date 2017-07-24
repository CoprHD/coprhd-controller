/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

public interface ComputeController extends Controller {

    public void discoverComputeSystems(AsyncTask[] tasks) throws InternalException;

    public void createHosts(URI varray, URI poolId, Map<Host,URI> hostsMap, AsyncTask[] tasks) throws InternalException;

    public void clearDeviceSession(URI computeSystemId) throws InternalException;

    public void deactivateHost(AsyncTask[] tasks) throws InternalException;
}
