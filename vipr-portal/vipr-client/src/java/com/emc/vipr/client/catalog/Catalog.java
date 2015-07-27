/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import com.emc.vipr.client.ViPRCatalogClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.exceptions.ValidationException;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.*;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import static com.emc.vipr.client.catalog.impl.PathConstants.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import static com.emc.vipr.client.catalog.impl.ApiListUtils.*;

public class Catalog {
    protected final ViPRCatalogClient parent;
    protected final RestClient client;

    public Catalog(ViPRCatalogClient parent, RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    /**
     * Retrieves asset options for the defined asset with no parameters.
     * <p>
     * API Call: GET /api/options/{asset}
     *
     * @param asset Name of the asset to retrieve options for.
     * @return Options for this asset type.
     */
    public List<Option> getAssetOptions(String asset) {
        return getAssetOptions(asset, null);
    }

    /**
     * Retrieves asset options for the defined asset.
     * <p>
     * API Call: GET /api/options/{asset}
     *
     * @param asset Name of the asset to retrieve options for.
     * @param parameters Parameters used to match for asset dependencies.
     * @return Options for this asset type.
     */
    public List<Option> getAssetOptions(String asset, Map<String,Object> parameters) {
        UriBuilder uriBuilder = client.uriBuilder(ASSET_OPTIONS_URL);
        if (parameters != null) {
            for (Map.Entry<String,Object> param: parameters.entrySet()) {
                uriBuilder = uriBuilder.queryParam(param.getKey(), param.getValue());
            }
        }
        List<Option> apiList = getApiListUri(client, new GenericType<List<Option>>() {}, uriBuilder.build(asset));
        return apiList;
    }

    /**
     * Retrieves asset option dependencies for the defined asset.
     * <p>
     * API Call: GET /api/options/{asset}/dependencies?service={service}
     *
     * @param asset Name of the asset to retrieve dependencies for.
     * @param service The serviceId of the service descriptor or ID of the catalog service.
     *                The service is used to compute dependencies for a particular service form.
     * @return Dependencies for this asset type.
     */
    public List<Reference> getAssetDependencies(String asset, String service) {
        UriBuilder uriBuilder = client.uriBuilder(ASSET_DEPS_URL).queryParam("service", service);
        List<Reference> apiList = getApiListUri(client, new GenericType<List<Reference>>() {}, uriBuilder.build(asset));
        return apiList;
    }

    /**
     * Retrieves a category by identifier.
     * <p>
     * API Call: GET /api/categories/{id}
     *
     * @param id Category identifier.
     * @return Category information.
     */
    public CategoryInfo getCategory(String id) {
        return client.get(CategoryInfo.class, CATEGORY_URL, id);
    }

    /**
     * Retrieves a service by identifier.
     * <p>
     * API Call: GET /api/services/{id}
     *
     * @param id Service identifier.
     * @return Service information.
     */
    public ServiceInfo getService(String id) {
        return client.get(ServiceInfo.class, SERVICE_URL, id);
    }

    /**
     * Retrieves a service descriptor for the identifier.
     * <p>
     * API Call: GET /api/services/{id}/descriptor
     *
     * @param id Service identifier.
     * @return Service Descriptor information for this service (as JSON)
     */
    public String getServiceDescriptor(String id) {
        return client.get(String.class, SERVICE_DESCRIPTOR_URL, id);
    }

    /**
     * Browse the root of the service catalog.
     * <p>
     * API Call: GET /api/catalog
     *
     * @return Category information.
     */
    public CategoryInfo browse() {
        return client.getURI(CategoryInfo.class, catalogPath(null));
    }

    /**
     * Browse the service catalog by path to a cateogy (Path separated with slashes).
     * <p>
     * API Call: GET /api/catalog/{ ... path ...}
     *
     * @param path Slash separate path to the category.
     * @return Category information.
     */
    public CategoryInfo browseCategory(String path) {
        return client.getURI(CategoryInfo.class, catalogPath(path));
    }

    /**
     * Browse the service catalog by path to a service (Path separated with slashes).
     * <p>
     * API Call: GET /api/catalog/{ ... path ...}
     *
     * @param path Slash separate path to the service.
     * @return Service information.
     */
    public ServiceInfo browseService(String path) {
        return client.getURI(ServiceInfo.class, catalogPath(path));
    }

    /**
     * Places an order for the service with the given service identifier.
     * <p>
     * API Call: POST /api/services/{serviceId}
     *
     * @param serviceId Service identifier.
     * @param parameters Map containing key-value parameter pairs.
     * @return Information on submitted order.
     * @throws ValidationException
     */
    public OrderInfo order(String serviceId, Map<String,Object> parameters) throws ValidationException {
        return doOrder(client.uriBuilder(SERVICE_URL).build(serviceId), parameters);
    }

    /**
     * Places an order for the service with the given service identifier.
     * <p>
     * API Call: POST /api/services/{serviceId}
     *
     * @param serviceId Service identifier.
     * @param parameters MultivaluedMap containing key-value parameter pairs.
     * @return Information on submitted order.
     * @throws ValidationException
     */
    public OrderInfo order(String serviceId, MultivaluedMap<String,String> parameters) throws ValidationException {
        return doOrder(client.uriBuilder(SERVICE_URL).build(serviceId), parameters);
    }

    /**
     * Places an order for the service with the given service path.
     * <p>
     * API Call: POST /api/catalog/{ ... path ...}
     *
     * @param path Slash separate path to the category.
     * @param parameters Map containing key-value parameter pairs.
     * @return Information on submitted order.
     * @throws ValidationException
     */
    public OrderInfo orderByPath(String path, Map<String,Object> parameters) throws ValidationException {
        return doOrder(catalogPath(path), parameters);
    }

    /**
     * Places an order for the service with the given service identifier.
     * <p>
     * API Call: POST /api/catalog/{ ... path ...}
     *
     * @param path Slash separate path to the category.
     * @param parameters MultivaluedMap containing key-value parameter pairs.
     * @return Information on submitted order.
     * @throws ValidationException
     */
    public OrderInfo orderByPath(String path, MultivaluedMap<String,String> parameters) throws ValidationException {
        return doOrder(catalogPath(path), parameters);
    }

    private OrderInfo doOrder(URI uri, Map<String,Object> parameters) {
        WebResource.Builder builder = client.getClient().resource(uri).accept(
            client.getConfig().getMediaType()).type(MediaType.APPLICATION_FORM_URLENCODED);
        return builder.post(OrderInfo.class, toMultiValuedMap(parameters));
    }

    private OrderInfo doOrder(URI uri, MultivaluedMap parameters) {
        WebResource.Builder builder = client.getClient().resource(uri).accept(
            client.getConfig().getMediaType()).type(MediaType.APPLICATION_FORM_URLENCODED);
        return builder.post(OrderInfo.class, parameters);
    }

    private MultivaluedMap toMultiValuedMap(Map<String,Object> parameters) {
        MultivaluedMapImpl map = new MultivaluedMapImpl();
        for (Map.Entry<String,Object> entry: parameters.entrySet()) {
            map.add(entry.getKey(), entry.getValue().toString());
        }
        return map;
    }

    private URI catalogPath(String path) {
        UriBuilder builder = client.uriBuilder(CATALOG_URL);
        if (path != null) {
            builder.path(path);
        }
        return builder.build();
    }
}
