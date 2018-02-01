/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "block_protection_update")
public class BlockVirtualPoolProtectionUpdateParam extends VirtualPoolProtectionParam {

    private VirtualPoolProtectionMirrorParam continuousCopies;
    private VirtualPoolProtectionRPChanges recoverPoint;
    private VirtualPoolRemoteProtectionUpdateParam remoteCopies;

    public BlockVirtualPoolProtectionUpdateParam() {
    }

    public BlockVirtualPoolProtectionUpdateParam(
            VirtualPoolProtectionMirrorParam continuousCopies,
            VirtualPoolProtectionRPChanges recoverPoint,
            VirtualPoolRemoteProtectionUpdateParam remoteCopies) {
        this.continuousCopies = continuousCopies;
        this.recoverPoint = recoverPoint;
        this.remoteCopies = remoteCopies;
    }

    /**
     * The new mirror protection settings for the virtual pool.
     * 
     */
    @XmlElement(name = "continuous_copies", required = false)
    public VirtualPoolProtectionMirrorParam getContinuousCopies() {
        return continuousCopies;
    }

    public void setContinuousCopies(
            VirtualPoolProtectionMirrorParam continuousCopies) {
        this.continuousCopies = continuousCopies;
    }

    /**
     * The new Recoverpoint protection settings for the virtual pool.
     * 
     */
    @XmlElement(name = "recoverpoint", required = false)
    public VirtualPoolProtectionRPChanges getRecoverPoint() {
        return recoverPoint;
    }

    public void setRecoverPoint(VirtualPoolProtectionRPChanges recoverPoint) {
        this.recoverPoint = recoverPoint;
    }

    @XmlElement(name = "remote_copies", required = false)
    public VirtualPoolRemoteProtectionUpdateParam getRemoteCopies() {
        return remoteCopies;
    }

    public void setRemoteCopies(VirtualPoolRemoteProtectionUpdateParam remoteCopies) {
        this.remoteCopies = remoteCopies;
    }

    /**
     * Convenience method that determines if RP protection has
     * been specified.
     * 
     * @return
     */
    public boolean specifiesRPProtection() {
        return (recoverPoint != null
        && ((recoverPoint.getAdd() != null && !recoverPoint.getAdd().isEmpty()) || (recoverPoint.getRemove() != null && !recoverPoint
                .getRemove().isEmpty())));
    }

    /**
     * Convenience method that determines if mirroring protection
     * has been specified with all fields populated:
     * - protection mirror vpool
     * - max native continuous copies
     * 
     * @return
     */
    public boolean specifiesMirroring() {
        return (continuousCopies != null
                && continuousCopies.getMaxMirrors() != null
                && continuousCopies.getMaxMirrors() != VirtualPoolProtectionMirrorParam.MAX_DISABLED);
    }

    public boolean specifiesRemoteMirroring() {
        return (remoteCopies != null
        && ((remoteCopies.getAdd() != null && !remoteCopies.getAdd().isEmpty()) || (remoteCopies.getRemove() != null && !remoteCopies
                .getRemove().isEmpty())));
    }

    /**
     * Convenience method that determines if continuous copy (mirror) protection
     * has been enabled.
     * 
     * @return true if maxMirrors is something other than MAX_DISABLED.
     */
    public boolean enablesContinuousCopies() {
        return continuousCopies != null
                && continuousCopies.getMaxMirrors() != null
                && continuousCopies.getMaxMirrors() != VirtualPoolProtectionMirrorParam.MAX_DISABLED;
    }
}
