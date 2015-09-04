/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;

public class StorageProtocol {

    public static final String ATMOSIMPORT_DEVICETYPE = "ATMOS_IMPORTER";
    public static final String ATMOS_DEVICETYPE = "ATMOS";

    // storage block protocols
    public static enum Block {
        iSCSI,              // block
        FC,                 // block
        FCoE,               // FC block protocol with Ethernet transport
        ScaleIO;            // ScaleIO Data Clients

        private static final Set<String> protocols = new HashSet<String>();

        static {
            for (Block protocol : EnumSet.allOf(Block.class)) {
                protocols.add(protocol.name());
            }
        }

        public static boolean isProtocolSupported(String protocol) {
            return protocols.contains(protocol);
        }
    }

    // storage file protocols
    public static enum File {
        NFS,                // file, NFSv2 & NFSv3
        NFSv4,              // file, authenticated NFS
        CIFS,               // file
        NFS_OR_CIFS,        // NFS or CIFS
    }

    // storage file protocols
    public static enum Object {
        File,                // Objects hosted on file-share based devices
    }

    public static enum Transport {
        FC,         // fibre channel networks
        IP,         // IP networks for iSCSI, NFS, CIFS
        Ethernet,   // Ethernet networks for FCoE
        ScaleIO,    // ScaleIO Data Clients
    }

    /**
     * Convert block protocol to its related transport type.
     *
     * @param protocol
     * @return
     */
    public static Transport block2Transport(String protocol) {
        if (protocol == null) {
            return null;
        }
        if (Block.iSCSI.name().equals(protocol))
            return Transport.IP;
        if (Block.FCoE.name().equals(protocol))
            return Transport.Ethernet;
        if (Block.FC.name().equals(protocol))
            return Transport.FC;
        if (Block.ScaleIO.name().equals(protocol)) {
            return Transport.ScaleIO;
        }
        throw new RuntimeException("Invalid block protocol");
    }

    /**
     * Check if block protocol selection is good.
     *
     * @param type VirtualPool type: block or file
     * @param protocols set of protocol names to be validated
     * @return
     */
    public static boolean checkProtocols(VirtualPool.Type type, Set<String> protocols) {
        Iterator<String> it = protocols.iterator();

        try {
            while (it.hasNext()) {
                if (type.name().equals(VirtualPool.Type.block.name())) {
                    Block.valueOf(it.next());
                } else if (type.name().equals(VirtualPool.Type.file.name())) {
                    File.valueOf(it.next());
                } else {
                    return false;
                }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    /**
     * Validate initiator node and port formats for the given protocol.
     * 
     * @param protocol
     * @param initiatorNode
     * @param initiatorPort
     * @return
     */
    public static boolean checkInitiator(String protocol, String initiatorNode, String initiatorPort) {
        if (Block.FC.name().equals(protocol)) {
            return WWNUtility.isValidWWN(initiatorNode) && WWNUtility.isValidWWN(initiatorPort);
        }
        if (Block.iSCSI.name().equals(protocol)) {
            return (iSCSIUtility.isValidIQNPortName(initiatorPort) || iSCSIUtility.isValidEUIPortName(initiatorPort));

        }
        return true;
    }

    public static boolean isFCEndpoint(String endpoint) {
        return WWNUtility.isValidWWN(endpoint);
    }
}
