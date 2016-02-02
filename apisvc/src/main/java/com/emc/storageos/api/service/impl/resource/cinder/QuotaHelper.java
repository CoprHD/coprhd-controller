/* Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.model.UsageStats;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaClassOfCinder;
import com.emc.storageos.db.client.model.QuotaOfCinder;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.security.authentication.StorageOSUser;

public class QuotaHelper {
    private DbClient _dbClient;
    private PermissionsHelper _permissionsHelper;
    private static final Logger _log = LoggerFactory.getLogger(QuotaHelper.class);
    private static QuotaHelper instQuotaHelpers = null;
    private static Object mutex = new Object();
    private static final long GB = 1024 * 1024 * 1024;

    private QuotaHelper(DbClient dbClient, PermissionsHelper permissionsHelper) {
        _dbClient = dbClient;
        _permissionsHelper = permissionsHelper;
    }

    public QuotaHelper() {
    }

  
    public static QuotaHelper getInstance(DbClient dbClient, PermissionsHelper permissionsHelper) {

        if (instQuotaHelpers == null) {
            synchronized (mutex) {
                if (instQuotaHelpers == null) {
                    instQuotaHelpers = new QuotaHelper(dbClient, permissionsHelper);
                }
            }
        }
        return instQuotaHelpers;
    }
    
    private CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient, _permissionsHelper);
    }
    
    
   
    /*
     * This function populates the quotas map with the vpool default quotas
     * @param qMap - existing quotas hashmap
     * @param openstackTargetTenantId - target tenant for whose vpools we are populating
     * @param vPoolName - if we want to specifically populate a specific pool, we have to pass this.
     * @return updated quotas map
     */
    public HashMap<String, String> populateVolumeTypeDefaultsForQuotaClass(HashMap<String, String> qMap, 
    																	  String openstackTargetTenantId,
    																	  String vPoolName){    	
    	List<URI> vpools = _dbClient.queryByType(VirtualPool.class, true);
		
        for (URI vpool : vpools) {
            VirtualPool pool = _dbClient.queryObject(VirtualPool.class, vpool);
            _log.debug("Looking up vpool {}", pool.getLabel());
            
            if( (vPoolName != null) && (!vPoolName.equals(pool.getLabel().toString())) ){
            	continue;            	
            }
            if (pool != null && pool.getType().equalsIgnoreCase(VirtualPool.Type.block.name())) {
                if (_permissionsHelper.tenantHasUsageACL(URI.create(openstackTargetTenantId), pool)) {
                
                	for(CinderConstants.ResourceQuotaDefaults item : CinderConstants.ResourceQuotaDefaults.class.getEnumConstants()){
                		if(!qMap.containsKey(item.getResource()+"_"+pool.getLabel())){
                			qMap.put(item.getResource()+"_"+pool.getLabel() , 
                											String.valueOf(CinderConstants.DEFAULT_VOLUME_TYPE_QUOTA));
                		}
            		}                    
                }
            }
        }        
        return qMap;    	    	
    }
    
    
    /*
     * This function populates the quotas map with the vpool default quotas     * 
     * @param vPoolName - vpool for which we want create default limits. If null, we create defaults string
     * for the project.
     * @return string with limits in key value pairs demarcated by :
     * example: "volumes=200:snapshots=100:gigabytes=10000:"
     */
    public String createDefaultLimitsInStrFormat(String vPoolName){    	
    	String suffix = "";
    	if(!TextUtils.isEmpty(vPoolName)){
    		suffix = "_" + vPoolName ;    		
    	}
    	String strLimits = "";        
        for(CinderConstants.ResourceQuotaDefaults item : CinderConstants.ResourceQuotaDefaults.class.getEnumConstants()){
        	if(TextUtils.isEmpty(vPoolName)){
        		strLimits = strLimits + item.getResource() + suffix + "=" + String.valueOf(item.getLimit()) +":";	
        	}
        	else{
        		strLimits = strLimits + item.getResource() + suffix + "=" + String.valueOf(CinderConstants.DEFAULT_VOLUME_TYPE_QUOTA) +":";
        	}
        	
 		}  
        
        return strLimits;
    }
       
    /*
     * This function populates the hashmap with quota class details 
     * @param className - quota class name
     * @return hashmap of strings with resource name being the key and resource limit
     * being the value of the hashmap 
     * 
     */
    public HashMap<String, String> loadFromDbQuotaClass(String className){
    	List<URI> quotasInDb = _dbClient.queryByType(QuotaClassOfCinder.class, true);    	
    	    	
    	for (URI quota : quotasInDb) {
    		QuotaClassOfCinder defaultQuota = _dbClient.queryObject(QuotaClassOfCinder.class, quota);
    		
    		if(defaultQuota.getQuotaClass().equals(className)){
    			_log.debug("defaultQuota.getLimits() is {}", defaultQuota.getLimits());
    			return convertKeyValPairsStringToMap(defaultQuota.getLimits());    			
    		}    		    	
    	}    
    	return null;
    }
    
    
    /*
     * This function populates the quotas map with the vpool default quotas     * 
     * @param quotasInStringFormat - quota limits in string representation ex:"volumes=200:snapshots=100:gigabytes=10000:"
     * @return hashmap of resource and quota limit pairs.{(volumes , 200) , (snapshots,100),(gigabytes,10000)}
     * 
     */
    public HashMap<String, String> convertKeyValPairsStringToMap(String quotasInStringFormat){
    	String[] splits = quotasInStringFormat.split(":");
    	HashMap<String, String> resp = new HashMap<String, String> ();
    	
		for(String resourceLimit: splits){
			_log.info("resourceLimit is {}", resourceLimit);
			if(!resourceLimit.equalsIgnoreCase("")){
				String[] resourceAndItsLimit = resourceLimit.split("=", 2);
				String resource = resourceAndItsLimit[0];
				String quota = resourceAndItsLimit[1];
				resp.put(resource, quota);
			}
		}
		_log.info("resp  is {}", resp.toString());
		return resp;
    }
    
    /*
     * @brief This function populates the quotas map with the vpool default quotas      
     * 
     * @param quotasMap of resource and quota limit pairs."{(volumes , 200) , (snapshots,100),(gigabytes,10000)}"
     * @return String- quota limits in string representation ex:"volumes=200:snapshots=100:gigabytes=10000:"
     * 
     */
    public String convertMapToKeyValPairsString(HashMap<String, String> quotasMap){    	
    	String strResp = "";
		for(String item : quotasMap.keySet()){			
			strResp = strResp+ item + "=" + quotasMap.get(item)+":";
		}		
		return strResp;		
    }
    /**
     * @brief load default quota class details from the DB. 
     * If the db entry is not there, then it is created with core attributes defined in the enum
     * CinderConstants.ResourceQuotaDefaults
     * @return HashMap with details of resource and its limits.
     */
    public HashMap<String,String> loadDefaultsMapFromDb(){    	    	    
    	HashMap<String, String>  defaultQuotaMap = new HashMap<String, String>();    	
    	HashMap<String,String> map = loadFromDbQuotaClass(CinderConstants.DEFAULT_QUOTA_CLASS);
    		
    	if(map == null){
    		QuotaClassOfCinder objQuotaClassOfCinder = new QuotaClassOfCinder();    		
    		String tmpStr = "";
    		
    		for(CinderConstants.ResourceQuotaDefaults item : CinderConstants.ResourceQuotaDefaults.class.getEnumConstants()){
    			tmpStr = tmpStr + item.getResource() + "=" + item.getLimit()+":";
    			defaultQuotaMap.put(item.getResource(), String.valueOf(item.getLimit()));
    		}
    		    		
    		objQuotaClassOfCinder.setLimits(tmpStr);
    		objQuotaClassOfCinder.setQuotaClass(CinderConstants.DEFAULT_QUOTA_CLASS);
    		objQuotaClassOfCinder.setId(URI.create(UUID.randomUUID().toString()));
            _dbClient.createObject(objQuotaClassOfCinder);
    	}
    	else{
    		return map;
    	}
    	return defaultQuotaMap;
    }


    /**
     * Get quota for provided vpool
     * 
     * 
     * @prereq none
     * 
     * @param tenantId
     * @param vpool
     * 
     * @brief get vpool quota
     * @return quota
     */

    public QuotaOfCinder getVPoolQuota(String tenantId, VirtualPool vpool, StorageOSUser user) {
        _log.debug("In getVPoolQuota");
        Project project = getCinderHelper().getProject(tenantId.toString(), user);

        List<URI> quotas = _dbClient.queryByType(QuotaOfCinder.class, true);
        for (URI quota : quotas) {
            QuotaOfCinder quotaObj = _dbClient.queryObject(QuotaOfCinder.class, quota);
            URI vpoolUri = quotaObj.getVpool();

            if (vpoolUri == null) {
                continue;
            }
            else if ((quotaObj.getProject() != null) &&
                    (quotaObj.getProject().toString().equalsIgnoreCase(project.getId().toString()))) {
                VirtualPool pool = _dbClient.queryObject(VirtualPool.class, vpoolUri);

                if ((pool != null) && (pool.getLabel().equals(vpool.getLabel())) && (vpool.getLabel() != null)
                        && (vpool.getLabel().length() > 0)) {
                    return quotaObj;
                }
            }
        }

        HashMap< String, String> qMap = getCompleteDefaultConfiguration(tenantId);
        return createVpoolDefaultQuota(project, vpool, qMap);
    }

    /**
     * Get quota for given project/tenant
     * 
     * 
     * @prereq none
     * 
     * @param tenantId
     * 
     * @brief get project quota
     * @return quota
     */
    public QuotaOfCinder getProjectQuota(String tenantId, StorageOSUser user) {
        Project project = getCinderHelper().getProject(tenantId.toString(), user);

        List<URI> quotas = _dbClient.queryByType(QuotaOfCinder.class, true);
        for (URI quota : quotas) {
            QuotaOfCinder quotaObj = _dbClient.queryObject(QuotaOfCinder.class, quota);
            URI vpoolUri = quotaObj.getVpool();
            if (vpoolUri != null) {
                continue;
            }

            if ((quotaObj.getProject() != null) &&
                    (quotaObj.getProject().toString().equalsIgnoreCase(project.getId().toString()))) {
                return quotaObj;
            }
        }
        HashMap< String, String> qMap = getCompleteDefaultConfiguration(tenantId);
        return createProjectDefaultQuota(project, qMap);
    }

    /**
     * Get default quota for provided project
     * 
     * 
     * @prereq none
     * 
     * @param project
     * param defaultQuotaMap (comprehensive default quota map)
     * @brief get project default quota
     * @return quota
     */
    public QuotaOfCinder createProjectDefaultQuota(Project project, HashMap<String,String> defaultQuotaMap) {
        
        long maxQuota = 0;
        
        if (project.getQuotaEnabled()) {
            maxQuota = (long) (project.getQuota().intValue());
        }
        else {
            maxQuota = Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.GIGABYTES.getResource()));
        }

        QuotaOfCinder quotaObj = new QuotaOfCinder();
        quotaObj.setId(URI.create(UUID.randomUUID().toString()));
        quotaObj.setProject(project.getId());        
        
        quotaObj.setVolumesLimit(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.VOLUMES.getResource())));
        quotaObj.setSnapshotsLimit(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.SNAPSHOTS.getResource())));
        quotaObj.setTotalQuota(maxQuota);
          
        _log.info("Creating default quota for project");
        _dbClient.createObject(quotaObj);
        return quotaObj;
    }

    /**
     * Create default quota for vpool
     * 
     * 
     * @prereq none
     * 
     * @param project
     * @param vpool
     * @param defaultQuotaMap (comprehensive default quota map)
     * @brief create default quota
     * @return quota
     */
    public QuotaOfCinder createVpoolDefaultQuota(Project project, VirtualPool vpool, HashMap<String,String> defaultQuotaMap) {
    	//HashMap<String, String> defaultQuotaMap = loadDefaultsMap();
        QuotaOfCinder objQuotaOfCinder = new QuotaOfCinder();
        objQuotaOfCinder.setProject(project.getId());
        
        objQuotaOfCinder.setVolumesLimit(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.VOLUMES.getResource()+"_"+vpool.getLabel())));
        objQuotaOfCinder.setSnapshotsLimit(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.SNAPSHOTS.getResource()+"_"+vpool.getLabel())));
        objQuotaOfCinder.setTotalQuota(Long.valueOf(defaultQuotaMap.get(CinderConstants.ResourceQuotaDefaults.GIGABYTES.getResource()+"_"+vpool.getLabel())));

        objQuotaOfCinder.setId(URI.create(UUID.randomUUID().toString()));
        objQuotaOfCinder.setVpool(vpool.getId());
            
        _log.info("Create vpool default quota");
        _dbClient.createObject(objQuotaOfCinder);
        return objQuotaOfCinder;
    }

    /**
     * Get usage statistics(like total number of volumes, snapshots and total size) for given vpool
     * 
     * 
     * @prereq none
     * 
     * @param vpool
     * 
     * @brief get statistics
     * @return UsageStats
     */
    public UsageStats getStorageStats(URI vpool, URI projectId) {
        UsageStats objStats = new UsageStats();
        long totalSnapshotsUsed = 0;
        long totalSizeUsed = 0;
        long totalVolumesUsed = 0;
        URIQueryResultList uris = new URIQueryResultList();

        if (vpool != null) {
            URIQueryResultList volUris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVirtualPoolVolumeConstraint(vpool), volUris);
            for (URI voluri : volUris) {
                Volume volume = _dbClient.queryObject(Volume.class, voluri);
                if (volume != null && !volume.getInactive()) {
                    totalSizeUsed += (long) (volume.getAllocatedCapacity() / GB);
                    totalVolumesUsed++;
                }

                URIQueryResultList snapList = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(voluri), snapList);

                for (URI snapUri : snapList) {
                    BlockSnapshot blockSnap = _dbClient.queryObject(BlockSnapshot.class, snapUri);
                    if (blockSnap != null && !blockSnap.getInactive()) {
                        _log.info("ProvisionedCapacity = {} ", blockSnap.getProvisionedCapacity());
                        totalSizeUsed += (long) (blockSnap.getProvisionedCapacity() / GB);
                        totalSnapshotsUsed++;
                    }
                }
            }
        }
        else {
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getProjectVolumeConstraint(projectId), uris);

            for (URI volUri : uris) {
                Volume volume = _dbClient.queryObject(Volume.class, volUri);
                if (volume != null && !volume.getInactive()) {
                    totalSizeUsed += (long) (volume.getAllocatedCapacity() / GB);
                    totalVolumesUsed++;
                }

                URIQueryResultList snapList = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volUri), snapList);

                for (URI snapUri : snapList) {
                    BlockSnapshot blockSnap = _dbClient.queryObject(BlockSnapshot.class, snapUri);
                    if (blockSnap != null && !blockSnap.getInactive()) {
                        totalSizeUsed += (long) (blockSnap.getProvisionedCapacity() / GB);
                        totalSnapshotsUsed++;
                    }
                }
            }
        }

        objStats.snapshots = totalSnapshotsUsed;
        objStats.volumes = totalVolumesUsed;
        objStats.spaceUsed = totalSizeUsed;

        return objStats;
    }
    
    /**
     * Get default quota class "default" from db. If not defined
     * create an entry in the db for the core attributes(snapshots, volumes, gigabytes). 
     * Additionally, it populates defaults for vpools if not already defined for the quota class.
     * 
     * 
     * @prereq none
     * 
     * @param openstackTargetTenantId openstack tenant id
     * 
     * @brief quotaMap
     * @return HashMap
     */
    public HashMap<String,String> getCompleteDefaultConfiguration(String openstackTargetTenantId){
    	HashMap<String,String> qMap = loadDefaultsMapFromDb();
    	qMap = populateVolumeTypeDefaultsForQuotaClass(qMap , openstackTargetTenantId, null);
    	_log.debug("getCompleteDefaultConfiguration is {}",qMap);
		return qMap;
    }
    
}
