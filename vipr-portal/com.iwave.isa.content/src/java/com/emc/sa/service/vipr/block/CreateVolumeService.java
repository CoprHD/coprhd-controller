/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils.VolumeParams;
import com.emc.sa.service.vipr.block.BlockStorageUtils.VolumeTable;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

@Service("CreateVolume")
public class CreateVolumeService extends ViPRService {

    @Bindable(itemType = VolumeTable.class)
    protected VolumeTable[] volumeTable;

    @Bindable
    protected VolumeParams volumeParams = new VolumeParams();

    protected List<CreateBlockVolumeHelper> createBlockVolumeHelpers = Lists.newArrayList();

    @Override
    public void precheck() {
        BlockStorageUtils.checkVolumeLimit(getClient(), volumeParams.project);

        //checks to make if pool supports remote replication
        BlockVirtualPoolRestRep vpool = getClient().blockVpools().get(volumeParams.virtualPool);
        if ((vpool != null) && (vpool.getProtection().getRemoteReplicationParam() != null)) {

            // one remote replication set must be available
            List<NamedRelatedResourceRep> rrSets = getClient().remoteReplicationSets().
                    listRemoteReplicationSets(volumeParams.virtualArray,volumeParams.virtualPool).
                    getRemoteReplicationSets();
            if ((rrSets == null) || (rrSets.isEmpty())) {
                throw new IllegalStateException("No Remote Replication Set found for this Virtual " +
                        "Pool and Virtual Array");
            }
            if (rrSets.size() > 1) {
                throw new IllegalStateException("Multiple Remote Replication Sets found.  Only one " +
                        "set can exist for this Virtual Pool and Virtual Array.  Found " +
                        rrSets.size() + " sets " + rrSets);
            }

            // mode must be specified
            if(Strings.isNullOrEmpty(volumeParams.remoteReplicationMode)) {
                throw new IllegalStateException("Remote Replication Mode not specified");
            }
        }
    }

    @Override
    public void init() throws Exception {
        super.init();

        // for each pair of volume name and size, create a createBlockVolumeHelper
        for (VolumeTable volumes : volumeTable) {
            CreateBlockVolumeHelper createBlockVolumeHelper = new CreateBlockVolumeHelper();
            BindingUtils.bind(createBlockVolumeHelper, BlockStorageUtils.createParam(volumes, volumeParams));
            createBlockVolumeHelpers.add(createBlockVolumeHelper);
        }
    }

    @Override
    public void execute() throws Exception {
        if (!createBlockVolumeHelpers.isEmpty()) {
            List<URI> volumeIds = Lists.newArrayList();
            volumeIds.addAll(BlockStorageUtils.createMultipleVolumes(createBlockVolumeHelpers));
        }
    }
}
