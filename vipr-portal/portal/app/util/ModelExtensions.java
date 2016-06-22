/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.net.URI;
import java.util.Calendar;
import java.util.List;

import models.CompatibilityStatus;
import models.ConnectionStatus;
import models.DiscoveryStatus;
import models.RegistrationStatus;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import play.templates.JavaExtensions;

import com.emc.storageos.db.client.model.uimodels.ExecutionLog.LogLevel;
import com.emc.storageos.db.client.model.uimodels.ExecutionPhase;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.ServiceItemContainerRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;
import com.emc.vipr.model.sys.ClusterInfo;
import com.google.common.collect.Lists;

import controllers.catalog.Orders.OrderDetails;

/**
 * Extensions for model classes.
 * 
 * @author jonnymiller
 */
public class ModelExtensions extends JavaExtensions {

    public static boolean isErrorMessage(OrderRestRep order) {
        return isError(order) || isRejected(order);
    }

    public static boolean isError(OrderRestRep order) {
        return isStatus(order, OrderStatus.ERROR);
    }

    public static boolean isPending(OrderRestRep order) {
        return isStatus(order, OrderStatus.PENDING);
    }

    public static boolean isScheduled(OrderRestRep order) {
        return isStatus(order, OrderStatus.SCHEDULED);
    }

    public static boolean isExecuting(OrderRestRep order) {
        return isStatus(order, OrderStatus.EXECUTING);
    }

    public static boolean isRejected(OrderRestRep order) {
        return isStatus(order, OrderStatus.REJECTED);
    }
    
    public static boolean isPaused(OrderRestRep order) {
        return isStatus(order, OrderStatus.PAUSED);
    }

    public static boolean isStatus(OrderRestRep order, OrderStatus status) {
        return status.name().equals(order.getOrderStatus());
    }

    public static boolean isError(OrderLogRestRep log) {
        return LogLevel.ERROR.equals(log.getLevel()); // NOSONAR
                                                      // ("Suppressing Sonar violation of Remove this call to "equals"; comparisons between unrelated types always return false.Both are same types.")
    }

    public static boolean isPrecheck(ExecutionStateRestRep state) {
        return ExecutionStatus.PRECHECK.name().equals(state.getExecutionStatus());
    }

    public static boolean isExecute(ExecutionStateRestRep state) {
        return ExecutionStatus.EXECUTE.name().equals(state.getExecutionStatus());
    }

    public static boolean isRollback(ExecutionStateRestRep state) {
        return ExecutionStatus.ROLLBACK.name().equals(state.getExecutionStatus());
    }

    public static boolean isPrecheck(OrderLogRestRep log) {
        return ExecutionPhase.PRECHECK.name().equals(log.getPhase());
    }

    public static boolean isExecute(OrderLogRestRep log) {
        return ExecutionPhase.EXECUTE.name().equals(log.getPhase());
    }

    public static boolean isRollback(OrderLogRestRep log) {
        return ExecutionPhase.ROLLBACK.name().equals(log.getPhase());
    }

    public static boolean isPending(ApprovalRestRep approval) {
        return approval != null && approval.isPending();
    }

    public static boolean isApproved(ApprovalRestRep approval) {
        return approval != null && approval.isApproved();
    }

    public static boolean isRejected(ApprovalRestRep approval) {
        return approval != null && approval.isRejected();
    }

    public static List<ExecutionLogRestRep> getRunningTaskLogs(OrderDetails details) {
        List<ExecutionLogRestRep> logs = Lists.newArrayList();
        if (details.precheckTaskLogs != null) {
            for (ExecutionLogRestRep log : details.precheckTaskLogs) {
                if (log.getElapsed() == null) {
                    logs.add(log);
                }
            }
        }
        if (details.executeTaskLogs != null) {
            for (ExecutionLogRestRep log : details.executeTaskLogs) {
                if (log.getElapsed() == null) {
                    logs.add(log);
                }
            }
        }
        if (details.rollbackTaskLogs != null) {
            for (ExecutionLogRestRep log : details.rollbackTaskLogs) {
                if (log.getElapsed() == null) {
                    logs.add(log);
                }
            }
        }
        return logs;
    }

    public static List<VcenterDataCenterRestRep> datacenters(VcenterRestRep vcenter) {
        return VCenterUtils.getDataCentersInVCenter(vcenter);
    }

    public static List<ClusterRestRep> clusters(VcenterDataCenterRestRep datacenter) {
        return VCenterUtils.getClustersInDataCenter(datacenter);
    }

    public static List<HostRestRep> hosts(VcenterDataCenterRestRep datacenter) {
        return HostUtils.getHostsByDataCenter(datacenter);
    }

    public static List<HostRestRep> hosts(ClusterRestRep cluster) {
        return HostUtils.getHostsByCluster(cluster);
    }

    public static List<IpInterfaceRestRep> ipInterfaces(HostRestRep host) {
        if (host != null) {
            return HostUtils.getIpInterfaces(host.getId());
        }
        return Lists.newArrayList();
    }

    public static List<InitiatorRestRep> initiators(HostRestRep host) {
        if (host != null) {
            return HostUtils.getInitiators(host.getId());
        }
        return Lists.newArrayList();
    }

    public static String wwn(InitiatorRestRep initiator) {
        if (StringUtils.isBlank(initiator.getInitiatorNode())) {
            return initiator.getInitiatorPort();
        }
        else {
            return initiator.getInitiatorNode() + ":" + initiator.getInitiatorPort();
        }
    }

    public static Integer getLun(VolumeRestRep volume, ExportGroupRestRep export) {
        if (volume == null || export == null) {
            return null;
        }
        for (ExportBlockParam exportVolume : export.getVolumes()) {
            if (ObjectUtils.equals(exportVolume.getId(), volume.getId())) {
                return exportVolume.getLun();
            }
        }
        return null;
    }

    public static boolean usesNextExecutionWindow(CatalogServiceRestRep service) {
        return ExecutionWindowRestRep.isNextWindow(service.getDefaultExecutionWindow());
    }

    public static ExecutionWindowRestRep getExecutionWindow(CatalogServiceRestRep service) {
        if (usesNextExecutionWindow(service)) {
            return ExecutionWindowUtils.getActiveOrNextExecutionWindow();
        }
        else if (service.getDefaultExecutionWindow() != null) {
            return ExecutionWindowUtils.getExecutionWindow(service.getDefaultExecutionWindow().getId());
        }
        return null;
    }

    public static boolean isActive(ExecutionWindowRestRep window) {
        return ExecutionWindowUtils.isExecutionWindowActive(window);
    }

    public static long getNextWindowTime(ExecutionWindowRestRep window) {
        Calendar nextTime = ExecutionWindowUtils.calculateNextWindowTime(window);
        return nextTime.getTimeInMillis();
    }

    public static InitiatorRestRep getInitiator(ExportGroupRestRep export, URI id) {
        for (InitiatorRestRep initiator : export.getInitiators()) {
            if (ObjectUtils.equals(id, initiator.getId())) {
                return initiator;
            }
        }
        return null;
    }

    public static InitiatorRestRep getInitiator(ExportGroupRestRep export, ITLRestRep itl) {
        return (itl != null) ? getInitiator(export, itl.getInitiator().getId()) : null;
    }

    public static boolean isISCSI(InitiatorRestRep initiator) {
        return StringUtils.equals(initiator.getProtocol(), "iSCSI");
    }

    public static boolean isFC(InitiatorRestRep initiator) {
        return StringUtils.equals(initiator.getProtocol(), "FC");
    }

    public static boolean isStable(ClusterInfo clusterInfo) {
        return "STABLE".equalsIgnoreCase(clusterInfo.getCurrentState());
    }

    public static boolean isNotStable(ClusterInfo clusterInfo) {
        return !isStable(clusterInfo);
    }

    public static boolean isRegistered(DiscoveredSystemObjectRestRep system) {
        return RegistrationStatus.isRegistered(system.getRegistrationStatus());
    }

    public static boolean isUnregistered(DiscoveredSystemObjectRestRep system) {
        return RegistrationStatus.isUnregistered(system.getRegistrationStatus());
    }

    public static String getDiscoveryStatus(StorageProviderRestRep system) {
        if (DiscoveryStatus.isError(system.getScanStatus())) {
            if (isUnreachable(system)) {
                return MessagesUtils.get("discoveryError.notReachable");
            }
            return system.getLastScanStatusMessage();
        }
        return DiscoveryStatus.getDisplayValue(system.getScanStatus());
    }

    public static String getDiscoveryStatus(DiscoveredSystemObjectRestRep system) {
        if (DiscoveryStatus.isError(system.getDiscoveryJobStatus())) {
            if (isUnreachable(system)) {
                return MessagesUtils.get("discoveryError.notReachable");
            }
            return system.getLastDiscoveryStatusMessage();
        }
        return DiscoveryStatus.getDisplayValue(system.getDiscoveryJobStatus());
    }

    public static boolean isUnreachable(DiscoveredSystemObjectRestRep system) {
        if (system instanceof StorageSystemRestRep) {
            return isUnreachable((StorageSystemRestRep) system);
        }
        else if (system instanceof ProtectionSystemRestRep) {
            return isUnreachable((ProtectionSystemRestRep) system);
        }
        return false;
    }

    public static boolean isUnreachable(StorageSystemRestRep system) {
        return Boolean.TRUE.equals(system.getReachableStatus());
    }

    public static boolean isUnreachable(ProtectionSystemRestRep system) {
        return Boolean.TRUE.equals(system.getReachableStatus());
    }

    public static boolean isUnreachable(StorageProviderRestRep system) {
        return ConnectionStatus.isConnected(system.getConnectionStatus());
    }

    public static Long getDiscoveryDate(StorageProviderRestRep system) {
        return system.getLastScanTime();
    }

    public static Long getDiscoveryDate(DiscoveredSystemObjectRestRep system) {
        return system.getLastDiscoveryRunTime();
    }

    public static String getCompatibilityStatus(StorageProviderRestRep system) {
        return CompatibilityStatus.getDisplayValue(system.getCompatibilityStatus());
    }

    public static String getCompatibilityStatus(DiscoveredSystemObjectRestRep system) {
        return CompatibilityStatus.getDisplayValue(system.getCompatibilityStatus());
    }

    public static Long getLastSuccessfulDiscovery(StorageProviderRestRep system) {
        return system.getSuccessScanTime();
    }

    public static Long getLastSuccessfulDiscovery(DiscoveredSystemObjectRestRep system) {
        return system.getSuccessDiscoveryTime();
    }

    public static List<ServiceItemRestRep> getAllItemsList(ServiceItemContainerRestRep container) {
        List<ServiceItemRestRep> items = Lists.newArrayList();
        for (ServiceItemRestRep item : container.getItems()) {
            if (item.isField()) {
                items.add(item);
            }
            else if (item instanceof ServiceItemContainerRestRep) {
                items.addAll(getAllItemsList((ServiceItemContainerRestRep) item));
            }
        }
        return items;
    }
}
