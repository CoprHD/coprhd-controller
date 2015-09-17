/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import java.io.Serializable;
import java.util.List;

// Each RP copy has a list of Journal volumes and a name
@SuppressWarnings("serial")
public class GetCopyResponse implements Serializable {
    private String name;
    private List<GetVolumeResponse> journals;
    private boolean production;

    public GetCopyResponse() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<GetVolumeResponse> getJournals() {
        return journals;
    }

    public void setJournals(List<GetVolumeResponse> journals) {
        this.journals = journals;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nCopy: " + name);
        sb.append("\nProduction: " + production);
        if (journals != null) {
            sb.append("\nJournals:");
            for (GetVolumeResponse volume : journals) {
                sb.append(volume.toString());
            }
        }
        return sb.toString();
    }

}
