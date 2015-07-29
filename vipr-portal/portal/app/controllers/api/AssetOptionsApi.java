/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.api;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static render.RenderApiModel.renderApi;
import static util.api.ApiMapperUtils.newAssetOptionsReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Scope;
import play.mvc.Util;
import play.mvc.With;
import play.utils.Utils;
import storageapi.APIException;
import util.AssetOptionUtils;
import util.CatalogServiceUtils;
import util.MessagesUtils;
import util.ServiceDescriptorUtils;

import com.emc.vipr.model.catalog.AssetOption;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.Option;
import com.emc.vipr.model.catalog.Reference;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;

/**
 * API controller for providing access to the asset options.
 * 
 * @author Chris Dail
 */
@With(Common.class)
public class AssetOptionsApi extends Controller {

    public static final Set<String> IGNORED_PARAMS = Sets.newHashSet("body", "format", "asset");

    public static void options(String asset) {
        params.checkAndParse();

        try {
            List<AssetOption> assetOptions = getOptionsAsync(cleanAssetType(asset), paramsToMap(params));
            List<Option> options = wrapOptions(toOptions(assetOptions));
            renderApi(options);
        } catch (APIException e) {
            String message = e.getStatusText();

            String messageKey = null;
            if (e.getStatusCode() == Http.StatusCode.NOT_FOUND) {
                messageKey = "storageapi.error.404";
            }
            else if (e.getStatusCode() == Http.StatusCode.BAD_REQUEST) {
                messageKey = "storageapi.error.400";
            }
            else if (e.getStatusCode() == Http.StatusCode.INTERNAL_ERROR) {
                messageKey = "storageapi.error.500";
            }
            else if (e.getStatusCode() == Http.StatusCode.GATEWAY_TIMEOUT) {
                messageKey = "storageapi.error.504";
            }
            if (StringUtils.isNotBlank(messageKey)) {
                message = MessagesUtils.get(messageKey, e.getStatusCode());
            }

            error(e.getStatusCode(), message);
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    public static void dependencies(String asset, String service) {
        // The 'service' may be the baseService or a service ID
        ServiceDescriptorRestRep descriptor;
        if (StringUtils.isEmpty(service)) {
            error(Messages.get("AssetOptionsApi.serviceParameterIsRequired"));
        }
        if (service.startsWith("urn:")) {
            CatalogServiceRestRep catalogService = CatalogServiceUtils.getCatalogService(uri(service));
            descriptor = catalogService.getServiceDescriptor();
        }
        else {
            descriptor = ServiceDescriptorUtils.getDescriptor(service);
        }
        Set<String> allAssetTypes = ServiceDescriptorUtils.getAllAssetTypes(descriptor);
        List<String> dependencies = calculateAssetDependencies(cleanAssetType(asset), allAssetTypes);
        List<Reference> references = Lists.newArrayList();
        for (String dependency : dependencies) {
            references.add(newAssetOptionsReference(dependency));
        }
        renderApi(references);
    }

    private static String cleanAssetType(String assetType) {
        return assetType.replaceFirst("assetType\\.", "");
    }

    private static List<Option> wrapOptions(List<AssetOption> options) {
        List<Option> result = Lists.newArrayList();
        for (AssetOption option : options) {
            result.add(new Option(option.key, option.value));
        }
        return result;
    }

    @Util
    public static List<AssetOption> getOptionsAsync(String assetType, Map<String, String> assetParameters) {
        return AssetOptionUtils.getAssetOptions(assetType, assetParameters);
    }

    @Util
    public static List<AssetOption> getOptions(String assetType, Map<String, String> assetParameters) throws APIException {
        List<AssetOption> asssetOptions = getOptionsAsync(assetType, assetParameters);
        return toOptions(asssetOptions);
    }

    @Util
    public static List<AssetOption> toOptions(List<AssetOption> assetOptions) throws APIException {
        List<AssetOption> options = Lists.newArrayList();
        for (AssetOption option : assetOptions) {
            options.add(new AssetOption(option.key, option.value));
        }
        return options;
    }

    @Util
    public static List<String> calculateAssetDependencies(String assetType, Set<String> allTypesInDescriptor) {
        return AssetOptionUtils.getAssetDependencies(assetType, allTypesInDescriptor);
    }

    private static Map<String, String> paramsToMap(Scope.Params params) {
        Map<String, String> filteredMap = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String[]> e : params.all().entrySet()) {
            if (!IGNORED_PARAMS.contains(e.getKey())) {
                filteredMap.put(e.getKey(), Utils.join(e.getValue(), ", "));
            }
        }
        return filteredMap;
    }

}
