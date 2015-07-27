/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.sa.model.DBClientTestBase;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.storageos.db.client.URIUtil;

public class SortedIndexTest extends DBClientTestBase {

    private static final Logger _logger = Logger.getLogger(SortedIndexTest.class);
    
    @Test
    public void testSorting() {
        Order o1 = createOrder(OrderStatus.PENDING);
        
        ModelClient modelClient = getModelClient();
        modelClient.save(o1);
        
        OrderParameter op1 = createOrderParameter("op1", "op1Value", 2);
        op1.setOrderId(o1.getId());
        modelClient.save(op1);
        
        OrderParameter op2 = createOrderParameter("op2", "op2Value", 1);
        op2.setOrderId(o1.getId());
        modelClient.save(op2);
        
        OrderParameter op3 = createOrderParameter("op3", "op3Value", 3);
        op3.setOrderId(o1.getId());
        modelClient.save(op3);        
        
        List<OrderParameter> orderParameters = modelClient.orderParameters().findByOrderId(o1.getId());
        Assert.assertNotNull(orderParameters);
        Assert.assertEquals(3, orderParameters.size());
        Assert.assertEquals(op2.getLabel(), orderParameters.get(0).getLabel());
        Assert.assertEquals(op1.getLabel(), orderParameters.get(1).getLabel());
        Assert.assertEquals(op3.getLabel(), orderParameters.get(2).getLabel());
        
        SortedIndexUtils.moveDown(op1, modelClient);
        
        orderParameters = modelClient.orderParameters().findByOrderId(o1.getId());
        Assert.assertNotNull(orderParameters);
        Assert.assertEquals(3, orderParameters.size());        
        Assert.assertEquals(op2.getLabel(), orderParameters.get(0).getLabel());
        Assert.assertEquals(op3.getLabel(), orderParameters.get(1).getLabel());
        Assert.assertEquals(op1.getLabel(), orderParameters.get(2).getLabel());    
        
        SortedIndexUtils.moveUp(op3, modelClient);
        
        orderParameters = modelClient.orderParameters().findByOrderId(o1.getId());
        Assert.assertNotNull(orderParameters);
        Assert.assertEquals(3, orderParameters.size());        
        Assert.assertEquals(op3.getLabel(), orderParameters.get(0).getLabel());
        Assert.assertEquals(op2.getLabel(), orderParameters.get(1).getLabel());
        Assert.assertEquals(op1.getLabel(), orderParameters.get(2).getLabel());   
        
        OrderParameter op4 = createOrderParameter("op4", "op4Value", null);
        op4.setOrderId(o1.getId());
        modelClient.save(op4);        
        
        orderParameters = modelClient.orderParameters().findByOrderId(o1.getId());
        Assert.assertNotNull(orderParameters);
        Assert.assertEquals(4, orderParameters.size());        
        Assert.assertEquals(op3.getLabel(), orderParameters.get(0).getLabel());
        Assert.assertEquals(op2.getLabel(), orderParameters.get(1).getLabel());
        Assert.assertEquals(op1.getLabel(), orderParameters.get(2).getLabel());
        Assert.assertEquals(op4.getLabel(), orderParameters.get(3).getLabel());
        
    }
    
    private static Order createOrder(OrderStatus status) {
        Order model = new Order();
        model.setId(URIUtil.createId(Order.class));
        model.setLabel("foo");
        URI catalogServiceId = URIUtil.createId(CatalogService.class);
        model.setCatalogServiceId(catalogServiceId);
        Date d = new Date();
        model.setDateCompleted(d);
        model.setMessage("my message");
        model.setOrderStatus(status.name());
        model.setSubmittedByUserId("urn:User:1");
        model.setSummary("my summary");
        return model;
    }
    
    private static OrderParameter createOrderParameter(String label, String value, Integer sortedIndex) {
        OrderParameter model = new OrderParameter();
        model.setId(URIUtil.createId(OrderParameter.class));
        model.setLabel(label);
        model.setFriendlyLabel("my friendly name");
        model.setFriendlyValue("my friendly value");
        model.setUserInput(false);
        model.setValue(value);
        model.setSortedIndex(sortedIndex);
        return model;
    }    
    
}
