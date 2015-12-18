/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.systemservices.impl.ipsec.IPsecManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * Web resource class for IPsec
 */
@Path("/ipsec")
public class IPsecService {


    private static final String IPSEC_SERVICE_TYPE = "ipsec";
    @Autowired
    private IPsecManager ipsecMgr;

    @Autowired
    private AuditLogManager auditMgr;

    /**
     * Rotate the VIPR IPsec Pre-shared key.
     * @return the new version of the key which is used for checking status if needed
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public String rotateIPsecKey() {
        String version = ipsecMgr.rotateKey();
        auditMgr.recordAuditLog(null, null,
                IPSEC_SERVICE_TYPE,
                OperationTypeEnum.UPDATE_SYSTEM_PROPERTY,
                System.currentTimeMillis(),
                AuditLogManager.AUDITLOG_SUCCESS,
                null);

        return version;
    }

    /**
     * Check the status of IPsec.
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    public IPsecStatus getIPsecStatus() {
        return ipsecMgr.checkStatus();
    }
}
