/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import java.text.DateFormat
import java.text.SimpleDateFormat

import com.emc.storageos.model.BulkIdParam
import com.emc.vipr.client.core.util.ResourceUtils
import com.emc.vipr.model.catalog.CatalogCategoryRestRep
import com.emc.vipr.model.catalog.CatalogServiceCreateParam
import com.emc.vipr.model.catalog.CatalogServiceFieldParam
import com.emc.vipr.model.catalog.CatalogServiceRestRep
import com.emc.vipr.model.catalog.ExecutionLogRestRep
import com.emc.vipr.model.catalog.ExecutionStateRestRep
import com.emc.vipr.model.catalog.OrderCreateParam
import com.emc.vipr.model.catalog.OrderLogRestRep
import com.emc.vipr.model.catalog.OrderRestRep
import com.emc.vipr.model.catalog.Parameter
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep


class OrderServiceHelper {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    static List<URI> createdOrders;
    static List<URI> createdServices;

    static createOrder(URI tenantId, URI service, List<Parameter> params) {
        OrderCreateParam orderCreate = new OrderCreateParam();
        orderCreate.setCatalogService(service);
        orderCreate.setTenantId(tenantId);
        orderCreate.setParameters(params)

        return catalog.orders().submit(orderCreate);
    }

    static createService(List<CatalogServiceFieldParam> params,
            String serviceId, URI categoryId) {

        CatalogServiceCreateParam serviceCreate = new CatalogServiceCreateParam();
        serviceCreate.setBaseService(serviceId);
        serviceCreate.setApprovalRequired(false);
        serviceCreate.setCatalogCategory(categoryId);
        serviceCreate.setCatalogServiceFields(params);
        serviceCreate.setDescription("Sample Service");
        serviceCreate.setExecutionWindowRequired(false);
        serviceCreate.setTitle("Sample Service");
        serviceCreate.setName("SampleService");
        serviceCreate.setImage("SampleService.png");

        return catalog.services().create(serviceCreate);
    }

    static void orderServiceTest() {
        println "  ## Order Service Test ## "
        createdOrders = new ArrayList<URI>();
        createdServices = new ArrayList<URI>();

        println "Getting tenantId to create an order"
        URI tenantId = catalog.getUserTenantId();
        println ""

        println "tenantId: " + tenantId
        println ""

        println "Getting root catalog category"
        CatalogCategoryRestRep rootCategory =
                catalog.categories().getRootCatalogCategory(tenantId.toString());
        println ""

        URI rootCategoryId = rootCategory.getId();
        println "rootCategoryId: " + rootCategoryId;
        println ""

        println "Getting all service descriptors"
        List<ServiceDescriptorRestRep> sds =
                catalog.serviceDescriptors().getServiceDescriptors();
        println ""

        assertNotNull(sds);
        assertEquals(Boolean.TRUE, sds.size() > 0);

        println "Finding Sample Service Descriptor"
        String sampleServiceDescriptorId = null;
        ServiceDescriptorRestRep sampleServiceDescriptor = null;

        sds.each {

            if (it != null
            && it.getServiceId() == "SampleService") {
                println "Found sample service descriptor: " + it.getServiceId();
                println ""
                sampleServiceDescriptorId = it.getServiceId();
                sampleServiceDescriptor = it;
            }
        }

        assertNotNull(sampleServiceDescriptorId);
        assertNotNull(sampleServiceDescriptor);

        println "Creating Sample Service"

        CatalogServiceRestRep sampleService =
                createService(null,sampleServiceDescriptorId,rootCategoryId)
        createdServices.add(sampleService.getId());

        assertNotNull(sampleService);
        assertNotNull(sampleService.getId());
        assertEquals(rootCategoryId, sampleService.getCatalogCategory().getId());

        Parameter param1 = new Parameter();
        param1.setLabel("text");
        param1.setFriendlyValue("testing");
        param1.setValue("testing");

        Parameter param2 = new Parameter();
        param2.setLabel("number");
        param2.setFriendlyValue("1");
        param2.setValue("1");

        Parameter param3 = new Parameter();
        param3.setFriendlyValue("Odd");
        param3.setLabel("choice");
        param3.setValue("odd");

        Parameter param4 = new Parameter();
        param4.setFriendlyValue("Success");
        param4.setLabel("completion");
        param4.setValue("success");

        Parameter param5 = new Parameter();
        param5.setLabel("password");
        param5.setFriendlyValue("password");
        param5.setValue("password");
        param5.setEncrypted(true);

        List<Parameter> params = new ArrayList<Parameter>();
        params.add(param1);
        params.add(param2);
        params.add(param3);
        params.add(param4);
        params.add(param5);

        println "Creating an order"
        println ""
        OrderRestRep createdOrder =
                createOrder(tenantId, sampleService.getId(), params);
        createdOrders.add(createdOrder.getId());

        assertNotNull(createdOrder);
        assertNotNull(createdOrder.getId());
        assertEquals(sampleService.getId(), createdOrder.getCatalogService().getId());
        assertNotNull(createdOrder.getParameters());
        assertEquals(5, createdOrder.getParameters().size());
        assertNotNull(createdOrder.getCreationTime());
        assertNotNull(createdOrder.getMessage());
        assertNotNull(createdOrder.getOrderNumber());
        assertNotNull(createdOrder.getOrderStatus());

        println "Created order " + createdOrder.getId();
        println ""

        println "Creating another order"
        println ""
        OrderRestRep anotherOrder =
                createOrder(tenantId, sampleService.getId(), params);
        createdOrders.add(anotherOrder.getId());

        assertNotNull(anotherOrder);
        assertNotNull(anotherOrder.getId());
        assertEquals(sampleService.getId(), anotherOrder.getCatalogService().getId());
        assertNotNull(anotherOrder.getParameters());
        assertEquals(5, anotherOrder.getParameters().size());
        assertNotNull(anotherOrder.getCreationTime());
        assertNotNull(anotherOrder.getMessage());
        assertNotNull(anotherOrder.getOrderNumber());
        assertNotNull(anotherOrder.getOrderStatus());

        println "Created order " + anotherOrder.getId();
        println ""

        List<URI> orderIds = new ArrayList<URI>();
        orderIds.add(createdOrder.getId());
        orderIds.add(anotherOrder.getId());

        println "Listing bulk resources - orders"
        println ""

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(orderIds);

        List<OrderRestRep> orders =
                catalog.orders().getBulkResources(bulkIds);

        assertNotNull(orders);
        assertEquals(2, orders.size());
        assertEquals(Boolean.TRUE, orderIds.contains(orders.get(0).getId()));
        assertEquals(Boolean.TRUE, orderIds.contains(orders.get(1).getId()));

        println "Listing orders by tenant"
        println ""

        orders =
                catalog.orders().getByTenant(tenantId);

        List<URI> retrievedOrders = ResourceUtils.ids(orders);

        assertNotNull(orders);
        assertEquals(Boolean.TRUE, , orders.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedOrders.contains(orderIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedOrders.contains(orderIds.get(1)));

        println "sleeping till order finishes zzzzzzzz ";
        println ""
        pause(30000);

        println "Getting order " + createdOrder.getId();
        OrderRestRep retrievedOrder = catalog.orders().get(createdOrder.getId());
        println ""

        assertEquals(createdOrder.getId(), retrievedOrder.getId());

        println "Getting order execution state " + retrievedOrder.getId();
        println ""

        ExecutionStateRestRep orderState =
                catalog.orders().getExecutionState(retrievedOrder.getId());

        assertNotNull(orderState);
        assertNotNull(orderState.getStartDate());
        assertNotNull(orderState.getExecutionStatus());
        assertNotNull(orderState.getCurrentTask());

        println "Getting order execution logs " + retrievedOrder.getId();
        println ""

        List<ExecutionLogRestRep> executionLogs =
                catalog.orders().getExecutionLogs(retrievedOrder.getId());

        assertNotNull(executionLogs);
        assertEquals(Boolean.TRUE, , executionLogs.size() >= 3);
        assertNotNull(executionLogs.get(0));
        assertNotNull((executionLogs.get(0)).getDetail());
        assertNotNull((executionLogs.get(0)).getElapsed());

        println "Getting order logs " + retrievedOrder.getId();
        println ""

        List<OrderLogRestRep> orderLogs =
                catalog.orders().getLogs(retrievedOrder.getId());

        assertNotNull(orderLogs);
        assertEquals(Boolean.TRUE, , orderLogs.size() >= 4);
        assertNotNull(orderLogs.get(0));
        assertNotNull((orderLogs.get(0)).getDate());
        assertNotNull((orderLogs.get(0)).getLevel());
        assertNotNull((orderLogs.get(0)).getMessage());

        println "Searching orders by status SUCCESS";
        println ""

        List<OrderRestRep> orderResults =
                catalog.orders().search().byStatus(OrderStatus.SUCCESS.toString(), tenantId).run();

        retrievedOrders = ResourceUtils.ids(orderResults);

        assertNotNull(orderResults);
        assertEquals(Boolean.TRUE, , orderResults.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedOrders.contains(orderIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedOrders.contains(orderIds.get(1)));

        Calendar cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date startDate = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date endDate = cal.getTime();

        DateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);

        println "Searching orders by status by TIMERANGE - out of bounds";
        println ""

        orderResults =
                catalog.orders().search().byTimeRange(dateFormat.format(startDate),
                dateFormat.format(endDate), tenantId).run();

        retrievedOrders = ResourceUtils.ids(orderResults);

        assertNotNull(orderResults);

        if (!orderResults.isEmpty()){
            assertEquals("Search By Time Range doesn't contain " + orderIds.get(0), Boolean.FALSE, retrievedOrders.contains(orderIds.get(0)));
            assertEquals("Search By Time Range doesn't contain " + orderIds.get(1), Boolean.FALSE, retrievedOrders.contains(orderIds.get(1)));
        }

        println "Searching orders by status by TIMERANGE - in bounds";
        println ""

        startDate = new Date();

        cal = new GregorianCalendar();
        cal.setTime(startDate);
        cal.add(Calendar.DAY_OF_MONTH, -1);

        startDate = cal.getTime();

        orderResults =
                catalog.orders().search().byTimeRange(dateFormat.format(startDate), dateFormat.format(endDate)).run();

        retrievedOrders = ResourceUtils.ids(orderResults);

        assertNotNull(orderResults);
        assertEquals(Boolean.TRUE, , orderResults.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedOrders.contains(orderIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedOrders.contains(orderIds.get(1)));
    }

    private static void pause(long sleepTime) throws InterruptedException {
        Thread.sleep(sleepTime);
    }

    static void orderServiceTearDown() {
        println "  ## Order Service Test Clean up ## "

        println "Getting created services"
        println ""
        if (createdServices != null) {

            createdServices.each {
                println "Getting test services: " + it;
                println ""
                CatalogServiceRestRep serviceToDelete =
                        catalog.services().get(it);
                if (serviceToDelete != null
                && !serviceToDelete.getInactive()) {
                    println "Deleting test service: " + it;
                    println ""
                    catalog.services().deactivate(it);
                }
            }
        }

        println "Cleanup Complete.";
        println ""
    }
}
