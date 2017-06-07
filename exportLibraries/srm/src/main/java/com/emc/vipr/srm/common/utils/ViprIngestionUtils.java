package com.emc.vipr.srm.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.vipr.srm.exception.ViprSRMException;
import com.emc.vipr.srm.webservice.accessors.dataaccessors.impl.SRMDataAccessor;

@SuppressWarnings("unchecked")
public class ViprIngestionUtils {
    static Map<String,String> srmToViprVolumeMap;
    static Map<String,String> srmToViprVolumeOpitionalMap;
    static {
        srmToViprVolumeMap = (Map) ApplicationContextUtils
                .getBean("srmToViprVolumeMapper");
        srmToViprVolumeOpitionalMap = (Map) ApplicationContextUtils
                .getBean("optionalValues");
    }


    public static List<Map<String,String>> fetchUnManagedVolumes(Map<String, String> vmap) throws ViprSRMException {
        List<Map<String,String>> srmToViprVolumes = new ArrayList<Map<String,String>>();
        List<Map<String, String>> results = SRMDataAccessor.retrieveDataWithMultipleKey(
                SRMQueryResource.fetchSRMFilterQuery("fetchAllNamedLunsFromArray_Filter", vmap), Arrays.asList(
                        SRMQueryResource.srmPropertiestoFetch("fetchAllNamedLunsFromArray_Properties").split(",")));
        
       results.addAll(SRMDataAccessor.retrieveDataWithMultipleKey(
                SRMQueryResource.fetchSRMFilterQuery("fetchAllWithoutNameLunsFromArray_Filter", vmap), Arrays.asList(
                        SRMQueryResource.srmPropertiestoFetch("fetchAllWithoutNameLunsFromArray_Properties").split(","))));

        System.out.println(results.size());

        List<Map<String, String>> replicas = SRMDataAccessor.retrieveDataWithMultipleKey(
                SRMQueryResource.fetchSRMFilterQuery("fetchAllReplicasFromArray_Filter", vmap), Arrays.asList(
                        SRMQueryResource.srmPropertiestoFetch("fetchAllReplicasFromArray_Properties").split(",")));

        System.out.println(replicas.size());

        for (Map<String, String> map : results) {
            String nativeid = map.get("partid");
            //map.put("SYSTEM_TYPE", "vmax");
            if(!map.containsKey("alias")) {
                map.put("alias", "Volume " + nativeid);
            }
            for (Map<String, String> replica : replicas) {
                if (nativeid.equals(replica.get("partid")) && !"N/A".equals(replica.get("srclun"))) {
                    map.put("LOCAL_REPLICA_SOURCE_VOLUME", replica.get("srclun"));
                    map.put("IS_REPLICA", "true");
                } else if (nativeid.equals(replica.get("srclun"))) {
                    map.put("HAS_REPLICAS", "true");
                    if (map.containsKey("MIRRORS")) {
                        StringBuilder mirrors = new StringBuilder(map.get("REPLICA_LUNS"));
                        mirrors.append(",").append(replica.get("partid"));
                        map.put("REPLICA_LUNS", mirrors.toString());
                    } else {
                        map.put("REPLICA_LUNS", replica.get("partid"));
                    }
                }
            }
            //map srm volumes to vipr attributes volumes
            Map<String, String> viprVolumeMap = new HashMap<String,String>();
            for(Map.Entry<String, String> entry : srmToViprVolumeMap.entrySet()) {
                viprVolumeMap.put(entry.getKey(), map.get(entry.getValue()));
            }
            for(Map.Entry<String, String> entry : srmToViprVolumeOpitionalMap.entrySet()) {
                if(null != map.get(entry.getValue())) {
                    viprVolumeMap.put(entry.getKey(), map.get(entry.getValue()));
                }else {
                    viprVolumeMap.put(entry.getKey(), "false");
                }
            }

            //query on each lun for its capacity info
            vmap.put("partid", nativeid);

            Float provisionedCapacity = SRMDataAccessor.getAggregateData(SRMQueryResource.fetchSRMFilterQuery("fetchProvisionedCapacityforLun_Filter", vmap), null);
            viprVolumeMap.put("PROVISIONED_CAPACITY", provisionedCapacity +" GB");

            Float usedCapacity = SRMDataAccessor.getAggregateData(SRMQueryResource.fetchSRMFilterQuery("fetchUsedCapacityforLun_Filter", vmap), null);
            viprVolumeMap.put("ALLOCATED_CAPACITY", usedCapacity +" GB");
            
            srmToViprVolumes.add(viprVolumeMap);
            System.out.println(viprVolumeMap);
        }
        return srmToViprVolumes;
    }
}
