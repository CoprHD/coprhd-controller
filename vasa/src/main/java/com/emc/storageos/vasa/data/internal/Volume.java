/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "volume")
public class Volume {

	public Volume() {

	}

	public Volume(String id) {
		this.id = id;
	}

	@XmlElement
	private String id;

	@XmlElement
	private String name;

	@XmlElement
	private boolean inactive;

	@XmlElement(name = "thinly_provisioned")
	private boolean thinlyProvisioned;

	@XmlElement(name = "protocols")
	private Protocol protocols;

	@XmlElement(name = "storage_controller")
	private String storageController;

	@XmlElement(name = "provisioned_capacity_gb")
	private double provisionedCapacityInGB;

	@XmlElement(name = "requested_capacity_gb")
	private double requestedCapacityInGB;

	@XmlElement(name = "allocated_capacity_gb")
	private double allocatedCapacityInGB;

	@XmlElement(name = "vpool")
	private AssociatedCoS cos;

	@XmlElement(name = "wwn")
	private String WWN;

	private Itls exports;

	private AssociatedPool associatedPool;

	@XmlElement(name = "high_availability_backing_volumes")
	private HighAvailabilityVolumes haVolumes;

	public Itls getExports() {
		return exports;
	}

	public void setExports(Itls exports) {
		this.exports = exports;
	}

	public double getProvisionedCapacityInGB() {
		return provisionedCapacityInGB;
	}

	public boolean isThinlyProvisioned() {
		return thinlyProvisioned;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isInactive() {
		return inactive;
	}

	public Protocol getProtocols() {
		return protocols;
	}

	public String getStorageController() {
		return storageController;
	}

	public double getAllocatedCapacityInGB() {
		return allocatedCapacityInGB;
	}

	public AssociatedCoS getCos() {
		return cos;
	}

	public String getWWN() {
		return WWN;
	}

	public double getRequestedCapacityInGB() {
		return requestedCapacityInGB;
	}

	public HighAvailabilityVolumes getHaVolumeList() {
		return haVolumes;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Volume [id=");
		builder.append(id);
		builder.append(", name=");
		builder.append(name);
		builder.append(", inactive=");
		builder.append(inactive);
		builder.append(", thinlyProvisioned=");
		builder.append(thinlyProvisioned);
		builder.append(", protocols=");
		builder.append(protocols);
		builder.append(", storageController=");
		builder.append(storageController);
		builder.append(", provisionedCapacityInGB=");
		builder.append(provisionedCapacityInGB);
		builder.append(", requestedCapacityInGB=");
		builder.append(requestedCapacityInGB);
		builder.append(", allocatedCapacity=");
		builder.append(allocatedCapacityInGB);
		builder.append(", cos=");
		builder.append(cos);
		builder.append(", WWN=");
		builder.append(WWN);
		builder.append(", exports=");
		builder.append(exports);
		builder.append(", associatedPool=");
		builder.append(associatedPool);
		builder.append(", HAVolumes=");
		builder.append(haVolumes);
		builder.append("]");
		return builder.toString();
	}

	@XmlRootElement(name = "high_availability_backing_volumes")
	public static class HighAvailabilityVolumes {

		@XmlElement(name = "high_availability_backing_volume")
		private List<HighAvailabilityVolume> haVolumeList;

		public List<HighAvailabilityVolume> getHaVolumeList() {
			return haVolumeList;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("HighAvailabilityVolumes [" + haVolumeList + "]");
			return builder.toString();
		}

	}

	@XmlRootElement(name = "high_availability_backing_volume")
	public static class HighAvailabilityVolume {

		@XmlElement(name = "id")
		private String id;

		@XmlElement(name = "link")
		private Link link;

		public String getId() {
			return id;
		}

		public Link getLink() {
			return link;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("HighAvailabilityVolume [id=");
			builder.append(id);
			builder.append(", link=");
			builder.append(link);
			builder.append("]");
			return builder.toString();
		}

	}

	@XmlRootElement(name = "related_storage_pool")
	public static class AssociatedPool {

		@XmlElement(name = "storage_pool")
		private Pool storagepool;

		public Pool getStoragepool() {
			return storagepool;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("AssociatedPool [storagepool=");
			builder.append(storagepool);
			builder.append("]");
			return builder.toString();
		}

		@XmlRootElement(name = "storage_pool")
		public static class Pool {

			@XmlElement(name = "id")
			private String id;

			@XmlElement(name = "name")
			private String name;
			
			@XmlElement(name = "link")
			private Link link;
			

			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("Pool [id=");
				builder.append(id);
				builder.append(", name=");
				builder.append(name);
				builder.append(", link=");
				builder.append(link);
				builder.append("]");
				return builder.toString();
			}

			public String getId() {
				return id;
			}

			public String getName() {
				return name;
			}

			public Link getLink() {
				return link;
		}
			
	}
	}

	@XmlRootElement(name = "itls")
	public static class Itls {

		@XmlElement(name = "itl")
		private List<Itl> itls;

		public List<Itl> getItls() {
			return itls;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Itls [itls=");
			builder.append(itls);
			builder.append("]");
			return builder.toString();
		}

		@XmlRootElement(name = "itl")
		public static class Itl {
		    @XmlElement
		    private String hlu;
			@XmlElement(name = "device")
			private Device device;
			@XmlElement(name = "initiator")
			private Initiator initiator;
			@XmlElement(name = "target")
		    private Target target;

			public Target getTarget() {
				return target;
			}

			public String getHlu() {
				return hlu;
			}

			public Device getDevice() {
				return device;
			}

			public Initiator getInitiator() {
				return initiator;
			}

			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("Itl [hlu=");
				builder.append(hlu);
				builder.append(", device=");
				builder.append(device);
				builder.append(", initiator=");
				builder.append(initiator);
				builder.append(", target=");
				builder.append(target);
				builder.append("]");
				return builder.toString();
			}

			@XmlRootElement(name = "device")
			public static class Device {
				@XmlElement(name = "id")
				private String id;

				@XmlElement(name = "wwn")
				private String wwn;

				@Override
				public String toString() {
					StringBuilder builder = new StringBuilder();
					builder.append("Device [id=");
					builder.append(id);
					builder.append(", wwn=");
					builder.append(wwn);
					builder.append("]");
					return builder.toString();
				}

				public String getId() {
					return id;
				}

				public String getWwn() {
					return wwn;
				}

			}

			@XmlRootElement(name = "initiator")
			public static class Initiator {

				@XmlElement(name = "id")
				private String id;

				@XmlElement(name = "port")
				private String port;

				@Override
				public String toString() {
					StringBuilder builder = new StringBuilder();
					builder.append("Initiator [id=");
					builder.append(id);
					builder.append(", port=");
					builder.append(port);
					builder.append("]");
					return builder.toString();
				}

				public String getId() {
					return id;
				}

				public String getPort() {
					return port;
				}

			}

			@XmlRootElement(name = "target")
			public static class Target {

				@XmlElement(name = "id")
				private String id;

				@XmlElement(name = "port")
				private String port;

				@XmlElement(name = "link")
				private Link link;

				@Override
				public String toString() {
					StringBuilder builder = new StringBuilder();
					builder.append("Target [id=");
					builder.append(id);
					builder.append(", port=");
					builder.append(port);
					builder.append(", link=");
					builder.append(link);
					builder.append("]");
					return builder.toString();
				}

				public String getId() {
					return id;
				}

				public String getPort() {
					return port;
				}

				public Link getLink() {
					return link;
				}

			}

		}

	}

}

