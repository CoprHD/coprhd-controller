/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource;

/**
 * @author fengs5
 *
 */
public enum EmulationType {

    FBA("FBA"),
    CELERRA_FBA("CELERRA_FBA"),
    VME_512_FBA("VME_512_FBA"),
    CKD("CKD"),
    CKD_3390("CKD-3390"),
    CKD_3380("CKD-3380"),
    AS_400_M2107_A02("AS/400_M2107_A02"),
    AS_400_M2107_A04("AS/400_M2107_A04"),
    AS_400_M2107_A05("AS/400_M2107_A05"),
    AS_400_M2107_A06("AS/400_M2107_A06"),
    AS_400_M2107_A07("AS/400_M2107_A07"),
    AS_400_M2107_A82("AS/400_M2107_A82"),
    AS_400_M2107_A84("AS/400_M2107_A84"),
    AS_400_M2107_A85("AS/400_M2107_A85"),
    AS_400_M2107_A86("AS/400_M2107_A86"),
    AS_400_M2107_A87("AS/400_M2107_A87"),
    AS_400_M2107_099("AS/400_M2107_099"),
    AS_400_M2107_050("AS/400_M2107_050"),
    AS_400_M4326_50("AS/400_M4326_50"),
    AS_400_M4327_50("AS/400_M4327_50"),
    AS_400_M4328_50("AS/400_M4328_50");

    String value;

    EmulationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
