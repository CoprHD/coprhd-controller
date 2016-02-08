/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.object.BucketACLUpdateParams;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class ObjectProvider extends BaseAssetOptionsProvider {

    @Asset("sourceBuckets")
    @AssetDependencies("project")
    public List<AssetOption> getSourceBuckets(AssetOptionsContext ctx, URI project) {
        debug("getting source block volumes (project=%s)", project);
        return createBucketOptions(null, listSourceBuckets(api(ctx), project));
    }
    
    protected static List<AssetOption> createBucketOptions(ViPRCoreClient client, Collection<? extends DataObjectRestRep> bucketObjects) {
        return createBucketOptions(client, null, null, bucketObjects);
    }

    protected static List<AssetOption> createBucketOptions(ViPRCoreClient client, URI project, URI hostId,
            Collection<? extends DataObjectRestRep> bucketObjects) {
        Map<URI, BucketRestRep> bucketNames = getProjectBucketNames(client, project);
        List<AssetOption> options = Lists.newArrayList();
        for (DataObjectRestRep bucketObject : bucketObjects) {
            options.add(createBucketOption(client, hostId, bucketObject, bucketNames));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected static AssetOption createBucketOption(ViPRCoreClient client, URI hostId, DataObjectRestRep bucketObject,
            Map<URI, BucketRestRep> bucketNames) {
        String name = getBucketObjectLabel(client, bucketObject, bucketNames);
        String hardQuota = getBucketObjectHardQuota(bucketObject);
        String softQuota = getBucketObjectSoftQuota(bucketObject);
        String retention = getBucketObjectRetention(bucketObject);
        String label = getMessage("object.bucket.label", name, softQuota, hardQuota, retention);
        return new AssetOption(bucketObject.getId(), label);
    }

    protected static Map<URI, BucketRestRep> getProjectBucketNames(ViPRCoreClient client, URI project) {
        if (project == null) {
            return Collections.emptyMap();
        }
        return ResourceUtils.mapById(client.objectBuckets().findByProject(project));
    }

    private static String getBucketObjectHardQuota(DataObjectRestRep bucketObject) {
        if (bucketObject instanceof BucketRestRep) {
            BucketRestRep bucket = (BucketRestRep) bucketObject;
            return bucket.getHardQuota();
        }
        return "N/A";
    }

    private static String getBucketObjectSoftQuota(DataObjectRestRep bucketObject) {
        if (bucketObject instanceof BucketRestRep) {
            BucketRestRep bucket = (BucketRestRep) bucketObject;
            return bucket.getSoftQuota();
        }
        return "N/A";
    }

    private static String getBucketObjectRetention(DataObjectRestRep bucketObject) {
        if (bucketObject instanceof BucketRestRep) {
            BucketRestRep bucket = (BucketRestRep) bucketObject;
            return bucket.getRetention();
        }
        return "N/A";
    }

    private static String getBucketObjectLabel(ViPRCoreClient client, DataObjectRestRep bucketObject, Map<URI, BucketRestRep> volumeNames) {
        if (bucketObject instanceof BucketRestRep) {
            BucketRestRep bucket = (BucketRestRep) bucketObject;
            return bucket.getName();
        }
        return bucketObject.getName();
    }
    
    @SafeVarargs
    public static List<BucketRestRep> listSourceBuckets(ViPRCoreClient client, URI project, ResourceFilter<BucketRestRep>... filters) {
        return client.objectBuckets().findByProject(project);
    }

    @Asset("objectVirtualPool")
    @AssetDependencies({ "virtualArray" })
    public List<AssetOption> getObjectVirtualPoolsForVirtualArray(AssetOptionsContext ctx, URI virtualArray) {
        debug("getting getObjectVirtualPoolsForVirtualArray(virtualArray=%s)", virtualArray);
        List<ObjectVirtualPoolRestRep> virtualPools = api(ctx).objectVpools().getByVirtualArray(virtualArray);
        return BlockProvider.createVirtualPoolResourceOptions(virtualPools);
    }

    @Asset("objectVirtualPool")
    public List<AssetOption> getObjectVirtualPools(AssetOptionsContext ctx) {
        debug("getting objectVirtualPools");
        return createBaseResourceOptions(api(ctx).objectVpools().getAll());
    }
    
    @Asset("objectACLPermission")
    public List<AssetOption> getObjectACLPermissions(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (BucketACLUpdateParams.BucketPermissions perm : BucketACLUpdateParams.BucketPermissions.values()) {
            options.add(newAssetOption(perm.name(), String.format("Object.ACL.permission.%s", perm.name())));
        }
        return options;
    }
}
