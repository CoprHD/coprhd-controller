/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;

import models.FileProtectionSystemTypes;
import jobs.vipr.ConnectedFileVirtualPoolsCall;
import jobs.vipr.MatchingFileStoragePoolsCall;
import play.Logger;
import play.data.validation.Min;
import util.VirtualPoolUtils;
import util.builders.FileVirtualPoolBuilder;
import util.builders.FileVirtualPoolUpdateBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.FileReplicationPolicy;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolReplicationParam;
import com.emc.storageos.model.vpool.FileVirtualPoolReplicationUpdateParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.emc.vipr.client.core.FileVirtualPools;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

public class FileVirtualPoolForm extends VirtualPoolCommonForm<FileVirtualPoolRestRep> {
    @Min(0)
    public Integer maxSnapshots;

    public Boolean longTermRetention;
    
    public String replicationCopiesJson = "[]";
    public ReplicationCopyForm[] replicationCopies = {};
    
    public String replicationType;
    public String replicationMode;
    public Long replicationRpo;
    public String rpRpoType;
    
    public Boolean scheduleSnapshots;
    
    
    public void deserialize() {
        Gson g = new Gson();
        replicationCopies = g.fromJson(replicationCopiesJson, ReplicationCopyForm[].class);
        
    }
    
    public ConnectedFileVirtualPoolsCall replicationVirtualPools() {
        List<URI> varrayIds = uris(virtualArrays);
        return new ConnectedFileVirtualPoolsCall(varrayIds);
    }

    @Override
    public void load(FileVirtualPoolRestRep virtualPool) {
        doLoad(virtualPool);
        FileVirtualPools vpools = getViprClient().fileVpools();
        loadQuota(vpools);
        loadTenantACLs(vpools);
    }

    protected void doLoad(FileVirtualPoolRestRep virtualPool) {
        loadCommon(virtualPool);
        FileVirtualPoolProtectionParam protection = virtualPool.getProtection();
        replicationType = virtualPool.getFileReplicationType();
        FileVirtualPoolReplicationParam replication = protection.getReplicationParam();
        if ((protection != null) && (protection.getSnapshots() != null)) {
            maxSnapshots = protection.getSnapshots().getMaxSnapshots();
        }
        if (protection != null) {
            scheduleSnapshots = protection.getScheduleSnapshots();
        }
       
        if(replication != null){
            FileReplicationPolicy replicationPolicy = replication.getSourcePolicy();
            replicationMode = replicationPolicy.getCopyMode();
            rpRpoType = replicationPolicy.getRpoType();
            replicationRpo = replicationPolicy.getRpoValue();
            Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> rpCopies = replication.getCopies();
            List<ReplicationCopyForm> copyForms = Lists.newArrayList();
            for (VirtualPoolRemoteProtectionVirtualArraySettingsParam rpCopy : rpCopies){
                ReplicationCopyForm replicationCopyForm = new ReplicationCopyForm();
                replicationCopyForm.load(rpCopy);
                copyForms.add(replicationCopyForm);
            }
            
            replicationCopies = copyForms.toArray(new ReplicationCopyForm[0]);
            Gson gson = new Gson();
            replicationCopiesJson = gson.toJson(replicationCopies);
            
        }
        longTermRetention = virtualPool.getLongTermRetention();
    }

    @Override
    public FileVirtualPoolRestRep save() {
        FileVirtualPoolRestRep virtualPool = doSave();
        FileVirtualPools vpools = getViprClient().fileVpools();
        saveQuota(vpools);
        saveTenantACLs(vpools);
        return virtualPool;
    }

    protected FileVirtualPoolRestRep doSave() {
        FileVirtualPoolRestRep virtualPool;
        if (isNew()) {
            FileVirtualPoolBuilder builder = apply(new FileVirtualPoolBuilder());
            virtualPool = VirtualPoolUtils.create(builder.getVirtualPool());
            this.id = ResourceUtils.stringId(virtualPool);
        }
        else {
            FileVirtualPoolRestRep oldVirtualPool = VirtualPoolUtils.getFileVirtualPool(id);
            FileVirtualPoolUpdateBuilder builder = apply(new FileVirtualPoolUpdateBuilder(oldVirtualPool));
            virtualPool = VirtualPoolUtils.update(id, builder.getVirtualPoolUpdate());
            List<NamedRelatedResourceRep> matchingPools = VirtualPoolUtils.refreshMatchingPools(virtualPool);
            Logger.info("Refreshed File Virtual Pool '%s' matching pools: %d", virtualPool.getName(), matchingPools.size());
        }
        virtualPool = saveStoragePools(virtualPool);
        return virtualPool;
    }

    private FileVirtualPoolBuilder apply(FileVirtualPoolBuilder builder) {
        applyCommon(builder);
        builder.setSnapshots(maxSnapshots);
        builder.setScheduleSnapshots(scheduleSnapshots);
        builder.setLongTermRetention(longTermRetention);
        
        FileReplicationPolicy fileReplicationPolicy = new FileReplicationPolicy();
        fileReplicationPolicy.setReplicationType(replicationType);
        FileVirtualPoolReplicationParam replicationParam = new FileVirtualPoolReplicationParam();
        
        
        if(FileProtectionSystemTypes.isTypeLocal(replicationType)){
            fileReplicationPolicy.setCopyMode(replicationMode);
            fileReplicationPolicy.setRpoType(rpRpoType);
            fileReplicationPolicy.setRpoValue(replicationRpo);
        }
        
       if (FileProtectionSystemTypes.isTypeRemote(replicationType)){
           Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies = Sets.newLinkedHashSet();
           fileReplicationPolicy.setCopyMode(replicationMode);
           fileReplicationPolicy.setRpoType(rpRpoType);
           fileReplicationPolicy.setRpoValue(replicationRpo);
           
           for (ReplicationCopyForm replicationCopyForm : replicationCopies) {
               if (replicationCopyForm != null ) {
                   copies.add(replicationCopyForm.write(replicationMode));
               }
           }
           replicationParam.setCopies(copies);
       }
       
       replicationParam.setSourcePolicy(fileReplicationPolicy);
       builder.setReplicationParam(replicationParam);
        
        return builder;
    }

    private FileVirtualPoolUpdateBuilder apply(FileVirtualPoolUpdateBuilder builder) {
        applyCommon(builder);
        builder.setSnapshots(maxSnapshots);
        builder.setScheduleSnapshots(scheduleSnapshots);
        builder.setLongTermRetention(longTermRetention);
        
        
        FileReplicationPolicy fileReplicationPolicy = new FileReplicationPolicy();
        fileReplicationPolicy.setReplicationType(replicationType);
        FileVirtualPoolReplicationUpdateParam replicationParam = new FileVirtualPoolReplicationUpdateParam();
        
        
        if(FileProtectionSystemTypes.isTypeLocal(replicationType)){
            fileReplicationPolicy.setCopyMode(replicationMode);
            fileReplicationPolicy.setRpoType(rpRpoType);
            fileReplicationPolicy.setRpoValue(replicationRpo);
        }
        
       if (FileProtectionSystemTypes.isTypeRemote(replicationType)){
           fileReplicationPolicy.setCopyMode(replicationMode);
           fileReplicationPolicy.setRpoType(rpRpoType);
           fileReplicationPolicy.setRpoValue(replicationRpo);
           Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies = Sets.newLinkedHashSet();
           for (ReplicationCopyForm replicationCopyForm : replicationCopies) {
               
               if (replicationCopyForm != null ) {
                   copies.add(replicationCopyForm.write(replicationMode));
               }
           }
           FileVirtualPoolReplicationParam oldPoolReplicationParam = builder.getOldReplicationParam();
           Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> oldCopies = oldPoolReplicationParam.getCopies();
           
           replicationParam.getAddRemoteCopies().addAll(CollectionUtils.subtract(copies, oldCopies));
           replicationParam.getRemoveRemoteCopies().addAll(CollectionUtils.subtract(oldCopies, copies));
           
       }       
       replicationParam.setSourcePolicy(fileReplicationPolicy);
       builder.setReplicationParam(replicationParam);
        
        return builder;
    }

    @Override
    protected FileVirtualPoolRestRep updateStoragePools(Set<String> add, Set<String> remove) {
        return VirtualPoolUtils.updateAssignedFilePools(id, add, remove);
    }

    public MatchingFileStoragePoolsCall matchingStoragePools() {
        FileVirtualPoolBuilder builder = new FileVirtualPoolBuilder();
        apply(builder);
        builder.setUseMatchedPools(true);
        return new MatchingFileStoragePoolsCall(builder.getVirtualPool());
    }

    public ConnectedFileVirtualPoolsCall connectedVirtualPools() {
        return new ConnectedFileVirtualPoolsCall(uris(virtualArrays));
    }
}
