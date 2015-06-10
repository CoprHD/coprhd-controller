/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "block_protection")
public class BlockVirtualPoolProtectionParam extends VirtualPoolProtectionParam {

    private VirtualPoolProtectionMirrorParam continuousCopies;
    private VirtualPoolProtectionRPParam recoverPoint;
    private VirtualPoolRemoteMirrorProtectionParam remoteCopies;
    
    public BlockVirtualPoolProtectionParam() {}
    
    public BlockVirtualPoolProtectionParam(
            VirtualPoolProtectionMirrorParam continuousCopies,
            VirtualPoolProtectionRPParam recoverPoint,
            VirtualPoolRemoteMirrorProtectionParam remoteCopies) {
        this.continuousCopies = continuousCopies;
        this.recoverPoint = recoverPoint;
        this.remoteCopies = remoteCopies;
    }
    
    /**
     * Returns pool parameters if protection type is 'Mirror' 
     * @valid none
     */     
    @XmlElement(name = "continuous_copies", required = false)
    public VirtualPoolProtectionMirrorParam getContinuousCopies() {
        return continuousCopies;
    }

    public void setContinuousCopies(VirtualPoolProtectionMirrorParam continuousCopies) {
        this.continuousCopies = continuousCopies;
    }

    /**
     * Returns pool parameters if protection type is 'Recover Point' 
     * @valid none
     */     
    @XmlElement(name = "recoverpoint", required = false)
    public VirtualPoolProtectionRPParam getRecoverPoint() {
        return recoverPoint;
    }

    public void setRecoverPoint(VirtualPoolProtectionRPParam recoverPoint) {
        this.recoverPoint = recoverPoint;
    }

    
    
    
    /**
     * Convenience method that determines if RP protection has
     * been specified.
     * 
     * @return
     */
    public boolean specifiesRPProtection() {
        return (recoverPoint != null 
                && (recoverPoint.getCopies() != null && !recoverPoint.getCopies().isEmpty()));
    }
    
    /**
     * Convenience method that determines if mirroring protection
     * has been specified.
     * 
     * @return
     */
    public boolean specifiesMirroring() {
        return (continuousCopies != null
                && continuousCopies.getMaxMirrors() != null
                && continuousCopies.getMaxMirrors() != VirtualPoolProtectionMirrorParam.MAX_DISABLED);
    }
    
    /**
     * Convenience method that determines if remote mirroring protection has been specified
     * @return
     */
    public boolean specifiesRemoteMirroring() {
        return (null != remoteCopies
                && null != remoteCopies.getRemoteCopySettings());
                
    }

    /**
     * Returns pool parameters if protection type is 'Remote Copy'
     * @valid none
     */
    @XmlElement(name = "remote_copies", required = false)
    public VirtualPoolRemoteMirrorProtectionParam getRemoteCopies() {
        return remoteCopies;
    }

    public void setRemoteCopies(VirtualPoolRemoteMirrorProtectionParam remoteCopies) {
        this.remoteCopies = remoteCopies;
    }

	/**
	 * Convenience method to tell if any of the subfields have content
	 * @return true if any protection field is populated
	 */
	public boolean hasAnyProtection() {
		if (getRecoverPoint() != null || getContinuousCopies() != null || getSnapshots() != null || getRemoteCopies() != null) {
			return true;
		}
		return false;
	}
}
