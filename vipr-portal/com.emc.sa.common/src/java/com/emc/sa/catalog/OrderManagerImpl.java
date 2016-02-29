/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsManager;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.descriptor.ServiceField;
import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.ApprovalStatus;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderAndParams;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.CreationTimeComparator;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.sa.util.ResourceType;
import com.emc.sa.util.TextUtils;
import com.emc.sa.zookeeper.OrderCompletionQueue;
import com.emc.sa.zookeeper.OrderExecutionQueue;
import com.emc.sa.zookeeper.OrderMessage;
import com.emc.sa.zookeeper.OrderNumberSequence;
import com.emc.storageos.auth.TokenManager;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Maps;

@Component
public class OrderManagerImpl implements OrderManager {

    private static final Logger log = Logger.getLogger(OrderManagerImpl.class);

    @Autowired
    private ModelClient client;

    @Autowired
    private OrderExecutionQueue orderExecutionQueue;

    @Autowired
    private OrderCompletionQueue orderCompletionQueue;

    @Resource(name = "tokenManager")
    private TokenManager tokenManager;

    @Autowired
    private OrderNumberSequence orderNumberSequence;

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private ApprovalManager approvalManager;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private ServiceDescriptors serviceDescriptors;

    @Autowired
    private AssetOptionsManager assetOptionsManager;

    @PostConstruct
    private void init() throws Exception {
        orderCompletionQueue.listenForRequests(new OrderCompletionConsumer(this));
    }

    public Order getOrderById(URI id) {
        if (id == null) {
            return null;
        }

        Order order = client.orders().findById(id);

        return order;
    }

    public Order createOrder(Order order, List<OrderParameter> orderParameters, StorageOSUser user) {

        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(order.getCatalogServiceId());
        ServiceDescriptor serviceDescriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), catalogService.getBaseService());

        order.setOrderNumber(getNextOrderNumber());
        order.setSummary(catalogService.getTitle());
        if (catalogService.getExecutionWindowRequired()) {
            order.setExecutionWindowId(catalogService.getDefaultExecutionWindowId());
        }
        order.setMessage("");
        order.setSubmittedByUserId(user.getUserName());
        order.setOrderStatus(OrderStatus.PENDING.name());
        createExecutionState(order, user);

        client.save(order);

        Map<String, String> assetOptions = getAssetValues(serviceDescriptor, orderParameters);
        for (OrderParameter orderParameter : orderParameters) {
            ServiceField serviceField = findServiceField(serviceDescriptor, orderParameter.getLabel());
            String friendlyLabel = serviceField.getLabel();

            StringBuilder friendlyValue = new StringBuilder();
            List<String> values = TextUtils.parseCSV(orderParameter.getValue());
            for (String value : values) {
                if (friendlyValue.length() > 0) {
                    friendlyValue.append(",");
                }
                friendlyValue.append(getFriendlyValue(serviceField, value, assetOptions, user));
            }

            orderParameter.setFriendlyLabel(friendlyLabel);
            orderParameter.setFriendlyValue(friendlyValue.toString());
            createOrderParameter(orderParameter);
        }

        return order;
    }

    public void createOrderParameter(OrderParameter orderParameter) {
        client.save(orderParameter);
    }

    // For 'asset' type fields, put in the label instead of the actual value.
    // This prevents the receipt page from having things like FileSystem: 123
    private String getFriendlyValue(ServiceField serviceField, String value, Map<String, String> assetValues, StorageOSUser user) {
        if (serviceField != null && StringUtils.isNotBlank(value)) {
            String[] labels = value.split(",");
            for (int i = 0; i < labels.length; i++) {
                if (serviceField.isAsset()) {
                    labels[i] = getOptionLabelForAsset(labels[i], serviceField.getAssetType(), assetValues, user);
                }
                else if (serviceField.getOptions() != null && serviceField.getOptions().size() > 0) {
                    String optionLabel = serviceField.getOptions().get(labels[i]);
                    if (StringUtils.isNotBlank(optionLabel)) {
                        labels[i] = optionLabel;
                    }
                }
            }
            return StringUtils.join(labels, ",");
        }
        return "";
    }

    private boolean canGetResourceLabel(String resourceId) {
        switch (ResourceType.fromResourceId(resourceId)) {
            case VOLUME:
            case PROJECT:
            case HOST:
            case CLUSTER:
            case QUOTA_DIRECTORY:
            case VIRTUAL_ARRAY:
            case VIRTUAL_POOL:
            case CONSISTENCY_GROUP:
            case STORAGE_SYSTEM:
            case EXPORT_GROUP:
            case FILE_SHARE:
            case FILE_SNAPSHOT:
            case BLOCK_SNAPSHOT:
            case BLOCK_SNAPSHOT_SESSION:
            case VCENTER:
            case VCENTER_DATA_CENTER:
            case SMIS_PROVIDER:
            case STORAGE_POOL:
            case NETWORK_SYSTEM:
            case PROTECTION_SYSTEM:
            case UNMANAGED_VOLUME:
            case UNMANAGED_FILESYSTEM:
            case UNMANAGED_EXPORTMASK:
            case BLOCK_CONTINUOUS_COPY:
            case VPLEX_CONTINUOUS_COPY:
                return true;
            default:
                return false;
        }
    }

    private String getOptionLabelForAsset(String key, String assetType, Map<String, String> assetValues, StorageOSUser user) {
        try {
            if (canGetResourceLabel(key)) {
                return getResourceLabel(key);
            }
            else {
                // Defer to AssetOptions if it's not a ViPR resource
                AssetOptionsContext context = assetOptionsManager.createDefaultContext(user);
                List<AssetOption> options = assetOptionsManager.getOptions(context, assetType, assetValues);
                for (AssetOption option : options) {
                    if (option.key.equals(key)) {
                        return option.value;
                    }
                }
            }
        } catch (Exception e) {
            log.error(String.format("Error getting label for asset %s", key), e);
        }

        return key;
    }

    protected String getResourceLabel(String resourceId) {
        DataObject dataObject = null;
        try {
            URI id = uri(resourceId);
            switch (ResourceType.fromResourceId(resourceId)) {
                case VOLUME:
                    dataObject = client.findById(Volume.class, id);
                    break;
                case PROJECT:
                    dataObject = client.findById(Project.class, id);
                    break;
                case HOST:
                    dataObject = client.findById(Host.class, id);
                    break;
                case CLUSTER:
                    dataObject = client.findById(Cluster.class, id);
                    break;
                case QUOTA_DIRECTORY:
                    dataObject = client.findById(QuotaDirectory.class, id);
                    break;
                case VIRTUAL_ARRAY:
                    dataObject = client.findById(VirtualArray.class, getTargetVirtualArray(id));
                    break;
                case VIRTUAL_POOL:
                    dataObject = client.findById(VirtualPool.class, id);
                    break;
                case CONSISTENCY_GROUP:
                    dataObject = client.findById(BlockConsistencyGroup.class, id);
                    break;
                case STORAGE_SYSTEM:
                    dataObject = client.findById(StorageSystem.class, id);
                    break;
                case EXPORT_GROUP:
                    dataObject = client.findById(ExportGroup.class, id);
                    break;
                case FILE_SHARE:
                    dataObject = client.findById(FileShare.class, id);
                    break;
                case FILE_SNAPSHOT:
                    dataObject = client.findById(Snapshot.class, id);
                    break;
                case BLOCK_SNAPSHOT:
                    dataObject = client.findById(BlockSnapshot.class, id);
                    break;
                case BLOCK_SNAPSHOT_SESSION:
                    dataObject = client.findById(BlockSnapshotSession.class, id);
                    break;
                case VCENTER:
                    dataObject = client.findById(Vcenter.class, id);
                    break;
                case VCENTER_DATA_CENTER:
                    dataObject = client.findById(VcenterDataCenter.class, id);
                    break;
                case SMIS_PROVIDER:
                    dataObject = client.findById(StorageProvider.class, id);
                    break;
                case STORAGE_POOL:
                    dataObject = client.findById(StoragePool.class, id);
                    break;
                case NETWORK_SYSTEM:
                    dataObject = client.findById(Network.class, id);
                    break;
                case PROTECTION_SYSTEM:
                    dataObject = client.findById(ProtectionSystem.class, id);
                    break;
                case UNMANAGED_VOLUME:
                    dataObject = client.findById(UnManagedVolume.class, id);
                    break;
                case UNMANAGED_FILESYSTEM:
                    dataObject = client.findById(UnManagedFileSystem.class, id);
                    break;
                case UNMANAGED_EXPORTMASK:
                    dataObject = client.findById(UnManagedExportMask.class, id);
                    break;
                case BLOCK_CONTINUOUS_COPY:
                    dataObject = client.findById(BlockMirror.class, id);
                    break;
                case VPLEX_CONTINUOUS_COPY:
                    dataObject = client.findById(VplexMirror.class, id);
                    break;
            }
        } catch (Exception e) {
            log.error(String.format("Error getting resource %s", resourceId), e);
        }

        if (dataObject != null) {
            return augmentDataObjectName(dataObject);
        } else {
            return resourceId;
        }
    }

    private URI getTargetVirtualArray(URI id) {
        if (id.toString().startsWith("tgt:")) {
            return URI.create(StringUtils.substringAfter(id.toString(), ":"));
        } else {
            return id;
        }
    }

    /**
     * Allows manipulation of the string returned from the DataObjectResponse.getName()
     */
    private String augmentDataObjectName(DataObject dataObject) {
        if (dataObject instanceof Host) {
            URI clusterResource = ((Host) dataObject).getCluster();
            if (clusterResource != null) {
                Cluster cluster = client.findById(Cluster.class, clusterResource);
                if (cluster != null) {
                    return String.format("%s [cluster: %s]", dataObject.getLabel(), cluster.getLabel());
                }
            }
        }
        return dataObject.getLabel();
    }

    private Map<String, String> getAssetValues(ServiceDescriptor descriptor, List<OrderParameter> orderParameters) {
        Map<String, String> assetOptionParams = Maps.newHashMap();
        if (descriptor != null && orderParameters != null) {
            // Create a map of assetType -> Value
            for (ServiceField serviceField : descriptor.getAllFieldList()) {
                OrderParameter orderParameter = findOrderParameter(serviceField.getName(), orderParameters);
                if (orderParameter != null) {
                    assetOptionParams.put(serviceField.getAssetType(), orderParameter.getValue());
                }
            }

        }
        return assetOptionParams;
    }

    private ServiceField findServiceField(ServiceDescriptor serviceDescriptor, String serviceFieldName) {
        for (ServiceField serviceField : serviceDescriptor.getAllFieldList()) {
            if (StringUtils.equalsIgnoreCase(serviceFieldName, serviceField.getName())) {
                return serviceField;
            }
        }
        return null;
    }

    private OrderParameter findOrderParameter(String serviceFieldName, List<OrderParameter> orderParameters) {
        for (OrderParameter orderParameter : orderParameters) {
            if (serviceFieldName.equals(orderParameter.getLabel())) {
                return orderParameter;
            }
        }
        return null;
    }

    private ExecutionState createExecutionState(Order order, StorageOSUser user) {
        ExecutionState state = new ExecutionState();
        String proxyToken = tokenManager.getProxyToken(user);
        state.setProxyToken(proxyToken);
        state.setExecutionStatus(ExecutionStatus.NONE.name());
        client.save(state);
        order.setExecutionStateId(state.getId());
        return state;
    }

    public void updateOrder(Order order) {
        client.save(order);
    }

    public void deleteOrder(Order order) {
        client.delete(order);
    }

    public List<Order> getOrders(URI tenantId) {
        return client.orders().findAll(tenantId.toString());
    }

    public List<Order> getUserOrders(StorageOSUser user) {
        return client.orders().findByUserId(user.getUserName());
    }

    public List<Order> findOrdersByStatus(URI tenantId, OrderStatus orderStatus) {
        return client.orders().findByOrderStatus(tenantId.toString(), orderStatus);
    }

    public List<Order> findOrdersByTimeRange(URI tenantId, Date startTime, Date endTime) {
        return client.orders().findByTimeRange(tenantId, startTime, endTime);
    }

    public List<ExecutionLog> getOrderExecutionLogs(Order order) {
        ExecutionState executionState = getOrderExecutionState(order.getExecutionStateId());
        List<ExecutionLog> logs = client.executionLogs().findByIds(executionState.getLogIds());
        Collections.sort(logs, CreationTimeComparator.OLDEST_FIRST);
        return logs;
    }

    public List<ExecutionTaskLog> getOrderExecutionTaskLogs(Order order) {
        ExecutionState executionState = getOrderExecutionState(order.getExecutionStateId());
        List<ExecutionTaskLog> logs = client.executionTaskLogs().findByIds(executionState.getTaskLogIds());
        Collections.sort(logs, CreationTimeComparator.OLDEST_FIRST);
        return logs;
    }

    public ExecutionState getOrderExecutionState(URI executionStateId) {
        return client.executionStates().findById(executionStateId);
    }

    public List<OrderParameter> getOrderParameters(URI orderId) {
        List<OrderParameter> parameters = client.orderParameters().findByOrderId(orderId);
        SortedIndexUtils.sort(parameters);
        return parameters;
    }

    public List<OrderAndParams> getOrdersAndParams(List<URI> ids) {
        List<OrderAndParams> ordersAndParams =
                new ArrayList<OrderAndParams>();
        if (ids == null) {
            return null;
        }

        for (URI id : ids) {
            Order order = client.orders().findById(id);
            if (order != null) {
                List<OrderParameter> params =
                        getOrderParameters(order.getId());
                OrderAndParams orderAndParams =
                        new OrderAndParams();
                orderAndParams.setOrder(order);
                orderAndParams.setParameters(params);
                ordersAndParams.add(orderAndParams);
            }
        }

        return ordersAndParams;
    }

    private void submitOrderToQueue(Order order) {
        try {
            orderExecutionQueue.putItem(new OrderMessage(order.getId().toString()));
        } catch (Exception e) {
            log.error(String.format("Unable to send order %s to Order Execution queue", order.getId()), e);
            failOrder(order, "Unable to send order to Order Execution queue", e);
            throw new RuntimeException(e);
        }
    }

    public String getNextOrderNumber() {
        return Long.toString(orderNumberSequence.nextOrderNumber());
    }

    public void processOrder(Order order) {
        CatalogService service = catalogServiceManager.getCatalogServiceById(order.getCatalogServiceId());
        OrderStatus status = OrderStatus.valueOf(order.getOrderStatus());
        switch (status) {
            case PENDING:
                processPendingOrder(order, service);
                break;
            case APPROVAL:
                processApprovalOrder(order, service);
                break;
            case APPROVED:
                processApprovedOrder(order, service);
                break;
            case REJECTED:
                processRejectedOrder(order, service);
                break;
            case SCHEDULED:
                processScheduledOrder(order, service);
                break;
            case CANCELLED:
                processCancelledOrder(order, service);
                break;
            case SUCCESS:
                processSuccessOrder(order, service);
                break;
            case ERROR:
                processErrorOrder(order, service);
                break;
        }
    }

    private void processPendingOrder(Order order, CatalogService service) {
        if (Boolean.TRUE.equals(service.getApprovalRequired())) {
            requireApproval(order, service);
        }
        else if (Boolean.TRUE.equals(service.getExecutionWindowRequired())) {
            scheduleOrder(order, service);
        }
        else {
            submitOrderToQueue(order);
        }
    }

    private void processApprovalOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        ApprovalStatus status = ApprovalStatus.valueOf(approval.getApprovalStatus());
        switch (status) {
            case APPROVED:
                approveOrder(order, service);
                break;
            case REJECTED:
                rejectOrder(order, service);
                break;
        }
    }

    private void processApprovedOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        notificationManager.notifyUserOfApprovalStatus(order, service, approval);
        if (Boolean.TRUE.equals(service.getExecutionWindowRequired())) {
            scheduleOrder(order, service);
        }
        else {
            submitOrderToQueue(order);
        }

    }

    private void processRejectedOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        notificationManager.notifyUserOfApprovalStatus(order, service, approval);
    }

    private void processScheduledOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        notificationManager.notifyUserOfOrderStatus(order, service, approval);
    }

    private void processCancelledOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        notificationManager.notifyUserOfOrderStatus(order, service, approval);
    }

    private void processSuccessOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        notificationManager.notifyUserOfOrderStatus(order, service, approval);
    }

    private void processErrorOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        notificationManager.notifyUserOfOrderStatus(order, service, approval);
    }

    private void approveOrder(Order order, CatalogService service) {
        order.setOrderStatus(OrderStatus.APPROVED.name());
        updateOrder(order);
        processApprovedOrder(order, service);
    }

    private void rejectOrder(Order order, CatalogService service) {
        order.setOrderStatus(OrderStatus.REJECTED.name());
        updateOrder(order);
        processRejectedOrder(order, service);
    }

    private void scheduleOrder(Order order, CatalogService service) {
        log.info(String.format("Order %s is scheduled", order.getId()));
        order.setOrderStatus(OrderStatus.SCHEDULED.name());
        updateOrder(order);
        processScheduledOrder(order, service);
    }

    private void requireApproval(Order order, CatalogService service) {
        log.info(String.format("Order %s requires approval", order.getId()));
        order.setOrderStatus(OrderStatus.APPROVAL.name());
        updateOrder(order);

        ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setApprovalStatus(ApprovalStatus.PENDING.name());
        approvalRequest.setOrderId(order.getId());
        approvalRequest.setTenant(order.getTenant());
        approvalManager.createApproval(approvalRequest);

        notificationManager.notifyApproversOfApprovalRequest(order, service, approvalRequest);
    }

    private void failOrder(Order order, String message, Throwable e) {
        ExecutionLog failedLog = new ExecutionLog();
        failedLog.setMessage(message);
        failedLog.addStackTrace(e);
        failedLog.setLevel(ExecutionLog.LogLevel.ERROR.name());
        failedLog.setDate(new Date());
        client.save(failedLog);

        ExecutionState state = getOrderExecutionState(order.getExecutionStateId());
        state.addExecutionLog(failedLog);
        state.setEndDate(new Date());
        state.setExecutionStatus(ExecutionStatus.FAILED.name());
        client.save(state);

        order.setMessage(message);
        order.setOrderStatus(OrderStatus.ERROR.name());
        updateOrder(order);
    }

}
