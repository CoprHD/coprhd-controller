/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.storageos.model.BulkIdParam
import com.emc.vipr.client.AuthClient
import com.emc.vipr.client.ViPRCatalogClient2
import com.emc.vipr.client.core.util.ResourceUtils
import com.emc.vipr.model.catalog.ApprovalRestRep
import com.emc.vipr.model.catalog.ApprovalUpdateParam
import com.emc.vipr.model.catalog.CatalogCategoryRestRep
import com.emc.vipr.model.catalog.CatalogPreferencesRestRep
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam
import com.emc.vipr.model.catalog.CatalogServiceCreateParam
import com.emc.vipr.model.catalog.CatalogServiceFieldParam
import com.emc.vipr.model.catalog.CatalogServiceRestRep
import com.emc.vipr.model.catalog.OrderCreateParam
import com.emc.vipr.model.catalog.OrderRestRep
import com.emc.vipr.model.catalog.Parameter
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep


class ApprovalServiceHelper {

    static List<URI> createdOrders;
    static List<URI> createdServices;

    private static boolean existingPreferences = false
    private static String existingApprovalUrl
    private static String existingApproverEmail

    static updateApproval(URI approval) {

        String rootAuthToken = new AuthClient(clientConfig).login(System.getenv("AD_USER2_USERNAME"), System.getenv("AD_USER_PASSWORD"))
        ViPRCatalogClient2 rootCatalog = new ViPRCatalogClient2(clientConfig).withAuthToken(rootAuthToken)

        ApprovalUpdateParam approvalUpdate= new ApprovalUpdateParam();
        approvalUpdate.setApprovalStatus("APPROVED");
        approvalUpdate.setMessage("Updated");

        return rootCatalog.approvals().update(approval, approvalUpdate);
    }

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
        serviceCreate.setApprovalRequired(true);
        serviceCreate.setCatalogCategory(categoryId);
        serviceCreate.setCatalogServiceFields(params);
        serviceCreate.setDescription("Sample Service");
        serviceCreate.setExecutionWindowRequired(false);
        serviceCreate.setTitle("Sample Service");
        serviceCreate.setName("Sample Service");
        serviceCreate.setImage("SampleService.png");

        return catalog.services().create(serviceCreate);
    }

    static void approvalServiceTest() {

        println "  ## Approval Service Test ## "

        println "  ## Catalog Preferences Service Test ## "

        CatalogPreferencesRestRep preferences = catalog.catalogPreferences().getPreferences();

        assertNotNull("Catalog Preferences are not null", preferences)

        existingPreferences = true
        existingApprovalUrl = preferences.getApprovalUrl()
        existingApproverEmail = preferences.getApproverEmail()

        CatalogPreferencesUpdateParam updateParam = new CatalogPreferencesUpdateParam()
        updateParam.setApprovalUrl("https://localhost/bla")
        updateParam.setApproverEmail("test@test.com")
        catalog.catalogPreferences().updatePreferences(updateParam);

        CatalogPreferencesRestRep updatedPreferences = catalog.catalogPreferences().getPreferences();

        assertNotNull("Updated Catalog Prerferences are not null", updatedPreferences)
        assertEquals("Approval URL was updated and returned correctly", "https://localhost/bla", updatedPreferences.getApprovalUrl())
        assertEquals("Approver Email was updated and returned correctly", "test@test.com", updatedPreferences.getApproverEmail())


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

        List<Parameter> params = new ArrayList<Parameter>();
        params.add(param1);
        params.add(param2);
        params.add(param3);
        params.add(param4);

        println "Creating an order"
        println ""
        OrderRestRep createdOrder =
                createOrder(tenantId, sampleService.getId(), params);
        createdOrders.add(createdOrder.getId());

        assertNotNull(createdOrder);
        assertNotNull(createdOrder.getId());
        assertEquals(sampleService.getId(), createdOrder.getCatalogService().getId());
        assertNotNull(createdOrder.getParameters());
        assertEquals(4, createdOrder.getParameters().size());
        assertNotNull(createdOrder.getCreationTime());
        assertNotNull(createdOrder.getMessage());
        assertNotNull(createdOrder.getOrderNumber());
        assertEquals(OrderStatus.APPROVAL.name(), createdOrder.getOrderStatus())

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
        assertEquals(4, anotherOrder.getParameters().size());
        assertNotNull(anotherOrder.getCreationTime());
        assertNotNull(anotherOrder.getMessage());
        assertNotNull(anotherOrder.getOrderNumber());
        assertEquals(OrderStatus.APPROVAL.name(), anotherOrder.getOrderStatus())

        println "Created order " + anotherOrder.getId();
        println ""

        println "Check approvals"
        println ""
        List<URI> approvalIds = new ArrayList<URI>();
        createdOrders.each { URI orderId ->
            ApprovalRestRep approval = catalog.approvals().search().byOrderId(orderId).first()
            assertNotNull(approval);
            assertNotNull(approval.id);
            assertEquals(orderId, approval.order.id);
            assertEquals("PENDING", approval.approvalStatus);
            assertNotNull(approval.creationTime);

            approvalIds.add(approval.id)
        }

        println "Listing bulk resources - approvals"
        println ""

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(approvalIds);

        List<ApprovalRestRep> approvals =
                catalog.approvals().getBulkResources(bulkIds);

        assertNotNull(approvals);
        assertEquals(2, approvals.size());
        assertEquals(Boolean.TRUE, approvalIds.contains(approvals.get(0).getId()));
        assertEquals(Boolean.TRUE, approvalIds.contains(approvals.get(1).getId()));

        println "Listing approvals by tenant"
        println ""

        approvals =
                catalog.approvals().getByTenant(tenantId);

        List<URI> retrievedApprovals = ResourceUtils.ids(approvals);

        assertNotNull(approvals);
        assertEquals(Boolean.TRUE, approvals.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedApprovals.contains(approvalIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedApprovals.contains(approvalIds.get(1)));

        println "Getting approval " + approvalIds.get(0);
        ApprovalRestRep retrievedApproval = catalog.approvals().get(approvalIds.get(0));
        println ""

        assertEquals(approvalIds.get(0), retrievedApproval.getId());

        println "Updating approval " + retrievedApproval.getId();
        println ""

        ApprovalRestRep updatedApproval =
                updateApproval(retrievedApproval.getId());

        assertNotNull("Updated approval is not null", updatedApproval);
        assertNotNull("Updated approval id is not null", updatedApproval.id);
        assertEquals("Check expectd approval status on update", "APPROVED", updatedApproval.approvalStatus);
        assertNotNull("Ensure data actioned is not null on update", updatedApproval.dateActioned);
        assertEquals("ApprovedBy is set to the current user", System.getenv("AD_USER2_USERNAME"), updatedApproval.approvedBy)
        assertEquals("Check updated message on approval", "Updated", updatedApproval.message);
    }

    static void approvalServiceTearDown() {
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

        println " restoring catalog preferences "

        if (existingPreferences) {
            CatalogPreferencesRestRep preferences = catalog.catalogPreferences().getPreferences()
            CatalogPreferencesUpdateParam updateParam = new CatalogPreferencesUpdateParam()
            updateParam.setApprovalUrl(existingApprovalUrl)
            updateParam.setApproverEmail(existingApproverEmail)
            catalog.catalogPreferences().updatePreferences(updateParam);
        }

        println "Cleanup Complete.";
        println ""
    }
}
