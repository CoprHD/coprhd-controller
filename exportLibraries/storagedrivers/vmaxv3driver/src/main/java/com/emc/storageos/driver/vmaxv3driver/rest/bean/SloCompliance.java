/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class(nested) for "sloprovisioning/symmetrix" GET method JSON result deserialization.
 *
 * Created by gang on 6/23/16.
 */
public class SloCompliance {
    private Integer slo_marginal;
    private Integer slo_stable;
    private Integer slo_critical;

    @Override
    public String toString() {
        return "SloCompliance{" +
            "slo_marginal=" + slo_marginal +
            ", slo_stable=" + slo_stable +
            ", slo_critical=" + slo_critical +
            '}';
    }

    public Integer getSlo_marginal() {
        return slo_marginal;
    }

    public void setSlo_marginal(Integer slo_marginal) {
        this.slo_marginal = slo_marginal;
    }

    public Integer getSlo_stable() {
        return slo_stable;
    }

    public void setSlo_stable(Integer slo_stable) {
        this.slo_stable = slo_stable;
    }

    public int getSlo_critical() {
        return slo_critical;
    }

    public void setSlo_critical(Integer slo_critical) {
        this.slo_critical = slo_critical;
    }
}
