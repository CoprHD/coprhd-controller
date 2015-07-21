/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "tenant_info")
public class Tenant {
	@XmlElement(name = "id")
	String id;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Tenant [id=").append(id).append("]");
		return builder.toString();
	}

	@XmlRootElement(name = "tenant_info")
	public static class TenantListElement {

		@XmlElement
		protected String id;

		@XmlElement
		protected String name;

		/**
		 * @return the id
		 */
		public String getId() {
			return id;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TenantListElement [id=").append(id)
					.append(", name=").append(name).append("]");
			return builder.toString();
		}
		
		
	}

	@XmlRootElement(name = "tenant")
	public static class TenantDetail extends TenantListElement {

		@XmlElement
		private boolean inactive;

		@XmlElement
		private String description;

		@XmlElement(name = "enterprise-suffix")
		private String enterpriseSuffix;

		@XmlElement(name = "parent-tenant")
		private String parentTenant;

		/**
		 * @return the inactive
		 */
		public boolean isInactive() {
			return inactive;
		}

		/**
		 * @return the description
		 */
		public String getDescription() {
			return description;
		}

		/**
		 * @return the enterpriseSuffix
		 */
		public String getEnterpriseSuffix() {
			return enterpriseSuffix;
		}

		/**
		 * @return the parentTenant
		 */
		public String getParentTenant() {
			return parentTenant;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TenantDetail [id=").append(id)
					.append(", inactive=").append(inactive).append(", name=")
					.append(name).append(", description=").append(description)
					.append(", enterpriseSuffix=").append(enterpriseSuffix)
					.append(", parentTenant=").append(parentTenant).append("]");
			return builder.toString();
		}

	}

	@XmlRootElement(name = "subtenants")
	public static class SubTenantList {

		private ArrayList<TenantListElement> listElement = new ArrayList<Tenant.TenantListElement>();

		/**
		 * @return the listElement
		 */
		@XmlElement(name="subtenant")
		public ArrayList<TenantListElement> getListElement() {
			return listElement;
		}

	}

}

