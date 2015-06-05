/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import util.MessagesUtils;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.google.common.collect.Lists;

public class DiscoveryStatusUtils {
    private static final String UNREACHABLE = "InfrastructureErrors.NOT_REACHABLE";
    private static final String ERROR = "ERROR";

    public static Long getLastDiscoveryDate(DiscoveredSystemObjectRestRep data) {
        return data.getLastDiscoveryRunTime();
    }

    public static Long getLastDiscoveryDate(StorageProviderRestRep data) {
        return data.getLastScanTime();
    }

    public static String getDiscoveryStatus(DiscoveredSystemObjectRestRep data) {
        return data.getDiscoveryJobStatus();
    }

    public static String getDiscoveryStatus(StorageProviderRestRep data) {
        return data.getScanStatus();
    }

    public static String getDiscoveryMessage(StorageProviderRestRep data) {
        return data.getLastScanStatusMessage();
    }

    public static String getDiscoveryMessage(DiscoveredSystemObjectRestRep data) {
        return data.getLastDiscoveryStatusMessage();
    }

    public static String getErrorSummary(DiscoveredSystemObjectRestRep data) {
        List<String> messages = Lists.newArrayList();
        if (DiscoveryStatus.isError(data.getDiscoveryJobStatus())) {
            messages.add(DiscoveryStatus.getDisplayValue(data.getDiscoveryJobStatus()));
        }
        if (CompatibilityStatus.isIncompatible(data.getCompatibilityStatus())) {
            messages.add(CompatibilityStatus.getDisplayValue(data.getCompatibilityStatus()));
        }
        if (isUnreachable(data)) {
            messages.add(MessagesUtils.get(UNREACHABLE));
        }
        return StringUtils.join(messages, "; ");
    }

    private static boolean isUnreachable(DiscoveredSystemObjectRestRep data) {
        if (data instanceof StorageSystemRestRep) {
            Boolean reachable = ((StorageSystemRestRep) data).getReachableStatus();
            return Boolean.FALSE.equals(reachable);
        }
        else if (data instanceof ProtectionSystemRestRep) {
            Boolean reachable = ((ProtectionSystemRestRep) data).getReachableStatus();
            return Boolean.FALSE.equals(reachable);
        }
        return false;
    }

    public static String getErrorSummary(StorageProviderRestRep data) {
        if (DiscoveryStatus.isError(data.getScanStatus())) {
            List<String> messages = Lists.newArrayList();
            messages.add(DiscoveryStatus.getDisplayValue(data.getScanStatus()));
            if (ConnectionStatus.isDisconnected(data.getConnectionStatus())) {
                messages.add(ConnectionStatus.getDisplayValue(data.getConnectionStatus()));
            }
            if (CompatibilityStatus.isIncompatible(data.getCompatibilityStatus())) {
                messages.add(CompatibilityStatus.getDisplayValue(data.getCompatibilityStatus()));
            }
            return StringUtils.join(messages, "; ");
        }
        else {
            return null;
        }
    }

    public static String getErrorDetails(DiscoveredSystemObjectRestRep data) {
        if (DiscoveryStatus.isError(getDiscoveryStatus(data))) {
            return data.getLastDiscoveryStatusMessage();
        }
        else {
            return null;
        }
    }

    public static String getErrorDetails(StorageProviderRestRep data) {
        if (DiscoveryStatus.isError(getDiscoveryStatus(data))) {
            return data.getLastScanStatusMessage();
        }
        else {
            return null;
        }
    }
    
    public static String getCompatibilityStatus(DiscoveredSystemObjectRestRep data) {
    	return data.getCompatibilityStatus();
    }
    
    public static String getCompatibilityStatus(StorageProviderRestRep data) {
    	return data.getCompatibilityStatus();
    }
}
