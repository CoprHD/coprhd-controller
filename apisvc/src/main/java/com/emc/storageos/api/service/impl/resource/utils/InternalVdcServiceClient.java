/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.vdc.VirtualDataCenterList;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

public class InternalVdcServiceClient extends BaseServiceClient {
    private static final String INTERNAL_VDC_ROOT = "/internal/vdc";
    private static final String INTERNAL_VDC_GET = INTERNAL_VDC_ROOT + "/%s";
    private static final String INTERNAL_VDC_SET_INUSE = INTERNAL_VDC_ROOT + "/%s?inuse=%s";

    final private Logger _log = LoggerFactory
            .getLogger(InternalTenantServiceClient.class);

    public InternalVdcServiceClient() {
    }

    public InternalVdcServiceClient(String server) {
        setServer(server);
    }

    /**
     * Make client associated with this api server host (IP)
     * 
     * @param server IP
     */
    @Override
    public void setServer(String server) {
        setServiceURI(URI.create("https://" + server + ":8443"));
    }

    /**
     * Get config information for given vdc
     * 
     * @param vdcId - vdc uuid
     * @return
     */
    public VirtualDataCenterRestRep getVdc(URI vdcId) {
        String getVdcPath = String.format(INTERNAL_VDC_GET, vdcId.toString());
        WebResource rRoot = createRequest(getVdcPath);
        VirtualDataCenterRestRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .get(VirtualDataCenterRestRep.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not get information for vdc {}. Err:{}", vdcId, e);
        }
        return resp;
    }

    /**
     * List all vdc uuid
     * 
     * @return
     */
    public VirtualDataCenterList listVdc() {
        WebResource rRoot = createRequest(INTERNAL_VDC_ROOT);
        VirtualDataCenterList resp = null;
        try {
            resp = addSignature(rRoot)
                    .get(VirtualDataCenterList.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not list vdc. Err:{}", e);
        }
        return resp;
    }

    /**
     * Update vdc in use. If inUse state is true, the vdc should not be removed from geo system
     * 
     * @param vdcId - vdc uuid
     * @param inUse
     * @return
     */
    public ClientResponse setVdcInUse(URI vdcId, Boolean inUse) {
        String setInUsePath = String.format(INTERNAL_VDC_SET_INUSE, vdcId.toString(), inUse);
        WebResource rRoot = createRequest(setInUsePath);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot).put(ClientResponse.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not set vdc inuse for {}. Err:{}", vdcId, e);
        }
        return resp;
    }
}
