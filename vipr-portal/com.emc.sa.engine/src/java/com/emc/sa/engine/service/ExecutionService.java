/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.service;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;


public interface ExecutionService {
    public void init() throws Exception;
    
    public void precheck() throws Exception;

	public void preLaunch() throws Exception ;
	
    public void execute() throws Exception;
    
	public void postLaunch() throws Exception;

	public void postcheck() throws Exception ;
	
    public void destroy();
    
    /*
     * Gets the order status for a completed order. This can either be SUCCESS or PARTIAL_SUCCESS.
     */
    public OrderStatus getCompletedOrderStatus();
}
