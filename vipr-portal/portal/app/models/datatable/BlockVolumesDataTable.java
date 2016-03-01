/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.AppSupportUtil;
import util.BourneUtil;
import util.datatable.DataTable;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import controllers.resources.BlockVolumes;

public class BlockVolumesDataTable extends DataTable {

    public BlockVolumesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("capacity").setRenderFunction("render.sizeInGb");
        addColumn("varray");
        addColumn("vpool");
        addColumn("protocols");
        addColumn("wwn");
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static List<Volume> fetch(URI projectId, URI applicationId) {
        if (projectId == null && applicationId ==null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<VolumeRestRep> volumes = Lists.newArrayList();
        List<Volume> results = Lists.newArrayList();
        Map<URI, String> virtualArrays = ResourceUtils.mapNames(client.varrays().list());
        Map<URI, String> virtualPools = ResourceUtils.mapNames(client.blockVpools().list());
        if (projectId != null && applicationId == null) {
             volumes = client.blockVolumes().findByProject(projectId);
             for (VolumeRestRep volume : volumes) {
                     results.add(new Volume(volume, virtualArrays, virtualPools));
             }
        }
        else if(applicationId!=null) {
            List<VolumeRestRep> result = Lists.newArrayList();
            List<NamedRelatedResourceRep> groups = AppSupportUtil.getVolumesByApplication(applicationId.toString());
            List<NamedRelatedResourceRep> clones = AppSupportUtil.getFullCopiesByApplication(applicationId.toString());
            for (NamedRelatedResourceRep volume : groups) {
                result.add(BourneUtil.getViprClient().blockVolumes().get((volume.getId())));
            }
            for (NamedRelatedResourceRep clone:clones) {
                result.add(BourneUtil.getViprClient().blockVolumes().get((clone.getId())));
            }
            for (VolumeRestRep volumeApplication : result) {
                results.add(new Volume(volumeApplication, virtualArrays, virtualPools));
            }
        }
        
        return results;
        }

    public static class Volume {
        public URI id;
        public String name;
        public String capacity;
        public String varray;
        public String vpool;
        public Set<String> protocols;
        public boolean srdfTarget;
        public String wwn = "";

        public Volume(VolumeRestRep volume, Map<URI, String> varrayMap, Map<URI, String> vpoolMap) {
            id = volume.getId();
            name = volume.getName();
            wwn = volume.getWwn();
            srdfTarget = volume.getProtection() != null && volume.getProtection().getSrdfRep() != null
                    && volume.getProtection().getSrdfRep().getAssociatedSourceVolume() != null;
            capacity = volume.getProvisionedCapacity();
            if (volume.getVirtualArray() != null) {
                varray = varrayMap.get(volume.getVirtualArray().getId());
            }
            if (volume.getVirtualPool() != null) {
                vpool = vpoolMap.get(volume.getVirtualPool().getId());
            }
            protocols = volume.getProtocols();
        }
    }
}
