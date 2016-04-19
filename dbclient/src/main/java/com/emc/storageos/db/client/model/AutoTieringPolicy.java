/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;

import java.net.URI;
import java.util.Set;

/**
 * FAST Policy Objects for VMAX and VNX
 */
@Cf("AutoTieringPolicy")
public class AutoTieringPolicy extends DiscoveredDataObject {
    private String _policyName;
    // indicates whether FAST Policy is Enabled
    private Boolean _policyEnabled;
    // Indicates the Provisioning Type (FAST VP or FAST DP)
    private String _provisioningType;
    // Storage Pools associated with this Policy
    private StringSet _pools;
    // Storage System to which this Policy belongs to
    private URI _storageSystem;
    // Storage Group would get created for each FAST Policy Discovered.
    // Dedicated Storage Group (Thin or thick or All) based on policy provision type
    private String _storageGroupName;
    // EMC VMAX - used to reference the SLO policy setting
    private String _vmaxSLO;
    private String _vmaxWorkload;
    // AverageExpectedResponseTime - VMAX V3 SLO Policy support
    private Double _avgExpectedResponseTime;
    // type of array e.g. vnxBlock, vnxFile, isilon, vmax, netapp
    private String _systemType;

    /** DEFAULT_START_HIGH_THEN_AUTOTIER is the default and recommended policy in VNX */
    public static enum VnxFastPolicy {
        DEFAULT_NO_MOVEMENT,
        DEFAULT_AUTOTIER,
        DEFAULT_HIGHEST_AVAILABLE,
        DEFAULT_LOWEST_AVAILABLE,
        DEFAULT_START_HIGH_THEN_AUTOTIER
    }

    public static enum ProvisioningType {
        ThinlyProvisioned("2"),
        ThicklyProvisioned("3"),
        All("4");

        private String _key;

        private ProvisioningType(String key) {
            _key = key;
        }

        public String getKey() {
            return _key;
        }

        public static String getType(String key) {
            for (ProvisioningType type : values()) {
                if (type.getKey().equalsIgnoreCase(key)) {
                    return type.toString();
                }

            }
            return null;
        }

    }

    // Enum to hold Hitachi TieringPolicies.
    public static enum HitachiTieringPolicy {
        All("0"),
        T1("1"),
        T1_T2("2"),
        T2("3"),
        T2_T3("4"),
        T3("5"),
        Custom1("6"),
        Custom2("7"),
        Custom3("8"),
        Custom4("9"),
        Custom5("10"),
        Custom6("11"),
        Custom7("12"),
        Custom8("13"),
        Custom9("14"),
        Custom10("15"),
        Custom11("16"),
        Custom12("17"),
        Custom13("18"),
        Custom14("19"),
        Custom15("20"),
        Custom16("21"),
        Custom17("22"),
        Custom18("23"),
        Custom19("24"),
        Custom20("25"),
        Custom21("26"),
        Custom22("27"),
        Custom23("28"),
        Custom24("29"),
        Custom25("30"),
        Custom26("31");

        private String _key;
        private static HitachiTieringPolicy[] copyOfValues = values();

        HitachiTieringPolicy(String key) {
            _key = key;
        }

        public String getKey() {
            return _key;
        }

        public static String getType(String key) {
            for (HitachiTieringPolicy type : copyOfValues) {
                if (type.getKey().equalsIgnoreCase(key)) {
                    return type.toString();
                }
            }
            return null;
        }

        public static HitachiTieringPolicy getPolicy(String id) {
            for (HitachiTieringPolicy policyType : copyOfValues) {
                if (policyType.name().equalsIgnoreCase(id)) {
                    return policyType;
                }
            }
            return null;
        }

    }

    /*********************************************************
     * AlternateIDIndex - PolicyName (serialID-policyName) *
     * RelationIndex - Empty *
     *********************************************************/
    public void setPolicyName(String policyName) {
        _policyName = policyName;
        setChanged("policyName");
    }

    @Name("policyName")
    @AlternateId("AltIdIndex")
    public String getPolicyName() {
        return _policyName;
    }

    public void setPools(StringSet pools) {
        _pools = pools;
    }

    @Name("pools")
    @AlternateId("PoolToFASTPolicy")
    public StringSet getPools() {
        return _pools;
    }

    public void addPools(Set<String> pools) {
        if (null != _pools) {
            _pools.clear();
        } else {
            setPools(new StringSet());
        }
        if (!pools.isEmpty()) {
            _pools.addAll(pools);
        }
    }

    public void setStorageSystem(URI storageSystem) {
        this._storageSystem = storageSystem;
        setChanged("storageDevice");
    }

    @Name("storageDevice")
    @RelationIndex(cf = "storageSystemToFASTPolicy", type = StorageSystem.class)
    public URI getStorageSystem() {
        return _storageSystem;
    }

    public void setPolicyEnabled(Boolean policyEnabled) {
        this._policyEnabled = policyEnabled;
        setChanged("policyEnabled");
    }

    @Name("policyEnabled")
    public Boolean getPolicyEnabled() {
        return _policyEnabled;
    }

    public void setProvisioningType(String provisioningType) {
        this._provisioningType = provisioningType;
        setChanged("provisioningType");
    }

    @Name("provisioningType")
    public String getProvisioningType() {
        return _provisioningType;
    }

    public void setStorageGroupName(
            String storageGroupName) {
        _storageGroupName = storageGroupName;
        setChanged("storageGroupName");
    }

    @Name("storageGroupName")
    public String getStorageGroupName() {
        return _storageGroupName;
    }

    @Name("systemType")
    public String getSystemType() {
        return _systemType;
    }

    public void setSystemType(String systemType) {
        _systemType = systemType;
        setChanged("systemType");
    }

    @Name("avgExpectedResponseTime")
    public Double getAvgExpectedResponseTime() {
        return _avgExpectedResponseTime;
    }

    public void setAvgExpectedResponseTime(Double _avgExpectedResponseTime) {
        this._avgExpectedResponseTime = _avgExpectedResponseTime;
        setChanged("avgExpectedResponseTime");
    }

    public void addPool(String poolId) {
        if (_pools == null) {
            _pools = new StringSet();
        }
        _pools.add(poolId);
    }

    @Name("vmaxSLO")
    public String getVmaxSLO() {
        return _vmaxSLO;
    }

    public void setVmaxSLO(String vmaxSLO) {
        this._vmaxSLO = vmaxSLO;
        setChanged("vmaxSLO");
    }

    @Name("vmaxWorkload")
    public String getVmaxWorkload() {
        return _vmaxWorkload;
    }

    public void setVmaxWorkload(String vmaxWorkload) {
        this._vmaxWorkload = vmaxWorkload;
        setChanged("vmaxWorkload");
    }
}
