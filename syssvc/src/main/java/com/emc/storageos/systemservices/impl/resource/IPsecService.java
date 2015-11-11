/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.systemservices.impl.ipsec.IPsecManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/ipsec")
public class IPsecService {

    @Autowired
    IPsecManager ipsecMgr;

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
        // auditOp(OperationTypeEnum.IPSEC_KEY_ROTATE, true, null, version);
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
