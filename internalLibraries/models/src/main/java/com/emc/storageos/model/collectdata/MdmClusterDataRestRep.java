/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

import java.util.Arrays;

/**
 * MDM Cluster attributes
 * 
 */
public class MdmClusterDataRestRep {
	private String clusterState;
	private String id;
	private String goodNodesNum;
	private TieBreakersDataRestRep[] tieBreakers;
	private String name;
	private SlavesDataRestRep[] slaves;
	private String goodReplicasNum;
	private MasterDataRestRep master;
	private String clusterMode;

	public String getClusterState ()
	{
		return clusterState;
	}

	public void setClusterState (String clusterState)
	{
		this.clusterState = clusterState;
	}

	public String getId ()
	{
		return id;
	}

	public void setId (String id)
	{
		this.id = id;
	}

	public String getGoodNodesNum ()
	{
		return goodNodesNum;
	}

	public void setGoodNodesNum (String goodNodesNum)
	{
		this.goodNodesNum = goodNodesNum;
	}


	public TieBreakersDataRestRep[] getTieBreakers () {
		if(null == tieBreakers){
			return null;
		}
		return Arrays.copyOf(tieBreakers,tieBreakers.length);
	}

	public void setTieBreakers (TieBreakersDataRestRep[] tieBreakers) {
		if(null == tieBreakers){
			return;
		}
		this.tieBreakers = Arrays.copyOf(tieBreakers,tieBreakers.length);
	}

	public String getName ()
	{
		return name;
	}

	public void setName (String name)
	{
		this.name = name;
	}

	public SlavesDataRestRep[] getSlaves () {
		if(null == slaves){
			return null;
		}
		return Arrays.copyOf(slaves,slaves.length);
	}

	public void setSlaves (SlavesDataRestRep[] slaves) {
		if(null == slaves){
			return;
		}
		this.slaves = Arrays.copyOf(slaves,slaves.length);
	}

	public String getGoodReplicasNum ()
	{
		return goodReplicasNum;
	}

	public void setGoodReplicasNum (String goodReplicasNum)
	{
		this.goodReplicasNum = goodReplicasNum;
	}

	public MasterDataRestRep getMaster ()
	{
		return master;
	}

	public void setMaster (MasterDataRestRep master)
	{
		this.master = master;
	}

	public String getClusterMode ()
	{
		return clusterMode;
	}

	public void setClusterMode (String clusterMode)
	{
		this.clusterMode = clusterMode;
	}

}
