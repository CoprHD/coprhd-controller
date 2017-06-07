package com.emc.storageos.model.block;


public class SRMVolumeInfo {
    
    private String volumeWWN;
    private String poolPath;
    private String provisioningType;
    
    public enum Type {
        REGULAR,
        SOURCE,
        TARGET
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
    

}
