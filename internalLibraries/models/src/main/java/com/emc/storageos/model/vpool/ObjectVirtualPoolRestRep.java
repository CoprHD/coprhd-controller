/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "object_vpool")
public class ObjectVirtualPoolRestRep extends VirtualPoolCommonRestRep {
    private String writeTraffic;
    private String readTraffic;
    private String userDataPendingRepl;
    public String metaDataPendingRepl;
    private String dataPendingXor;

    public ObjectVirtualPoolRestRep() {
    }

    @XmlElement(name = "write_trafiic", required = false)
	public String getWriteTraffic() {
		return writeTraffic;
	}

	public void setWriteTraffic(String writeTraffic) {
		this.writeTraffic = writeTraffic;
	}

	 @XmlElement(name = "read_trafiic", required = false)
	public String getReadTraffic() {
		return readTraffic;
	}

	public void setReadTraffic(String readTraffic) {
		this.readTraffic = readTraffic;
	}

	 @XmlElement(name = "userDataPendingRepl", required = false)
	public String getUserDataPendingRepl() {
		return userDataPendingRepl;
	}

	public void setUserDataPendingRepl(String userDataPendingRepl) {
		this.userDataPendingRepl = userDataPendingRepl;
	}
	
	 @XmlElement(name = "metaDataPendingRepl", required = false)
	public String getMetaDataPendingRepl() {
			return metaDataPendingRepl;
		}

		public void setMetaDataPendingRepl(String metaDataPendingRepl) {
			this.metaDataPendingRepl = metaDataPendingRepl;
		}

	@XmlElement(name = "dataPendingXor", required = false)
	public String getDataPendingXor() {
		return dataPendingXor;
	}

	public void setDataPendingXor(String dataPendingXor) {
		this.dataPendingXor = dataPendingXor;
	}
}
