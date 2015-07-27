/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.workflow.Workflow;

public interface ComputeDeviceController extends Controller {

    public void discoverComputeSystem(URI csId) throws InternalException;

    public void createHost(URI csId, URI vcpoolId, URI varray, URI hostId, String opId) throws InternalException;

    public String addStepsPreOsInstall(Workflow workflow, String waitFor, URI computeSystemId, URI hostId,
            String prepStepId);

    public String addStepsPostOsInstall(Workflow workflow, String waitFor, URI computeSystemId, URI computeElementId,
            URI hostId, String contextStepId, URI volumeId);

    public String addStepsDeactivateHost(Workflow workflow, String waitFor, URI hostId, boolean deactivateBootVolume)
            throws InternalException;

    public String addStepsVcenterClusterCleanup(Workflow workflow, String waitFor, URI clusterId)
            throws InternalException;

    public String addStepsVcenterHostCleanup(Workflow workflow, String waitFor, URI hostId) throws InternalException;

    public void setSanBootTarget(URI computeSystemId, URI computeElementId, URI hostId, URI volumeId,
            boolean waitForServerRestart) throws InternalException;

}
