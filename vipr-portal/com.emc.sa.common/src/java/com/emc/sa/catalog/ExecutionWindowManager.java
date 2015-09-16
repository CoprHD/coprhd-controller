/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;

public interface ExecutionWindowManager {

    public ExecutionWindow getExecutionWindowById(URI id);

    public void createExecutionWindow(ExecutionWindow executionWindow);

    public void updateExecutionWindow(ExecutionWindow executionWindow);

    public void deleteExecutionWindow(ExecutionWindow executionWindow);

    public List<ExecutionWindow> getExecutionWindows(URI tenantId);

    public ExecutionWindow getExecutionWindow(String name, URI tenantId);

}
