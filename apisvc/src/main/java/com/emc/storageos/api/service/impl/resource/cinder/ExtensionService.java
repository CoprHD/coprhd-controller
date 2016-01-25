/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.api.service.impl.resource.cinder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.cinder.model.Version;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/v2")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class ExtensionService {

    public class Versions {
        private List<Version> versions;

        @XmlElement(name = "versions")
        public List<Version> getVersions() {
            if (versions == null) {
                versions = new ArrayList<Version>();
            }
            return versions;
        }
    }

    /**
     * Get the current API version
     * 
     * 
     * @prereq none
     * 
     * 
     * @brief Show version
     * @return Version
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Version getVersion(@HeaderParam("X-Cinder-V1-Call") String isV1Call) {
    	Version v = new Version();
        v.status = "CURRENT";
        if (isV1Call != null) {
        	v.id = "v1.0";
	        v.updated = VolumeService.date(Calendar.getInstance().getTimeInMillis());
        }
        else{
        	v.id = "v2.0";
	        v.updated = VolumeService.date(Calendar.getInstance().getTimeInMillis());        	
        }
        return v;	
    }

}
