/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;



import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.model.CinderAvailZonesResp;
import com.emc.storageos.cinder.model.CinderAvailabiltyZone;
import com.emc.storageos.cinder.model.CinderExtension;
import com.emc.storageos.cinder.model.CinderExtensionsRestResp;
import com.emc.storageos.cinder.model.CinderLimits;
import com.emc.storageos.cinder.model.CinderLimitsDetail;
import com.emc.storageos.cinder.model.CinderOsServicesRestResp;
import com.emc.storageos.cinder.model.CinderOsService;
import com.emc.storageos.cinder.model.CinderOsVolumeTransferRestResp;
import com.emc.storageos.cinder.model.UsageStats;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaOfCinder;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import com.emc.storageos.db.client.model.StorageSystem;
import java.util.Iterator;
import java.util.Date;
import java.text.SimpleDateFormat;

@Path("/v2/{tenant_id}")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE }, writeRoles = {})
@SuppressWarnings({ "unchecked", "rawtypes" })
public class MiscService extends TaskResourceService {
    private static final Logger _log = LoggerFactory.getLogger(MiscService.class);
    private CinderHelpers helper = null;
    
    @Autowired
    private CoordinatorClient _coordinator;

    @Override
    public Class<CinderLimitsDetail> getResourceClass() {
        return CinderLimitsDetail.class;
    }

    private CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient, _permissionsHelper);
    }

    private QuotaHelper getQuotaHelper() {
        return QuotaHelper.getInstance(_dbClient, _permissionsHelper);
    }
    
    /**
     * Get Limits
     * 
     * @prereq none
     * @param tenant_id the URN of the tenant
     * @brief Get Limits
     * @return limits
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/limits")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getLimits(@PathParam("tenant_id") String openstack_tenant_id, @Context HttpHeaders header) {
        _log.info("START get limits");
        CinderLimits limitsResp = new CinderLimits();
        Project project = getCinderHelper().getProject(openstack_tenant_id.toString(), getUserFromContext());
        if (project == null) {
            throw APIException.badRequests.projectWithTagNonexistent(openstack_tenant_id);
        }
        
        HashMap<String, String> defaultQuotaMap = getQuotaHelper().loadDefaultsMapFromDb();
        
        int totalSizeUsed = 0;
        int maxQuota = Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.GIGABYTES.getResource())).intValue();
        int maxTotalVolumes = 0;
        int maxTotalSnapshots = 0;
        int totalVolumesUsed = 0;
        int totalSnapshotsUsed = 0;

        if (project.getQuotaEnabled()) {
            maxQuota = (int) (project.getQuota().intValue());
        }

        UsageStats objUsageStats = new UsageStats();
        objUsageStats = getQuotaHelper().getStorageStats(null, project.getId());

        totalVolumesUsed = (int) objUsageStats.volumes;
        totalSnapshotsUsed = (int) objUsageStats.snapshots;
        totalSizeUsed = (int) objUsageStats.spaceUsed;

        QuotaOfCinder projQuota = getQuotaHelper().getProjectQuota(openstack_tenant_id, getUserFromContext());
        if (projQuota != null) {
            maxTotalVolumes = projQuota.getVolumesLimit().intValue();
            maxTotalSnapshots = (int) projQuota.getSnapshotsLimit().intValue();
        }
        else {
            QuotaOfCinder quotaObj = new QuotaOfCinder();
            quotaObj.setId(URI.create(UUID.randomUUID().toString()));
            quotaObj.setProject(project.getId());
            quotaObj.setVolumesLimit(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.VOLUMES.getResource())));
            quotaObj.setSnapshotsLimit(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.SNAPSHOTS.getResource())));
            quotaObj.setTotalQuota((long) maxQuota);
            _dbClient.createObject(quotaObj);
            maxTotalSnapshots = quotaObj.getSnapshotsLimit().intValue();
            maxTotalVolumes = quotaObj.getVolumesLimit().intValue();
        }

        Map<String, Integer> absoluteDetailsMap = new HashMap<String, Integer>();
        absoluteDetailsMap.put("totalSnapshotsUsed", totalSnapshotsUsed);
        absoluteDetailsMap.put("maxTotalVolumeGigabytes", maxQuota);
        absoluteDetailsMap.put("totalGigabytesUsed", totalSizeUsed);
        absoluteDetailsMap.put("maxTotalSnapshots", maxTotalSnapshots);
        absoluteDetailsMap.put("totalVolumesUsed", totalVolumesUsed);
        absoluteDetailsMap.put("maxTotalVolumes", maxTotalVolumes);
        limitsResp.absolute = absoluteDetailsMap;
        _log.info("END get limits");
        return CinderApiUtils.getCinderResponse(limitsResp, header, true);
    }

    /**
     * Get extensions
     * 
     * @prereq none
     * @param tenant_id the URN of the tenant
     * @brief Get extensions
     * @return extensions
     * NOTE: Horizon invokes GET request on /extensions URI. 
     * This function is implemented, so as to not break the horizon UI.  
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/extensions")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getExtensions(@PathParam("tenant_id") String openstack_tenant_id, @Context HttpHeaders header) {
        // Here we ignore the openstack tenant id
        _log.info("START get extensions");
        CinderExtensionsRestResp extResp = new CinderExtensionsRestResp();
        CinderExtension objExt = new CinderExtension();
        objExt.alias = "os-availability-zone";
        objExt.description = "Describe Availability Zones.";
        objExt.namespace = "http://docs.openstack.org/volume/ext/os-availability-zone/api/v1";
        // TODO what do we do about the below hard coding?
        objExt.updated = "2013-06-27T00:00:00+00:00";
        objExt.name = "AvailabilityZones";
        extResp.getExtensions().add(objExt);
        _log.info("END get extensions");
        return CinderApiUtils.getCinderResponse(extResp, header, false);
    }

    /**
     * Get os-volume-transfer details
     * NOTE: This dummy function has been implemented so that it does not break the horizon.
     * We are not returning an unsupported exception, as horizon invokes this call, when we 
     * click on volumes tab. Hence, we implement the dummy.
     * @prereq none
     * @param tenant_id the URN of the tenant
     * @return transfers
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/os-volume-transfer/detail")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getVolumeTransfers(@PathParam("tenant_id") String openstack_tenant_id, @Context HttpHeaders header) {
        _log.info("START getVolumeTransfers");
        CinderOsVolumeTransferRestResp volTransferResp = new CinderOsVolumeTransferRestResp();
        return CinderApiUtils.getCinderResponse(volTransferResp, header, false);
    }

    /**
     * Get os-services
     * This function returns the cinder services and their details.
     * need to support system Information os-service
     * @prereq none
     * @param tenant_id the URN of the tenant
     * @return services
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/os-services")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getOsServices(@PathParam("tenant_id") String openstack_tenant_id, @Context HttpHeaders header) {
        _log.info("START getOsServices");

        boolean serviceDown = false;
        
        CinderOsServicesRestResp osServicesResp = new CinderOsServicesRestResp();
        CinderOsService volumeService = new CinderOsService();
        CinderOsService schedulerService = new CinderOsService();
        Date curDate = new Date();
        SimpleDateFormat  format = new SimpleDateFormat("dd-M-yyyy hh:mm:ss:SSS");
        
        volumeService.setBinary("coprHD-volume");
        volumeService.setZone("nova"); 
        schedulerService.setBinary("coprHD-scheduler");
        schedulerService.setZone("nova");        
        
        if (_coordinator == null || !_coordinator.isConnected())
        {
            _log.info("Coordinator service  is Down");
            serviceDown = true;
        } else 
        {
        	if((_coordinator.locateAllSvcsAllVers("geosvc").isEmpty()) || 
        	  (_coordinator.locateAllSvcsAllVers("geodbsvc").isEmpty()) ||
        	  (_coordinator.locateAllSvcsAllVers("dbsvc").isEmpty()) ||
        	  (_coordinator.locateAllSvcsAllVers("authsvc").isEmpty()) ||
        	  (_coordinator.locateAllSvcsAllVers("syssvc").isEmpty()) ||
        	  (_coordinator.locateAllSvcsAllVers("controllersvc").isEmpty()))       	  
        	{
                _log.info("Services like geosvc/geodbsvc/dbsvc/authsvc/syssvc/controllersvc may be Down");
                serviceDown = true;	
        	}  
        }
        
        if (serviceDown)
        {
            volumeService.setHost(""); 
           	volumeService.setState("down");
        	volumeService.setStatus("disabled");
        	volumeService.setDisabledReason("Required coprHD service or services may be down");
            curDate = new Date();
            volumeService.setUpdatedAt(format.format(curDate));
            osServicesResp.getServices().add(volumeService);
            
           	schedulerService.setState("down");
        	schedulerService.setStatus("disabled");
            schedulerService.setDisabledReason("Required coprHD service or services may be down");             
            schedulerService.setHost(""); 
            curDate = new Date();
            schedulerService.setUpdatedAt(format.format(curDate));
            osServicesResp.getServices().add(schedulerService);
        	
            return CinderApiUtils.getCinderResponse(osServicesResp, header, false);     	
        }
        
        String localNodeId = _coordinator.getInetAddessLookupMap().getNodeId();       
        volumeService.setHost(localNodeId); 
        //If Apisvc is running and any storage system is registered then coprHD-volume is up
        List<URI> ids = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> iter = _dbClient.queryIterativeObjects(StorageSystem.class, ids);
        if (iter.hasNext())
        {
        	volumeService.setState("up");
        	volumeService.setStatus("enabled");
        	volumeService.setDisabledReason(null);
        } else
        {
        	volumeService.setState("down");
        	volumeService.setStatus("disabled");
        	volumeService.setDisabledReason("No storage system is discovered");
        }
        
        curDate = new Date();
        volumeService.setUpdatedAt(format.format(curDate));
        osServicesResp.getServices().add(volumeService);

        schedulerService.setHost(localNodeId);        
        schedulerService.setState("up");
        schedulerService.setStatus("enabled");
        schedulerService.setDisabledReason(null);      
  
        curDate = new Date();
        schedulerService.setUpdatedAt(format.format(curDate));
        osServicesResp.getServices().add(schedulerService);
               
        return CinderApiUtils.getCinderResponse(osServicesResp, header, false);
    }

    /**
     * Get availability zones. 
     * NOTE:The availability zone in Openstack maps to the virtual array in the ViPR
     * 
     * @prereq none
     * @param tenant_id the URN of the tenant
     * @brief Get availability zones
     * @return availability zones
     */

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/os-availability-zone")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public CinderAvailZonesResp getAvailabilityZones(@PathParam("tenant_id") String openstack_tenant_id) {
        // Here we ignore the openstack tenant id
        _log.info("START get availability zones");

        CinderAvailZonesResp objCinderAvailZonesResp = new CinderAvailZonesResp();
        List<CinderAvailabiltyZone> az_list = objCinderAvailZonesResp.getAvailabilityZoneInfo();

        _log.debug("retrieving virtual arrays via dbclient");

        getCinderHelper().getAvailabilityZones(az_list, getUserFromContext());

        return objCinderAvailZonesResp;
    }

    private CinderAvailabiltyZone extractAvailabilityZone(VirtualArray nh) {
        CinderAvailabiltyZone objAz = new CinderAvailabiltyZone();
        objAz.zoneName = nh.getLabel();
        objAz.zoneState.available = !nh.getInactive();
        return objAz;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, VirtualPool.class);
    }

    @Override
    protected DataObject queryResource(URI id) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected URI getTenantOwner(URI id) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        throw new UnsupportedOperationException();
    }

}
