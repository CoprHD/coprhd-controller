/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.sa.catalog;

import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.util.ExecutionWindowHelper;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsManager;
import com.emc.sa.asset.AssetOptionsProvider;
import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.model.util.CreationTimeComparator;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.sa.util.ResourceType;
import com.emc.sa.util.CatalogSerializationUtils;
import com.emc.sa.util.TextUtils;
import com.emc.sa.zookeeper.OrderCompletionQueue;
import com.emc.sa.zookeeper.OrderExecutionQueue;
import com.emc.sa.zookeeper.OrderMessage;
import com.emc.sa.zookeeper.OrderNumberSequence;
import com.emc.storageos.auth.TokenManager;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Maps;

@Component
public class OrderManagerImpl implements OrderManager {
    private static final Logger log = LoggerFactory.getLogger(OrderManagerImpl.class);
    private long noDeletePeriod = 2592000000L;

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
    private WorkflowServiceDescriptor workflowServiceDescriptor;

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

    public void setNoDeletePeriod(long noDeletePeriod) {
        this.noDeletePeriod = noDeletePeriod;
    }

    public long getNoDeletePeriod() {
        return noDeletePeriod;
    }

    public Order createOrder(Order order, List<OrderParameter> orderParameters, StorageOSUser user) {
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(order.getCatalogServiceId());
        ServiceDescriptor serviceDescriptor = ServiceDescriptorUtil.getServiceDescriptorByName(serviceDescriptors, workflowServiceDescriptor, catalogService.getBaseService());

        order.setOrderNumber(getNextOrderNumber());
        order.setSummary(catalogService.getTitle());

        if (order.getScheduledEventId() == null) {
            // Order is scheduled with traditional way but not the new scheduler framework

            if (catalogService.getExecutionWindowRequired()) {
                if (catalogService.getDefaultExecutionWindowId() == null ||
                    catalogService.getDefaultExecutionWindowId().getURI().equals(ExecutionWindow.NEXT)) {
                    // For default execution window, null is deemed as NEXT window as well.
                    // But we always need to set order execution window to NEXT explicitly to different it
                    // with INFINITE window in new scheduler framework.

                    // Set schedule time to latest updated time.  It would still be scheduled in executed window
                    Calendar scheduleTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    scheduleTime.setTime(new Date());
                    order.setScheduledTime(scheduleTime);
                    order.setExecutionWindowId(new NamedURI(ExecutionWindow.NEXT, "NEXT"));
                } else {
                    // Set schedule time to
                    // either 1) the next execution window starting time
                    // or     2) the current time if it is in current execution window
                    ExecutionWindow executionWindow = client.findById(catalogService.getDefaultExecutionWindowId().getURI());
                    ExecutionWindowHelper helper = new ExecutionWindowHelper(executionWindow);
                    order.setScheduledTime(helper.getScheduledTime());
                    order.setExecutionWindowId(catalogService.getDefaultExecutionWindowId());
                }
            } else {
                // In traditional order procedure,
                // If no execution window is indicated, order will be submitted to DQ immediately.
                ;
            }
        } else {
            // Order is scheduled with new scheduler framework
            // ExecutionWindow and ScheduleTime are already set via Parameter
            ;
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
            case COMPUTE_VIRTUAL_POOL:
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
            case STORAGE_PORT:
            case REMOTE_REPLICATION_SET:
            case REMOTE_REPLICATION_GROUP:
            case REMOTE_REPLICATION_PAIR:
                return true;
            default:
                return false;
        }
    }

    private String getOptionLabelForAsset(String key, String assetType, Map<String, String> assetValues, StorageOSUser user) {
        try {
            if (canGetResourceLabel(key)) {
                return getResourceLabel(key);
            } else if (CatalogSerializationUtils.isSerializedObject(key)) {
                Map<URI, List<URI>> port = (Map<URI, List<URI>>) CatalogSerializationUtils.serializeFromString(key);
                String s = new String("{");
                for (Map.Entry<URI, List<URI> > entry : port.entrySet()) {
                    s += getResourceLabel(entry.getKey().toString());
                    s += ":[";
                    List<String> portLabels = new ArrayList<String>();
                    for (URI p : entry.getValue()) {
                        portLabels.add(getResourceLabel(p.toString()));
                    }
                    s += String.join(",", portLabels);
                    s += "]";
                }
                s += "}";
                log.info(String.format("Serialized label: %s", s));
                return s;
            }
            else {

                // if provider prefers raw labels (because retrieval is too slow) use raw value
                final AssetOptionsProvider assetProvider = assetOptionsManager.getProviderForAssetType(assetType);
                if(assetProvider != null && assetProvider.useRawLabels()){
                    return key;
                }

                // Defer to AssetOptions if it's not a ViPR resource
                log.info(String.format("AssetType %s not a ViPR resource, deferring to AssetOptions to get value.", key));
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
                case COMPUTE_VIRTUAL_POOL:
                    dataObject = client.findById(ComputeVirtualPool.class, id);
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
                case STORAGE_PORT:
                    dataObject = client.findById(StoragePort.class, id);
                    break;
                case INITIATOR:
                    dataObject = client.findById(Initiator.class, id);
                    break;
                case REMOTE_REPLICATION_SET:
                    dataObject = client.findById(RemoteReplicationSet.class, id);
                    break;
                case REMOTE_REPLICATION_GROUP:
                    dataObject = client.findById(RemoteReplicationGroup.class, id);
                    break;
                case REMOTE_REPLICATION_PAIR:
                    dataObject = client.findById(RemoteReplicationPair.class, id);
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
        } else if (dataObject instanceof StoragePort) {
            return ((StoragePort) dataObject).getPortName();
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

        log.info(String.format("Unexpected service field value found: %s", serviceFieldName));
        ServiceField field = new ServiceField();
        field.setName(serviceFieldName);
        field.setLabel(serviceFieldName);
        return field;
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

    public void canBeDeleted(Order order, OrderStatus orderStatus) {
        if (order.getScheduledEventId()!=null) {
            throw APIException.badRequests.scheduledOrderNotAllowed("deactivation");
        }

        if (createdWithinOneMonth(order)) {
            throw APIException.badRequests.orderWithinOneMonth(order.getId());
        }

        OrderStatus status = OrderStatus.valueOf(order.getOrderStatus());

        if (orderStatus != null && status != orderStatus) {
            throw APIException.badRequests.orderCanNotBeDeleted(order.getId(), status.toString());
        }

        if (!status.canBeDeleted()) {
            throw APIException.badRequests.orderCanNotBeDeleted(order.getId(), status.toString());
        }

    }

    private boolean createdWithinOneMonth(Order order) {
        long now = System.currentTimeMillis();

        long createdTime = order.getCreationTime().getTimeInMillis();

        return (now - createdTime) < noDeletePeriod;
    }


    public void cancelOrder(Order order) {
        deleteOrderInDb(order);
    }

    public void deleteOrder(Order order) {
        canBeDeleted(order, null);
        deleteOrderInDb(order);
    }

    private void deleteOrderInDb(Order order) {
        URI orderId = order.getId();
        List<ApprovalRequest> approvalRequests = approvalManager.findApprovalsByOrderId(orderId);
        client.delete(approvalRequests);

        List<OrderParameter> orderParameters = getOrderParameters(orderId);
        client.delete(orderParameters);

        ExecutionState state = getOrderExecutionState(order.getExecutionStateId());
        if (state != null) {
            StringSet logIds = state.getLogIds();
            URI id = null;
            for (String logId : logIds) {
                try {
                    id = new URI(logId);
                } catch (URISyntaxException e) {
                    log.error("Invalid id {} e=", logId, e);
                    continue;
                }
                ExecutionLog execlog = client.getModelClient().findById(ExecutionLog.class, id);
                client.delete(execlog);
            }

            List<ExecutionTaskLog> logs = client.executionTaskLogs().findByIds(state.getTaskLogIds());
            for (ExecutionTaskLog taskLog: logs) {
                client.delete(taskLog);
            }

            client.delete(state);
        }

        client.delete(order);
    }

    public List<Order> getOrders(URI tenantId) {
        return client.orders().findAll(tenantId.toString());
    }

    public List<Order> getUserOrders(StorageOSUser user, long startTime, long endTime, int maxCount) {
        return client.orders().findOrdersByUserId(user.getUserName(), startTime, endTime, maxCount);
    }

    public long getOrderCount(StorageOSUser user, long startTime, long endTime) {
        return client.orders().getOrdersCount(user.getUserName(), startTime, endTime);
    }

    public Map<String, Long> getOrderCount(List<URI> tids, long startTime, long endTime) {
        return client.orders().getOrdersCount(tids, startTime, endTime);
    }

    public List<Order> findOrdersByStatus(URI tenantId, OrderStatus orderStatus) {
        return client.orders().findByOrderStatus(tenantId.toString(), orderStatus);
    }

    public List<Order> findOrdersByTimeRange(URI tenantId, Date startTime, Date endTime, int maxCount) {
        return client.orders().findByTimeRange(tenantId, startTime, endTime, maxCount);
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
                processScheduledEvent(order);
                break;
            case ERROR:
                processErrorOrder(order, service);
                processScheduledEvent(order);
                break;
        }
    }

    private void processPendingOrder(Order order, CatalogService service) {
        if (Boolean.TRUE.equals(service.getApprovalRequired())) {
            if (order.getScheduledEventId() != null) {
                // For scheduled event, the 1st order's APPROVAL request will be used to approve
                // the whole set of following orders (i.e. taking effect on the scheduled event.
                ScheduledEvent scheduledEvent = client.scheduledEvents().findById(order.getScheduledEventId());
                if (scheduledEvent != null) {
                    // Scheduler would always set the outofdate orders to ERROR and schedule a new order.
                    // Here all the previous scheduled orders have not been approved yet.
                    // We would always send a new approval request for the latest scheduled order.
                    if (scheduledEvent.getEventStatus() == ScheduledEventStatus.APPROVAL) {
                        requireApproval(order, service);
                        return;
                    }

                    // For the following orders, skipping order approval request (the event is already APPROVED)
                } else {
                    // Send Approval Request for the 1st order (Now the scheduled event is not persisted into DB yet.)
                    requireApproval(order, service);
                    return;
                }
            } else {
                // send approval request for the original order request.
                requireApproval(order, service);
                return;
            }
        }

        if (order.getScheduledEventId() != null) {
            // scheduledEvent always requires orders to be scheduled.
            scheduleOrder(order, service);
        } else if (Boolean.TRUE.equals(service.getExecutionWindowRequired())) {
            // direct orders would be executed in the next execution window.
            scheduleOrder(order, service);
        } else {
            submitOrderToQueue(order);
        }
    }

    private void processApprovalOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        ApprovalStatus status = ApprovalStatus.valueOf(approval.getApprovalStatus());
        switch (status) {
            case APPROVED:
                approveOrder(order, service);
                authorizeScheduledEvent(order, service, true);
                break;
            case REJECTED:
                rejectOrder(order, service);
                authorizeScheduledEvent(order, service, false);
                break;
        }
    }

    private void processApprovedOrder(Order order, CatalogService service) {
        ApprovalRequest approval = approvalManager.findFirstApprovalsByOrderId(order.getId());
        try {
            notificationManager.notifyUserOfApprovalStatus(order, service, approval);
        } catch (Exception e) {
            log.error(String.format("Unable to notify user of approved order %s", order.getId()), e);
        }

        if (order.getScheduledEventId() != null) {
            // orders always need to be scheduled via scheduledEvent.
            scheduleOrder(order, service);
        } else if (Boolean.TRUE.equals(service.getExecutionWindowRequired())) {
            // direct orders would be executed in the next execution window.
            scheduleOrder(order, service);
        } else {
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

    private void authorizeScheduledEvent(Order order, CatalogService service, boolean approved) {
        ScheduledEvent scheduledEvent = client.scheduledEvents().findById(order.getScheduledEventId());
        if (scheduledEvent != null) {
            scheduledEvent.setEventStatus(approved? ScheduledEventStatus.APPROVED: ScheduledEventStatus.REJECTED);
            client.save(scheduledEvent);
        }
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
        if (order.getScheduledEventId() != null) {
            approvalRequest.setScheduledEventId(order.getScheduledEventId());
        }
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

    public void processScheduledEvent(Order order) {
        URI scheduledEventId = order.getScheduledEventId();
        ScheduledEvent scheduledEvent = client.scheduledEvents().findById(scheduledEventId);
        if (scheduledEvent != null) {
            if (scheduledEvent.getEventType() == ScheduledEventType.ONCE) {
                // For ONCE event, update its status to FINISHED after order finished.
                // For REOCCURRENCE event, update its status during scheduler thread.
                if (OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.SUCCESS) ||
                    OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.PARTIAL_SUCCESS) ||
                    OrderStatus.valueOf(order.getOrderStatus()).equals(OrderStatus.ERROR) ) {
                    scheduledEvent.setEventStatus(ScheduledEventStatus.FINISHED);
                    client.save(scheduledEvent);
                }
            }
        }
        return;
    }
}
