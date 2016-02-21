/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.AbstractExecutionService;
import com.emc.sa.engine.service.ExecutionService;
import com.emc.sa.engine.service.ExecutionServiceFactory;
import com.emc.sa.engine.service.ServiceNotFoundException;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.mock.InMemoryDbClient;
import com.emc.sa.model.mock.StubCoordinatorClientImpl;
import com.emc.storageos.api.service.utils.DummyDBClient;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.google.common.collect.Maps;

@SuppressWarnings("unused")
public class ExecutionEngineImplTest {
    private static final Logger LOG = Logger.getLogger(ExecutionEngineImplTest.class);

    private ModelClient modelClient;
    private CoordinatorClient coordinatorClient;

    @Before
    public void setUp() {
        VdcUtil.setDbClient(MockDbClient.create());
        modelClient = new ModelClient(new InMemoryDbClient());
        coordinatorClient = new StubCoordinatorClientImpl(URI.create("urn:StubCoordinatorClientImpl"));
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSimpleService() {
        EmptyService service = new EmptyService() {
            @Param
            protected String value;
            @Param
            protected Long size;
        };

        Order valid = executeOrder(service, "Simple", "value=Test", "size=10");
        Assert.assertEquals(OrderStatus.SUCCESS.name(), valid.getOrderStatus());

        Order invalid = executeOrder(service, "Simple", "value=Test");
        Assert.assertEquals(OrderStatus.ERROR.name(), invalid.getOrderStatus());
    }

    @Test
    public void testRollback() {
        final EmptyTask firstRollbackTask = new EmptyTask("First Rollback");
        final EmptyTask secondRollbackTask = new EmptyTask("Second Rollback");
        ExecutionService rollbackService = new EmptyService() {
            @Param
            protected boolean rollback;

            @Override
            public void execute() throws Exception {
                addRollback(firstRollbackTask);
                addRollback(secondRollbackTask);
                if (rollback) {
                    throw new Exception("Trigger rollback");
                }
            }
        };

        Order noRollbackOrder = executeOrder(rollbackService, "NoRollback", "rollback=false");
        Assert.assertEquals(OrderStatus.SUCCESS.name(), noRollbackOrder.getOrderStatus());
        Assert.assertFalse(firstRollbackTask.wasRun);
        Assert.assertFalse(secondRollbackTask.wasRun);

        Order rollbackOrder = executeOrder(rollbackService, "Rollback", "rollback=true");
        Assert.assertEquals(OrderStatus.ERROR.name(), rollbackOrder.getOrderStatus());
        Assert.assertTrue(firstRollbackTask.wasRun);
        Assert.assertTrue(secondRollbackTask.wasRun);
    }

    @Test
    public void testFailDuringRollback() {
        final FailureTask rollbackTask = new FailureTask("Fail During Rollback", "Fail during rollback");
        final EmptyTask skippedTask = new EmptyTask("Skipped Rollback");
        ExecutionService rollbackService = new EmptyService() {
            @Override
            public void execute() throws Exception {
                addRollback(skippedTask);
                addRollback(rollbackTask);
                throw new Exception("Trigger rollback");
            }
        };

        Order order = executeOrder(rollbackService, "FailDuringRollback");
        Assert.assertEquals(OrderStatus.ERROR.name(), order.getOrderStatus());
        Assert.assertEquals(true, rollbackTask.wasRun);
        Assert.assertEquals(false, skippedTask.wasRun);
    }

    @Test
    public void testClearRollback() {
        final EmptyTask rollback1 = new EmptyTask("Rollback 1");
        final EmptyTask rollback2 = new EmptyTask("Rollback 2");
        ExecutionService rollbackService = new EmptyService() {
            @Override
            public void execute() throws Exception {
                addRollback(rollback1);
                addRollback(rollback2);
                clearRollback();
                throw new Exception("Trigger rollback");
            }
        };

        Order order = executeOrder(rollbackService, "ClearRollback");
        Assert.assertEquals(OrderStatus.ERROR.name(), order.getOrderStatus());
        Assert.assertEquals(false, rollback1.wasRun);
        Assert.assertEquals(false, rollback2.wasRun);
    }

    @Test
    public void testClearSomeRollback() {
        final EmptyTask rollback1 = new EmptyTask("Rollback 1");
        final EmptyTask rollback2 = new EmptyTask("Rollback 2");
        ExecutionService rollbackService = new EmptyService() {
            @Override
            public void execute() throws Exception {
                addRollback(rollback1);
                clearRollback();
                addRollback(rollback2);
                throw new Exception("Trigger rollback");
            }
        };

        Order order = executeOrder(rollbackService, "ClearSomeRollback");
        Assert.assertEquals(OrderStatus.ERROR.name(), order.getOrderStatus());
        Assert.assertEquals(false, rollback1.wasRun);
        Assert.assertEquals(true, rollback2.wasRun);
    }

    @Test
    public void testRemoveRollbackByType() {
        final FailureTask rollback1 = new FailureTask("Rollback 1", "Fail 1");
        final EmptyTask rollback2 = new EmptyTask("Rollback 2");
        ExecutionService rollbackService = new EmptyService() {
            @Override
            public void execute() throws Exception {
                addRollback(rollback1);
                addRollback(rollback2);
                removeRollback(FailureTask.class);
                throw new Exception("Trigger rollback");
            }
        };

        Order order = executeOrder(rollbackService, "RemoveRollbackByType");
        Assert.assertEquals(OrderStatus.ERROR.name(), order.getOrderStatus());
        Assert.assertEquals(false, rollback1.wasRun);
        Assert.assertEquals(true, rollback2.wasRun);
    }

    @Test
    public void testErrorCreatingService() {
        ExecutionEngineImpl engine = new ExecutionEngineImpl();
        engine.setModelClient(modelClient);
        engine.setServiceFactory(new ExecutionServiceFactory() {
            @Override
            public ExecutionService createService(Order order, CatalogService catalogService)
                    throws ServiceNotFoundException {
                throw new RuntimeException("Unexpected error");
            }
        });
        Order order = executeOrder(engine, createOrder("ErrorCreatingService"));
        Assert.assertEquals(OrderStatus.ERROR.name(), order.getOrderStatus());
    }

    protected Order executeOrder(ExecutionService service, String orderName, String... params) {
        return executeOrder(service, createOrder(orderName, params));
    }

    protected Order executeOrder(ExecutionService service, Order order) {
        ExecutionEngine engine = createEngine(service);
        return executeOrder(engine, order);
    }

    protected Order executeOrder(ExecutionEngine engine, Order order) {
        engine.executeOrder(order);
        return order;
    }

    protected ExecutionEngine createEngine(ExecutionService service) {
        ExecutionEngineImpl engine = new ExecutionEngineImpl();
        engine.setModelClient(modelClient);
        engine.setServiceFactory(new SimpleServiceFactory(service));
        engine.setCoordinatorClient(coordinatorClient);
        return engine;
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

    protected static class SimpleServiceFactory implements ExecutionServiceFactory {
        private ExecutionService serviceInstance;

        public SimpleServiceFactory(ExecutionService serviceInstance) {
            this.serviceInstance = serviceInstance;
        }

        @Override
        public ExecutionService createService(Order order, CatalogService catalogService)
                throws ServiceNotFoundException {
            return serviceInstance;
        }
    }

    protected static class EmptyService extends AbstractExecutionService {
        @Override
        public void precheck() throws Exception {
            debug("precheck parameters: %s", ExecutionUtils.currentContext().getParameters());
        }

        @Override
        public void execute() throws Exception {
            debug("execute parameters: %s", ExecutionUtils.currentContext().getParameters());
        }

		@Override
		public void preLaunch() throws Exception {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postLaunch() throws Exception {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void postcheck() throws Exception {
			// TODO Auto-generated method stub
			
		}
    }

    protected static class EmptyTask extends ExecutionTask<Void> {
        protected boolean wasRun;

        public EmptyTask(String name) {
            setName(name);
        }

        @Override
        public Void executeTask() throws Exception {
            wasRun = true;
            return null;
        }
    }

    protected static class FailureTask extends EmptyTask {
        protected Exception failure;

        public FailureTask(String name, String message) {
            this(name, new Exception(message));
        }

        public FailureTask(String name, Exception failure) {
            super(name);
            this.failure = failure;
        }

        @Override
        public Void executeTask() throws Exception {
            super.executeTask();
            throw failure;
        }
    }

    /**
     * This overrides the queryIterativeObjects methods of DummyDBClient so that the URIUtil.createId will not fail. It
     * queries for VirtualDataCenters, if a null iterator is returned a NPE is thrown.
     */
    public static class MockDbClient extends DummyDBClient {
        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> ids) {
            return queryIterativeObjects(clazz, ids, true);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> ids,
                boolean activeOnly) throws DatabaseException {
            return Collections.EMPTY_LIST.iterator();
        }

        public static MockDbClient create() {
            MockDbClient client = new MockDbClient();
            client.start();
            return client;
        }
    }
}
