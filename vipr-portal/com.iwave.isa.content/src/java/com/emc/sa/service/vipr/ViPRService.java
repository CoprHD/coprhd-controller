/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.engine.ExecutionContext;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.extension.ExternalTaskParams;
import com.emc.sa.engine.service.AbstractExecutionService;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.plugins.object.GenericPluginUtils;
import com.emc.sa.service.vipr.tasks.AcquireHostLock;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

public abstract class ViPRService extends AbstractExecutionService  {
    @Autowired
    private ViPRProxyUser proxyUser;
    @Autowired
    private ModelClient modelClient;
    @Autowired
    private ClientConfig clientConfig;
    @Autowired
    private EncryptionProvider encryptionProvider;

    private ViPRCoreClient client;

    private List<String> locks = Lists.newArrayList();

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
        }
        else {
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

	@Override
	public void preLaunch() throws Exception {
    	if (genericExtensionTask !=null){
    		ExternalTaskParams genericExtensionTaskParams = new ExternalTaskParams();
    		ExecutionContext context = ExecutionUtils.currentContext();
    		genericExtensionTaskParams.setExternalParam((String)context.getParameters().get("externalParam"));
    		
    		 try {
    			 GenericPluginUtils.executeExtenstionTask(genericExtensionTask,genericExtensionTaskParams,"preLaunch");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
		
	}

	@Override
	public void postLaunch() throws Exception {
    	if (genericExtensionTask !=null){
    		ExternalTaskParams genericExtensionTaskParams = new ExternalTaskParams();
    		ExecutionContext context = ExecutionUtils.currentContext();
    		genericExtensionTaskParams.setExternalParam((String)context.getParameters().get("externalParam"));
    		//genericExtensionTaskParams.setExternalParam(externalParam);
    		
    		 try {
    			 GenericPluginUtils.executeExtenstionTask(genericExtensionTask,genericExtensionTaskParams,"postLaunch");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
	}

	@Override
	public void postcheck() throws Exception {

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

        if (cluster != null) {
            locks.add(cluster.getId().toString());
        }
    }

    private void releaseAllLocks() {
        for (String lock : locks) {
            logInfo("vipr.service.release.lock", lock);
            ExecutionUtils.releaseLock(lock);
        }
    }
}