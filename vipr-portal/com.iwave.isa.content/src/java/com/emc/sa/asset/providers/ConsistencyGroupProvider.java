/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.vipr.client.core.filters.ConsistencyGroupFilter;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class ConsistencyGroupProvider extends BaseAssetOptionsProvider {

    @Asset("consistencyGroupFilter")
    public List<AssetOption> getConsistencyGroupFilters(AssetOptionsContext ctx) {
        List<ProjectRestRep> projects = api(ctx).projects().getByTenant(ctx.getTenant());
        List<BlockConsistencyGroupRestRep> cgs = Lists.newArrayList();

        for (ProjectRestRep project : projects) {
            cgs.addAll(api(ctx).blockConsistencyGroups().findByProject(project));
        }

        List<AssetOption> options = createBaseResourceOptions(cgs);
        options.add(0, new AssetOption("None", "None"));
        return options;
    }

    @Asset("consistencyGroupAll")
    public List<AssetOption> getAllConsistencyGroup(AssetOptionsContext ctx) {
        List<ProjectRestRep> projects = api(ctx).projects().getByTenant(ctx.getTenant());
        List<BlockConsistencyGroupRestRep> cgs = Lists.newArrayList();

        for (ProjectRestRep project : projects) {
            cgs.addAll(api(ctx).blockConsistencyGroups().findByProject(project));
        }

        List<AssetOption> options = createBaseResourceOptions(cgs);
        return options;
    }

    @Asset("consistencyGroup")
    @AssetDependencies({ "project", "blockVirtualPool" })
    public List<AssetOption> getConsistencyGroups(AssetOptionsContext ctx, URI projectId, URI virtualPoolId) {
        BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);

        // Only provide consistency groups if the selected VPool supports it
        if (isSupportedVPool(vpool)) {
            return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId).run());
        } else {
            return Collections.emptyList();
        }
    }

    @Asset("consistencyGroupByProject")
    @AssetDependencies({ "project" })
    public List<AssetOption> getConsistencyGroupsByProject(AssetOptionsContext ctx, URI projectId) {
        return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId).run());
    }

    @Asset("rpConsistencyGroupByProject")
    @AssetDependencies({ "project" })
    public List<AssetOption> getRPConsistencyGroupsByProject(AssetOptionsContext ctx, URI projectId) {
        return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId)
                .filter(new ConsistencyGroupFilter(BlockConsistencyGroup.Types.RP.toString(), false)).run());
    }

    @Asset("consistencyGroup")
    @AssetDependencies({ "project" })
    public List<AssetOption> getConsistencyGroups(AssetOptionsContext ctx, URI projectId) {
        return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId).run());
    }

    @Asset("consistencyGroupWithVirtualPoolChangeOperation")
    @AssetDependencies({ "project", "virtualPoolChangeOperation", "targetVirtualPool" })
    public List<AssetOption> getConsistencyGroupsChangeVPool(AssetOptionsContext ctx, URI projectId,
            String virtualPoolChangeOperation, URI virtualPoolId) {
        // Only support for RP Protection for now
        if (virtualPoolChangeOperation.equals(VirtualPoolChangeOperationEnum.RP_PROTECTED.name())) {
            BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);
            // Only provide consistency groups if the selected VPool supports it
            if (isSupportedVPool(vpool)) {
                return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId)
                        .filter(new ConsistencyGroupFilter(BlockConsistencyGroup.Types.RP.toString(), true)).run());
            }
        }
        return Collections.emptyList();
    }

    private boolean isSupportedVPool(BlockVirtualPoolRestRep vpool) {
        return vpool != null && vpool.getMultiVolumeConsistent() != null && vpool.getMultiVolumeConsistent();
    }

}
