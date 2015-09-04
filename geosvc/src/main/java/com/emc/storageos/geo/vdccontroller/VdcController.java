/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.geo.vdccontroller;

import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

import java.security.KeyStore;
import java.util.List;

/**
 * Vdc Controller Interface
 */
public interface VdcController {
    /**
     * Connect the vdc into current geo system.
     *
     */
    public void connectVdc(VirtualDataCenter vdc, String task, List<Object> taskParams) throws InternalException;

    /**
     * Remove vdc from geo system
     */
    public void removeVdc(VirtualDataCenter vdc, String task, List<Object> taskParams) throws InternalException;

    /**
     * Update the vdc info
     *
     */
    public void updateVdc(VirtualDataCenter vdc, String task, List<Object> taskParams) throws Exception;

    /**
     * Disconnect the vdc info
     *
     */
    public void disconnectVdc(VirtualDataCenter vdc, String task, List<Object> taskParams) throws InternalException;

    /**
     * Reconnect the vdc info
     *
     */
    public void reconnectVdc(VirtualDataCenter vdc, String task, List<Object> taskParams) throws InternalException;

    public void setKeystore(KeyStore keystore);
}
