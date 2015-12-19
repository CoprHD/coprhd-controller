/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.emc.storageos.coordinator.client.model.Constants.*;

import com.emc.storageos.coordinator.client.service.PropertyInfoUtil;
import com.emc.storageos.coordinator.exceptions.DecodingException;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;

import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.model.property.PropertyMetadata;

import static com.emc.storageos.model.property.PropertyConstants.ENCRYPTEDSTRING;

/**
 * PropertyInfoExt is only published as a shared object
 * According to CoordinatorClassInfo's requirement, only id, kind are necessary.
 * To comply with other similar classes, we gave it a dummy attribute as "targetInfoExt"
 */
public class PropertyInfoExt extends PropertyInfoRestRep implements CoordinatorSerializable {
    public static final String CONFIG_VERSION = "config_version";
    public static final String CONNECTEMC_TRANSPORT = "system_connectemc_transport";
    // property constants
    public static final String ENCODING_SEPARATOR = "\0";
    public static final String ENCODING_EQUAL = "=";
    public static final String ENCODING_NEWLINE = "\n";
    public static final String TARGET_PROPERTY = "upgradetargetpropertyoverride";
    public static final String TARGET_PROPERTY_ID = "global";
    public static final String TARGET_INFO = "targetInfo";

    private static final List<String> SECRET_KEY_PROPS = new ArrayList<>(Arrays.asList(
            "ssh_host_ecdsa_key",
            "ssh_host_dsa_key",
            "ssh_host_rsa_key",
            "root_id_rsa",
            "root_id_dsa",
            "root_id_ecdsa",
            "storageos_id_rsa",
            "storageos_id_dsa",
            "storageos_id_ecdsa",
            "svcuser_id_rsa",
            "svcuser_id_dsa",
            "svcuser_id_ecdsa"
            ));

    public PropertyInfoExt() {
    }

    public PropertyInfoExt(Map<String, String> properties) {
        super(properties);
    }

    public PropertyInfoExt(String[] properties) {
        setProperties(PropertyInfoUtil.splitKeyValue(properties));
    }

    @Override
    public String toString() {
        return toString(true);
    }

    /**
     * 
     * @param withMask if true, replace all encrypted string with HIDDEN_TEXT_MASK,
     *            otherwise always print the real content.
     * @return
     */
    public String toString(boolean withMask) {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            sb.append(entry.getKey());
            sb.append(ENCODING_EQUAL);
            // Hide encrypted string in audit log
            if (withMask && (PropertyInfoExt.isEncryptedProperty(entry.getKey()) ||
                    PropertyInfoExt.isSecretProperty(entry.getKey()))) {
                sb.append(HIDDEN_TEXT_MASK);
            } else {
                sb.append(entry.getValue());
            }
            sb.append(ENCODING_NEWLINE);
        }
        return sb.toString();
    }

    public boolean hasRebootProperty() {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();

        if (getProperties() == null || metadata == null) {
            return false;
        }

        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            final String key = entry.getKey();
            final PropertyMetadata propertyMetadata = metadata.get(key);
            if (propertyMetadata != null
                    && propertyMetadata.getRebootRequired() != null
                    && propertyMetadata.getRebootRequired().booleanValue()) {
                return true;
            }
        }

        return false;
    }

    public boolean hasReconfigProperty() {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();

        if (getProperties() == null || metadata == null) {
            return false;
        }

        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            final String key = entry.getKey();
            final PropertyMetadata propertyMetadata = metadata.get(key);
            if (propertyMetadata != null
                    && propertyMetadata.getReconfigRequired() != null
                    && propertyMetadata.getReconfigRequired().booleanValue()) {
                return true;
            }
        }
        return false;
    }

    public List<String> getNotifierTags() {
        return getNotifierTags(false);
    }

    public List<String> getNotifierTags(boolean forReconfig) {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();
        List<String> ret = new ArrayList<>();

        if (getProperties() == null || metadata == null) {
            return ret;
        }

        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            final String key = entry.getKey();
            final PropertyMetadata propertyMetadata = metadata.get(key);
            if (propertyMetadata != null
                    && propertyMetadata.getNotifiers() != null) {
                // skip those properties that have notifiters but don't require reconfig
                if (forReconfig && (propertyMetadata.getReconfigRequired() == null ||
                        !propertyMetadata.getReconfigRequired())) {
                    continue;
                }

                String[] notifierTags = propertyMetadata.getNotifiers();
                // TODO: Note that the ordering is not deterministic across properties now
                for (String notifierTag : notifierTags) {
                    if (!ret.contains(notifierTag)) {
                        ret.add(notifierTag);
                    }
                }
            }
        }
        return ret;
    }

    public boolean hasReconfigAttributeWithoutNotifiers() {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();
        List<String> ret = new ArrayList<>();

        if (getProperties() == null || metadata == null) {
            return true; // call the old-fashion reconfig() in this case
        }

        for (Map.Entry<String, String> entry : getProperties().entrySet()) {
            final String key = entry.getKey();
            final PropertyMetadata propertyMetadata = metadata.get(key);
            if (propertyMetadata != null
                    && propertyMetadata.getReconfigRequired() != null
                    && propertyMetadata.getReconfigRequired().booleanValue()
                    && (propertyMetadata.getNotifiers() == null
                    || propertyMetadata.getNotifiers().length == 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given property is encryptedstring.
     * 
     * @param key property name
     * @return True if yes.
     */
    public static boolean isEncryptedProperty(String key) {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();

        if (metadata != null) {
            PropertyMetadata propertyMetadata = metadata.get(key);
            // check property type
            if (propertyMetadata != null && ENCRYPTEDSTRING.equals(propertyMetadata.getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSecretProperty(String key) {
        return SECRET_KEY_PROPS.contains(key);
    }

    @Override
    public String encodeAsString() {
        final StringBuilder s = new StringBuilder();
        Map<String, String> targetProps = getAllProperties();
        for (Map.Entry<String, String> entry : targetProps.entrySet()) {
            s.append(entry.getKey());
            s.append(ENCODING_EQUAL);
            s.append(entry.getValue());
            s.append(ENCODING_SEPARATOR);
        }

        return s.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public PropertyInfoExt decodeFromString(String infoStr) throws DecodingException {
        try {
            PropertyInfo targetProps = constructPropertyObj(infoStr);
            if (targetProps != null) {
                return new PropertyInfoExt(targetProps.getAllProperties());
            }

            return null;
        } catch (Exception e) {
            throw CoordinatorException.fatals.decodingError(e.toString());
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(TARGET_PROPERTY_ID, TARGET_PROPERTY, "targetInfoExt");
    }

    /**
     * Method used to construct a property object from property string
     * 
     * @param stateStr Property string
     * @return Property object decoded from string
     * @throws Exception
     */
    public PropertyInfo constructPropertyObj(String stateStr) throws Exception {
        if (stateStr != null) {
            final String[] strings = stateStr.split(ENCODING_SEPARATOR);
            if (strings.length == 0) {
                return new PropertyInfo();
            }

            return new PropertyInfo(PropertyInfoUtil.splitKeyValue(strings));
        }
        return null;
    }
}
