package com.emc.storageos.validation;

public enum ValCk {
	/**
	 * Identity - Volumes: typically device nativeId, WWN, and size
	 */
	ID,			
	/**
	 * VPLEX  - Volumes: identities of associatedVolumes and any mirrors; local/distributed; consistency group membership
	 * storage view membership
	 */
	VPLEX		
}
