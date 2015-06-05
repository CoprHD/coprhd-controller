/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.placement;

/**
 * Enumeration used to identify the number of volumes created
 * on each site, for each type of RP protection configuration.
 */
public enum RPConfiguration {
	CDP(0),
	CRR(1),
	CLR(2),
	UNKNOWN(3);
	
	private long _localReplicaVolumeCount = 0;
	private long _localReplicaJournalVolumeCount = 0;
	private long _remoteReplicaVolumeCount = 0;
	private long _remoteReplicaJournalVolumeCount = 0;

	/**
	 * Default production journal size.  Always this
	 * number of production journal volumes created
	 * regardless of the number of production volumes.
	 */
	private final long _productionJournalVolumeCount = 1;
	
	private RPConfiguration(int configType) {
		switch (configType) {
			case 0:
				//CDP
				_localReplicaVolumeCount = 1;
				_localReplicaJournalVolumeCount = 1;
				break;
			case 1:
				// CRR
				_remoteReplicaVolumeCount = 1;
				_remoteReplicaJournalVolumeCount = 1;
				break;
			case 2:
				// CLR
				_localReplicaVolumeCount = 1;
				_localReplicaJournalVolumeCount = 1;
				_remoteReplicaVolumeCount = 1;
				_remoteReplicaJournalVolumeCount = 1;			
				break;
		}
	}
	
	/**
	 * Gets the number of local replica volumes required for the 
	 * given RP configuration. Dependent on the number of production
	 * volumes being created.
	 * @param resourceCount the number of production volumes to create
	 * @return
	 */
	public long getLocalReplicaVolumeCount(int resourceCount) {
		return (_localReplicaVolumeCount * resourceCount);
	}

	/**
	 * Gets the number of local replica journal volumes required for the 
	 * given RP configuration.
	 * @return
	 */
	public long getLocalReplicaJournalVolumeCount() {
		return _localReplicaJournalVolumeCount;
	}

	/**
	 * Gets the number of remote replica volumes required for the 
	 * given RP configuration. Dependent on the number of production
	 * volumes being created.
	 * @param resourceCount the number of production volumes to create
	 * @return
	 */
	public long getRemoteReplicaVolumeCount(int resourceCount) {
		return (_remoteReplicaVolumeCount * resourceCount);
	}

	public long getRemoteReplicaJournalVolumeCount() {
		return _remoteReplicaJournalVolumeCount;
	}

	public long getProductionJournalVolumeCount() {
		return _productionJournalVolumeCount;
	}

	/**
	 * Gets the number of production volumes that need to be
	 * created.
	 * 
	 * @param resourceCount the number of production volumes to create
	 * @return
	 */
	public long getProductionVolumeCount(int resourceCount) {
		return (_productionJournalVolumeCount + resourceCount);
	}

	/**
	 * Gets the remote site volume count, consisting of 
	 * remote replica volumes and journals.
	 * @param resourceCount the number of production volumes to create
	 * @return
	 */
	public long getRemoteVolumeCount(int resourceCount) {
		return (getRemoteReplicaVolumeCount(resourceCount) + 
				getRemoteReplicaJournalVolumeCount());
	}
	
	/**
	 * Gets the number of local site volumes that need to be created.
	 * Production volumes/journals are always on the local site,
	 * therefore value consists of production volumes/journals, and 
	 * local replica volumes/journals.
	 * @param resourceCount the number of production volumes to create
	 * @return
	 */
	public long getLocalVolumeCount(int resourceCount) {
		return (getLocalReplicaVolumeCount(resourceCount) + 
				getLocalReplicaJournalVolumeCount() +
				getProductionVolumeCount(resourceCount));
	}

}
