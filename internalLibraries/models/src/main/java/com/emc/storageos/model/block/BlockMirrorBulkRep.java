/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_block_mirrors")
public class BlockMirrorBulkRep extends BulkRestRep {
	
	private List<BlockMirrorRestRep> blockMirrors;
	
	/**
     * List of Block Mirrors.
     * 
     */
    @XmlElement(name = "block_mirror")
    public List<BlockMirrorRestRep> getBlockMirrors() {
        if (blockMirrors == null) {
        	blockMirrors = new ArrayList<BlockMirrorRestRep>();
        }
        return blockMirrors;
    }

    public void setBlockMirrors(List<BlockMirrorRestRep> blockMirrors) {
        this.blockMirrors = blockMirrors;
    }

    public BlockMirrorBulkRep() {
    }

    public BlockMirrorBulkRep(List<BlockMirrorRestRep> blockMirrors) {
        this.blockMirrors = blockMirrors;
    }	

}
