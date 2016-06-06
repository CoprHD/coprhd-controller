/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import models.StorageSystemTypes;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.mvc.Controller;
import util.HostUtils;
import util.StoragePoolUtils;
import util.StorageSystemUtils;
import util.TaskUtils;
import util.validation.HostNameOrIpAddress;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;

@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class DirectDriver extends Controller{
 
    public static void createDirecVolume() {
        renderArgs.put("storageArrayTypeList", Arrays.asList(StorageSystemTypes.OPTIONS));
        renderArgs.put("hostList",getHosts());
        render();
    }
    
    public static void getIpFromType(String type) {
        List<StorageSystemRestRep> storageSystems = StorageSystemUtils.getStorageSystems();
        List<StorageSystemRestRep> results = Lists.newArrayList();
        for(StorageSystemRestRep system : storageSystems) {
            if (system.getSystemType()!=null && system.getSystemType().equals(type)) {
                results.add(system);
            }
              if(type.equals("vmax") && system.getSystemType().equals("vnxblock")) {
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
    
    public static void exportDirectVolume(String id) {
        
    }
    
    @FlashException(keep = true, referrer = { "createDirecVolume" })
    public static void saveVolume(DirectDriverForm volume) {
        Boolean value = volume.save();
       
            //flash.success("Created and exported successfully");
        
        createDirecVolume();
    }
    
    public static void cancel() {
        
    }
    
    public static void list() {
        
    }
    
    public static class DirectDriverForm {
        public  String name;
        public  String size;
        public String sizeGB;
        public  Integer count;
        public  String arrayType;
        public  String ipAddress;
        public  String pool;
        public  URI vpool;
        public  URI varray;
        public  URI project;
        public List<URI> hosts;
        public  Map<String, String> passThroughParamPool = new LinkedHashMap<String, String>();;
        public  String passThroughParamExport = "direct";
        
        
        public DirectDriverForm() {
            this.vpool = null;
            this.varray = null;
            this.project = null;
        }
        
        public DirectDriverForm(VolumeCreate volumeDriver) {
            this.name = volumeDriver.getName();
            this.size = volumeDriver.getSize();
            this.count = volumeDriver.getCount();
        }
        
        public boolean save() {
            VolumeCreate volumeDriver = new VolumeCreate();
            this.passThroughParamPool.put("storage-pool", pool);
            this.passThroughParamPool.put("storage-system", ipAddress);
            List<URI> volumes = new ArrayList<URI>();
            
            volumeDriver.setName(name);
            volumeDriver.setCount(count);
            volumeDriver.setSize(size+"GB");
            volumeDriver.setVarray(uri("aa"));
            volumeDriver.setVpool(uri("aa"));
            volumeDriver.setProject(uri("aa"));
            volumeDriver.setPassThroughParams(passThroughParamPool);
            Task<VolumeRestRep> tasks = getViprClient().blockVolumes().create(volumeDriver).firstTask();
            URI taskId = tasks.getTaskResource().getId();
            URI volume = tasks.getResourceId();
            boolean value = false;
            while(!value) {
                TaskResourceRep task = TaskUtils.getTask(taskId);
                if(task.getState().equals("ready")) {
                    value = true;
                    export(volume);
                } else if(task.getState().equals("error")) {
                    flash.error("Error in creating volume");
                    value = true;
                }
            }
            return value;            
        }
        
        public void export(URI volume) {
            ExportCreateParam exportDriver = new ExportCreateParam();
            List<VolumeParam> listParam = Lists.newArrayList();
            VolumeParam volumeParam = new VolumeParam();
            volumeParam.setId(volume);
            listParam.add(volumeParam);
            exportDriver.setName(name);
            exportDriver.setType("Host");
            exportDriver.setProject(uri("aa"));
            exportDriver.setVarray(uri("aa"));
            exportDriver.setVolumes(listParam);
            exportDriver.setHosts(hosts);
            exportDriver.setExportPassThroughParam(passThroughParamExport);
            getViprClient().blockExports().create(exportDriver);
		flash.success("Created and exported successfully");
        }
    }
}
