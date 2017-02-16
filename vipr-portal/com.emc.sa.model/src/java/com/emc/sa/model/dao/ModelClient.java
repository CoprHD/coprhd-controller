/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.InitialSetup;
import com.emc.storageos.db.client.model.uimodels.ModelObjectWithACLs;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.RecentService;
import com.emc.storageos.db.client.model.uimodels.VirtualMachine;
import com.google.common.collect.Maps;

/**
 * @author Chris Dail
 */
public class ModelClient {
    /** Pattern for finding the 'type' from an ID. */
    private static final Pattern TYPE_PATTERN = Pattern.compile("urn\\:storageos\\:([^\\:]+)");
    /** The mapping of 'type' to classes. */
    private static Map<String, Class<? extends DataObject>> CLASSES = Maps.newHashMap();
    static {
        initClasses();
    }

    private DBClientWrapper client;

    // JM -- Don't attempt to instantiate the DAO fields directly, this will be called before the client field
    // is initialized in the constructor causing NPEs
    private ApprovalRequestFinder approvalRequestDAO;
    private CatalogCategoryFinder catalogCategoryDAO;
    private CatalogServiceFinder catalogServiceDAO;
    private CatalogServiceFieldFinder catalogServiceFieldDAO;
    private CatalogImageFinder catalogImageDAO;
    private DatacenterFinder datacenterDAO;
    private ESXHostFinder esxHostDAO;
    private ModelFinder<ExecutionLog> executionLogDAO;
    private ModelFinder<ExecutionState> executionStateDAO;
    private ModelFinder<ExecutionTaskLog> executionTaskLogDAO;
    private ExecutionWindowFinder executionWindowDAO;
    private OrderFinder orderModelDAO;
    private OrderParameterFinder orderParameterDAO;
    private ScheduledEventFinder scheduledEventModelDAO;
    private RecentServiceFinder recentServiceDAO;
    private VCenterFinder vcenterDAO;
    private VirtualMachineFinder virtualMachineDAO;
    private PreferencesFinder preferencesFinder;

    private HostFinder hostDAO;
    private InitiatorFinder initiatorDAO;
    private IpInterfaceFinder ipInterfaceDAO;
    private ClusterFinder clusterDAO;
    private ActionableEventFinder actionableEventDAO;
    private CustomServicesWorkflowFinder customServicesWorkflowDAO;
    private CustomServicesPrimitiveResourceFinder customServicesPrimitiveDAO;

    private WFDirectoryFinder wfDirectoryDAO;

    public ModelClient(DBClientWrapper client) {
        this.client = client;
    }

    public DBClientWrapper getModelClient() {
        return client;
    }

    public <T extends DataObject> ModelFinder<T> of(final Class<T> clazz) {
        return new ModelFinder<T>(clazz, client);
    }

    public <T extends DataObject> void save(T model) throws DataAccessException {
        boolean isNew = isNew(model);
        setIdIfRequired(model);
        setInactiveIfRequired(model);
        setCreationTimeIfRequired(model);
        setLastUpdatedIfRequired(model);

        if (isNew) {
            // Force setting the inactive flag
            model.setInactive(model != null && model.getInactive());
            client.create(model);
        }
        else {
            client.update(model);
        }
    }

    public <T extends DataObject> void delete(T model) throws DataAccessException {
        client.delete(model);
    }

    public <T extends DataObject> void delete(List<T> models) throws DataAccessException {
        client.delete(models);
    }

    public <T extends DataObject> List<URI> findByType(Class<T> clazz) {
        return client.findAllIds(clazz);
    }

    public <T extends DataObject> List<T> findByIds(Class<T> clazz, List<URI> ids) {
        return client.findByIds(clazz, ids);
    }

    public <T extends DataObject> T findById(Class<T> clazz, URI id) {
        return client.findById(clazz, id);
    }
    
    public <T extends DataObject> List<NamedElement> findBy(Class<T> clazz, String columnField, URI id) {
        return client.findBy(clazz, columnField, id);
    }

    /**
     * Finds an object by ID.
     * 
     * @param id
     *            the ID of the object.
     * @return the object.
     */
    public <T extends DataObject> T findById(URI id) {
        if (id == null) {
            throw new DataAccessException("ID provided was null");
        }
        Class<T> modelClass = getModelClass(id);
        if (modelClass != null) {
            return of(modelClass).findById(id);
        }
        else {
            return null;
        }
    }

    /**
     * Finds an object by ID.
     * 
     * @param id
     *            the ID of the object.
     * @return the object.
     */
    public <T extends DataObject> T findById(String id) {
        if (id == null) {
            throw new DataAccessException("ID provided was null");
        }
        return findById(URI.create(id));
    }

    public <T extends DataObject> List<NamedElement> findByAlternateId(Class<T> clazz, String columnField, String value) {
        return client.findByAlternateId(clazz, columnField, value);
    }

    public <T extends DataObject> List<NamedElement> findByLabel(Class<T> clazz, String label) {
        return client.findByPrefix(clazz, "label", label);
    }

    public ApprovalRequestFinder approvalRequests() {
        if (approvalRequestDAO == null) {
            approvalRequestDAO = new ApprovalRequestFinder(client);
        }
        return approvalRequestDAO;
    }

    public CatalogCategoryFinder catalogCategories() {
        if (catalogCategoryDAO == null) {
            catalogCategoryDAO = new CatalogCategoryFinder(client);
        }
        return catalogCategoryDAO;
    }

    public CatalogServiceFinder catalogServices() {
        if (catalogServiceDAO == null) {
            catalogServiceDAO = new CatalogServiceFinder(client);
        }
        return catalogServiceDAO;
    }

    public CatalogServiceFieldFinder catalogServiceFields() {
        if (catalogServiceFieldDAO == null) {
            catalogServiceFieldDAO = new CatalogServiceFieldFinder(client);
        }
        return catalogServiceFieldDAO;
    }

    public CatalogImageFinder catalogImages() {
        if (catalogImageDAO == null) {
            catalogImageDAO = new CatalogImageFinder(client);
        }
        return catalogImageDAO;
    }

    public DatacenterFinder datacenters() {
        if (datacenterDAO == null) {
            datacenterDAO = new DatacenterFinder(client);
        }
        return datacenterDAO;
    }

    public ESXHostFinder esxHosts() {
        if (esxHostDAO == null) {
            esxHostDAO = new ESXHostFinder(client);
        }
        return esxHostDAO;
    }

    public ModelFinder<ExecutionLog> executionLogs() {
        if (executionLogDAO == null) {
            executionLogDAO = of(ExecutionLog.class);
        }
        return executionLogDAO;
    }

    public ModelFinder<ExecutionState> executionStates() {
        if (executionStateDAO == null) {
            executionStateDAO = of(ExecutionState.class);
        }
        return executionStateDAO;
    }

    public ModelFinder<ExecutionTaskLog> executionTaskLogs() {
        if (executionTaskLogDAO == null) {
            executionTaskLogDAO = of(ExecutionTaskLog.class);
        }
        return executionTaskLogDAO;
    }

    public ExecutionWindowFinder executionWindows() {
        if (executionWindowDAO == null) {
            executionWindowDAO = new ExecutionWindowFinder(client);
        }
        return executionWindowDAO;
    }

    public OrderFinder orders() {
        if (orderModelDAO == null) {
            orderModelDAO = new OrderFinder(client);
        }
        return orderModelDAO;
    }

    public OrderParameterFinder orderParameters() {
        if (orderParameterDAO == null) {
            orderParameterDAO = new OrderParameterFinder(client);
        }
        return orderParameterDAO;
    }

    public ScheduledEventFinder scheduledEvents() {
        if (scheduledEventModelDAO == null) {
            scheduledEventModelDAO = new ScheduledEventFinder(client);
        }
        return scheduledEventModelDAO;
    }

    @Deprecated
    public RecentServiceFinder recentServices() {
        if (recentServiceDAO == null) {
            recentServiceDAO = new RecentServiceFinder(client);
        }
        return recentServiceDAO;
    }

    public VCenterFinder vcenters() {
        if (vcenterDAO == null) {
            vcenterDAO = new VCenterFinder(client);
        }
        return vcenterDAO;
    }

    public VirtualMachineFinder virtualMachines() {
        if (virtualMachineDAO == null) {
            virtualMachineDAO = new VirtualMachineFinder(client);
        }
        return virtualMachineDAO;
    }

    public PreferencesFinder preferences() {
        if (preferencesFinder == null) {
            preferencesFinder = new PreferencesFinder(client);
        }
        return preferencesFinder;
    }

    public HostFinder hosts() {
        if (hostDAO == null) {
            hostDAO = new HostFinder(client);
        }
        return hostDAO;
    }

    public ActionableEventFinder actionableEvents() {
        if (actionableEventDAO == null) {
            actionableEventDAO = new ActionableEventFinder(client);
        }
        return actionableEventDAO;
    }

    public InitiatorFinder initiators() {
        if (initiatorDAO == null) {
            initiatorDAO = new InitiatorFinder(client);
        }
        return initiatorDAO;
    }

    public IpInterfaceFinder ipInterfaces() {
        if (ipInterfaceDAO == null) {
            ipInterfaceDAO = new IpInterfaceFinder(client);
        }
        return ipInterfaceDAO;
    }

    public ClusterFinder clusters() {
        if (clusterDAO == null) {
            clusterDAO = new ClusterFinder(client);
        }
        return clusterDAO;
    }

    private TenantPreferencesFinder tenantPreferencesDAO;

    public TenantPreferencesFinder tenantPreferences() {
        if (tenantPreferencesDAO == null) {
            tenantPreferencesDAO = new TenantPreferencesFinder(client);
        }
        return tenantPreferencesDAO;
    }
    
    public CustomServicesWorkflowFinder customServicesWorkflows() {
        if(customServicesWorkflowDAO == null) {
            customServicesWorkflowDAO = new CustomServicesWorkflowFinder(client);
        }
        
        return customServicesWorkflowDAO;
    }
    
    public CustomServicesPrimitiveResourceFinder customServicesPrimitiveResources() {
        if( customServicesPrimitiveDAO == null ) {
            customServicesPrimitiveDAO = new CustomServicesPrimitiveResourceFinder(client);
        }
        return customServicesPrimitiveDAO;
    }

    public WFDirectoryFinder wfDirectory() {
        if (wfDirectoryDAO == null) {
            wfDirectoryDAO = new WFDirectoryFinder(client);
        }
        return wfDirectoryDAO;
    }

    private <T extends DataObject> boolean isNew(T model) {
        if (model != null) {
            // We automatically set ID/creation time so they will be null for new objects only
            // Occasionally ID has to be set before save, so check either field
            return (model.getId() == null) || (model.getCreationTime() == null);
        }
        return false;
    }

    private <T extends DataObject> void setIdIfRequired(T model) {
        if (model != null && model.getId() == null) {
            model.setId(URIUtil.createId(model.getClass()));
        }
    }

    private <T extends DataObject> void setInactiveIfRequired(T model) {
        if (model != null && (model.getInactive() == null)) {
            model.setInactive(false);
        }
    }

    private <T extends DataObject> void setCreationTimeIfRequired(T model) {
        if (model != null && model.getCreationTime() == null) {
            model.setCreationTime(Calendar.getInstance());
        }
    }

    private <T extends DataObject> void setLastUpdatedIfRequired(T model) {
        if (model instanceof ModelObject) {
            ((ModelObject) model).markUpdated();
        }
        else if (model instanceof ModelObjectWithACLs) {
            ((ModelObjectWithACLs) model).markUpdated();
        }
    }

    public static String getTypeName(URI id) {
        return getTypeName(id.toString());
    }

    public static String getTypeName(String id) {
        Matcher m = TYPE_PATTERN.matcher(id);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T extends DataObject> Class<T> getModelClass(URI id) {
        initClasses();
        String typeName = getTypeName(id);
        return (Class<T>) CLASSES.get(typeName);
    }

    @SuppressWarnings("deprecation")
    private static synchronized void initClasses() {
        addClass(ApprovalRequest.class);
        addClass(CatalogCategory.class);
        addClass(CatalogService.class);
        addClass(CatalogServiceField.class);
        addClass(ExecutionLog.class);
        addClass(ExecutionState.class);
        addClass(ExecutionWindow.class);
        addClass(ExecutionTaskLog.class);
        addClass(Host.class);
        addClass(Initiator.class);
        addClass(IpInterface.class);
        addClass(Order.class);
        addClass(OrderParameter.class);
        addClass(RecentService.class);
        addClass(Vcenter.class);
        addClass(VcenterDataCenter.class);
        addClass(VirtualMachine.class);
        addClass(UserPreferences.class);
        addClass(InitialSetup.class);
    }

    private static synchronized <T extends DataObject> void addClass(Class<T> modelClass) {
        CLASSES.put(modelClass.getSimpleName(), modelClass);
    }
}
