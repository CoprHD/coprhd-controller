/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "protocols")
public class Protocol {

	@XmlElement
	private List<String> protocol;

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
        public List<String> getProtocol() {
		return protocol;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Protocol [protocol=");
		builder.append(protocol);
		builder.append("]");
		return builder.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	

	

	

}
