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

    void deleteImage(AsyncTask task) throws InternalException;

    void installOperatingSystem(AsyncTask task, URI computeImageJob) throws InternalException;

    void verifyImageServerAndImportExistingImages(AsyncTask task, String opName);

    void importImageToServers(AsyncTask task) throws InternalException;

}
