/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.NasCifsServer;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.security.authorization.BasePermissionsHelper;

public class ProjectUtility {
	
	private static final String ACCESS_ZONE_LOCAL_PROVIDER_NAME = "lsa-local-provider";

	/**
	 * Checks if the if the domain of the virtual NAS matches with domain of the
	 * project
	 * 
	 * @param permissionsHelper
	 *            the permissionHelper
	 * @param project
	 *            the Project object
	 * @param vNAS
	 *            the VirtualNAS object
	 * @return true if the domain of the virtual NAS matches with domain of the
	 *         project or the project does not have domains configured, false
	 *         otherwise
	 */
	public static boolean doesProjectDomainMatchesWithVNASDomain(
			PermissionsHelper permissionsHelper, Project project,
			VirtualNAS vNAS) {

		Set<String> projectDomains = getDomainsOfProject(permissionsHelper,
				project);
		return doesProjectDomainMatchesWithVNASDomain(projectDomains, vNAS);

	}

	/**
	 * Checks if the if the domain of the virtual NAS matches with the set of
	 * given domains
	 * 
	 * @param projectDomains set of project domains
	 * @param vNAS the VirtualNAS object
	 * @return true if the domain of the virtual NAS matches with domain of the
	 *         project or the project does not have domains configured or there
	 *         are no domains configured with VirtualNAS, false otherwise
	 */
	public static boolean doesProjectDomainMatchesWithVNASDomain(
			Set<String> projectDomains, VirtualNAS vNAS) {

		if (projectDomains != null && !projectDomains.isEmpty()) {
			if (vNAS.getCifsServersMap() != null
					&& !vNAS.getCifsServersMap().isEmpty()) {
				Set<Entry<String, NasCifsServer>> nasCifsServers = vNAS
						.getCifsServersMap().entrySet();
				for (Entry<String, NasCifsServer> nasCifsServer : nasCifsServers) {
					if(ACCESS_ZONE_LOCAL_PROVIDER_NAME.equals(nasCifsServer.getKey())) {
						continue;
					}
					NasCifsServer cifsServer = nasCifsServer.getValue();
					if (projectDomains.contains(cifsServer.getDomain()
							.toUpperCase())) {
						return true;
					}
				}
			} else {
				return true;
			}
		} else {
			return true;
		}

		return false;

	}
	
	/**
	 * Returns the set of domains configured with the given project
	 * @param permissionsHelper
	 * @param project
	 * @return Set of project domains
	 */
	public static Set<String> getDomainsOfProject(
			PermissionsHelper permissionsHelper, Project project) {

		Set<String> projectDomains = new HashSet<String>();
		if (project != null) {
			NamedURI tenantUri = project.getTenantOrg();
			TenantOrg tenant = permissionsHelper.getObjectById(tenantUri,
					TenantOrg.class);
			if (tenant != null && tenant.getUserMappings() != null) {
				for (AbstractChangeTrackingSet<String> userMappingSet : tenant
						.getUserMappings().values()) {
					for (String existingMapping : userMappingSet) {
						UserMappingParam userMap = BasePermissionsHelper.UserMapping
								.toParam(BasePermissionsHelper.UserMapping
										.fromString(existingMapping));
						projectDomains.add(userMap.getDomain().toUpperCase());
					}
				}
			}
		}

		return projectDomains;
	}

}
