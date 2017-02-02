/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.HighAvailability;
import models.StorageSystemTypes;
import models.datatable.DirectDriverExportDataTable;
import models.datatable.DirectDriverVolumeDataTable;
import models.datatable.DirectDriverVolumeDataTable.DirectDriverVolume;
import models.datatable.NetworksDataTable;
import models.datatable.DirectDriverVolumeDataTable.DirectDriverVolume;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import com.emc.storageos.api.service.impl.resource.StorageSystemService;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.InitiatorParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.mvc.Controller;
import util.BourneUtil;
import util.HostUtils;
import util.NetworkSystemUtils;
import util.NetworkUtils;
import util.ProjectUtils;
import util.StoragePoolUtils;
import util.StorageSystemUtils;
import util.TaskUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class DirectDriver extends Controller{
	static final Logger _log = LoggerFactory.getLogger(DirectDriver.class);
 
    public static void createDirecVolume() {
        renderArgs.put("storageArrayTypeList", Arrays.asList(StorageSystemTypes.OPTIONS));
        renderArgs.put("storageArrayTypeListForVplex", Arrays.asList(StorageSystemTypes.OPTIONS));
          renderArgs.put("hostList",getHosts());
        
        renderArgs.put("ha_Options", Lists.newArrayList(                
        		HighAvailability.option(HighAvailability.VPLEX_LOCAL),
                HighAvailability.option(HighAvailability.VPLEX_DISTRIBUTED)));
        
        String tenantId = Models.currentAdminTenant();
        renderArgs.put("projectOptions", ProjectUtils.getProjects(tenantId));
        renderArgs.put("varrayOptions", VirtualArrayUtils.getVirtualArrays());
        renderArgs.put("vpoolOptions", VirtualPoolUtils.getBlockVirtualPools());
       render();
    }
    
    public static void getIpFromType(String type) {
        List<StorageSystemRestRep> storageSystems = StorageSystemUtils.getStorageSystems();
        List<StorageSystemRestRep> results = Lists.newArrayList();
        for(StorageSystemRestRep system : storageSystems) {
            if (system.getSystemType()!=null && system.getSystemType().equals(type)) {
                results.add(system);
            } if(type.equals("vmax") && system.getSystemType().equals("vnxblock")) {
                results.add(system);
            }
        }
        renderJSON((results));
    }
    
    public static List<HostRestRep> getHosts() {
        String tenantId = Models.currentAdminTenant();
        List<HostRestRep> hosts = HostUtils.getHosts(tenantId);
        return hosts;
    }
    
    public static void getPoolsFromSystem(String id) {
        List<StoragePoolRestRep> storagePools = StoragePoolUtils.getStoragePools(id);
        renderJSON(storagePools);
    }
    
    public static void getNetworksFromSystem(){
    	List<NetworkRestRep> networks = NetworkUtils.getNetworks();
    	renderJSON(networks);
    }
    
    public static void exportDirectVolume(String id) {
        
    }
    
    @FlashException(keep = true, referrer = { "createDirecVolume" })
    public static void saveVolume(DirectDriverForm volume) {
    	if(volume.vplexId != null && !(volume.vplexId.isEmpty()))
    	{
    		URI valueForVmaxVolume_1 = volume.save(volume.name.concat("1"), volume.ipAddress, volume.pool,
    				volume.varray, volume.vpool, volume.backendNetwork_1, volume.backendNetwork_2);
    		if(valueForVmaxVolume_1 == null) {
            	flash.error("Check ViPR logs for information.");
        	}
  			URI valueForVmaxVolume_2 = null;
  			if(volume.ha.contains("DISTRIBUTED")){
    			valueForVmaxVolume_2 = volume.save(volume.name.concat("2"), volume.ipAddressForHA,
    				volume.poolForHA, volume.varrayForHA, volume.vpoolForHA, volume.haBackendNetwork_1, volume.haBackendNetwork_2);
        		if(valueForVmaxVolume_2 == null) {
                	flash.error("Check ViPR logs for information.");
            	}
    		}

    		
    		boolean valueForVplexVolume = volume.save(volume.name, volume.vplexId, valueForVmaxVolume_1.toString(),
    			valueForVmaxVolume_2.toString(), volume.varray, volume.vpool);
    		if(!valueForVplexVolume)
    		{
            	flash.error("Check ViPR logs for information.");    			
    		}
    	}
    	else{
    		URI value = volume.save(volume.name, volume.ipAddress, volume.pool,
    							volume.varray, volume.vpool, null, null);
        	if(value == null) {
            	flash.error("Check ViPR logs for information.");
        	}
    	}
        createDirecVolume();
    }
    
    
    public static void cancel() {
        
    }
    
    public static void list() {
        renderArgs.put("dataTable", new DirectDriverVolumeDataTable());
        render();
    }
    
    public static void listJson() {
        List<DirectDriverVolumeDataTable.DirectDriverVolume> directVolumes = DirectDriverVolumeDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(directVolumes, params));
    }
    
    public static void export() {
        renderArgs.put("dataTable", new DirectDriverExportDataTable());
        render();
    }
    
    public static void listExportJson() {
        List<DirectDriverExportDataTable.DirectDriverExport> directExports = DirectDriverExportDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(directExports, params));
    }
    
    public static class DirectDriverForm {
        public  String name;
        public  String size;
        public String sizeGB;
        public  Integer count;
        public  String arrayType;
        public  String ipAddress;
        public  String vplexId;
        public  String ipAddressForHA;
        public  String pool;
        public  String poolForHA; 
        public  String ha;
        public  URI vpool;       
        public  URI vpoolForHA;
        public  URI varray; 
        public  URI varrayForHA;
        public  URI project;
        public  URI backendNetwork_1;
        public  URI backendNetwork_2;
        public  URI haBackendNetwork_1;
        public  URI haBackendNetwork_2;
        public List<URI> hosts;
        public  Map<String, String> passThroughParamPool = new LinkedHashMap<String, String>();
        public  String passThroughParamExport = "direct";
        
        
        public DirectDriverForm() {
//            this.vpool = null;
//            this.varray = null;
//            this.project = null;
        }
        
        public DirectDriverForm(VolumeCreate volumeDriver) {
            this.name = volumeDriver.getName();
            this.size = volumeDriver.getSize();
            this.count = volumeDriver.getCount();
            this.project = volumeDriver.getProject();
        }
        
        public URI save(String name, String ipAddress, String pool, URI varray, URI vpool, URI network_1, URI network_2) {
            VolumeCreate volumeDriver = new VolumeCreate();
            this.passThroughParamPool.put("storage-pool", pool);
            this.passThroughParamPool.put("storage-system", ipAddress);
           // List<URI> volumes = new ArrayList<URI>();
            volumeDriver.setName(name);
            volumeDriver.setCount(count);
            volumeDriver.setSize(size+"GB");
            volumeDriver.setVarray(varray);
            volumeDriver.setVpool(vpool);
            volumeDriver.setProject(project);
            
            volumeDriver.setPassThroughParams(passThroughParamPool);
            Task<VolumeRestRep> tasks = getViprClient().blockVolumes().create(volumeDriver).firstTask();
            URI value = null;
            URI volume = tasks.getResourceId();
            try {
                Thread.sleep(120000);
            }catch(Exception e) {
                
            }
            ViPRCoreClient client = BourneUtil.getViprClient();
            List<URI> volumeList = client.blockVolumes().listBulkIds();
            for(URI id:volumeList) {
                if((volume).equals(id)) {
                	_log.info("IN DirectDriver entering export");
                    export(id, network_1, network_2);
                    _log.info("Returned from export");
                    value = volume;
                }
            }
            return value;
        }
        
        public Boolean save(String name, String vplexId, String volumeId, String volumeForHAId, URI varray, URI vpool) {
            VolumeCreate volumeDriver = new VolumeCreate();
            this.passThroughParamPool.put("VPlex-Id", vplexId);
			this.passThroughParamPool.put("volumeId", volumeId);
			this.passThroughParamPool.put("volume-for-HA", volumeForHAId);
            //List<URI> volumes = new ArrayList<URI>();
            volumeDriver.setName(name);
            volumeDriver.setCount(count);
            volumeDriver.setSize(size+"GB");
            volumeDriver.setVarray(varray);
            volumeDriver.setVpool(vpool);
            volumeDriver.setProject(project);
            
            volumeDriver.setPassThroughParams(passThroughParamPool);
            Task<VolumeRestRep> tasks = getViprClient().blockVolumes().create(volumeDriver).firstTask();
            boolean value = false;
            URI volume = tasks.getResourceId();
            try {
                Thread.sleep(120000);
            }catch(Exception e) {
                
            }
            ViPRCoreClient client = BourneUtil.getViprClient();
            List<URI> volumeList = client.blockVolumes().listBulkIds();
            for(URI id:volumeList) {
                if((volume).equals(id)) {
                	_log.info("IN DirectDriver entering export");
                    export(id, null, null);
                    _log.info("Returned from export");
                    value = true;
                }
            }
            return value;
        }
        
        public void export(URI volume,URI network_1,URI network_2) {
            ExportCreateParam exportDriver = new ExportCreateParam();
        	URI ipAddress1 = null;
			try {
				ipAddress1 = new URI(ipAddress);
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			ViPRCoreClient client = BourneUtil.getViprClient();
			List<NamedRelatedResourceRep> storagePortsList = client.storagePorts().listByStorageSystem(ipAddress1);
			List<URI> initiators = new ArrayList<URI>();
			if(network_1 != null && network_2 != null){
        		List<InitiatorRestRep> initiatorsForNetwork_1 = NetworkUtils.getInitiators(network_1);
        		_log.info("IN DirectDriver, 1 initiatorsForNetwork_1 --- {}" ,initiatorsForNetwork_1.toString());
        		_log.info("IN DirectDriver, 2 initiatorsForNetwork_1 --- {}" ,initiatorsForNetwork_1);
        		for(InitiatorRestRep list : initiatorsForNetwork_1)
        		{
        			String port = client.initiators().get(list.getId()).getInitiatorPort();
        				if(storagePortsList.contains(port))
        				{
        					_log.info("Initiators Form network-1: {}",list.getId());
        					initiators.add(list.getId());
        				}
        		}
        		List<InitiatorRestRep> initiatorsForNetwork_2 = NetworkUtils.getInitiators(network_2);
        		for(InitiatorRestRep list : initiatorsForNetwork_2)
        		{
        			String port = client.initiators().get(list.getId()).getInitiatorPort();
        				if(storagePortsList.contains(port))
        				{
        					_log.info("Initiators Form network-2: {}",list.getId());
        					initiators.add(list.getId());
        				}
        		}
        	}
            List<VolumeParam> listParam = Lists.newArrayList();
            VolumeParam volumeParam = new VolumeParam();
            volumeParam.setId(volume);
            listParam.add(volumeParam);
            exportDriver.setName(name);
            if(!initiators.isEmpty())
            {
            	exportDriver.setType("Initiator");
                exportDriver.setInitiators(initiators);
            }
            else{
            	exportDriver.setType("Host");
                exportDriver.setHosts(hosts);
            }
           	exportDriver.setProject(project);
            exportDriver.setVarray(varray);
            exportDriver.setVolumes(listParam);
            exportDriver.setExportPassThroughParam(passThroughParamExport);
            getViprClient().blockExports().create(exportDriver);
            flash.success("Volume created and exported successfully");
        }
    }
}