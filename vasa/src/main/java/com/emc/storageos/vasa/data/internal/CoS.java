/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "CoS")
public class CoS {

	public CoS() {

	}

	public CoS(String id) {
		this.id = id;
	}

	@XmlElement
	private String id;

	@XmlElement
	private boolean inactive;

	@XmlElement(name = "name")
	private String label;

	@XmlElement
	private String description;

	@XmlElement
	private String allocateOnDemand;

	@XmlElement
	private int maxSnapshots;

	@XmlElement
	private boolean multiVolumeConsistency;

	@XmlElement
	private int numPaths;

	@XmlElement
	private String performance;

	@XmlElement
	private Protocol protocols;

	@XmlElement
	private int resiliencyMin;

	@XmlElement
	private int resiliencyMax;

	@XmlElement
	private String spaceEfficiency;

	@XmlElement
	private String type;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the inactive
	 */
	public boolean isInactive() {
		return inactive;
	}

	/**
	 * @return the label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return the allocateOnDemand
	 */
	public String getAllocateOnDemand() {
		return allocateOnDemand;
	}

	/**
	 * @return the maxSnapshots
	 */
	public int getMaxSnapshots() {
		return maxSnapshots;
	}

	/**
	 * @return the multiVolumeConsistency
	 */
	public boolean isMultiVolumeConsistency() {
		return multiVolumeConsistency;
	}

	/**
	 * @return the numPaths
	 */
	public int getNumPaths() {
		return numPaths;
	}

	/**
	 * @return the performance
	 */
	public String getPerformance() {
		return performance;
	}

	/**
	 * @return the protocols
	 */
	public Protocol getProtocols() {
		return protocols;
	}

	/**
	 * @return the resiliencyMin
	 */
	public int getResiliencyMin() {
		return resiliencyMin;
	}

	/**
	 * @return the resiliencyMax
	 */
	public int getResiliencyMax() {
		return resiliencyMax;
	}

	/**
	 * @return the spaceEfficiency
	 */
	public String getSpaceEfficiency() {
		return spaceEfficiency;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CoS [id=");
		builder.append(id);
		builder.append(", inactive=");
		builder.append(inactive);
		builder.append(", label=");
		builder.append(label);
		builder.append(", description=");
		builder.append(description);
		builder.append(", allocateOnDemand=");
		builder.append(allocateOnDemand);
		builder.append(", maxSnapshots=");
		builder.append(maxSnapshots);
		builder.append(", multiVolumeConsistency=");
		builder.append(multiVolumeConsistency);
		builder.append(", numPaths=");
		builder.append(numPaths);
		builder.append(", performance=");
		builder.append(performance);
		builder.append(", protocols=");
		builder.append(protocols);
		builder.append(", resiliencyMin=");
		builder.append(resiliencyMin);
		builder.append(", resiliencyMax=");
		builder.append(resiliencyMax);
		builder.append(", spaceEfficiency=");
		builder.append(spaceEfficiency);
		builder.append(", type=");
		builder.append(type);
		builder.append("]");
		return builder.toString();
	}

	@XmlRootElement(name = "cos")
	public static class CoSElement {

		@XmlElement(name = "id")
		private String id;

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
			builder.append("CoSElement [id=");
			builder.append(id);
			builder.append("]");
			return builder.toString();
		}
	}

	@XmlRootElement(name = "vpool_list")
	public static class CoSList {

		@XmlElement(name = "virtualpool")
		private List<CoSElement> cosElements;

		public List<CoSElement> getCosElements() {
			return cosElements;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CoSList [cosElements=");
			builder.append(cosElements);
			builder.append("]");
			return builder.toString();
		}
	}

	public String getDetail() {
		String detail = "";
		detail += "CoS:";
		detail += "Protocols: " + protocols;
		detail += ", Resiliency: (" + resiliencyMin + ":" + resiliencyMax + ")";
		detail += ", Number of paths: " + numPaths;
		return detail;
	}

	@XmlRootElement(name = "block_vpool")
	public static class BlockCoS extends CoS {

		@Override
		public String toString() {
			return "BlockCoS [" + super.toString() + "]";
		}

	}
	
	@XmlRootElement(name = "file_vpool")
	public static class FileCoS extends CoS {

		@Override
		public String toString() {
			return "FileCoS [" + super.toString() + "]";
		}

	}
}


