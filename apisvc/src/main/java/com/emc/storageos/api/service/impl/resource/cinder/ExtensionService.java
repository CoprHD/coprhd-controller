/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.cinder.model.Extensions;
import com.emc.storageos.cinder.model.Version;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/v2")
@DefaultPermissions( readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = {ACL.OWN, ACL.ALL},
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = {ACL.OWN, ACL.ALL})
@SuppressWarnings({ "unchecked", "rawtypes" })
public class ExtensionService {

	public class Versions {
		private List<Version> versions;
		
		@XmlElement (name = "versions")
		public List<Version> getVersions(){
			if (versions == null){
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
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Version getVersion() {
		return getV2();
    }

   

    // INTERNAL FUNCTIONS
    
    private Version getV2() {
        Version v2 = new Version();
        v2.status = "CURRENT";
        v2.id = "v2.0";
        v2.updated = VolumeService.date(Calendar.getInstance().getTimeInMillis());
        return v2;
	}

}
