/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.util.List;

// Each RP copy has a list of Journal volumes and a name
@SuppressWarnings("serial")
public class CreateCopyParams implements Serializable {
    private String name;
    private List<CreateVolumeParams> journals;

    public  CreateCopyParams() {
    }
    
    public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<CreateVolumeParams> getJournals() {
		return journals;
	}

	public void setJournals(List<CreateVolumeParams> journals) {
		this.journals = journals;
	}

	@Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nCopy: " + name);
        if (journals!=null) {
            sb.append("\nJournals:");
            for (CreateVolumeParams volume : journals) {
                sb.append(volume.toString());
            }
        }
        return sb.toString();
    }
}
