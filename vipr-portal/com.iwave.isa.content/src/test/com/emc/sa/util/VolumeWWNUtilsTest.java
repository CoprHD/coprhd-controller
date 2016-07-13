/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.emc.storageos.model.block.VolumeRestRep;

public class VolumeWWNUtilsTest {

    @Test
    public void testMatchingWWNs() throws Exception {
        VolumeRestRep blockObject = new VolumeRestRep();
        blockObject.setWwn("60060abcdef0123456789abcdef01234");
        String actualWwn = "60060abcdef0123456789abcdef01234";
        assertTrue(VolumeWWNUtils.wwnMatches(actualWwn, blockObject));
    }

    @Test
    public void testNonMatchingWWNs() throws Exception {
        VolumeRestRep blockObject = new VolumeRestRep();
        blockObject.setWwn("60060abcdef0123456789abcdef01235");
        String actualWwn = "60060abcdef0123456789abcdef01234";
        assertFalse(VolumeWWNUtils.wwnMatches(actualWwn, blockObject));
    }

    @Test
    public void testPartial() throws Exception {
        VolumeRestRep blockObject = new VolumeRestRep();
        blockObject.setWwn("514abcdef0123456");
        String actualWwn = "514abcdef0123456";
        assertTrue(VolumeWWNUtils.wwnMatches(actualWwn, blockObject));
    }

    @Test
    public void testPartialAtEnd() throws Exception {
        VolumeRestRep blockObject = new VolumeRestRep();
        blockObject.setWwn("514abcdef0123456");
        String actualWwn = "12345514abcdef0123456";
        assertTrue(VolumeWWNUtils.wwnMatches(actualWwn, blockObject));
    }

    @Test
    public void testPartialInMiddle() throws Exception {
        VolumeRestRep blockObject = new VolumeRestRep();
        blockObject.setWwn("514abcdef0123456");
        String actualWwn = "01030008514abcdef01234560200000e";
        assertTrue(VolumeWWNUtils.wwnMatches(actualWwn, blockObject));
    }

    @Test
    public void testIncompletePartial() throws Exception {
        VolumeRestRep blockObject = new VolumeRestRep();
        // WWN on the block object is less than the PARTIAL_WWN size (16 characters)
        blockObject.setWwn("514abcdef012345");
        String actualWwn = "01030008514abcdef01234560200000e";
        assertFalse(VolumeWWNUtils.wwnMatches(actualWwn, blockObject));
    }
}
