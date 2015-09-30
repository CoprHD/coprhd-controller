/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

public class UnManagedDiscoveredObject extends DataObject {
    // Unique Bourne identifier.
    private String _nativeGuid;

    private StringSet supportedVpoolUris;

    public enum ExportType {
        EXPORTED, UNEXPORTED;

        private static final ExportType[] exportTypes = values();

        public static ExportType lookup(final String name) {
            for (ExportType value : exportTypes) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    @AlternateId("StandAloneObjectsAltIdIdnex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    public static enum SupportedProvisioningType {
        THIN("TRUE"),
        THICK("FALSE");

        private String _provisioningType;

        SupportedProvisioningType(String provisioningType) {
            _provisioningType = provisioningType;
        }

        public String getProvisioningTypeValue() {
            return _provisioningType;
        }

        public static String getProvisioningType(String isThinlyProvisioned) {
            for (SupportedProvisioningType provisioningType : values()) {
                if (provisioningType.getProvisioningTypeValue().equalsIgnoreCase(isThinlyProvisioned)) {
                    return provisioningType.toString();
                }
            }
            return null;
        }
    }

    @IndexByKey
    @AlternateId("SupportedVPoolUriIndex")
    @Name("supportedVpoolUris")
    public StringSet getSupportedVpoolUris() {
        if (null == supportedVpoolUris) {
            this.setSupportedVpoolUris(new StringSet());
        }
        return supportedVpoolUris;
    }

    public void setSupportedVpoolUris(StringSet supportedVpoolUris) {
        this.supportedVpoolUris = supportedVpoolUris;
    }
}
