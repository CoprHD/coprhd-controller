/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import models.BreadCrumb;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.With;
import util.AssetOptionUtils;
import util.CatalogServiceUtils;
import util.ExecutionWindowUtils;
import util.MessagesUtils;
import util.ServiceDescriptorUtils;
import util.ServiceFormUtils;
import util.ServiceFormUtils.AssetFieldDescriptor;

import com.emc.vipr.model.catalog.AssetOption;
import com.emc.vipr.model.catalog.CatalogServiceFieldRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import controllers.Common;
import controllers.catalog.ServiceCatalog.CategoryDef;
import controllers.tenant.TenantSelector;
import controllers.util.Models;
import static controllers.Common.angularRenderArgs;
import static controllers.Common.copyRenderArgsToAngular;

@With(Common.class)
public class Services extends Controller {
    private static void addBreadCrumbToRenderArgs(CatalogServiceRestRep service) {
        addBreadCrumbToRenderArgs(service, null);
    }

    private static void addBreadCrumbToRenderArgs(CatalogServiceRestRep service, String backUrlInput) {
        List<BreadCrumb> breadcrumbs = ServiceCatalog.createBreadCrumbs(Models.currentAdminTenant(), service);
        renderArgs.put("breadcrumbs", breadcrumbs);

        String backUrl;
        if (StringUtils.isEmpty(backUrlInput)) {
            backUrl = request.params.get("return");
            if (StringUtils.isBlank(backUrl)) {
                String path = "";
                URI categoryId = service.getCatalogCategory().getId();
                if (categoryId != null) {
                    Map<String, CategoryDef> catalog = ServiceCatalog.getCatalog(Models.currentAdminTenant());
                    CategoryDef category = catalog.get(categoryId.toString());
                    path = (category != null) ? category.path : path;
                }
                backUrl = Common.reverseRoute(ServiceCatalog.class, "view") + "#" + path;
            }
        }
        else {
            backUrl = backUrlInput;
        }

        renderArgs.put("backUrl", backUrl);
    }

    /**
     * Builds a form for a particular service
     */
    public static void showForm(String serviceId) {
        TenantSelector.addRenderArgs();
        boolean isTestWorkflow = false;
        CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
        if(null == service.getCatalogCategory()){
            isTestWorkflow = true;
        }

        List<CatalogServiceFieldRestRep> serviceFields = service.getCatalogServiceFields();
        // If serviceDescriptor is null render another template that spells out the problem for the user.
        ServiceDescriptorRestRep serviceDescriptor = service.getServiceDescriptor();
        if (serviceDescriptor == null) {
            corruptedService(service);
        }

        Map<String, Object> fieldOptions = new HashMap<String, Object>();

        // add the breadcrumb
        if(!isTestWorkflow) {
            addBreadCrumbToRenderArgs(service);
        }
        else{
            addBreadCrumbToRenderArgs(service, Common.reverseRoute(WorkflowBuilder.class, "view"));
        }


        // Mark the service as recently used
        // RecentUtils.usedService(service);

        Map<String, AssetFieldDescriptor> assetFieldDescriptors = ServiceFormUtils
                .createAssetFieldDescriptors(serviceDescriptor);

        // Calculate default values for all fields
        Map<String, String> defaultValues = getDefaultValues(serviceDescriptor);

        // Calculate asset parameters for any fields that are overridden
        Map<String, String> overriddenValues = getOverriddenValues(service);
        Map<String, String> availableAssets = getAvailableAssets(assetFieldDescriptors, overriddenValues);

        // Load any Asset Options for root fields so they are rendered directly onto the form
        List<ServiceFieldRestRep> allFields = ServiceDescriptorUtils.getAllFieldList(serviceDescriptor.getItems());
        for (ServiceFieldRestRep field : allFields) {
            if (field.isAsset()) {
                // Compute to see if we have all dependencies. We have all dependencies if fieldsWeDependOn is empty
                // or it only contains fields we have in our overridden values.
                AssetFieldDescriptor fieldDescriptor = assetFieldDescriptors.get(field.getName());
                boolean hasAllDependencies = overriddenValues.keySet().containsAll(fieldDescriptor.fieldsWeDependOn);
                boolean isOverridden = overriddenValues.containsKey(field.getName());

                if (hasAllDependencies && !isOverridden) {
                    List<AssetOption> options = getAssetOptions(field, availableAssets);
                    fieldOptions.put(field.getType() + "-options", options);

                    // If a required field is missing any options, display a warning message
                    if (options.isEmpty() && field.isRequired() && !field.getType().equalsIgnoreCase("field")) {
                        flash.put("rawWarning", MessagesUtils.get("service.missingAssets", field.getLabel()));
                    }
                }
            }
        }

        Gson gson = new Gson();
        String defaultValuesJSON = gson.toJson(defaultValues);
        String assetFieldDescriptorsJSON = gson.toJson(assetFieldDescriptors);
        String overriddenValuesJSON = gson.toJson(overriddenValues);

        boolean showForm = true;
        // Display an error message and don't display the form if an execution window is required but none are defined
        if (Boolean.TRUE.equals(service.isExecutionWindowRequired()) && !hasExecutionWindows()) {
            flash.error(MessagesUtils.get("service.noExecutionWindows"));
            showForm = false;
        }

        renderArgs.data.putAll(new ImmutableMap.Builder<String, Object>()
                .put("service", service)
                .put("serviceFields", serviceFields)
                .put("serviceDescriptor", serviceDescriptor)
                .put("assetFieldDescriptorsJSON", assetFieldDescriptorsJSON)
                .put("defaultValuesJSON", defaultValuesJSON)
                .put("overriddenValuesJSON", overriddenValuesJSON)
                .put("showForm", new Boolean(showForm))
                .build());

        // Adding to request, as renderArgs can't be dynamically named
        request.current().args.putAll(fieldOptions);
        copyRenderArgsToAngular();
        angularRenderArgs().putAll(fieldOptions);

        angularRenderArgs().putAll(ImmutableMap.of(
                "assetFieldDescriptors", assetFieldDescriptors,
                "defaultValues", defaultValues,
                "overriddenValues", overriddenValues
                ));

        render();
    }

    public static void corruptedService(CatalogServiceRestRep service) {
        TenantSelector.addRenderArgs();
        addBreadCrumbToRenderArgs(service);
        String backUrl = Common.reverseRoute(ServiceCatalog.class, "view");
        renderArgs.put("backUrl", backUrl);
        renderArgs.put("serviceWarning", MessagesUtils.get("service.corrupted"));
        renderArgs.put("showForm", Boolean.FALSE);
        render(service);
    }

    /**
     * Gets the default field values for the given service.
     * 
     * @param descriptor
     *            the service descriptor.
     * @return the default field values.
     */
    private static Map<String, String> getDefaultValues(ServiceDescriptorRestRep descriptor) {
        Map<String, String> defaultValues = Maps.newHashMap();
        List<ServiceFieldRestRep> allFields = ServiceDescriptorUtils.getAllFieldList(descriptor.getItems());
        for (ServiceFieldRestRep field : allFields) {
            if (flash.contains(field.getName())) {
                defaultValues.put(field.getName(), flash.get(field.getName()));
            }
            else {
                defaultValues.put(field.getName(), field.getInitialValue());
            }
        }
        return defaultValues;
    }

    /**
     * Gets the overridden (locked) values for the catalog service.
     * 
     * @param service
     *            the service.
     * @return the overridden values.
     */
    private static Map<String, String> getOverriddenValues(CatalogServiceRestRep service) {
        List<CatalogServiceFieldRestRep> serviceFields = service.getCatalogServiceFields();
        Map<String, String> overriddenValues = Maps.newHashMap();
        for (CatalogServiceFieldRestRep field : serviceFields) {
            boolean overridden = Boolean.TRUE.equals(field.getOverride());
            boolean hasValue = field.getValue() != null;
            if (overridden && hasValue) {
                overriddenValues.put(field.getName(), field.getValue());
            }
        }
        return overriddenValues;
    }

    /**
     * Gets the available assets from the overridden values. The available assets are mapped by type not name (like the
     * overridden values are).
     * 
     * @param descriptors
     *            the asset field descriptors.
     * @param overriddenValues
     *            the overridden values.
     * @return the available assets mapped by type.
     */
    private static Map<String, String> getAvailableAssets(Map<String, AssetFieldDescriptor> descriptors,
            Map<String, String> overriddenValues) {
        Map<String, String> availableAssets = Maps.newHashMap();

        for (Map.Entry<String, AssetFieldDescriptor> entry : descriptors.entrySet()) {
            String name = entry.getKey();
            AssetFieldDescriptor descriptor = entry.getValue();

            boolean isOverridden = overriddenValues.containsKey(name);

            // Add any overridden asset value to the map by type
            if (isOverridden) {
                String value = overriddenValues.get(name);
                availableAssets.put(descriptor.assetType, value);
            }
        }
        return availableAssets;
    }

    /**
     * Gets the asset options for the specified field, given the available assets. This should only be called for asset
     * fields which have all dependencies satisfied.
     * 
     * @param field
     *            the field.
     * @param availableAssets
     *            the available assets.
     * @return the list of asset options.
     */
    private static List<AssetOption> getAssetOptions(ServiceFieldRestRep field, Map<String, String> availableAssets) {
        List<AssetOption> allOptions = Lists.newArrayList();
        try {
            List<AssetOption> options = AssetOptionUtils.getAssetOptions(field.getAssetType(), availableAssets);
            allOptions.addAll(options);
        } catch (RuntimeException e) {
            addAssetError(field, availableAssets, e);
        }

        return allOptions;
    }

    private static void addAssetError(ServiceFieldRestRep field, Map<String, String> availableAssets, RuntimeException e) {
        Logger.error(e, "Could not retrieve asset options for %s (type: %s, available: %s)", field.getName(), field.getType(),
                availableAssets);

        String errorMessage = Messages.get("service.assetError", field.getLabel(), e.getMessage());
        String assetError = StringUtils.trimToEmpty(flash.get("assetError"));
        if (StringUtils.isNotBlank(assetError)) {
            assetError += "\n";
        }
        assetError += errorMessage;
        flash.put("assetError", assetError);
    }

    /**
     * Determines if there are any execution windows defined.
     * 
     * @return true if there are execution windows.
     */
    private static boolean hasExecutionWindows() {
        List<ExecutionWindowRestRep> executionWindows = ExecutionWindowUtils.getExecutionWindows();
        return (executionWindows != null && !executionWindows.isEmpty());
    }
}
