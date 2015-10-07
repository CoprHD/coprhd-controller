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

import util.datatable.DataTable;

import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import controllers.resources.ObjectBuckets;



public class ObjectBucketsDataTable extends DataTable {
    
    public ObjectBucketsDataTable() {
        addColumn("name");
        addColumn("hardquota").setRenderFunction("render.sizeInGb");
        addColumn("softquota").setRenderFunction("render.sizeInGb");
        addColumn("varray");
        addColumn("vpool");
        addColumn("protocols");
        sortAll();
        setDefaultSort("name", "asc");

        setRowCallback("createRowLink");
    }

    public static List<Bucket> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<BucketRestRep> buckets = client.objectBuckets().findByProject(projectId);
        Map<URI, String> virtualArrays = ResourceUtils.mapNames(client.varrays().list());
        Map<URI, String> virtualPools = ResourceUtils.mapNames(client.objectVpools().list());

        List<Bucket> results = Lists.newArrayList();
        for (BucketRestRep bucket : buckets) {
            results.add(new Bucket(bucket, virtualArrays, virtualPools));
        }
        return results;
    }

    public static class Bucket {
        public String rowLink;
        public URI id;
        public String name;
        public String softquota;
        public String hardquota;
        public String varray;
        public String vpool;
        public Set<String> protocols;

        public Bucket(BucketRestRep bucket, Map<URI, String> varrayMap, Map<URI, String> vpoolMap) {
            id = bucket.getId();
            name = bucket.getName();
            this.rowLink = createLink(ObjectBuckets.class, "bucket", "bucketId", id);
            softquota = bucket.getSoftQuota();
            hardquota = bucket.getHardQuota();
            if (bucket.getVirtualArray() != null) {
                varray = varrayMap.get(bucket.getVirtualArray().getId());
            }
            if (bucket.getVirtualPool() != null) {
                vpool = vpoolMap.get(bucket.getVirtualPool().getId());
            }
            protocols = bucket.getProtocols();
        }
    }

}
