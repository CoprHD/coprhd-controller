/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog.impl;

public class PathConstants {
    public static final String ID_URL_FORMAT = "%s/{id}";

    public static final String ORDER_URL = "/api/orders";
    public static final String EXECUTION_URL_FORMAT = "%s/{id}/execution";

    public static final String APPROVALS_URL = "/api/approvals";
    public static final String APPROVALS_PENDING_URL = APPROVALS_URL + "/pending";
    public static final String APPROVE_URL = APPROVALS_URL + "{id}/approve";
    public static final String REJECT_URL = APPROVALS_URL + "{id}/reject";

    public static final String EXECUTION_WINDOWS_URL = "/admin/api/executionwindows";

    public static final String ASSET_OPTIONS_URL = "/api/options/{asset}";
    public static final String ASSET_DEPS_URL = "/api/options/{asset}/dependencies";
    public static final String CATEGORY_URL = "/api/categories/{id}";
    public static final String SERVICE_URL = "/api/services/{id}";
    public static final String SERVICE_DESCRIPTOR_URL = SERVICE_URL + "/descriptor";
    public static final String CATALOG_URL = "/api/catalog";

    public static final String SETUP_SKIP_URL = "/api/setup/skip";

    // New URLs
    public static final String CATALOG_CATEGORY_URL = "/catalog/categories";
    public static final String CATALOG_RESET_URL = CATALOG_CATEGORY_URL + "/reset";
    public static final String CATALOG_UPGRADE_URI = "/catalog/categories/upgrade";
    public static final String CATALOG_CATEGORY_MOVE_UP_URL = CATALOG_CATEGORY_URL + "/{catalogCategoryId}/move/up";
    public static final String CATALOG_CATEGORY_MOVE_DOWN_URL = CATALOG_CATEGORY_URL + "/{catalogCategoryId}/move/down";
    public static final String CATALOG_SERVICE_URL = "/catalog/services";
    public static final String CATALOG_SERVICE_RECENT_URL = CATALOG_SERVICE_URL + "/recent";
    public static final String CATALOG_SERVICE_MOVE_UP_URL = CATALOG_SERVICE_URL + "/{catalogServiceId}/move/up";
    public static final String CATALOG_SERVICE_MOVE_DOWN_URL = CATALOG_SERVICE_URL + "/{catalogServiceId}/move/down";
    public static final String CATALOG_SERVICE_FIELD_MOVE_UP_URL = CATALOG_SERVICE_URL + "/{catalogServiceId}/fields/{fieldName}/move/up";
    public static final String CATALOG_SERVICE_FIELD_MOVE_DOWN_URL = CATALOG_SERVICE_URL
            + "/{catalogServiceId}/fields/{fieldName}/move/down";
    public static final String APPROVALS2_URL = "/catalog/approvals";
    public static final String EXECUTION_WINDOWS2_URL = "/catalog/execution-windows";
    public static final String ORDER2_URL = "/catalog/orders";
    public static final String ORDER2_MY_URL = ORDER2_URL+"/my-orders";
    public static final String ORDER2_ALL_URL = ORDER2_URL + "/all";
    public static final String ORDER2_LOGS_URL = ORDER2_URL + "/{id}/logs";
    public static final String ORDER2_CANCEL_URL = ORDER2_URL + "/{id}/cancel";
    public static final String ORDER2_EXECUTION_STATE_URL = ORDER2_URL + "/{id}/execution";
    public static final String ORDER2_EXECUTION_LOGS_URL = ORDER2_EXECUTION_STATE_URL + "/logs";
    public static final String ORDER2_MY_COUNT = ORDER2_URL + "/my-order-count";
    public static final String ORDER2_DELETE_ORDERS = ORDER2_URL;
    public static final String ORDER2_QUERY_ORDER_JOB = ORDER2_URL+"/job-status";
    public static final String ORDER2_DOWNLOAD_ORDER = ORDER2_URL+"/download";
    public static final String ORDER2_ALL_COUNT = ORDER2_URL + "/count";
    public static final String ASSET_OPTIONS2_URL = "/catalog/asset-options";
    public static final String ASSET_OPTIONS2_OPTIONS_URL = ASSET_OPTIONS2_URL + "/{assetType}";
    public static final String ASSET_OPTIONS2_DEP_URL = ASSET_OPTIONS2_OPTIONS_URL + "/dependencies";
    public static final String SERVICE_DESCRIPTORS_URL = "/catalog/service-descriptors";
    public static final String SERVICE_DESCRIPTOR_NAME_URL = "/catalog/service-descriptors/{name}";
    public static final String CATALOG_IMAGE_URL = "/catalog/images";
    public static final String CATALOG_SUB_CATEGORIES_URL = CATALOG_CATEGORY_URL + "/{id}/categories";
    public static final String CATALOG_SUB_SERVICES_URL = CATALOG_CATEGORY_URL + "/{id}/services";
    public static final String CATALOG_PREFERENCES = "/catalog/preferences";
    public static final String USER_PREFERENCES = "/user/preferences";
    public static final String SCHEDULED_EVENTS_URL = "/catalog/events";
    public static final String SCHEDULED_EVENTS_DEACTIVATE_URL = SCHEDULED_EVENTS_URL + "/{id}/deactivate";
    public static final String SCHEDULED_EVENTS_CANCELLATION_URL = SCHEDULED_EVENTS_URL + "/{id}/cancel";
    public static final String WF_DIRECTORIES = "/workflow/directory";
    public static final String WF_DIRECTORY_BULK = "/workflow/directory/bulk";
    public static final String WF_DIRECTORY = "/workflow/directory/{id}";
    public static final String WF_DIRECTORY_DELETE = "/workflow/directory/{id}/deactivate";
    public static final String CUSTOM_SERVICES_PRIMITIVES = "/primitives";
    public static final String CUSTOM_SERVICES_PRIMITIVE = "/primitives/{id}";
    public static final String CUSTOM_SERVICES_PRIMITIVE_RESOURCE = "/primitives/resource/{type}";
    public static final String CUSTOM_SERVICES_WORKFLOWS = "/workflows";
    public static final String CUSTOM_SERVICES_WORKFLOW_DELETE = "/workflows/{id}/deactivate";
    public static final String CUSTOM_SERVICES_WORKFLOW_VALIDATE = "/workflows/{id}/validate";
    public static final String CUSTOM_SERVICES_WORKFLOW_PUBLISH = "/workflows/{id}/publish";
    public static final String CUSTOM_SERVICES_WORKFLOW_UNPUBLISH = "/workflows/{id}/unpublish";
    public static final String CUSTOM_SERVICES_WORKFLOW = "/workflows/{id}";
}
