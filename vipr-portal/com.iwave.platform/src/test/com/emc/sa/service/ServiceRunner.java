/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.access.BeanFactoryLocator;
import org.springframework.beans.factory.access.BeanFactoryReference;
import org.springframework.context.ApplicationContext;
import org.springframework.context.access.ContextSingletonBeanFactoryLocator;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.sa.engine.ExecutionEngine;
import com.emc.sa.engine.ExecutionEngineImpl;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.services.util.LoggingUtils;
import com.google.common.collect.Maps;

public class ServiceRunner {
    private static ExecutionEngine ENGINE;
    private static ModelClient MODELS;
    private static ClassPathXmlApplicationContext CONTEXT;

    private static void initLogging() {
        LoggingUtils.configureIfNecessary("sasvc-log4j.properties");
    }

    private static void init() {
        if (StringUtils.isBlank(System.getProperty("platform.home"))) {
            System.setProperty("platform.home", new File("runtime/platform").getAbsolutePath());
        }
        if (CONTEXT == null) {
            initLogging();
            BeanFactoryLocator locator = ContextSingletonBeanFactoryLocator.getInstance();
            BeanFactoryReference ref = locator.useBeanFactory("platform");
            ApplicationContext parentContext = (ApplicationContext) ref.getFactory();

            String[] configs = { "service-runner.xml" };
            CONTEXT = new ClassPathXmlApplicationContext(configs, ServiceRunner.class, parentContext);
            ENGINE = (ExecutionEngineImpl)CONTEXT.getBean("ExecutionEngine",ExecutionEngineImpl.class);
            MODELS = (ModelClient)CONTEXT.getBean("ModelClient",ModelClient.class);
        }
    }

    public static void shutdown() {
        if (CONTEXT != null) {
            CONTEXT.stop();
            CONTEXT = null;
        }
    }

    public static ModelClient getModels() {
        init();
        return MODELS;
    }

    public static ExecutionEngine getEngine() {
        init();
        return ENGINE;
    }

    public static <T extends DataObject> T save(T model) {
        getModels().save(model);
        return model;
    }

    public static <T extends DataObject> T load(URI id, Class<T> clazz) {
        return getModels().of(clazz).findById(id);
    }

    public static <T extends DataObject> List<T> load(List<URI> ids, Class<T> clazz) {
        return getModels().of(clazz).findByIds(ids);
    }

    public static <T extends DataObject> List<T> load(StringSet ids, Class<T> clazz) {
        return getModels().of(clazz).findByIds(ids);
    }

    public static Order executeOrder(String name, String... params) {
        Order order = createOrder(name, params);
        getEngine().executeOrder(order);
        return order;
    }

    public static Order createOrder(String name, String... params) {
        Map<String, String> values = Maps.newLinkedHashMap();
        for (String param : params) {
            String paramName = StringUtils.substringBefore(param, "=");
            String paramValue = StringUtils.substringAfter(param, "=");
            values.put(paramName, paramValue);
        }

        return createOrder(name, values);
    }

    public static Order createOrder(String name, Map<String, String> params) {
        init();

        Order order = new Order();
        order.setCatalogServiceId(createService(name).getId());

        order = save(order);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            OrderParameter param = createParam(entry.getKey(), entry.getValue());
            param.setOrderId(order.getId());
            getModels().save(param);
        }

        return order;
    }

    private static CatalogService createService(String name) {
        CatalogService service = new CatalogService();
        service.setLabel(name);
        service.setTitle(name);
        service.setBaseService(name);
        return save(service);
    }

    private static OrderParameter createParam(String name, String value) {
        OrderParameter param = new OrderParameter();
        param.setLabel(name);
        param.setValue(value);
        return save(param);
    }

    public static void dumpOrder(Order order) {
        print("Order: %s", order.getId());
        print("  Status: %s", order.getOrderStatus());
        print("  Message: %s", order.getMessage());
        CatalogService service = load(order.getCatalogServiceId(), CatalogService.class);
        print("  Service: %s (%s)", service.getLabel(), service.getBaseService());

        ExecutionState state = load(order.getExecutionStateId(), ExecutionState.class);
        print("  Time: %s -> %s (%.1f s)", state.getStartDate(), state.getEndDate(),
                (state.getEndDate().getTime() - state.getStartDate().getTime()) / 1000.0);

        List<ExecutionLog> logs = load(state.getLogIds(), ExecutionLog.class);
        Collections.sort(logs, LOG_COMPARATOR);

        if (logs.size() > 0) {
            print("  Logs");
            for (ExecutionLog log : logs) {
                print("    - %s\t%s\t%s", log.getDate(), log.getLevel(), log.getMessage());
            }
        }

        List<ExecutionTaskLog> taskLogs = load(state.getTaskLogIds(), ExecutionTaskLog.class);
        Collections.sort(taskLogs, LOG_COMPARATOR);
        if (taskLogs.size() > 0) {
            print("  Task Logs");
            for (ExecutionTaskLog log : taskLogs) {
                print("    - %s\t%s\t%s\t%s", log.getDate(), log.getLevel(), log.getMessage(), log.getDetail());
            }
        }
    }

    private static void print(String message, Object... args) {
        System.out.println(args.length > 0 ? String.format(message, args) : message);
    }

    private static final Comparator<ExecutionLog> LOG_COMPARATOR = new Comparator<ExecutionLog>() {
        @Override
        public int compare(ExecutionLog arg0, ExecutionLog arg1) {
            return arg0.getDate().compareTo(arg1.getDate());
        }
    };
}
