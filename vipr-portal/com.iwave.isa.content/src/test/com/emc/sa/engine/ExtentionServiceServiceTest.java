package com.emc.sa.engine;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.sa.engine.ExecutionEngineImplTest.EmptyService;
import com.emc.sa.engine.ExecutionEngineImplTest.MockDbClient;
import com.emc.sa.engine.ExecutionEngineImplTest.SimpleServiceFactory;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.extension.ExternalTaskParams;
import com.emc.sa.engine.service.AbstractExecutionService;
import com.emc.sa.engine.service.DefaultExecutionServiceFactory;
import com.emc.sa.engine.service.ExecutionService;
import com.emc.sa.engine.service.ExternalTaskExecutor;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.plugins.object.GenericPluginService;
import com.emc.sa.service.vipr.plugins.object.GenericPluginServiceHelper;
import com.emc.sa.service.vipr.plugins.object.GenericPluginUtils;
import com.emc.sa.service.vipr.plugins.object.GenericRestRep;
import com.emc.sa.service.vipr.plugins.tasks.CustomSample;
import com.emc.sa.engine.service.ServiceNotFoundException;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.mock.InMemoryDbClient;
import com.emc.sa.model.mock.StubCoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Maps;

public class ExtentionServiceServiceTest {


	private ModelClient modelClient;
	private CoordinatorClient coordinatorClient;

	public void setUp() {
		VdcUtil.setDbClient(MockDbClient.create());
		modelClient = new ModelClient(new InMemoryDbClient());
		coordinatorClient = new StubCoordinatorClientImpl(URI.create("urn:StubCoordinatorClientImpl"));
	}

	public void tearDown() throws Exception {
	}
	
    public void testSimpleService() {
                
        GenericPluginService gservice = new GenericPluginService();
        
        CustomSample genericExtensionTask = new CustomSample();
        
        gservice.setGenericExtensionTask(genericExtensionTask);

        Order valid = executeOrder(gservice, "Simple", "value=Test", "size=10");
        Assert.assertEquals(OrderStatus.SUCCESS.name(), valid.getOrderStatus());

        Order invalid = executeOrder(gservice, "Simple", "value=Test");
        Assert.assertEquals(OrderStatus.ERROR.name(), invalid.getOrderStatus());
    }
    
    protected Order executeOrder(ExecutionService service, String orderName, String... params) {
        return executeOrder(service, createOrder(orderName, params));
    }

    protected Order executeOrder(ExecutionService service, Order order) {
        ExecutionEngine engine = createEngine(service);
        return executeOrder(engine, order);
    }

    protected ExecutionEngine createEngine(ExecutionService service) {
        ExecutionEngineImpl engine = new ExecutionEngineImpl();
        engine.setModelClient(modelClient);
        engine.setServiceFactory(new SimpleServiceFactory(service));
        engine.setCoordinatorClient(coordinatorClient);
        return engine;
    }
    protected Order executeOrder(ExecutionEngine engine, Order order) {
        engine.executeOrder(order);
        return order;
    }
    

    protected Order createOrder(String name, String... params) {
        Map<String, String> values = Maps.newLinkedHashMap();
        for (String param : params) {
            String paramName = StringUtils.substringBefore(param, "=");
            String paramValue = StringUtils.substringAfter(param, "=");
            values.put(paramName, paramValue);
        }

        return createOrder(name, values);
    }

    protected Order createOrder(String name, Map<String, String> params) {
        ExecutionState state = new ExecutionState();
        modelClient.save(state);

        Order order = new Order();
        order.setExecutionStateId(state.getId());
        CatalogService service = new CatalogService();
        service.setLabel(name);
        modelClient.save(service);
        order.setCatalogServiceId(service.getId());

        modelClient.save(order);
        int index = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            OrderParameter param = new OrderParameter();
            param.setLabel(entry.getKey());
            param.setValue(entry.getValue());
            param.setOrderId(order.getId());
            modelClient.save(param);
        }
        return order;
    }


	public static void main(String[] args) throws Exception {
		ExtentionServiceServiceTest exs = new ExtentionServiceServiceTest();
		exs.setUp();
		exs.testSimpleService();

		exs.tearDown();

	}
}
