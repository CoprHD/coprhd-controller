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

    /**
     * Delete image from all available imageServers
     *
     * @param task {@link AsyncTask} instance
     */
    void deleteImage(AsyncTask task) throws InternalException;

    /**
     * Install OS
     * @param task {@link AsyncTask}
     * @param computeImageJob {@link URI} compute imageJob id
     * @throws InternalException
     */
    void installOperatingSystem(AsyncTask task, URI computeImageJob) throws InternalException;

    void verifyImageServerAndImportExistingImages(AsyncTask task, String opName);

    /**
     * Import image to all available imageServers
     *
     * @param task {@link AsyncTask} instance
     */
    void importImageToServers(AsyncTask task) throws InternalException;

}
