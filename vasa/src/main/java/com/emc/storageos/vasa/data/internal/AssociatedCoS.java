/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public class AssociatedCoS {
	
	@XmlElement
	private String id;
	
	@XmlElement(name="cos_params")
	private Params cosParams;
	
	
	
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the cosParams
	 */
	public Params getCosParams() {
		return cosParams;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AssociatedCoS [id=");
		builder.append(id);
		builder.append(", cosParams=");
		builder.append(cosParams);
		builder.append("]");
		return builder.toString();
	}

	@XmlRootElement(name="cos_params")
	public static class Params {
		
		@XmlElement(name="cos_param")		
		private List<Param> paramList;

		/**
		 * @return the paramList
		 */
		public List<Param> getParamList() {
			return paramList;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Params [paramList=");
			builder.append(paramList);
			builder.append("]");
			return builder.toString();
		}
		
	}
	
	@XmlRootElement(name="cos_param")
	public static class Param {
		
		@XmlElement(name="name")
		private String name;
		@XmlElement(name="value")
		private String value;
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Param [name=");
			builder.append(name);
			builder.append(", value=");
			builder.append(value);
			builder.append("]");
			return builder.toString();
		}
		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}
		/**
		 * @return the value
		 */
		public String getValue() {
			return value;
		}
		
		
	}
	
}




