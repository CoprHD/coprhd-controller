/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package controllers.api;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static render.RenderApiModel.renderApi;
import static util.BourneUtil.getCatalogClient;
import static util.Json.renderPrettyJson;
import static util.api.ApiMapperUtils.newCategoryInfo;
import static util.api.ApiMapperUtils.newOrderInfo;
import static util.api.ApiMapperUtils.newServiceInfo;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import play.data.validation.Validation;
import play.i18n.Messages;
import util.CatalogCategoryUtils;
import util.CatalogServiceUtils;
import util.ServiceDescriptorUtils;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogServiceFieldRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ValidationError;
import com.google.common.collect.Lists;

import controllers.catalog.OrderExecution;

/**
 * Catalog API. API for access services and performing orders.
 *
 * Note: setSelectedVdc() is used before most of the service. This is a utility to set the VDC based on the VDC name
 * passed in. The API does not have the same interaction as the portal where the user can choose the active VDC in the
 * corner. Instead, the VDC is passed in on each request. Set this into the session so the code is consistent
 * regardless of where the VDC comes from.
 *
 * @author Chris Dail
 */
public class CatalogApi extends OrderExecution {

    public static void serviceDescriptor(String serviceId) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
        ServiceDescriptorRestRep descriptor = service.getServiceDescriptor();

        // Remove any locked fields so user does not see things they cannot change
        for (CatalogServiceFieldRestRep catalogServiceField : service.getCatalogServiceFields()) {
            if (catalogServiceField.getOverride()) {
                ServiceFieldRestRep serviceField = ServiceDescriptorUtils.getField(descriptor, catalogServiceField.getName());
                descriptor.getItems().remove(serviceField);
            }
        }

        renderPrettyJson(descriptor);
    }

    public static void invoke(String serviceId) {
        runCatalogService(serviceId);
    }
    
    public static void invokeByPath(String sp1, String sp2, String sp3, String sp4, String sp5) {
        CatalogCategoryRestRep catalog = CatalogCategoryUtils.getRootCategory();

        DataObjectRestRep results = findCategoryOrService(catalog, sp1, sp2, sp3, sp4, sp5);
        if (results != null && results instanceof CatalogServiceRestRep) {
            CatalogServiceRestRep catalogService = (CatalogServiceRestRep) results;
            runCatalogService(catalogService.getId().toString());
        }
        else {
            notFound();
        }
    }

    public static void browseCatalog(String sp1, String sp2, String sp3, String sp4, String sp5) {
        CatalogCategoryRestRep catalogCategory = CatalogCategoryUtils.getRootCategory();

        DataObjectRestRep result = findCategoryOrService(catalogCategory, sp1, sp2, sp3, sp4, sp5);
        if (result instanceof CatalogCategoryRestRep) {
            renderApi(newCategoryInfo((CatalogCategoryRestRep) result));
        }
        else if (result instanceof CatalogServiceRestRep) {
            renderApi(newServiceInfo((CatalogServiceRestRep) result));
        }
    }

    public static void category(String categoryId) {
        CatalogCategoryRestRep category = CatalogCategoryUtils.getCatalogCategory(uri(categoryId));
        renderApi(newCategoryInfo(category));
    }

    public static void service(String serviceId) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
        renderApi(newServiceInfo(service));
    }

    /**
     * Recursively traverse a list of sub-paths to find the category or service.
     */
    private static DataObjectRestRep findCategoryOrService(CatalogCategoryRestRep root, String... subPaths) {
        if (subPaths == null) {
            return root;
        }

        DataObjectRestRep result = root;
        for (String path: subPaths) {
            if (StringUtils.isBlank(path)) {
                continue;
            }

            if (result instanceof CatalogCategoryRestRep) {
                result = getCategoryOrService((CatalogCategoryRestRep) result, path);
                if (result == null) {
                    notFound(Messages.get("CatalogApi.subpathNotFound", path));
                }
            }

        }
        return result;
    }

    private static DataObjectRestRep getCategoryOrService(CatalogCategoryRestRep category, String subPath) {
        if (category != null) {
            List<CatalogServiceRestRep> catalogServices = CatalogServiceUtils.getCatalogServices(category);
            for (CatalogServiceRestRep catalogService : catalogServices) {
                if (StringUtils.equalsIgnoreCase(subPath, catalogService.getName())) {
                    return catalogService;
                }
            }
            List<CatalogCategoryRestRep> subCatalogCategories = CatalogCategoryUtils.getCatalogCategories(category);
            for (CatalogCategoryRestRep subCatalogCategory : subCatalogCategories) {
                if (StringUtils.equalsIgnoreCase(subPath, subCatalogCategory.getName())) {
                    return subCatalogCategory;
                }
            }
        }
        return null;
    }

    private static void runCatalogService(String serviceId) {
        params.checkAndParse();

        OrderCreateParam order = createAndValidateOrder(serviceId);
        OrderRestRep submittedOrder = getCatalogClient().orders().submit(order);
        renderApi(newOrderInfo(submittedOrder));
    }

    private static OrderCreateParam createAndValidateOrder(String serviceId) {
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
        ServiceDescriptorRestRep descriptor = service.getServiceDescriptor();
        
        // Filter out actual Service Parameters
        Map<String, String> parameters = parseParameters(service, descriptor);
        if (Validation.hasErrors()) {
            response.status = HttpStatus.SC_BAD_REQUEST;
            renderApi(getValidationErrors());
        }
        // Create request and perform selection
        OrderCreateParam order = createOrder(service, descriptor, parameters);
        return order;
    }


    private static List<ValidationError> getValidationErrors() {
        List<ValidationError> errors = Lists.newArrayList();
        for (play.data.validation.Error error: Validation.errors()) {
            errors.add(new ValidationError(error.getKey(), error.message()));
        }
        return errors;
    }
}
