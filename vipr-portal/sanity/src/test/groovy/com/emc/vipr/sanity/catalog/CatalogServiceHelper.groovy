/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.catalog.OrderStatus.*
import static org.junit.Assert.*

import com.emc.storageos.model.BulkIdParam
import com.emc.vipr.client.core.util.ResourceUtils
import com.emc.vipr.model.catalog.CatalogCategoryCreateParam
import com.emc.vipr.model.catalog.CatalogCategoryRestRep
import com.emc.vipr.model.catalog.CatalogCategoryUpdateParam
import com.emc.vipr.model.catalog.CatalogServiceCreateParam
import com.emc.vipr.model.catalog.CatalogServiceFieldParam
import com.emc.vipr.model.catalog.CatalogServiceRestRep
import com.emc.vipr.model.catalog.CatalogServiceUpdateParam
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam
import com.emc.vipr.model.catalog.ExecutionWindowRestRep
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep
import com.emc.vipr.model.catalog.ServiceItemContainerRestRep


class CatalogServiceHelper {

    static List<URI> createdCategories;
    static List<URI> createdServices;
    static List<URI> createdExecutionWindows;

    /** place the order given by 'servicePath' using the provided parameters */
    static placeOrder(servicePath, overrideParameters) {
        println sprintf("Get '%s' service", servicePath)
        def service = findServiceByPath(servicePath)
        println sprintf("%s Service:", servicePath)
        println service
        println ""

        println sprintf("Get '%s' service descriptor", servicePath)
        def serviceDescriptor = getServiceDescriptor(service.id)
        println ""

        println "Get parameters for API call"
        def parameters = getParameters(service.id, serviceDescriptor, overrideParameters)
        println "API call parameters: " + parameters
        println ""

        println "Place Order"
        services_run++
        def order = executeService(service, parameters)
        println ""

        return order
    }

    /** find a catalog service using the given path */
    static findServiceByPath(path) {
        return catalog.browse().path(path).service()
    }

    /** get the service descriptor for the given service id */
    static getServiceDescriptor(serviceId) {
        return catalog.services().get(serviceId)?.serviceDescriptor
    }

    /** execute a service with the given parameters */
    static executeService(service, parameters) {
        assertNotNull(service)
        println sprintf("Executing service %s with parameters: %s", service, parameters)

        def orderInfo = catalog.orders().submit(catalog.getUserTenantId(), service.id, parameters)
        assertNotNull(orderInfo)

        def status = OrderStatus.valueOf(orderInfo.orderStatus)
        while ( status.equals(PENDING) || status.equals(EXECUTING) ) {
            println orderInfo
            sleep 3000
            orderInfo = catalog.orders().get(orderInfo.id)
            status = OrderStatus.valueOf(orderInfo.orderStatus)
        }
        println orderInfo

        assertEquals(SUCCESS, status)

        return orderInfo
    }

    /** get the parameters to use in an API call for the given service */
    static getParameters(serviceId, ServiceDescriptorRestRep descriptor, overrideParameters) {
        println "Service Descriptor: $descriptor.title"
        println ""

        // the total number of fields in this service descriptor
        println "This descriptor has ${descriptor.items.size()} fields"
        println ""

        // a map of the asset name to the asset type
        def parameterTypeMap = [:]

        // a map of the asset name to the selected value we'll use to send to the API call
        def parameters = [:]

        // cycle through the fields and attempt to get the asset options for each one
        for ( field in descriptor.items ) {
            def fieldName = field.name

            // only try to get the asset options if we don't already have a value selected for this asset type
            if ( parameters[fieldName] == null ) {

                if (field.isTable() || field.isGroup()) {
                    ServiceItemContainerRestRep table = (ServiceItemContainerRestRep)field;
                    for ( item in table.getItems()) {
                        println sprintf("Field: %s [%s]", item.label, field.type)
                        def option = getAssetOptionValue(serviceId, item, overrideParameters, parameterTypeMap, parameters)
                        // if we found an option for this asset type, add it to the parameters list
                        if (option) {
                            parameters[item.name] = option
                            println sprintf("Added parameter: %s:%s", item.name, option);
                        }

                        // make sure we record the entry in the type map
                        parameterTypeMap[fieldName] = field.type
                        println ""
                    }
                } else {
                    println sprintf("Field: %s [%s]", field.label, field.type)
                    def option = getAssetOptionValue(serviceId, field, overrideParameters, parameterTypeMap, parameters)
                    // if we found an option for this asset type, add it to the parameters list
                    if (option) {
                        parameters[fieldName] = option
                        println sprintf("Added parameter: %s:%s", fieldName, option);
                    }

                    // make sure we record the entry in the type map
                    parameterTypeMap[fieldName] = field.type
                    println ""
                }

            }
        }

        println ""
        println sprintf("Parameters map has %s entries.", parameters.size())
        println sprintf("Parameter Type Map: %s", parameterTypeMap)
        println sprintf("Parameters Map: %s", parameters)
        println "\n\n"

        return parameters
    }

    /** get a value for the given field */
    static getAssetOptionValue(serviceId, field, overrideParameters, parameterTypeMap, parameters) {
        if (overrideParameters[field.name] != null) {
            return overrideParameters[field.name]
        }
        else if (isAssetType(field.type)) {
            // if this is an asset type we can try to get the asset options from the API
            return getAssetOptionsForAssetField(serviceId, field, parameters, parameterTypeMap)
        }
        else if (field.initialValue != null) {
            // if the field has an initial value just select that
            return field.initialValue
        }
        else if (field.required) {
            // if the field is not required just return an empty value
            return ""
        }
    }

    /** get asset options for the given field */
    static getAssetOptionsForAssetField(serviceId, field, parameters, parameterTypeMap) {
        // try to get the asset options from the API for the given field
        def options = getAssetOptions(serviceId, field, parameters, parameterTypeMap)
        println sprintf("Options: %s", options ?: "none")

        if ( options == null || options.size() == 0) {
            if (field.required) {
                throw new RuntimeException("No options for field $field, Field is required.")
            }
            else {
                println "No options, but field is not required"
            }
        }
        else {
            return options[0]?.key
        }
        return ""
    }

    /** Get asset options for the given field in the given service. 
     *  Put the results in the 'parameters' map.
     */
    static getAssetOptions(serviceId, field, parameters, parameterTypeMap) {
        def dependencies = getAssetDependencies(serviceId, field)
        if ( dependencies.size() == 0 ) {
            // if there are no dependencies we can look up the options right away
            return catalog.assetOptions().getAssetOptions(field.type)
        }
        else {
            def assetParameters = buildAssetParameters(dependencies, parameters, parameterTypeMap)
            if (assetParameters?.size() == dependencies?.size()) {
                println sprintf("Asset Options Parameters: %s", assetParameters)
                return catalog.assetOptions().getAssetOptions(field.type, assetParameters)
            }
            println "Unable to find required asset dependencies"
        }
    }

    /** get the asset dependency list for the given field in the given service */
    static getAssetDependencies(serviceId, field) {
        def dependencies = catalog.assetOptions().getAssetDependencies(field.type, serviceId)
        println sprintf("Asset Dependencies : %s", dependencies ?: "none")
        return dependencies
    }

    /** build the list of parameters to use in the asset options API call */
    static buildAssetParameters(dependencies, parameters, parameterTypeMap) {
        def assetParameters = [:]
        for ( dep in dependencies ) {
            def assetType = findParameterAssetNameByType(parameterTypeMap, dep)
            if (assetType) {
                def dependencyValue = parameters[assetType]
                assetParameters[getAssetName(dep)] = dependencyValue
            }
        }
        return assetParameters
    }

    /** lookup the asset type in the parameter type map*/
    static findParameterAssetNameByType(parameterTypeMap, assetType) {
        for ( paramType in parameterTypeMap ) {
            if ( paramType.value.equals("assetType."+assetType) ) {
                return paramType.key
            }
        }
    }

    /** Get the asset name without the assetType prefix */
    static getAssetName(fullAssetName) {
        return fullAssetName.replaceFirst("assetType.","")
    }

    /** Check if the given asset name an asset type or not */
    static isAssetType(assetName) {
        return assetName.startsWith("assetType")
    }

    /** Get info for */
    static getExecutionInfo(order) {
        catalog.orders().getExecutionState(order.id)
    }

    static createCategory(URI tenantId, URI rootCategory) {
        CatalogCategoryCreateParam categoryCreate = new CatalogCategoryCreateParam();
        categoryCreate.setCatalogCategoryId(rootCategory);
        categoryCreate.setDescription("testing create CategoryOne.");
        categoryCreate.setImage("TestCreateOne.png");
        categoryCreate.setName("CategoryOne");
        categoryCreate.setTenantId(tenantId.toString());
        categoryCreate.setTitle("CategoryOneTitle");

        return catalog.categories().create(categoryCreate);
    }

    static createAnotherCategory(URI tenantId, URI rootCategory) {
        CatalogCategoryCreateParam categoryCreate = new CatalogCategoryCreateParam();
        categoryCreate.setCatalogCategoryId(rootCategory);
        categoryCreate.setDescription("testing create CategoryTwo.");
        categoryCreate.setImage("TestCreateTwo.png");
        categoryCreate.setName("CategoryTwo");
        categoryCreate.setTenantId(tenantId.toString());
        categoryCreate.setTitle("CategoryTwoTitle");
        return catalog.categories().create(categoryCreate);
    }

    static updateCategory(URI categoryId, URI newCategory) {
        CatalogCategoryUpdateParam categoryUpdate =
                new CatalogCategoryUpdateParam();
        categoryUpdate.setCatalogCategoryId(newCategory);
        categoryUpdate.setDescription("testing update CategoryOne.");
        categoryUpdate.setImage("TestUpdateOne.png");
        categoryUpdate.setName("CategoryOneModified");
        categoryUpdate.setTitle("CategoryOneTitleModified");
        return catalog.categories().update(categoryId, categoryUpdate);
    }

    static void catalogCategoryServiceTest() {
        println "  ## Catalog Category Test ## "
        createdCategories = new ArrayList<URI>();

        println "Getting tenantId to create category"
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

        println "Creating category"
        CatalogCategoryRestRep createdCategory =
                createCategory(tenantId, rootCategoryId);
        createdCategories.add(createdCategory.getId());
        println ""

        println "createdCategoryId: " + createdCategory.getId();
        println ""

        println "Creating another category"
        CatalogCategoryRestRep anotherCategory =
                createAnotherCategory(tenantId, rootCategoryId);
        createdCategories.add(anotherCategory.getId());
        println ""

        println "createdCategoryId: " + anotherCategory.getId();
        println ""

        assertNotNull(createdCategory);
        assertNotNull(createdCategory.id);
        assertEquals("CategoryOne", createdCategory.getName());
        assertEquals("testing create CategoryOne.", createdCategory.getDescription());
        assertEquals("CategoryOneTitle", createdCategory.getTitle());
        assertEquals("TestCreateOne.png", createdCategory.getImage());
        assertEquals(rootCategoryId, createdCategory.catalogCategory.getId());
        assertEquals(tenantId, createdCategory.getTenant().getId());

        List<URI> categoryIds = new ArrayList<URI>();
        categoryIds.add(createdCategory.getId());
        categoryIds.add(anotherCategory.getId());

        println "Listing bulk resources - categories"
        println ""

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(categoryIds);

        List<CatalogCategoryRestRep> categories =
                catalog.categories().getBulkResources(bulkIds);

        assertNotNull(categories);
        assertEquals(2, categories.size());
        assertEquals(Boolean.TRUE, categoryIds.contains(categories.get(0).getId()));
        assertEquals(Boolean.TRUE, categoryIds.contains(categories.get(1).getId()));

        println "Listing categories by tenant"
        println ""

        categories =
                catalog.categories().getByTenant(tenantId);

        List<URI> retrievedCategories = ResourceUtils.ids(categories);

        assertNotNull(categories);
        assertEquals(Boolean.TRUE, categories.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedCategories.contains(categoryIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedCategories.contains(categoryIds.get(1)));

        println "Getting category " + createdCategory.id;
        CatalogCategoryRestRep retrievedCategory = catalog.categories().get(createdCategory.getId());
        println ""

        assertEquals(createdCategory.getId(), retrievedCategory.getId());

        println "Updating category " + retrievedCategory.getId();
        println ""

        CatalogCategoryRestRep updatedCategory =
                updateCategory(retrievedCategory.getId(), anotherCategory.getId());

        assertNotNull(updatedCategory);
        assertEquals("CategoryOneModified", updatedCategory.getName());
        assertEquals("testing update CategoryOne.", updatedCategory.getDescription());
        assertEquals("CategoryOneTitleModified", updatedCategory.getTitle());
        assertEquals("TestUpdateOne.png", updatedCategory.getImage());
        assertEquals(anotherCategory.getId(), updatedCategory.catalogCategory.getId());

        println "Deleting categories";
        println ""

        catalog.categories().deactivate(updatedCategory.getId());
        catalog.categories().deactivate(anotherCategory.getId());

        println "Getting deactivated category " + updatedCategory.getId();
        updatedCategory = catalog.categories().get(updatedCategory.getId());
        println ""

        if (updatedCategory != null) {
            assertEquals(true, updatedCategory.getInactive());
        }

        println "Retrieving categories from root";
        println ""

        categories =
                catalog.categories().getByTenant(tenantId);

        retrievedCategories = ResourceUtils.ids(categories);

        println "Ensuring custom categories are no longer there";
        println ""

        assertNotNull(categories);
        assertEquals(Boolean.FALSE, retrievedCategories.contains(categoryIds.get(0)));
        assertEquals(Boolean.FALSE, retrievedCategories.contains(categoryIds.get(1)));

        println "Checking for catalog upgrade";
        println ""

        Boolean isUpgradeAvailable =
                catalog.categories().upgradeAvailable(tenantId);

        //no assertion - just to check if the api path is configured for now
    }

    static void catalogCategoryServiceTearDown() {
        println "  ## Catalog Category Test Clean up ## "

        println "Getting created categories"
        println ""
        if (createdCategories != null) {

            createdCategories.each {
                println "Getting test category: " + it;
                println ""
                CatalogCategoryRestRep categoryToDelete =
                        catalog.categories().get(it);
                if (categoryToDelete != null
                && !categoryToDelete.getInactive()) {
                    println "Deleting test category: " + it;
                    println ""
                    catalog.categories().deactivate(it);
                }
            }
        }

        println "Cleanup Complete.";
        println ""
    }

    static createService(List<CatalogServiceFieldParam> params,
            String serviceId, URI categoryId, URI executionWindow) {

        CatalogServiceCreateParam serviceCreate = new CatalogServiceCreateParam();
        serviceCreate.setBaseService(serviceId);
        serviceCreate.setApprovalRequired(false);
        serviceCreate.setCatalogCategory(categoryId);
        serviceCreate.setCatalogServiceFields(params);
        serviceCreate.setDefaultExecutionWindow(executionWindow);
        serviceCreate.setDescription("Test Service");
        serviceCreate.setExecutionWindowRequired(true);
        serviceCreate.setImage("TestService.png");
        serviceCreate.setMaxSize(10);
        serviceCreate.setTitle("ServiceOne");
        serviceCreate.setName("ServiceOne");

        return catalog.services().create(serviceCreate);
    }

    static createAnotherService(List<CatalogServiceFieldParam> params,
            String serviceId, URI categoryId, URI executionWindow) {

        CatalogServiceCreateParam serviceCreate = new CatalogServiceCreateParam();
        serviceCreate.setBaseService(serviceId);
        serviceCreate.setApprovalRequired(true);
        serviceCreate.setCatalogCategory(categoryId);
        serviceCreate.setCatalogServiceFields(params);
        serviceCreate.setDescription("Test Service Two");
        serviceCreate.setExecutionWindowRequired(false);
        serviceCreate.setImage("TestService2.png");
        serviceCreate.setMaxSize(20);
        serviceCreate.setTitle("ServiceTwo");
        serviceCreate.setName("ServiceTwo");

        return catalog.services().create(serviceCreate);
    }

    static createExecutionWindow(URI tenantId) {
        ExecutionWindowCreateParam ewCreate = new ExecutionWindowCreateParam();
        ewCreate.setDayOfMonth(1);
        ewCreate.setDayOfWeek(2);
        ewCreate.setExecutionWindowLength(3);
        ewCreate.setExecutionWindowLengthType("HOURS");
        ewCreate.setExecutionWindowType("DAILY");
        ewCreate.setHourOfDayInUTC(4);
        ewCreate.setLastDayOfMonth(false);
        ewCreate.setMinuteOfHourInUTC(5);
        ewCreate.setTenant(tenantId);
        ewCreate.setName("test");
        return catalog.executionWindows().create(ewCreate);
    }

    static createAnotherExecutionWindow(URI tenantId) {
        ExecutionWindowCreateParam ewCreate = new ExecutionWindowCreateParam();
        ewCreate.setDayOfMonth(1);
        ewCreate.setDayOfWeek(2);
        ewCreate.setExecutionWindowLength(3);
        ewCreate.setExecutionWindowLengthType("HOURS");
        ewCreate.setExecutionWindowType("DAILY");
        ewCreate.setHourOfDayInUTC(4);
        ewCreate.setLastDayOfMonth(false);
        ewCreate.setMinuteOfHourInUTC(5);
        ewCreate.setTenant(tenantId);
        ewCreate.setName("test1");
        return catalog.executionWindows().create(ewCreate);
    }

    static updateService(URI serviceToUpdateId, List<CatalogServiceFieldParam> params,
            String serviceId, URI categoryId, URI executionWindow) {

        CatalogServiceUpdateParam serviceUpdate = new CatalogServiceUpdateParam();
        serviceUpdate.setBaseService(serviceId);
        serviceUpdate.setApprovalRequired(true);
        serviceUpdate.setCatalogCategory(categoryId);
        serviceUpdate.setCatalogServiceFields(params);
        serviceUpdate.setDefaultExecutionWindow(executionWindow);
        serviceUpdate.setDescription("Test Service Updated");
        serviceUpdate.setExecutionWindowRequired(false);
        serviceUpdate.setImage("TestServiceUpdate.png");
        serviceUpdate.setMaxSize(30);
        serviceUpdate.setTitle("ServiceUpdate");
        serviceUpdate.setName("ServiceUpdate");

        return catalog.services().update(serviceToUpdateId, serviceUpdate);
    }

    static void catalogServiceServiceTest() {

        println "  ## Catalog Service Test ## "
        createdServices = new ArrayList<URI>();
        createdExecutionWindows = new ArrayList<URI>();

        println "Getting tenantId"
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

        println "Getting serviceId of first service descriptor"
        println ""

        ServiceDescriptorRestRep sd = sds.get(0);

        assertNotNull(sds.get(0));
        assertNotNull(sds.get(0).getServiceId());

        String serviceId = sd.getServiceId();

        println "Using " + serviceId + " as base service to create new service."
        println ""

        println "Creating an execution window"
        ExecutionWindowRestRep createdWindow =
                createExecutionWindow(tenantId);
        createdExecutionWindows.add(createdWindow.getId());
        println ""

        println "createdWindowId: " + createdWindow.getId();
        println ""

        println "Creating an execution window"
        ExecutionWindowRestRep anotherWindow =
                createAnotherExecutionWindow(tenantId);
        createdExecutionWindows.add(anotherWindow.getId());
        println ""

        println "createdWindowId: " + anotherWindow.getId();
        println ""

        assertNotNull(createdWindow);
        assertNotNull(createdWindow.id);
        assertEquals(1, createdWindow.getDayOfMonth());
        assertEquals(2, createdWindow.getDayOfWeek());
        assertEquals(3, createdWindow.getExecutionWindowLength());
        assertEquals("HOURS", createdWindow.getExecutionWindowLengthType());
        assertEquals("DAILY", createdWindow.getExecutionWindowType());
        assertEquals(4, createdWindow.getHourOfDayInUTC());
        assertEquals(Boolean.FALSE, createdWindow.getLastDayOfMonth());
        assertEquals(5, createdWindow.getMinuteOfHourInUTC());
        assertEquals(tenantId, createdWindow.getTenant().getId());

        CatalogServiceFieldParam param1 = new CatalogServiceFieldParam();
        param1.setName("param1");
        param1.setValue("value1");
        param1.setOverride(false);

        CatalogServiceFieldParam param2 = new CatalogServiceFieldParam();
        param2.setName("param2");
        param2.setValue("value2");
        param2.setOverride(false);

        List<CatalogServiceFieldParam> params =
                new ArrayList<CatalogServiceFieldParam>();
        params.add(param1);
        params.add(param2);

        println "Creating service"
        CatalogServiceRestRep createdService =
                createService(params, serviceId, rootCategoryId, createdWindow.getId());
        createdServices.add(createdService.getId());
        println ""

        println "createdServiceId: " + createdService.getId();
        println ""

        assertNotNull(createdService);
        assertNotNull(createdService.getId());
        assertEquals(serviceId, createdService.getBaseService());
        assertEquals(Boolean.FALSE, createdService.isApprovalRequired());
        assertEquals(rootCategoryId, createdService.getCatalogCategory().getId());
        assertEquals(createdWindow.getId(), createdService.getDefaultExecutionWindow().getId());
        assertEquals("Test Service", createdService.getDescription());
        assertEquals(Boolean.TRUE, createdService.isExecutionWindowRequired());
        assertEquals("TestService.png", createdService.getImage());
        assertEquals(10, createdService.getMaxSize());
        assertEquals("ServiceOne", createdService.getTitle());
        assertNotNull(createdService.getCatalogServiceFields());
        assertEquals(2, createdService.getCatalogServiceFields().size());
        assertTrue(createdService.getCatalogServiceFields().get(0).getName().equals("param1")
                || createdService.getCatalogServiceFields().get(0).getName().equals("param2"));
        assertTrue(createdService.getCatalogServiceFields().get(1).getName().equals("param1")
                || createdService.getCatalogServiceFields().get(1).getName().equals("param2"));
        assertTrue(createdService.getCatalogServiceFields().get(0).getValue().equals("value1")
                || createdService.getCatalogServiceFields().get(0).getValue().equals("value2"));
        assertTrue(createdService.getCatalogServiceFields().get(1).getValue().equals("value1")
                || createdService.getCatalogServiceFields().get(1).getValue().equals("value2"));

        CatalogServiceFieldParam param3 = new CatalogServiceFieldParam();
        param3.setName("param3");
        param3.setValue("value3");
        param3.setOverride(false);

        CatalogServiceFieldParam param4 = new CatalogServiceFieldParam();
        param4.setName("param4");
        param4.setValue("value4");
        param4.setOverride(false);

        List<CatalogServiceFieldParam> moreParams =
                new ArrayList<CatalogServiceFieldParam>();
        moreParams.add(param3);
        moreParams.add(param4);

        println "Listing categories by tenant"
        println ""

        List<CatalogCategoryRestRep> categories =
                catalog.categories().getByTenant(tenantId);

        assertNotNull(categories);
        assertEquals(Boolean.TRUE, categories.size() > 0);
        assertNotNull(categories.get(0));
        assertNotNull(categories.get(0).getId());

        println "Using category " + categories.get(0).getId();
        println ""

        println "Getting serviceId of second service descriptor"
        println ""

        ServiceDescriptorRestRep sd1 = sds.get(1);

        assertNotNull(sds.get(1));
        assertNotNull(sds.get(1).getServiceId());

        String anotherServiceId = sd1.getServiceId();

        println "Using " + anotherServiceId + " as base service to create another service."
        println ""

        println "Creating another service"
        CatalogServiceRestRep anotherService =
                createService(moreParams, anotherServiceId, categories.get(0).getId(), null);
        createdServices.add(anotherService.getId());
        println ""

        println "created service id: " + anotherService.getId();
        println ""

        println "Listing bulk resources - services"
        println ""

        List<URI> serviceIds = new ArrayList<URI>();
        serviceIds.add(createdService.getId());
        serviceIds.add(anotherService.getId());

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(serviceIds);

        List<CatalogServiceRestRep> services =
                catalog.services().getBulkResources(bulkIds);

        assertNotNull(services);
        assertEquals(2, services.size());
        assertEquals(Boolean.TRUE, serviceIds.contains(services.get(0).getId()));

        println "Listing services for category: " + categories.get(0).getId();
        println ""

        services =
                catalog.services().findByCatalogCategory(categories.get(0).getId());

        List<URI> retrievedServices = ResourceUtils.ids(services);

        assertNotNull(services);
        assertEquals(Boolean.TRUE, services.size() >= 1);
        assertEquals(Boolean.TRUE, retrievedServices.contains(serviceIds.get(1)));


        println "Getting service " + createdService.getId();
        CatalogServiceRestRep retrievedService = catalog.services().get(createdService.getId());
        println ""

        assertEquals(createdService.getId(), retrievedService.getId());

        println "Updating category " + retrievedService.getId();
        println ""

        CatalogServiceRestRep updatedService =
                updateService(retrievedService.getId(), moreParams, anotherServiceId, categories.get(0).getId(), anotherWindow.getId());

        assertNotNull(updatedService);
        assertEquals(anotherServiceId, updatedService.getBaseService());
        assertEquals(Boolean.TRUE, updatedService.isApprovalRequired());
        assertEquals(categories.get(0).getId(), updatedService.getCatalogCategory().getId());
        assertEquals(anotherWindow.getId(), updatedService.getDefaultExecutionWindow().getId());
        assertEquals("Test Service Updated", updatedService.getDescription());
        assertEquals(Boolean.FALSE, updatedService.isExecutionWindowRequired());
        assertEquals("TestServiceUpdate.png", updatedService.getImage());
        assertEquals(30, updatedService.getMaxSize());
        assertEquals("ServiceUpdate", updatedService.getTitle());
        assertNotNull(updatedService.getCatalogServiceFields());
        assertEquals(2, updatedService.getCatalogServiceFields().size());
        assertTrue(updatedService.getCatalogServiceFields().get(0).getName().equals("param3")
                || updatedService.getCatalogServiceFields().get(0).getName().equals("param4"));
        assertTrue(updatedService.getCatalogServiceFields().get(1).getName().equals("param3")
                || updatedService.getCatalogServiceFields().get(1).getName().equals("param4"));
        assertTrue(updatedService.getCatalogServiceFields().get(0).getValue().equals("value3")
                || updatedService.getCatalogServiceFields().get(0).getValue().equals("value4"));
        assertTrue(updatedService.getCatalogServiceFields().get(1).getValue().equals("value3")
                || updatedService.getCatalogServiceFields().get(1).getValue().equals("value4"));

        println "Deleting services";
        println ""

        catalog.services().deactivate(updatedService.getId());
        catalog.services().deactivate(anotherService.getId());

        println "Getting deactivated service " + updatedService.getId();
        updatedService = catalog.services().get(updatedService.getId());
        println ""

        if (updatedService != null) {
            assertEquals(true, updatedService.getInactive());
        }

    }

    static void catalogServiceServiceTearDown() {
        println "  ## Catalog Service Test Clean up ## "

        println "Getting created services"
        println ""
        if (createdServices != null) {

            createdServices.each {
                println "Getting test service: " + it;
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

        println "Getting created execution windows"
        println ""
        if (createdExecutionWindows != null) {

            createdExecutionWindows.each {
                println "Getting test executionWindows: " + it;
                println ""
                ExecutionWindowRestRep windowToDelete =
                        catalog.executionWindows().get(it);
                if (windowToDelete != null
                && !windowToDelete.getInactive()) {
                    println "Deleting test window: " + it;
                    println ""
                    catalog.executionWindows().deactivate(it);
                }
            }
        }

        println "Cleanup Complete.";
        println ""
    }

}
