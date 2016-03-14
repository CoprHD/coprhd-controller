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
import org.w3c.dom.DOMException;

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
import com.sun.jersey.api.client.ClientResponse;

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
     * @brief Update Quota class. If the quota class exists, then we update the quota class.
     * If the quota class does not exist, then we create a quota class.
     * 
     * @return Quota details of quota class
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{quota_class_name}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response updateQuotaClass(@PathParam("tenant_id") String openstackTenantId,            
            CinderQuotaClassDetails quotaClassUpdates, @Context HttpHeaders header) {
    	    
    	String quotaClassName = quotaClassUpdates.quota_class_set.get(CinderConstants.CLASS_NAME_KEY);
    	_log.debug("quotaClassUpdates.quota_class_set is {}",quotaClassUpdates.quota_class_set);
    	
    	boolean bVpoolQuotaUpdate = isVpoolQuotaUpdate(quotaClassUpdates.quota_class_set);
    	    	
    	String vpoolName = null;
    	VirtualPool objVpool = null;
    	
    	if(bVpoolQuotaUpdate){
			vpoolName = getVpoolName(quotaClassUpdates.quota_class_set);
            _log.debug("Vpool for which quota is being updated is {}", vpoolName);
            objVpool = getCinderHelper().getVpool(vpoolName);

            if (objVpool == null) {
                _log.error("vpool with the given name doesnt exist");
                throw APIException.badRequests.parameterIsNotValid(vpoolName);
            }          
		}
    	
    	List<URI> quotaClasses = _dbClient.queryByType(QuotaClassOfCinder.class, true);
    	CinderQuotaClassDetails resp = new CinderQuotaClassDetails();
    	
    	_log.debug("quotaClassName is {}", quotaClassName);
    	for (URI quota : quotaClasses) {
    		QuotaClassOfCinder quotaClass = _dbClient.queryObject(QuotaClassOfCinder.class, quota);
    		
    		if(quotaClass.getQuotaClass().equals(quotaClassName)){
    			_log.debug("quotaClass.getLimits() is {}",quotaClass.getLimits());
    			HashMap<String,String> qMap = (HashMap<String, String>)getQuotaHelper().convertKeyValPairsStringToMap(quotaClass.getLimits());
    			qMap.putAll(quotaClassUpdates.quota_class_set);
    			qMap.remove("class_name");
    			quotaClass.setLimits(getQuotaHelper().convertMapToKeyValPairsString(qMap));
    			_dbClient.updateObject(quotaClass);    
    			
    			//Till this step, we have updated the quota class in the DB.
    			//Let us say that the update request body is as below
    			//{"quota_class_set": {"class_name": "default", "gigabytes_vnx-vpool-1": 102, "snapshots_vnx-vpool-1": 102, "volumes_vnx-vpool-1": 102}}
    			//now the result should contain the limits already defined in db for the project or volume types 
    			//and the default class limits for attributes pertaining to project and volume types, which haven't been defined
    			//and the attribute that have just now been updated.
    			// 
    			//{"quota_class_set": {"gigabytes_ViPR-VMAX": -1, "snapshots_ViPR-VMAX": -1, 
    			//						"snapshots": 10, "volumes_ViPR-VMAX": -1, 
    			//						"snapshots_vnx-vpool-1": 102,     			//						
    			//						"gigabytes_vnx-vpool-1": 102, "volumes_vnx-vpool-1": 102, 
    			//						"gigabytes": 1000, 
    			//						"gigabytes_vt-1": -1, "volumes": 10 }}
    			qMap = getQuotaHelper().populateVolumeTypeDefaultsForQuotaClass(qMap , openstackTenantId, null);
    			resp.quota_class_set.putAll(qMap);
    			_log.debug("resp.quota_class_set is {}" , resp.quota_class_set.toString());
    			return getQuotaClassDetailFormat(header, resp);
    		}
    		else{
    			continue;
    		}    			    	    		 
    	}
    	
    	//If we reached here, it means that we don't have an entry in db for the requested 
    	//quota class with the name quotaClassUpdates.quota_class_set.get(CinderConstants.CLASS_NAME_KEY). 
    	//Then We must create quota class and populate it in DB. 
    	QuotaClassOfCinder objQuotaClass = new QuotaClassOfCinder();
    	objQuotaClass.setQuotaClass(quotaClassName);
    	
    	HashMap<String,String> qMap = new HashMap<String,String>();
    	//populating the default project level quota attributes.
    	qMap.putAll(getQuotaHelper().convertKeyValPairsStringToMap(getQuotaHelper().createDefaultLimitsInStrFormat(null)));
    	//project level quota attributes, if defined explicitly in the Update request, then we overwrite the defaults we
    	//loaded in the previous step
		qMap.putAll(quotaClassUpdates.quota_class_set);
		//remove this class_name attribute as it is not needed in the PUT response.
		qMap.remove(CinderConstants.CLASS_NAME_KEY);
		
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
     * @brief  This function loads the quota class details from the DB. For quota class attributes, which dont
     * have explicit value defined by the user, we populate the default values in the hashmap before returning.
     * @return Default Quota details of target_tenant_id
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{quota_class_name}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getQuotaClass(
            @PathParam("quota_class_name") String quotaClassName, @PathParam("tenant_id") String tenantId, @Context HttpHeaders header) {

    	_log.info("In getQuotaDefaults");
    	CinderQuotaClassDetails respCinderQuota = new CinderQuotaClassDetails();
    	
    	HashMap<String, String>  defaultQuotaMap = getQuotaHelper().loadFromDbQuotaClass(quotaClassName);
    	
    	if(defaultQuotaMap == null){
    		if(quotaClassName.equals(CinderConstants.DEFAULT_QUOTA_CLASS)){
    			defaultQuotaMap = getQuotaHelper().loadDefaultsMapFromDb();
    		}
    		else{
	    		_log.error("quota class with the given name doesnt exist");
	            throw APIException.badRequests.parameterIsNotValid(quotaClassName);
    		}
    	}
    	_log.debug("defaultQuotaMap is {}", defaultQuotaMap.toString());
    	
		defaultQuotaMap = getQuotaHelper().populateVolumeTypeDefaultsForQuotaClass(defaultQuotaMap , tenantId, null);
		respCinderQuota.quota_class_set.putAll(defaultQuotaMap); 
		    	
    	_log.debug("respCinderQuota is {}", respCinderQuota.quota_class_set.toString());
    	return getQuotaClassDetailFormat(header, respCinderQuota);    	
    }
    
      
   
    /**
     *Depending on mediatype either xml/json Quota class details response is returned 
     */
    private Response getQuotaClassDetailFormat(HttpHeaders header, CinderQuotaClassDetails respCinderClassQuota) {
        if (CinderApiUtils.getMediaType(header).equals("xml")) {
        	try {
            return CinderApiUtils.getCinderResponse(CinderApiUtils
                    .convertMapToXML(respCinderClassQuota.quota_class_set, "quota_set",String.class),
                    header, false);
        	}catch (DOMException e) {
				_log.info("DOM exception occured during converting Map to XML");
				return Response.status(500).build();
			} catch (IllegalArgumentException e) { 
				_log.info("Illegal argument exception occured during converting Map to XML");
				return Response.status(500).build();
			} catch (IllegalAccessException e) { 				
				_log.info("Illegal access exception occured during converting Map to XML");
				return Response.status(500).build();
			}
        } else if (CinderApiUtils.getMediaType(header).equals("json")) {
            return CinderApiUtils.getCinderResponse(respCinderClassQuota, header, false);
        } else {
            return Response.status(ClientResponse.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode()).entity(ClientResponse.Status.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase())
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
