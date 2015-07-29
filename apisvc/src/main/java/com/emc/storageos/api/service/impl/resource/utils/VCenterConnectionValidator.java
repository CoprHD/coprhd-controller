/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URL;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterParam;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.InvalidLogin;

public class VCenterConnectionValidator {

    protected final static Logger log = LoggerFactory.getLogger(VCenterConnectionValidator.class);

    public VCenterConnectionValidator() {
        // TODO Auto-generated constructor stub
    }

    public static String isVCenterConnectionValid(VcenterParam vcenterParam) {
        String ipAddress = null;
        if (vcenterParam instanceof VcenterCreateParam) {
            ipAddress = ((VcenterCreateParam) vcenterParam).getIpAddress();
        }
        else if (vcenterParam instanceof VcenterUpdateParam) {
            ipAddress = ((VcenterUpdateParam) vcenterParam).getIpAddress();
        }
        return validateVCenterAPIConnection(ipAddress, vcenterParam);
    }

    protected static String validateVCenterAPIConnection(String hostname, VcenterParam vcenterParam) {
        try {
            URL url = new URL("https", hostname, vcenterParam.getPortNumber(), "/sdk");

            VCenterAPI vcenterAPI = new VCenterAPI(url);
            try {
                vcenterAPI.login(vcenterParam.getUserName(), vcenterParam.getPassword());
                AboutInfo aboutInfo = vcenterAPI.getAboutInfo();
                if (!StringUtils.equals(VCenterAPI.VCENTER_API_TYPE, aboutInfo.getApiType())) {
                    throw APIException.badRequests.invalidNotAVCenter(hostname, aboutInfo.getFullName());
                }
                log.info(String.format("vCenter version: %s", aboutInfo.getVersion()));
            } finally {
                vcenterAPI.logout();
            }

        } catch (Exception e) {
            return getVcenterAPIMessage(e);
        }

        return null;
    }

    protected static String getVcenterAPIMessage(Throwable t) {
        String message = null;

        if (ExceptionUtils.getRootCause(t) instanceof InvalidLogin) {
            message = "Login failed";
        }

        if (message == null) {
            return "Failed to validate vCenter (Invalid host, port, username or password)";
        }
        else {
            return String.format("Failed to validate vCenter (%s)", message);
        }
    }

}
