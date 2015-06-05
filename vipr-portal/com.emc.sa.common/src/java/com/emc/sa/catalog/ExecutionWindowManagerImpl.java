/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.sa.model.dao.ModelClient;

@Component
public class ExecutionWindowManagerImpl implements ExecutionWindowManager {

    private static final Logger log = Logger.getLogger(ExecutionWindowManagerImpl.class);
    
    @Autowired
    private ModelClient client;
    
    public ExecutionWindow getExecutionWindowById(URI id) {
        if (id == null) {
            return null;
        }

        ExecutionWindow executionWindow = client.executionWindows().findById(id);
        
        return executionWindow;
    }   
    
    public void createExecutionWindow(ExecutionWindow executionWindow) {
        client.save(executionWindow);
    }
    
    public void updateExecutionWindow(ExecutionWindow executionWindow) {
        client.save(executionWindow);
    }
    
    public void deleteExecutionWindow(ExecutionWindow executionWindow) {
        client.delete(executionWindow);
    }    
    
    public List<ExecutionWindow> getExecutionWindows(URI tenantId) {
        return client.executionWindows().findAll(tenantId.toString());
    }
    
    public ExecutionWindow getExecutionWindow(String name, URI tenantId) {
        List<ExecutionWindow> windows =  getExecutionWindows(tenantId);
        for (ExecutionWindow executionWindow: windows) {
            if (name.equals(executionWindow.getLabel())) {
                return executionWindow;
            }
        }
        return null;
    }
    
}
