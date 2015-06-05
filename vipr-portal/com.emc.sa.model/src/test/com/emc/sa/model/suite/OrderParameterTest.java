package com.emc.sa.model.suite;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;

public class OrderParameterTest extends BaseModelTest<OrderParameter> {

    private static final Logger _logger = Logger.getLogger(OrderParameterTest.class);
    
    public OrderParameterTest() {
        super(OrderParameter.class);
    }
    
    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist OrderParameter test");

        OrderParameter model = new OrderParameter();
        model.setLabel("foo");
        model.setFriendlyLabel("my friendly name");
        model.setFriendlyValue("my friendly value");
        model.setUserInput(false);
        model.setValue("my value");
        
        save(model);
        model = findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals("my friendly name", model.getFriendlyLabel());
        Assert.assertEquals("my friendly value", model.getFriendlyValue());
        Assert.assertEquals(false, model.getUserInput());
        Assert.assertEquals("my value", model.getValue());
        
    }
    
    protected static OrderParameter create(String name, String value) {
        OrderParameter model = new OrderParameter();
        model.setLabel(name);
        model.setFriendlyLabel(name);
        model.setFriendlyValue(value);
        model.setValue(value);
        return model;
    }
}
