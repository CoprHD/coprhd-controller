/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.Sanity.printMsg
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
        printMsg sprintf("Get '%s' service", servicePath)
        def service = findServiceByPath(servicePath)
        printMsg sprintf("%s Service:", servicePath)
        printMsg service
        printMsg ""

        printMsg sprintf("Get '%s' service descriptor", servicePath)
        def serviceDescriptor = getServiceDescriptor(service.id)
        printMsg ""

        printMsg "Get parameters for API call"
        def parameters = getParameters(service.id, serviceDescriptor, overrideParameters)
        printMsg "API call parameters: " + parameters
        printMsg ""

        printMsg "Place Order"
        services_run++
        def order = executeService(service, parameters)
        printMsg ""

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
        printMsg sprintf("Executing service %s with parameters: %s", service, parameters)

        def orderInfo = catalog.orders().submit(catalog.getUserTenantId(), service.id, parameters)
        assertNotNull(orderInfo)

        def status = OrderStatus.valueOf(orderInfo.orderStatus)
        while ( status.equals(PENDING) || status.equals(EXECUTING) ) {
            printMsg orderInfo
            sleep 3000
            orderInfo = catalog.orders().get(orderInfo.id)
            status = OrderStatus.valueOf(orderInfo.orderStatus)
        }
        printMsg orderInfo

        assertEquals(SUCCESS, status)

        return orderInfo
    }

    /** get the parameters to use in an API call for the given service */
    static getParameters(serviceId, ServiceDescriptorRestRep descriptor, overrideParameters) {
        printMsg "Service Descriptor: $descriptor.title"
        printMsg ""

        // the total number of fields in this service descriptor
        printMsg "This descriptor has ${descriptor.items.size()} fields"
        printMsg ""

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
                        printMsg sprintf("Field: %s [%s]", item.label, field.type)
                        def option = getAssetOptionValue(serviceId, item, overrideParameters, parameterTypeMap, parameters)
                        // if we found an option for this asset type, add it to the parameters list
                        if (option) {
                            parameters[item.name] = option
                            printMsg sprintf("Added parameter: %s:%s", item.name, option);
                        }

                        // make sure we record the entry in the type map
                        parameterTypeMap[fieldName] = field.type
                        printMsg ""
                    }
                } else {
                    printMsg sprintf("Field: %s [%s]", field.label, field.type)
                    def option = getAssetOptionValue(serviceId, field, overrideParameters, parameterTypeMap, parameters)
                    // if we found an option for this asset type, add it to the parameters list
                    if (option) {
                        parameters[fieldName] = option
                        printMsg sprintf("Added parameter: %s:%s", fieldName, option)
                    }

                    // make sure we record the entry in the type map
                    parameterTypeMap[fieldName] = field.type
                    printMsg ""
                }

            }
        }

        printMsg ""
        printMsg sprintf("Parameters map has %s entries.", parameters.size())
        printMsg sprintf("Parameter Type Map: %s", parameterTypeMap)
        printMsg sprintf("Parameters Map: %s", parameters)
        printMsg "\n\n"

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
        printMsg sprintf("Options: %s", options ?: "none")

        if ( options == null || options.size() == 0) {
            if (field.required) {
                throw new RuntimeException("No options for field $field, Field is required.")
            }
            else {
                printMsg "No options, but field is not required"
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
                printMsg sprintf("Asset Options Parameters: %s", assetParameters)
                return catalog.assetOptions().getAssetOptions(field.type, assetParameters)
            }
            printMsg "Unable to find required asset dependencies"
        }
    }

    /** get the asset dependency list for the given field in the given service */
    static getAssetDependencies(serviceId, field) {
        def dependencies = catalog.assetOptions().getAssetDependencies(field.type, serviceId)
        printMsg sprintf("Asset Dependencies : %s", dependencies ?: "none")
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


    static void catalogRemoteReplicationServiceTest() {
        printMsg "CatalogServiceHelper.catalogCategoryServiceTest() :: TODO: Implement Remote Replication Tests"
    }


    static void catalogCategoryServiceTest() {
        printMsg "  ## Catalog Category Test ## "
        createdCategories = new ArrayList<URI>();

        printMsg "Getting tenantId to create category"
        URI tenantId = catalog.getUserTenantId();
        printMsg ""

        printMsg "tenantId: " + tenantId
        printMsg ""

        printMsg "Getting root catalog category"
        CatalogCategoryRestRep rootCategory =
                catalog.categories().getRootCatalogCategory(tenantId.toString());
        printMsg ""

        URI rootCategoryId = rootCategory.getId();
        printMsg "rootCategoryId: " + rootCategoryId;
        printMsg ""

        printMsg "Creating category"
        CatalogCategoryRestRep createdCategory =
                createCategory(tenantId, rootCategoryId);
        createdCategories.add(createdCategory.getId());
        printMsg ""

        printMsg "createdCategoryId: " + createdCategory.getId();
        printMsg ""

        printMsg "Creating another category"
        CatalogCategoryRestRep anotherCategory =
                createAnotherCategory(tenantId, rootCategoryId);
        createdCategories.add(anotherCategory.getId());
        printMsg ""

        printMsg "createdCategoryId: " + anotherCategory.getId();
        printMsg ""

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

        printMsg "Listing bulk resources - categories"
        printMsg ""

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(categoryIds);

        List<CatalogCategoryRestRep> categories =
                catalog.categories().getBulkResources(bulkIds);

        assertNotNull(categories);
        assertEquals(2, categories.size());
        assertEquals(Boolean.TRUE, categoryIds.contains(categories.get(0).getId()));
        assertEquals(Boolean.TRUE, categoryIds.contains(categories.get(1).getId()));

        printMsg "Listing categories by tenant"
        printMsg ""

        categories =
                catalog.categories().getByTenant(tenantId);

        List<URI> retrievedCategories = ResourceUtils.ids(categories);

        assertNotNull(categories);
        assertEquals(Boolean.TRUE, categories.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedCategories.contains(categoryIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedCategories.contains(categoryIds.get(1)));

        printMsg "Getting category " + createdCategory.id;
        CatalogCategoryRestRep retrievedCategory = catalog.categories().get(createdCategory.getId());
        printMsg ""

        assertEquals(createdCategory.getId(), retrievedCategory.getId());

        printMsg "Updating category " + retrievedCategory.getId();
        printMsg ""

        CatalogCategoryRestRep updatedCategory =
                updateCategory(retrievedCategory.getId(), anotherCategory.getId());

        assertNotNull(updatedCategory);
        assertEquals("CategoryOneModified", updatedCategory.getName());
        assertEquals("testing update CategoryOne.", updatedCategory.getDescription());
        assertEquals("CategoryOneTitleModified", updatedCategory.getTitle());
        assertEquals("TestUpdateOne.png", updatedCategory.getImage());
        assertEquals(anotherCategory.getId(), updatedCategory.catalogCategory.getId());

        printMsg "Deleting categories";
        printMsg ""

        catalog.categories().deactivate(updatedCategory.getId());
        catalog.categories().deactivate(anotherCategory.getId());

        printMsg "Getting deactivated category " + updatedCategory.getId();
        updatedCategory = catalog.categories().get(updatedCategory.getId());
        printMsg ""

        if (updatedCategory != null) {
            assertEquals(true, updatedCategory.getInactive());
        }

        printMsg "Retrieving categories from root";
        printMsg ""

        categories =
                catalog.categories().getByTenant(tenantId);

        retrievedCategories = ResourceUtils.ids(categories);

        printMsg "Ensuring custom categories are no longer there";
        printMsg ""

        assertNotNull(categories);
        assertEquals(Boolean.FALSE, retrievedCategories.contains(categoryIds.get(0)));
        assertEquals(Boolean.FALSE, retrievedCategories.contains(categoryIds.get(1)));

        printMsg "Checking for catalog upgrade";
        printMsg ""

        Boolean isUpgradeAvailable =
                catalog.categories().upgradeAvailable(tenantId);

        //no assertion - just to check if the api path is configured for now
    }

    static void catalogCategoryServiceTearDown() {
        printMsg "  ## Catalog Category Test Clean up ## "

        printMsg "Getting created categories"
        printMsg ""
        if (createdCategories != null) {

            createdCategories.each {
                printMsg "Getting test category: " + it;
                printMsg ""
                CatalogCategoryRestRep categoryToDelete =
                        catalog.categories().get(it);
                if (categoryToDelete != null
                && !categoryToDelete.getInactive()) {
                    printMsg "Deleting test category: " + it;
                    printMsg ""
                    catalog.categories().deactivate(it);
                }
            }
        }

        printMsg "Cleanup Complete.";
        printMsg ""
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

        printMsg "  ## Catalog Service Test ## "
        createdServices = new ArrayList<URI>();
        createdExecutionWindows = new ArrayList<URI>();

        printMsg "Getting tenantId"
        URI tenantId = catalog.getUserTenantId();
        printMsg ""

        printMsg "tenantId: " + tenantId
        printMsg ""

        printMsg "Getting root catalog category"
        CatalogCategoryRestRep rootCategory =
                catalog.categories().getRootCatalogCategory(tenantId.toString());
        printMsg ""

        URI rootCategoryId = rootCategory.getId();
        printMsg "rootCategoryId: " + rootCategoryId;
        printMsg ""

        printMsg "Getting all service descriptors"
        List<ServiceDescriptorRestRep> sds =
                catalog.serviceDescriptors().getServiceDescriptors();
        printMsg ""

        assertNotNull(sds);
        assertEquals(Boolean.TRUE, sds.size() > 0);

        printMsg "Getting serviceId of first service descriptor"
        printMsg ""

        ServiceDescriptorRestRep sd = sds.get(0);

        assertNotNull(sds.get(0));
        assertNotNull(sds.get(0).getServiceId());

        String serviceId = sd.getServiceId();

        printMsg "Using " + serviceId + " as base service to create new service."
        printMsg ""

        printMsg "Creating an execution window"
        ExecutionWindowRestRep createdWindow =
                createExecutionWindow(tenantId);
        createdExecutionWindows.add(createdWindow.getId());
        printMsg ""

        printMsg "createdWindowId: " + createdWindow.getId();
        printMsg ""

        printMsg "Creating an execution window"
        ExecutionWindowRestRep anotherWindow =
                createAnotherExecutionWindow(tenantId);
        createdExecutionWindows.add(anotherWindow.getId());
        printMsg ""

        printMsg "createdWindowId: " + anotherWindow.getId();
        printMsg ""

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

        printMsg "Creating service"
        CatalogServiceRestRep createdService =
                createService(params, serviceId, rootCategoryId, createdWindow.getId());
        createdServices.add(createdService.getId());
        printMsg ""

        printMsg "createdServiceId: " + createdService.getId();
        printMsg ""

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

        printMsg "Listing categories by tenant"
        printMsg ""

        List<CatalogCategoryRestRep> categories =
                catalog.categories().getByTenant(tenantId);

        assertNotNull(categories);
        assertEquals(Boolean.TRUE, categories.size() > 0);
        assertNotNull(categories.get(0));
        assertNotNull(categories.get(0).getId());

        printMsg "Using category " + categories.get(0).getId();
        printMsg ""

        printMsg "Getting serviceId of second service descriptor"
        printMsg ""

        ServiceDescriptorRestRep sd1 = sds.get(1);

        assertNotNull(sds.get(1));
        assertNotNull(sds.get(1).getServiceId());

        String anotherServiceId = sd1.getServiceId();

        printMsg "Using " + anotherServiceId + " as base service to create another service."
        printMsg ""

        printMsg "Creating another service"
        CatalogServiceRestRep anotherService =
                createService(moreParams, anotherServiceId, categories.get(0).getId(), null);
        createdServices.add(anotherService.getId());
        printMsg ""

        printMsg "created service id: " + anotherService.getId();
        printMsg ""

        printMsg "Listing bulk resources - services"
        printMsg ""

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

        printMsg "Listing services for category: " + categories.get(0).getId();
        printMsg ""

        services =
                catalog.services().findByCatalogCategory(categories.get(0).getId());

        List<URI> retrievedServices = ResourceUtils.ids(services);

        assertNotNull(services);
        assertEquals(Boolean.TRUE, services.size() >= 1);
        assertEquals(Boolean.TRUE, retrievedServices.contains(serviceIds.get(1)));


        printMsg "Getting service " + createdService.getId();
        CatalogServiceRestRep retrievedService = catalog.services().get(createdService.getId());
        printMsg ""

        assertEquals(createdService.getId(), retrievedService.getId());

        printMsg "Updating category " + retrievedService.getId();
        printMsg ""

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

        printMsg "Deleting services";
        printMsg ""

        catalog.services().deactivate(updatedService.getId());
        catalog.services().deactivate(anotherService.getId());

        printMsg "Getting deactivated service " + updatedService.getId();
        updatedService = catalog.services().get(updatedService.getId());
        printMsg ""

        if (updatedService != null) {
            assertEquals(true, updatedService.getInactive());
        }

    }

    static void catalogServiceServiceTearDown() {
        printMsg "  ## Catalog Service Test Clean up ## "

        printMsg "Getting created services"
        printMsg ""
        if (createdServices != null) {

            createdServices.each {
                printMsg "Getting test service: " + it;
                printMsg ""
                CatalogServiceRestRep serviceToDelete =
                        catalog.services().get(it);
                if (serviceToDelete != null
                && !serviceToDelete.getInactive()) {
                    printMsg "Deleting test service: " + it;
                    printMsg ""
                    catalog.services().deactivate(it);
                }
            }
        }

        printMsg "Getting created execution windows"
        printMsg ""
        if (createdExecutionWindows != null) {

            createdExecutionWindows.each {
                printMsg "Getting test executionWindows: " + it;
                printMsg ""
                ExecutionWindowRestRep windowToDelete =
                        catalog.executionWindows().get(it);
                if (windowToDelete != null
                && !windowToDelete.getInactive()) {
                    printMsg "Deleting test window: " + it;
                    printMsg ""
                    catalog.executionWindows().deactivate(it);
                }
            }
        }

        printMsg "Cleanup Complete.";
        printMsg ""
    }

}
