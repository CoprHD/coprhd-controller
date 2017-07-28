/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.ComputeSystemDialogProperties;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemDiscoveryAdapter;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemDiscoveryVersionValidator;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.computesystemcontroller.impl.DiscoveryStatusUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public abstract class AbstractDiscoveryAdapter implements ComputeSystemDiscoveryAdapter {
    private Logger log;

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    @Autowired
    private ComputeSystemDiscoveryVersionValidator versionValidator;

    protected ModelClient modelClient;

    protected DbClient dbClient;

    protected CoordinatorClient coordinator;

    public ModelClient getModelClient() {
        return modelClient;
    }

    @Override
    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    @Override
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public ComputeSystemDiscoveryVersionValidator getVersionValidator() {
        return versionValidator;
    }

    protected <T extends DataObject> T findModelByLabel(List<T> models, String label) {
        for (T model : models) {
            if (StringUtils.equals(label, model.getLabel())) {
                return model;
            }
        }
        return null;
    }

    protected Host findHostByLabel(List<Host> models, String label) {
        for (Host model : models) {
            if (StringUtils.equals(label, model.getLabel())) {
                return model;
            }
        }
        return null;
    }

    protected IpInterface findInterfaceByIp(List<IpInterface> models, String ip) {
        for (IpInterface model : models) {
            if (StringUtils.equals(ip, model.getIpAddress())) {
                return model;
            }
        }
        return null;
    }

    /**
     * Gets a model object by ID.
     * 
     * @param modelClass
     *            the model class.
     * @param id
     *            the ID of the model object.
     * @return the model.
     */
    protected <T extends DataObject> T get(Class<T> modelClass, URI id) {
        return modelClient.of(modelClass).findById(id);
    }

    /**
     * Removes discovered IP interfaces from its host and ignores manually created IP interfaces
     * 
     * @param ipInterfaces
     *            list of IP interfaces
     */
    protected void removeDiscoveredInterfaces(Iterable<IpInterface> ipInterfaces) {
        updateManuallyCreatedInterfaces(ipInterfaces);
        Iterable<IpInterface> discoveredInterfaces = Iterables.filter(ipInterfaces, new Predicate<IpInterface>() {
            @Override
            public boolean apply(IpInterface ipInterface) {
                return ipInterface.getIsManualCreation() != null
                        && !ipInterface.getIsManualCreation();
            }
        });
        removeIpInterfaces(discoveredInterfaces);
    }

    /**
     * Updates IP interfaces by setting isManualCreation value to true if the value is null.
     * Handles the newly added isManualCreation field to IPInterface by assuming that null values
     * are true. During next discovery, any discovered interfaces will have isManualCreation
     * value set to false.
     * 
     * @param ipInterfaces
     *            list of IP interfaces
     */
    protected void updateManuallyCreatedInterfaces(Iterable<IpInterface> ipInterfaces) {
        for (IpInterface ipInterface : ipInterfaces) {
            if (ipInterface.getIsManualCreation() == null) {
                ipInterface.setIsManualCreation(true);
                modelClient.save(ipInterface);
            }
        }
    }

    protected void removeIpInterfaces(Iterable<IpInterface> ipInterfaces) {
        for (IpInterface ipInterface : ipInterfaces) {
            ipInterface.setHost(NullColumnValueGetter.getNullURI());
            ipInterface.setInactive(true);
            save(ipInterface);
        }
    }

    protected void addNewInitiatorsToExport(Host host, List<Initiator> newInitiators) {
        // update export if host is in use
        if (ComputeSystemHelper.isHostInUse(dbClient, host.getId())) {
            String taskId = UUID.randomUUID().toString();
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            List<URI> uris = Lists.newArrayList();
            for (Initiator initiator : newInitiators) {
                uris.add(initiator.getId());
            }
            controller.addInitiatorsToExport(host.getId(), uris, taskId);
        }
    }

    protected void removeOldInitiatorFromExport(Host host, List<Initiator> oldInitiators) {
        // update export if host is in use
        if (!oldInitiators.isEmpty() && ComputeSystemHelper.isHostInUse(dbClient, host.getId())) {
            String taskId = UUID.randomUUID().toString();
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            List<URI> uris = Lists.newArrayList();
            for (Initiator initiator : oldInitiators) {
                uris.add(initiator.getId());
            }
            controller.removeInitiatorsFromExport(host.getId(), uris, taskId);
        }
    }

    protected void addNewHostsToExport(URI clusterURI, List<Host> hosts, URI oldClusterURI) {
        // update export if host is in use
        if (ComputeSystemHelper.isClusterInExport(dbClient, clusterURI)) {
            String taskId = UUID.randomUUID().toString();
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            List<URI> hostIds = Lists.newArrayList();
            for (Host host : hosts) {
                hostIds.add(host.getId());
            }
            controller.addHostsToExport(hostIds, clusterURI, taskId, oldClusterURI, false);
        }
    }

    protected void removeOldHostsFromExport(URI clusterURI, List<Host> hosts) {
        // update export if host is in use
        if (ComputeSystemHelper.isClusterInExport(dbClient, clusterURI)) {
            String taskId = UUID.randomUUID().toString();
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            List<URI> hostIds = Lists.newArrayList();
            for (Host host : hosts) {
                hostIds.add(host.getId());
            }
            controller.removeHostsFromExport(hostIds, clusterURI, false, NullColumnValueGetter.getNullURI(), taskId);
        }
    }

    /**
     * Removes discovered initiators from its host and ignores manually created initiators
     * 
     * @param initiators
     *            list of initiators
     */
    protected void removeDiscoveredInitiators(Iterable<Initiator> initiators) {
        Iterable<Initiator> discoveredInitiators = Iterables.filter(initiators, new Predicate<Initiator>() {
            @Override
            public boolean apply(Initiator initiator) {
                return !initiator.getIsManualCreation();
            }
        });
        removeInitiators(discoveredInitiators);
    }

    protected void removeInitiators(Iterable<Initiator> initiators) {
        for (Initiator initiator : initiators) {
            initiator.setHost(NullColumnValueGetter.getNullURI());
            initiator.setHostName("");
            initiator.setClusterName("");
            save(initiator);
        }
    }

    protected Initiator findInitiatorByPort(List<Initiator> initiators, String port) {
        for (Initiator initiator : initiators) {
            if (StringUtils.equalsIgnoreCase(initiator.getInitiatorPort(), port)) {
                return initiator;
            }
        }
        // Search against the database
        for (Initiator initiator : modelClient.initiators().findByPort(port, true)) {
            if (StringUtils.equalsIgnoreCase(initiator.getInitiatorPort(), port)) {
                return initiator;
            }
        }
        return null;
    }

    protected Initiator getOrCreateInitiator(URI hostId, List<Initiator> initiators, String port) {
        Initiator initiator = findInitiatorByPort(initiators, port);
        if (initiator == null) {
            initiator = new Initiator();
            initiator.setInitiatorPort(port);
            initiator.setLabel(EndpointUtility.changeCase(port));
        } else {
            initiators.remove(initiator);
        }

        if (!NullColumnValueGetter.isNullURI(initiator.getHost()) && !initiator.getHost().equals(hostId)) {
            Host host = dbClient.queryObject(Host.class, hostId);
            Host initiatorHost = dbClient.queryObject(Host.class, initiator.getHost());
            throw ComputeSystemControllerException.exceptions.illegalInitiator(host != null ? host.forDisplay() : hostId.toString(),
                    initiator.getInitiatorPort(), initiatorHost != null ? initiatorHost.forDisplay() : initiator.getHost().toString());
        }
        initiator.setIsManualCreation(false);
        return initiator;
    }

    /**
     * Finds a matching value in the list of models by label, or creates one if none is found. If a match is found in
     * the list, it will be removed from the list before returning.
     * 
     * @param modelClass
     *            the model class.
     * @param models
     *            the list of models.
     * @param label
     *            the label to match.
     * @return the matched value, or a new instance created with the specified label.
     */
    protected <T extends DataObject> T getOrCreate(Class<T> modelClass, List<T> models, String label) {
        T model = findModelByLabel(models, label);
        if (model == null) {
            try {
                model = modelClass.newInstance();
            } catch (InstantiationException e) {
                throw new IllegalArgumentException(e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
            model.setLabel(label);
            info("Creating new instance of %s with label %s", modelClass.getSimpleName(), label);
        } else {
            models.remove(model);
        }
        return model;
    }

    /**
     * Finds a matching value in the list of IpInterfaces by ipAddress, or creates one if none is found. If a match is
     * found in
     * the list, it will be removed from the list before returning.
     * 
     * @param models
     *            the list of IpInterface models.
     * @param ip
     *            the ip to match.
     * @return the matched value, or a new instance created with the specified ip.
     */
    protected IpInterface getOrCreateInterfaceByIp(List<IpInterface> models, String ip) {
        IpInterface model = findInterfaceByIp(models, ip);
        if (model == null) {
            model = new IpInterface();
            model.setIsManualCreation(false);
            model.setIpAddress(ip);
            model.setLabel(ip);
        } else {
            models.remove(model);
        }
        return model;
    }

    protected void save(DataObject model) {
        if (model.getCreationTime() == null) {
            debug("Creating %s: %s", model.getClass().getSimpleName(), toString(model));
        } else {
            debug("Updating %s: %s", model.getClass().getSimpleName(), toString(model));
        }
        modelClient.save(model);
    }

    protected void delete(DataObject model) {
        debug("Deactivating %s: %s", model.getClass().getSimpleName(), toString(model));
        model.setInactive(true);
        modelClient.save(model);
    }

    protected <T extends DataObject> void delete(List<T> models) {
        for (T model : models) {
            delete(model);
        }
    }

    protected synchronized Logger getLog() {
        if (log == null) {
            log = LoggerFactory.getLogger(getClass());
        }
        return log;
    }

    protected void error(String message, Object... args) {
        if (args != null && args.length > 0) {
            getLog().error(String.format(message, args));
        } else {
            getLog().error(message);
        }
    }

    protected void warn(Throwable t, String message, Object... args) {
        if (args != null && args.length > 0) {
            getLog().warn(String.format(message, args), t);
        } else {
            getLog().warn(message, t);
        }
    }

    protected void warn(String message, Object... args) {
        if (args != null && args.length > 0) {
            getLog().warn(String.format(message, args));
        } else {
            getLog().warn(message);
        }
    }

    protected void info(String message, Object... args) {
        if (getLog().isInfoEnabled()) {
            if (args != null && args.length > 0) {
                getLog().info(String.format(message, args));
            } else {
                getLog().info(message);
            }
        }
    }

    protected void debug(String message, Object... args) {
        if (getLog().isDebugEnabled()) {
            if (args != null && args.length > 0) {
                getLog().debug(String.format(message, args));
            } else {
                getLog().debug(message);
            }
        }
    }

    protected String toString(DataObject model) {
        if (model.getClass() == Host.class) {
            return toString((Host) model);
        }
        if (model.getClass() == IpInterface.class) {
            return toString((IpInterface) model);
        }
        if (model.getClass() == Initiator.class) {
            return toString((Initiator) model);
        }
        if (StringUtils.isNotBlank(model.getLabel())) {
            return model.getLabel();
        }
        return model.toString();
    }

    protected String toString(Host host) {
        return String.format("%s [%s]", host.getHostName(), host.getType());
    }

    protected String toString(IpInterface ipInterface) {
        return String.format("%s [%s]", ipInterface.getIpAddress(), ipInterface.getProtocol());
    }

    protected String toString(Initiator initiator) {
        if (StringUtils.isNotBlank(initiator.getInitiatorNode())) {
            return String.format("%s:%s [%s]", initiator.getInitiatorNode(), initiator.getInitiatorPort(),
                    initiator.getProtocol());
        } else {
            return String.format("%s [%s]", initiator.getInitiatorPort(), initiator.getProtocol());
        }
    }

    @Override
    public String getErrorMessage(Throwable t) {
        Throwable rootCause = getRootCause(t);
        if (rootCause instanceof UnknownHostException) {
            return "Unknown host: " + rootCause.getMessage();
        } else if (rootCause instanceof ConnectException) {
            return "Error connecting: " + rootCause.getMessage();
        } else if (rootCause instanceof NoRouteToHostException) {
            return "No route to host: " + rootCause.getMessage();
        } else if (rootCause instanceof SSLException) {
            return "SSL error: " + rootCause.getMessage();
        }
        return getClosestErrorMessage(t);
    }

    protected String getClosestErrorMessage(Throwable originalThrowable) {
        String message = null;
        Throwable t = originalThrowable;
        while ((t != null) && (message == null)) {
            message = t.getMessage();
            t = t.getCause() != t ? t.getCause() : null;
        }
        if (message == null) {
            message = originalThrowable.getClass().getName();
        }
        return message;
    }

    protected Throwable getRootCause(Throwable t) {
        Throwable rootCause = t;
        while ((rootCause.getCause() != null) && (rootCause.getCause() != rootCause)) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    protected void setHostInterfaceRegistrationStatus(HostInterface hostInterface, Host host) {
        if (host.getRegistrationStatus().equals(RegistrationStatus.UNREGISTERED.toString())) {
            hostInterface.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
        } else if (hostInterface.getId() == null) {
            hostInterface.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        }
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return coordinator.locateService(
                clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    public void processHostChanges(HostStateChange changes) {
        processHostChanges(Collections.singletonList(changes), Collections.<URI> emptyList(), Collections.<URI> emptyList(), false);
    }

    public void processHostChanges(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters, boolean isVCenter) {

        log.info("There are " + changes.size() + " changes");

        // Iterate through all host state changes and create states for all of the affected export groups
        for (HostStateChange change : changes) {

            log.info("HostChange: " + change);

            Host host = dbClient.queryObject(Host.class, change.getHost().getId());

            // For every host change (added/removed initiator, cluster change), get all exports that this host
            // currently belongs to
            List<Initiator> newInitiatorObjects = dbClient.queryObject(Initiator.class, change.getNewInitiators());
            List<Initiator> oldInitiatorObjects = dbClient.queryObject(Initiator.class, change.getOldInitiators());

            if (newInitiatorObjects.isEmpty() && !oldInitiatorObjects.isEmpty()) {
                List<URI> hostInitiators = ComputeSystemHelper.getChildrenUris(dbClient, host.getId(), Initiator.class, "host");
                if (hostInitiators.size() == oldInitiatorObjects.size()) {
                    log.info("No initiators were re-discovered for host " + host.getId() + " so we will not delete its initiators");
                    DiscoveryStatusUtils.markAsFailed(getModelClient(), host, "No initiators were discovered", null);
                    continue;
                }
            }

            // check for changes as the following
            // 1) Detect vCenterDatacenter change
            // 2) If no datacenter change, detect cluster change
            // 3) If no datacenter or cluster change, make sure we have updated atleast the new datacenter for the host
            if (!NullColumnValueGetter.isNullURI(change.getOldDatacenter()) && !NullColumnValueGetter.isNullURI(change.getNewDatacenter())
                    && !change.getOldDatacenter().toString().equalsIgnoreCase(change.getNewDatacenter().toString())) {
                VcenterDataCenter oldDatacenter = dbClient.queryObject(VcenterDataCenter.class, change.getOldDatacenter());

                VcenterDataCenter currentDatacenter = dbClient.queryObject(VcenterDataCenter.class, change.getNewDatacenter());

                Cluster cluster = null;
                if (!NullColumnValueGetter.isNullURI(change.getNewCluster())) {
                    cluster = dbClient.queryObject(Cluster.class, change.getNewCluster());
                }

                URI oldClusterURI = change.getOldCluster();
                Cluster oldCluster = null;
                if (!NullColumnValueGetter.isNullURI(oldClusterURI)) {
                    oldCluster = dbClient.queryObject(Cluster.class, oldClusterURI);
                }

                if (!oldDatacenter.getVcenter().toString().equals(currentDatacenter.getVcenter().toString())) {
                    Vcenter oldVcenter = dbClient.queryObject(Vcenter.class, oldDatacenter.getVcenter());
                    Vcenter currentVcenter = dbClient.queryObject(Vcenter.class, currentDatacenter.getVcenter());
                    EventUtils.createActionableEvent(dbClient, EventUtils.EventCode.HOST_VCENTER_CHANGE, host.getTenant(),

                    ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterChangeLabel", oldVcenter.getLabel(),
                            currentVcenter.getLabel()),
                            ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterChangeDescription", host.getLabel(),
                                    oldCluster == null ? "N/A" : oldCluster.getLabel(), cluster == null ? " N/A " : cluster.getLabel()),
                            ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterChangeWarning"),
                            host, Lists.newArrayList(host.getId(), host.getCluster(),
                                    cluster == null ? NullColumnValueGetter.getNullURI() : cluster.getId()),
                            EventUtils.hostVcenterChange,
                            new Object[] { host.getId(), cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI(),
                                    currentDatacenter.getId(), isVCenter },
                            EventUtils.hostVcenterChangeDecline,
                            new Object[] { host.getId(), cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI(),
                                    currentDatacenter.getId(), isVCenter });
                } else {
                    EventUtils.createActionableEvent(dbClient, EventUtils.EventCode.HOST_DATACENTER_CHANGE, host.getTenant(),
                            ComputeSystemDialogProperties.getMessage("ComputeSystem.hostDatacenterChangeLabel", oldDatacenter.getLabel(),
                                    currentDatacenter.getLabel()),
                            ComputeSystemDialogProperties.getMessage("ComputeSystem.hostDatacenterChangeDescription", host.getLabel(),
                                    oldCluster == null ? "N/A" : oldCluster.getLabel(), cluster == null ? " N/A " : cluster.getLabel()),
                            ComputeSystemDialogProperties.getMessage("ComputeSystem.hostDatacenterChangeWarning"),
                            host, Lists.newArrayList(host.getId(), host.getCluster(),
                                    cluster == null ? NullColumnValueGetter.getNullURI() : cluster.getId()),
                            EventUtils.hostDatacenterChange,
                            new Object[] { host.getId(), cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI(),
                                    currentDatacenter.getId(), isVCenter },
                            EventUtils.hostDatacenterChangeDecline,
                            new Object[] { host.getId(), cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI(),
                                    currentDatacenter.getId(), isVCenter });
                }
            } else if ((change.getOldCluster() == null && change.getNewCluster() != null)
                    || (change.getOldCluster() != null && change.getNewCluster() == null)
                    || (change.getOldCluster() != null && change.getNewCluster() != null
                            && !change.getOldCluster().toString().equals(change.getNewCluster().toString()))) {

                Cluster cluster = null;
                if (!NullColumnValueGetter.isNullURI(change.getNewCluster())) {
                    cluster = dbClient.queryObject(Cluster.class, change.getNewCluster());
                }

                URI oldClusterURI = change.getOldCluster();
                Cluster oldCluster = null;
                if (!NullColumnValueGetter.isNullURI(oldClusterURI)) {
                    oldCluster = dbClient.queryObject(Cluster.class, oldClusterURI);
                }

                boolean oldClusterInUse = oldCluster == null ? false : ComputeSystemHelper.isClusterInExport(dbClient, oldCluster.getId());
                boolean newClusterInUse = cluster == null ? false : ComputeSystemHelper.isClusterInExport(dbClient, cluster.getId());

                if ((cluster != null || oldCluster != null) && (oldClusterInUse || newClusterInUse)) {
                    String name = null;
                    String description = null;

                    if (cluster != null && oldCluster == null) {
                        name = ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeAddedLabel", cluster.getLabel());
                        description = ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeAddedDescription",
                                host.getLabel(), cluster.getLabel());
                    } else if (cluster == null && oldCluster != null) {
                        name = ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeRemovedLabel",
                                oldCluster.getLabel());
                        description = ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeRemovedDescription",
                                host.getLabel(),
                                oldCluster.getLabel());
                    } else {
                        name = ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeMovedLabel", oldCluster.getLabel(),
                                cluster.getLabel());
                        description = ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeMovedDescription",
                                host.getLabel(), oldCluster.getLabel(),
                                cluster.getLabel());
                    }
                    EventUtils.createActionableEvent(dbClient, EventUtils.EventCode.HOST_CLUSTER_CHANGE, host.getTenant(),
                            name, description, ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeWarning"), host,
                            Lists.newArrayList(host.getId(), host.getCluster(),
                                    cluster == null ? NullColumnValueGetter.getNullURI() : cluster.getId()),
                            EventUtils.hostClusterChange,
                            new Object[] { host.getId(), cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI(),
                                    NullColumnValueGetter.isNullURI(change.getNewDatacenter()) ? NullColumnValueGetter.getNullURI()
                                            : change.getNewDatacenter(),
                                    isVCenter },
                            EventUtils.hostClusterChangeDecline,
                            new Object[] { host.getId(), cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI(),
                                    NullColumnValueGetter.isNullURI(change.getNewDatacenter()) ? NullColumnValueGetter.getNullURI()
                                            : change.getNewDatacenter(),
                                    isVCenter });
                } else {
                    host.setCluster(cluster == null ? NullColumnValueGetter.getNullURI() : cluster.getId());
                    dbClient.updateObject(host);
                    ComputeSystemHelper.updateHostAndInitiatorClusterReferences(dbClient, host.getCluster(), host.getId());
                    if (cluster != null) {
                        ComputeSystemHelper.updateHostVcenterDatacenterReference(dbClient, host.getId(),
                                cluster != null ? cluster.getVcenterDataCenter() : NullColumnValueGetter.getNullURI());
                    }
                }
            } else if (!NullColumnValueGetter.isNullURI(change.getNewDatacenter())) {
                VcenterDataCenter currentDatacenter = dbClient.queryObject(VcenterDataCenter.class, change.getNewDatacenter());
                host.setTenant(currentDatacenter.getTenant());
                host.setVcenterDataCenter(currentDatacenter.getId());
                dbClient.updateObject(host);
            }

            if (ComputeSystemHelper.isHostInUse(dbClient, host.getId()) && (!oldInitiatorObjects.isEmpty()
                    || !newInitiatorObjects.isEmpty())) {

                List<String> oldInitiatorPorts = Lists.newArrayList();
                List<String> newInitiatorPorts = Lists.newArrayList();
                List<URI> oldInitiatorIds = Lists.newArrayList();
                List<URI> newInitiatorIds = Lists.newArrayList();

                for (Initiator oldInitiator : oldInitiatorObjects) {
                    oldInitiatorPorts.add(oldInitiator.getInitiatorPort());
                    oldInitiatorIds.add(oldInitiator.getId());
                }
                for (Initiator newInitiator : newInitiatorObjects) {
                    newInitiatorPorts.add(newInitiator.getInitiatorPort());
                    newInitiatorIds.add(newInitiator.getId());
                }

                List<URI> allAffectedResources = Lists.newArrayList();
                allAffectedResources.add(host.getId());
                allAffectedResources.addAll(newInitiatorIds);
                allAffectedResources.addAll(oldInitiatorIds);

                String name = ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsLabelAddAndRemove",
                        StringUtils.join(newInitiatorPorts, ","), StringUtils.join(oldInitiatorPorts, ","));
                String description = ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsDescriptionAddAndRemove",
                        StringUtils.join(newInitiatorPorts, ","), StringUtils.join(oldInitiatorPorts, ","));

                if (!newInitiatorPorts.isEmpty() && oldInitiatorPorts.isEmpty()) {
                    name = ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsLabelAddOnly",
                            StringUtils.join(newInitiatorPorts, ","));
                    description = ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsDescriptionAddOnly",
                            StringUtils.join(newInitiatorPorts, ","), StringUtils.join(oldInitiatorPorts, ","));
                } else if (newInitiatorPorts.isEmpty() && !oldInitiatorPorts.isEmpty()) {
                    name = ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsLabelRemoveOnly",
                            StringUtils.join(newInitiatorPorts, ","));
                    description = ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsDescriptionRemoveOnly",
                            StringUtils.join(newInitiatorPorts, ","), StringUtils.join(oldInitiatorPorts, ","));
                }

                EventUtils.createActionableEvent(dbClient, EventUtils.EventCode.HOST_INITIATOR_UPDATES, host.getTenant(),
                        name, description, ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsWarning"),
                        host, allAffectedResources, EventUtils.updateInitiators,
                        new Object[] { host.getId(), newInitiatorIds, oldInitiatorIds }, EventUtils.updateInitiatorsDecline,
                        new Object[] { host.getId(), newInitiatorIds, oldInitiatorIds });
            } else {
                for (Initiator oldInitiator : oldInitiatorObjects) {
                    info("Deleting Initiator %s because it was not re-discovered and is not in use by any export groups",
                            oldInitiator.getId());
                    dbClient.removeObject(oldInitiator);
                }
            }
        }

        log.info("Number of undiscovered hosts: " + deletedHosts.size());

        Set<URI> incorrectDeletedHosts = Sets.newHashSet();
        for (URI deletedHost : deletedHosts) {
            Host host = dbClient.queryObject(Host.class, deletedHost);
            URI clusterId = host.getCluster();
            List<URI> clusterHosts = Lists.newArrayList();
            if (!NullColumnValueGetter.isNullURI(clusterId)) {
                clusterHosts = ComputeSystemHelper.getChildrenUris(dbClient, clusterId, Host.class, "cluster");
            }
            if (clusterHosts.contains(deletedHost)
                    && deletedHosts.containsAll(clusterHosts)) {
                incorrectDeletedHosts.add(deletedHost);
                DiscoveryStatusUtils.markAsFailed(getModelClient(), host, "Error discovering host cluster", null);
                log.info("Host " + host.getId()
                        + " is part of a cluster that was not re-discovered. Fail discovery and keep the host in our database");

            } else {
                Vcenter vcenter = ComputeSystemHelper.getHostVcenter(dbClient, host);
                EventUtils.createActionableEvent(dbClient, EventUtils.EventCode.UNASSIGN_HOST_FROM_VCENTER, host.getTenant(),
                        ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterUnassignLabel",
                                vcenter == null ? "N/A" : vcenter.getLabel()),
                        ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterUnassignDescription", host.getLabel(),
                                vcenter == null ? "N/A" : vcenter.getLabel()),
                        ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterUnassignWarning"),
                        host, Lists.newArrayList(host.getId(), host.getCluster()),
                        EventUtils.hostVcenterUnassign, new Object[] { deletedHost },
                        EventUtils.hostVcenterUnassignDecline, new Object[] { deletedHost });
            }
        }

        // delete clusters that don't contain any hosts, don't have any exports, and don't have any pending events
        for (URI clusterId : deletedClusters) {
            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(dbClient, clusterId, Host.class, "cluster");
            if (hostUris.isEmpty() && !ComputeSystemHelper.isClusterInExport(dbClient, clusterId)
                    && EventUtils.findAffectedResourcePendingEvents(dbClient, clusterId).isEmpty()) {
                Cluster cluster = dbClient.queryObject(Cluster.class, clusterId);
                info("Deactivating Cluster: " + clusterId);
                ComputeSystemHelper.doDeactivateCluster(dbClient, cluster);
            } else {
                info("Unable to delete cluster " + clusterId);
            }
        }

    }

    // TODO: move to AbstractHostDiscoveryAdapter once EsxHostDiscoveryAdatper is moved to extend it
    public void matchHostToComputeElements(Host host) {
        log.warn("Matching host to compute elements not supported for this host type.");
    }

}
