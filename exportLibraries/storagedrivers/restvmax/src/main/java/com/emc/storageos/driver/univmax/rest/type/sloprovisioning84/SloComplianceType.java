/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.sloprovisioning84;

public class SloComplianceType {

    // min/max occurs: 0/1
    private Integer slo_stable;
    // min/max occurs: 0/1
    private Integer slo_marginal;
    // min/max occurs: 0/1
    private Integer slo_critical;
    // min/max occurs: 0/1
    private Integer no_slo;

    public Integer getSlo_stable() {
        return slo_stable;
    }

    public Integer getSlo_marginal() {
        return slo_marginal;
    }

    public Integer getSlo_critical() {
        return slo_critical;
    }

    public Integer getNo_slo() {
        return no_slo;
    }
}
