/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import models.StorageProviderTypes;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.smis.StorageProviderCreateParam;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.smis.StorageProviderUpdateParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class StorageProviderUtils {
    public static List<StorageProviderRestRep> getStorageProviders() {
        return getViprClient().storageProviders().getAll();
    }

    public static List<StorageProviderRestRep> getStorageProviders(List<URI> ids) {
        return getViprClient().storageProviders().getByIds(ids);
    }

    public static StorageProviderRestRep getStorageProvider(URI id) {
        try {
            return getViprClient().storageProviders().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static Set<NamedRelatedResourceRep> getConnectedStorageSystems(StorageProviderRestRep provider) {
        return getConnectedStorageSystems(id(provider));
    }

    public static Set<NamedRelatedResourceRep> getConnectedStorageSystems(URI providerId) {
        Set<NamedRelatedResourceRep> results = new TreeSet<NamedRelatedResourceRep>(
                new NamedRelatedResourceComparator());
        results.addAll(getViprClient().storageSystems().listBySmisProvider(providerId));
        return results;
    }

    public static boolean hasStorageSystems(URI id) {
        List<StorageSystemRestRep> storageSystems = getViprClient().storageSystems().getBySmisProvider(id);
        return !storageSystems.isEmpty();
    }

    public static boolean hasStorageSystems(List<URI> ids) {
        for (URI id : ids) {
            if (hasStorageSystems(id)) {
                return true;
            }
        }
        return false;
    }

    public static Task<StorageProviderRestRep> create(String name, String ipAddress, Integer portNumber, String userName,
            String password, Boolean useSSL, String interfaceType, String secondaryUsername, String secondaryPassword,
            String elementManagerURL, String secretKey) {
        StorageProviderCreateParam update = new StorageProviderCreateParam();
        update.setName(name);
        update.setIpAddress(ipAddress);
        update.setPortNumber(portNumber);
        update.setUserName(userName);        
        update.setPassword(password);        
        update.setUseSSL(useSSL == null ? false : useSSL);
        update.setInterfaceType(interfaceType);
        update.setSecondaryUsername(StringUtils.defaultIfEmpty(secondaryUsername, null));
        update.setSecondaryPassword(StringUtils.defaultIfEmpty(secondaryPassword, null));
        update.setElementManagerURL(StringUtils.defaultIfEmpty(elementManagerURL, null));
        update.setSecretKey(StringUtils.defaultIfEmpty(secretKey, null));
        if (StorageProviderTypes.isScaleIOApi(interfaceType)) {
        	update.setUserName(secondaryUsername);
        	update.setPassword(secondaryPassword);
        	update.setSecondaryUsername(null);
        	update.setSecondaryPassword(null);
        }
        return getViprClient().storageProviders().create(update);
    }

    public static StorageProviderRestRep update(URI id, String name, String ipAddress, Integer portNumber,
            String userName, String password, Boolean useSSL, String interfaceType, String secondaryUsername,
            String secondaryPassword, String elementManagerURL, String secretKey) {
        StorageProviderUpdateParam update = new StorageProviderUpdateParam();
        update.setName(name);
        update.setIpAddress(ipAddress);
        update.setPortNumber(portNumber);
        update.setUserName(userName);
        update.setPassword(StringUtils.defaultIfEmpty(password, null));
        update.setUseSSL(useSSL == null ? false : useSSL);
        update.setInterfaceType(interfaceType);
        update.setSecondaryUsername(StringUtils.defaultIfEmpty(secondaryUsername, null));
        update.setSecondaryPassword(StringUtils.defaultIfEmpty(secondaryPassword, null));
        update.setElementManagerURL(StringUtils.defaultIfEmpty(elementManagerURL, null));
        update.setSecretKey(StringUtils.defaultIfEmpty(secretKey, null));
        if (StorageProviderTypes.isScaleIOApi(interfaceType)) {
        	update.setUserName(secondaryUsername);
        	update.setPassword(secondaryPassword);
        	update.setSecondaryUsername(null);
        	update.setSecondaryPassword(null);
        }
        return getViprClient().storageProviders().update(id, update);
    }

    public static void deactivate(URI id) {
        getViprClient().storageProviders().deactivate(id);
    }

    public static void discoverAll() {
        getViprClient().storageProviders().scanAll();
    }
}
