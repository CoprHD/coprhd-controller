/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.model.tenant.TenantNamespaceInfo;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.varray.VirtualArrayInternalFlags;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Internal API for maintaining mappings between tenant and namespace
 */
public class InternalVirtualArrayServiceClient extends BaseServiceClient {

    private static final String INTERNAL_VARRAY_ROOT = "/internal/vdc/varrays";
    private static final String INTERNAL_VARRAY_SET_PROTECTIONTYPE = INTERNAL_VARRAY_ROOT + "/%s/protectionType?value=%s";
    private static final String INTERNAL_VARRAY_GET_PROTECTIONTYPE = INTERNAL_VARRAY_ROOT + "/%s/protectionType";
    private static final String INTERNAL_VARRAY_UNSET_PROTECTIONTYPE = INTERNAL_VARRAY_ROOT + "/%s/protectionType";
    private static final String INTERNAL_VARRAY_SET_REGISTERED = INTERNAL_VARRAY_ROOT + "/%s/deviceRegistered?value=%s";
    private static final String INTERNAL_VARRAY_GET_REGISTERED = INTERNAL_VARRAY_ROOT + "/%s/deviceRegistered";

    final private Logger _log = LoggerFactory
            .getLogger(InternalVirtualArrayServiceClient.class);

    /**
     * Client without target hosts
     */
    public InternalVirtualArrayServiceClient() {
    }

    /**
     * Client with specific host
     * 
     * @param server
     */
    public InternalVirtualArrayServiceClient(String server) {
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
     * Set protection type for varray
     * 
     * @param id the URN of a ViPR varray
     * @param value the value of the protection type
     * @return the updated virtual array info
     */
    public VirtualArrayRestRep setProtectionType(URI id, String protectionType) {
        String setFlag = String.format(INTERNAL_VARRAY_SET_PROTECTIONTYPE, id.toString(), protectionType);
        WebResource rRoot = createRequest(setFlag);
        VirtualArrayRestRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .put(VirtualArrayRestRep.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not set protection type to varray {}. Err:{}", id, e);
            if (e.getResponse().getStatus() == 404) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
        }
        return resp;
    }

    /**
     * Get protectionType attached with a virtual array
     * 
     * @param id the URN of a ViPR varray
     * @return the protection type
     */
    public String getProtectionType(URI id) {
        String getFlag = String.format(INTERNAL_VARRAY_GET_PROTECTIONTYPE, id.toString());
        WebResource rRoot = createRequest(getFlag);
        VirtualArrayInternalFlags resp = null;
        try {
            resp = addSignature(rRoot)
                    .get(VirtualArrayInternalFlags.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not get protection of varray {}. Err:{}", id, e);
            if (e.getResponse().getStatus() == 404) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
        }

        return resp.getProtectionType();
    }

    /**
     * Unset protection type assigned to the varray
     *
     * @param id the URN of a ViPR varry
     * @prereq none
     * @brief unset protection type field
     * @return No data returned in response body
     */
    public ClientResponse unsetProtectionType(URI id) {
        String unsetFlag = String.format(INTERNAL_VARRAY_UNSET_PROTECTIONTYPE, id.toString());
        WebResource rRoot = createRequest(unsetFlag);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot)
                    .delete(ClientResponse.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not unset protection flag from varray {}. Err:{}", id, e);
            if (e.getResponse().getStatus() == 404) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
        }
        return resp;
    }

    /**
     * Set device registered flag for varray
     * 
     * @param id the URN of a ViPR varray
     * @param value the device registered status
     * @return the updated virtual array info
     */
    public VirtualArrayRestRep setDeviceRegistered(URI id, Boolean deviceRegistered) {
        String setFlag = String.format(INTERNAL_VARRAY_SET_REGISTERED, id.toString(), String.valueOf(deviceRegistered));
        WebResource rRoot = createRequest(setFlag);
        VirtualArrayRestRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .put(VirtualArrayRestRep.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not set registered status to varray {}. Err:{}", id, e);
            if (e.getResponse().getStatus() == 404) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
        }
        return resp;
    }

    /**
     * Get device registered status of a virtual array
     * 
     * @param id the URN of a ViPR varray
     * @return the device registered status
     */
    public Boolean getDeviceRegistered(URI id) {
        String getFlag = String.format(INTERNAL_VARRAY_GET_REGISTERED, id.toString());
        WebResource rRoot = createRequest(getFlag);
        VirtualArrayInternalFlags resp = null;
        try {
            resp = addSignature(rRoot)
                    .get(VirtualArrayInternalFlags.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not get registered status of varray {}. Err:{}", id, e);
            if (e.getResponse().getStatus() == 404) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
        }

        return resp.getDeviceRegistered();
    }

}
