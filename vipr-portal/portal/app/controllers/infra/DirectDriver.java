/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import models.StorageSystemTypes;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.mvc.Controller;
import util.StoragePoolUtils;
import util.StorageSystemUtils;
import util.validation.HostNameOrIpAddress;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class DirectDriver extends Controller{
 
    public static void createDirecVolume() {
        renderArgs.put("storageArrayTypeList", Arrays.asList(StorageSystemTypes.OPTIONS));
        //renderArgs.put("ipList",getIpFromType("isilon"));
        render();
    }
    
    public static void getIpFromType(String type) {
        List<StorageSystemRestRep> storageSystems = StorageSystemUtils.getStorageSystems();
        List<StorageSystemRestRep> results = Lists.newArrayList();
        for(StorageSystemRestRep system : storageSystems) {
            if (system.getSystemType()!=null && system.getSystemType().equals(type)) {
                results.add(system);
            }
        }
        renderJSON((results));
    }
    
    public static void getPoolsFromSystem(String id) {
        List<StoragePoolRestRep> storagePools = StoragePoolUtils.getStoragePools(id);
        renderJSON(storagePools);
    }
    
    public static void exportDirectVolume(String id) {
        
    }
    
    @FlashException(keep = true, referrer = { "createDirecVolume" })
    public static void saveVolume(DirectDriverForm volume) {
        volume.save();
        flash.success("Saved");
        createDirecVolume();
    }
    
    public static void cancel() {
        
    }
    
    public static void list() {
        
    }
    
    public static class DirectDriverForm {
        public  String name;
        public  String size;
        public  Integer count;
        public  String arrayType;
        public  String ipAddress;
        public  String pool;
        public  URI vpool;
        public  URI varray;
        public  URI project;
        public  Map<String, String> passThroughParamPool = new LinkedHashMap<String, String>();;
        public  Map<String, String> passThroughParamStorage = new LinkedHashMap<String, String>();
        
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
        
        public  void save() {
            VolumeCreate volumeDriver = new VolumeCreate();
            this.passThroughParamPool.put("storage-pool", pool);
            this.passThroughParamPool.put("storage-system", ipAddress);
            volumeDriver.setName(name);
            volumeDriver.setCount(count);
            volumeDriver.setSize(size);
            volumeDriver.setVarray(uri("aa"));
            volumeDriver.setVpool(uri("aa"));
            volumeDriver.setProject(uri("aa"));
            volumeDriver.setPassThroughParams(passThroughParamPool);
            Tasks<VolumeRestRep> task = getViprClient().blockVolumes().create(volumeDriver);
//            VolumeRestRep volume = task.get();
//            System.out.println("Created Volume: " + volume.getId());
//            return volume.getId();
            
        }
    }
}