/* Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.model.CinderQuotaClassDetails;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaClassOfCinder;
import com.emc.storageos.db.client.model.QuotaOfCinder;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@Path("/v2/{tenant_id}/os-quota-class-sets")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class QuotaClassService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(QuotaClassService.class);
    private static final String EVENT_SERVICE_TYPE = "block";
   
    private CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient, _permissionsHelper);
    }


    private QuotaHelper getQuotaHelper() {
        return QuotaHelper.getInstance(_dbClient, _permissionsHelper);
    }
       
    /**
     * Update a quota class
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param quota_class_name the name of quota class which needs to be modified
     * 
     * @brief Update Quota
     * @return Quota details of quota class
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{quota_class_name}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response updateQuotaClass(@PathParam("tenant_id") String openstackTenantId,            
            CinderQuotaClassDetails quotaClassUpdates, @Context HttpHeaders header) {
    	    
    	String quotaClassName = quotaClassUpdates.quota_class_set.get(CinderConstants.CLASS_NAME_KEY);
    	_log.info("quotaClassUpdates.quota_class_set is {}",quotaClassUpdates.quota_class_set);
    	
    	boolean bVpoolQuotaUpdate = isVpoolQuotaUpdate(quotaClassUpdates.quota_class_set);
    	    	
    	String vpoolName = null;
    	VirtualPool objVpool = null;
    	
    	if(bVpoolQuotaUpdate){
			vpoolName = getVpoolName(quotaClassUpdates.quota_class_set);
            _log.info("Vpool for which quota is being updated is {}", vpoolName);
            objVpool = getCinderHelper().getVpool(vpoolName);

            if (objVpool == null) {
                _log.info("vpool with the given name doesnt exist");
                throw APIException.badRequests.parameterIsNotValid(vpoolName);
            }          
		}
    	
    	List<URI> quotaClasses = _dbClient.queryByType(QuotaClassOfCinder.class, true);
    	CinderQuotaClassDetails resp = new CinderQuotaClassDetails();
    	
    	_log.info("quotaClassName is {}", quotaClassName);
    	for (URI quota : quotaClasses) {
    		QuotaClassOfCinder quotaClass = _dbClient.queryObject(QuotaClassOfCinder.class, quota);
    		
    		if(quotaClass.getQuotaClass().equals(quotaClassName)){
    			_log.info("quotaClass.getLimits() is {}",quotaClass.getLimits());
    			HashMap<String,String> qMap = (HashMap<String, String>)getQuotaHelper().convertKeyValPairsStringToMap(quotaClass.getLimits());
    			qMap.putAll(quotaClassUpdates.quota_class_set);
    			qMap.remove("class_name");
    			quotaClass.setLimits(getQuotaHelper().convertMapToKeyValPairsString(qMap));
    			_dbClient.updateObject(quotaClass);    			
    			qMap = getQuotaHelper().populateVolumeTypeQuotasWhenNotDefined(qMap , openstackTenantId, null);
    			resp.quota_class_set.putAll(qMap);
    			_log.info("resp.quota_class_set is {}" , resp.quota_class_set.toString());
    			return getQuotaClassDetailFormat(header, resp);
    		}
    		else{
    			continue;
    		}    			    	    		 
    	}
    	
    	//If we reached here, it means that we dont have an entry in db. We must create one.
    	//hence lets create one.
    	QuotaClassOfCinder objQuotaClass = new QuotaClassOfCinder();
    	objQuotaClass.setQuotaClass(quotaClassName);
    	
    	HashMap<String,String> qMap = new HashMap<String,String>();
		qMap.putAll(quotaClassUpdates.quota_class_set);
		qMap.remove("class_name");

		objQuotaClass.setLimits(getQuotaHelper().convertMapToKeyValPairsString(qMap));
    	objQuotaClass.setId(URI.create(UUID.randomUUID().toString()));
        _dbClient.createObject(objQuotaClass);

		resp.quota_class_set.putAll(qMap);
    	return getQuotaClassDetailFormat(header, resp);
    }

    /**
     * Get the quota class details
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant asking for quotas
     * @param  quota_class_name the quota class name
     * @brief
     * @return Default Quota details of target_tenant_id
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{quota_class_name}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getQuotaClass(
            @PathParam("quota_class_name") String quota_class_name, @PathParam("tenant_id") String tenantId, @Context HttpHeaders header) {

    	_log.info("In getQuotaDefaults");
    	CinderQuotaClassDetails respCinderQuota = new CinderQuotaClassDetails();
    	
    	HashMap<String, String>  defaultQuotaMap = getQuotaHelper().loadFromQuotaClassFromDb(quota_class_name);
    	_log.info("defaultQuotaMap is {}", defaultQuotaMap.toString());
    	
    	if(respCinderQuota == null){
    		_log.info("quota class with the given name doesnt exist");
            throw APIException.badRequests.parameterIsNotValid(quota_class_name);
    	}
    				
		defaultQuotaMap = getQuotaHelper().populateVolumeTypeQuotasWhenNotDefined(defaultQuotaMap , tenantId, null);
		respCinderQuota.quota_class_set.putAll(defaultQuotaMap); 
		    	
    	_log.info("respCinderQuota is {}", respCinderQuota.quota_class_set.toString());
    	return getQuotaClassDetailFormat(header, respCinderQuota);    	
    }
    
      
   
    /**
     *Depending on mediatype either xml/json Quota class details response is returned 
     */
    private Response getQuotaClassDetailFormat(HttpHeaders header, CinderQuotaClassDetails respCinderClassQuota) {
        if (CinderApiUtils.getMediaType(header).equals("xml")) {
            return CinderApiUtils.getCinderResponse(CinderApiUtils
                    .convertMapToXML(respCinderClassQuota.quota_class_set, "quota_set"),
                    header, false);
        } else if (CinderApiUtils.getMediaType(header).equals("json")) {
            return CinderApiUtils.getCinderResponse(respCinderClassQuota, header, false);
        } else {
            return Response.status(415).entity("Unsupported Media Type")
                    .build();
        }
    }
    
    /**
     *This function will return true, if the user is updating the quota of a vpool w.r.t a project
     * otherwise it will be set to false, if the user is updating the quota of the project
     */
    private boolean isVpoolQuotaUpdate(Map<String, String> updateMap) {
        for (String iter : updateMap.keySet()) {
            if (iter.startsWith("volumes_") || iter.startsWith("snapshots_") || iter.startsWith("gigabytes_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vpool name for the passed quota set
     * 
     */
    private String getVpoolName(Map<String, String> updateMap) {
        for (String iter : updateMap.keySet()) {
            if (iter.startsWith("volumes_") || iter.startsWith("snapshots_") || iter.startsWith("gigabytes_")) {
                String[] splits = iter.split("_", 2);
                return splits[1];
            }
        }
        return null;
    }
    
    /**
     * returns tenant owner
     */
    @Override
    protected URI getTenantOwner(URI id) {
        QuotaOfCinder objQuota = (QuotaOfCinder) queryResource(id);
        Project objProj = _dbClient.queryObject(Project.class, objQuota.getProject());
        return objProj.getTenantOrg().getURI();
    }

    /**
     * Quota is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }
    
    /**
     * returns resource type
     */
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VOLUME;
    }
    
    /**
     * returns service  type
     */
    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, Volume.class);
    }

    /**
     * returns quota object
     */
    @Override
    protected DataObject queryResource(URI id) {
        QuotaOfCinder objQuotaOfCinder = _dbClient.queryObject(QuotaOfCinder.class, id);
        return objQuotaOfCinder;
    }

}
