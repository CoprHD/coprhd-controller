/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
 package com.emc.storageos.db.client.model;
 /**
  * Quality of Service data object (for dynamic vpool)
  * 
  * QoS represents a set of attributes which can be changed dynamically
  */
 /**
  * @author degwea
  */
 @Cf("QoS")
 public class QoS extends DataObject {
    // service type
    private String _type;
    // fast Policy Name
    private String _autoTierPolicyName;
    // Thin or Thick or ThinandThick
    // combination of provisioningType & fast indicates FAST_VP or FAST_DP
    // Thin & Fast_ON --> FAST_VP
    private String _provisioningType;
    // percentage to specify thinVolumePreAllocateSize during provisioning.
    private Integer _thinVolumePreAllocationPercentage;
    // Maximum number of native snapshots allowed (0 == disabled, -1 == unlimited)
    private Integer _maxNativeSnapshots;
    // It indicates whether virtual pool supports schedule snapshot
    private Boolean scheduleSnapshot = false;
    // Maximum number of native continuous copies allowed (0 == disabled, -1 == unlimited)
    private Integer _maxNativeContinuousCopies;

}
