/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;

public interface ImageServerController extends Controller {

    public void importImage(AsyncTask task, URI imageServerId) throws InternalException;

    public void deleteImage(AsyncTask task, URI imageServerId) throws InternalException;

    public void installOperatingSystem(AsyncTask task, URI computeImageJob) throws InternalException;

}
