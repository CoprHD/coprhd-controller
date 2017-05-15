/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr;

import static com.emc.sa.service.ServiceParams.ARTIFICIAL_FAILURE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.AbstractExecutionService;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.AcquireClusterLock;
import com.emc.sa.service.vipr.tasks.AcquireHostLock;
import com.emc.sa.service.vipr.tasks.ReleaseHostLock;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.db.client.model.uimodels.ScheduledEvent;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.google.common.collect.Lists;

public abstract class ViPRService extends AbstractExecutionService {
    
    private static Charset UTF_8 = Charset.forName("UTF-8");
    
    @Autowired
    private ViPRProxyUser proxyUser;
    @Autowired
    private ModelClient modelClient;
    @Autowired
    private ClientConfig clientConfig;
    @Autowired
    private EncryptionProvider encryptionProvider;

    @Param(value=ARTIFICIAL_FAILURE, required=false)
    protected static String artificialFailure;

    private ViPRCoreClient client;

    private List<String> locks = Lists.newArrayList();

    public EncryptionProvider getEncryptionProvider() {
        return encryptionProvider;
    }

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        this.encryptionProvider = encryptionProvider;
    }

    public ViPRProxyUser getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(ViPRProxyUser proxyUser) {
        this.proxyUser = proxyUser;
    }

    public ModelClient getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public ViPRCoreClient getClient() {
        if (client == null) {
            String proxyToken = ExecutionUtils.currentContext().getExecutionState().getProxyToken();
            client = new ViPRCoreClient(clientConfig);
            proxyUser.login(client.auth());
            client.setProxyToken(proxyToken);
        }
        return client;
    }

    protected void addAffectedResource(URI resourceId) {
        if (resourceId != null) {
            addAffectedResource(resourceId.toString());
        }
    }

    protected void addAffectedResource(DataObjectRestRep value) {
        if (value != null) {
            addAffectedResource(value.getId());
        }
    }

    protected void addAffectedResource(Task<? extends DataObjectRestRep> task) {
        if (task.getResourceId() != null) {
            addAffectedResource(task.getResourceId());

            if (task.getAssociatedResources() != null
                    && !task.getAssociatedResources().isEmpty()) {
                for (URI id : ResourceUtils.refIds(task.getAssociatedResources())) {
                    addAffectedResource(id);
                }
            }
        } else {
            warn("null resource for task, not adding to affected resources: %s", task);
        }
    }

    protected void addAffectedResources(Tasks<? extends DataObjectRestRep> tasks) {
        if (tasks != null) {
            for (Task<? extends DataObjectRestRep> task : tasks.getTasks()) {
                addAffectedResource(task);
            }
        }
    }

    @Override
    public void init() throws Exception {
        addInjectedValue(ViPRCoreClient.class, getClient());
        addInjectedValue(ModelClient.class, modelClient);
        addInjectedValue(EncryptionProvider.class, encryptionProvider);
    }

    @Override
    public void destroy() {
        super.destroy();
        releaseAllLocks();
        if (client != null && client.auth().isLoggedIn()) {
            client.auth().logout();
        }
    }

    protected <T> void addInjectedValue(Class<? extends T> clazz, T value) {
        ExecutionUtils.currentContext().addInjectedValue(clazz, value);
    }

    public static URI uri(String uri) {
        return ResourceUtils.uri(uri);
    }

    public static List<URI> uris(List<String> ids) {
        return ResourceUtils.uris(ids);
    }

    protected void acquireHostLock(Host host) {
        execute(new AcquireHostLock(host));
        locks.add(host.getId().toString());
    }

    protected void acquireHostLock(Host host, Cluster cluster) {
        execute(new AcquireHostLock(host, cluster));
        locks.add(host.getId().toString());
        logDebug("Locks that already exist:", locks);
        if (cluster != null) {
            locks.add(cluster.getId().toString());
        }
    }

    protected void releaseHostLock(Host host, Cluster cluster) {
        execute(new ReleaseHostLock(host, cluster));
        locks.remove(host.getId().toString());
        logDebug("Locks that already exist:", locks);
        if (cluster != null) {
            locks.remove(cluster.getId().toString());
        }
    }

    protected void acquireClusterLock(Cluster cluster) {
        if (cluster != null) {
            execute(new AcquireClusterLock(cluster));
            locks.add(cluster.getId().toString());
        }
    }

    private void releaseAllLocks() {
        for (String lock : locks) {
            logInfo("vipr.service.release.lock", lock);
            ExecutionUtils.releaseLock(lock);
        }
    }
    
    /**
     * Check if it is a recurrent order and retention policy is defined 
     * 
     * @return true if retention policy defined
     */
    protected boolean isRetentionRequired() {
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        if (event == null) {
            return false;
        }
        try {
            OrderCreateParam param = OrderCreateParam.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(event.getOrderCreationParam().getBytes(UTF_8)));
            String additionalScheduleInfo = param.getAdditionalScheduleInfo();
            if (additionalScheduleInfo == null) {
                return false;
            }
            Integer.parseInt(additionalScheduleInfo);
        } catch (Exception ex) {
            error("Unexpected exception when checking scheduler retention", ex);
            return false;
        }
        return true;
    }
    
    /**
     * Add newly created replica for given volume/CG  to db and keep records for applying retention policy
     * 
     * @param sourceId
     * @param tasks
     */
    protected <T extends DataObjectRestRep> void addRetainedReplicas(URI sourceId, List< Task<T> > tasks) {
        if (!isRetentionRequired()) {
            return;
        }
        if (tasks == null) {
            return;
        }
        
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        RetainedReplica retention = new RetainedReplica();
        retention.setScheduledEventId(event.getId());
        retention.setResourceId(sourceId);
        StringSet retainedResource = new StringSet();
        retention.setAssociatedReplicaIds(retainedResource);
        
        for (Task<? extends DataObjectRestRep> task : tasks) {
            URI resourceId = task.getResourceId();
            if (resourceId != null && !sourceId.equals(resourceId)) {
                info("Add %s to retained replica", resourceId.toString());
                retainedResource.add(resourceId.toString());
            }

            if (task.getAssociatedResources() != null
                    && !task.getAssociatedResources().isEmpty()) {
                for (URI id : ResourceUtils.refIds(task.getAssociatedResources())) {
                    if (sourceId.equals(id)) {
                        continue;
                    }
                    info("Add %s to retained replica", id.toString());
                    retainedResource.add(id.toString());
                }
            }
        }

        modelClient.save(retention);
    }
    
    protected <T extends DataObjectRestRep> void addRetainedReplicas(URI sourceId, String replicaName) {
        if (!isRetentionRequired()) {
            return;
        }
        
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        RetainedReplica retention = new RetainedReplica();
        retention.setScheduledEventId(event.getId());
        retention.setResourceId(sourceId);
        StringSet retainedResource = new StringSet();
        retention.setAssociatedReplicaIds(retainedResource);
        retainedResource.add(replicaName);

        modelClient.save(retention);
    }
    
    /**
     * 
     * Find obsolete replicas for given resource according to defined retention policy of this order
     * 
     * @param resourceId
     * @return the replica to be removed. Otherwise null in case of no removal required
     */
    protected List<RetainedReplica> findObsoleteReplica(String resourceId) {
        List<RetainedReplica> obsoleteReplicas = new ArrayList<RetainedReplica>();
        
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        Integer maxNumOfCopies = Integer.MAX_VALUE;
        try {
            OrderCreateParam param = OrderCreateParam.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(event.getOrderCreationParam().getBytes(UTF_8)));
            
            String additionalScheduleInfo = param.getAdditionalScheduleInfo();
            maxNumOfCopies = Integer.parseInt(additionalScheduleInfo);
        } catch (Exception ex) {
            error("Unexpected exception when checking scheduler retention", ex);
            return obsoleteReplicas;
        }
        
        List<NamedElement> replicaIdList = modelClient.findBy(RetainedReplica.class, "scheduledEventId", event.getId());
        List<RetainedReplica> replicas = new ArrayList<RetainedReplica>();
        for (NamedElement uri : replicaIdList) {
            RetainedReplica retention = modelClient.findById(RetainedReplica.class, uri.getId());
            if (retention.getResourceId().toString().equals(resourceId)) {
                replicas.add(retention);
            }
        }
        
        if (replicas.size() >= maxNumOfCopies) {
            Collections.sort(replicas, new Comparator<RetainedReplica>() {
                public int compare(RetainedReplica o1, RetainedReplica o2){
                    return o1.getCreationTime().compareTo(o2.getCreationTime());
                }
            });
            // get top oldest records  
            
            int endIndex =  replicas.size() - maxNumOfCopies + 1;
            obsoleteReplicas.addAll(replicas.subList(0, endIndex));
        }
        return obsoleteReplicas;
    }

    /**
     * Check for a boot volume.  Throw failure message if found.
     * @param volumeId volume ID
     */
    public static void checkForBootVolume(URI volumeId) {
        checkForBootVolumes(Arrays.asList(volumeId.toASCIIString()));                
    }

    /**
     * Check for boot volumes.  Throw failure message if found.
     * @param volumeIds volume IDs
     */
    public static void checkForBootVolumes(Collection<String> volumeIds) {
        for (URI volumeId : ResourceUtils.uris(volumeIds)) {
            BlockObjectRestRep volume = BlockStorageUtils.getBlockResource(volumeId);
            if (BlockStorageUtils.isVolumeBootVolume(volume)) {
            ExecutionUtils.fail("failTask.verifyBootVolume", volume.getName(), volume.getName());
            }
        }
    }

    /**
     * Invoke a failure if the artificialFailure variable is passed by the service.
     * This is an internal-only setting that allows testers and automated suites to inject a failure
     * into a catalog service step at key locations to test rollback.
     *
     * @param failure
     *            key from above
     */
    public static void artificialFailure(String failure) {
        if (artificialFailure != null && artificialFailure.equals(failure)) {
        	log("Injecting catalog failure: " + failure);
            ExecutionUtils.fail("failTask.ArtificialFailure", artificialFailure, artificialFailure);
        }
    }
    
    /**
     * Local logging, needed for debug on failure detection. Copied from InvokeTestFailure#log(String)
     * 
     * @see com.emc.storageos.util.InvokeTestFailure#log(String)
     * @param msg error message
     */
    private static void log(String msg) {
        FileOutputStream fop = null;
        try {
            String logFileName = "/opt/storageos/logs/invoke-test-failure.log";
            File logFile = new File(logFileName);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            fop = new FileOutputStream(logFile, true);
            fop.flush();
            StringBuffer sb = new StringBuffer(msg + "\n");
            // Last chance, if file is deleted, write manually.
            fop.write(sb.toString().getBytes());
        } catch (IOException e) {
            // It's OK if we can't log this.
        } finally {
            IOUtils.closeQuietly(fop);
        }
    }    
}
