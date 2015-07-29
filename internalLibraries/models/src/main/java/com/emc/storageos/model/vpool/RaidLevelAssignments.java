/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;
import java.util.HashSet;
import java.util.Set;

/**
 * Class captures a list of Raid Levels to set or update in a virtual pool.
 */
public class RaidLevelAssignments {

    private Set<String> raidLevels;

    /**
     * Default Constructor.
     */
    public RaidLevelAssignments() {
    }

    public RaidLevelAssignments(Set<String> raidLevels) {
        this.raidLevels = raidLevels;
    }

    /**
     * The RAID levels for storage allocated to your volumes.
     * RAID levels set the amount of redundancy and striping.
     * Here is a quick definition of the various RAID levels.
     * 
     * RAID 0 is a striped set of disks without parity.
     * RAID 1 is a mirror copy on two disks.
     * RAID 2 is a stripe at the bit level rather than the block level. Rarely used or supported.
     * RAID 3 is a byte level striping with a dedicated parity disk.
     * RAID 4 is block level striping with a dedicated parity disk.
     * RAID 5 is block level striping with the parity data distributed across all disks.
     * RAID 6 extends RAID 5 by adding an additional parity block;
     * thus it uses block level striping with two parity blocks.
     * RAID 10 is a stripe of mirrors, i.e. a RAID 0 combination of RAID 1 drives.
     * 
     * @valid RAID0
     * @valid RAID1
     * @valid RAID2
     * @valid RAID3
     * @valid RAID4
     * @valid RAID5
     * @valid RAID6
     * @valid RAID10
     */
    @XmlElement(name = "raid_levels")
    @JsonProperty("raid_level")
    public Set<String> getRaidLevels() {
        if (raidLevels == null) {
            raidLevels = new HashSet<String>();
        }
        return raidLevels;
    }

    public void setRaidLevels(Set<String> raidLevels) {
        this.raidLevels = raidLevels;
    }

}
