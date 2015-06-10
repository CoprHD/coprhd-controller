/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Vsan implements Serializable {
    public static Map<String, Vsan> wwnToVsan = new HashMap<String, Vsan>();
    public static Map<String, Vsan> vsanIdToVsan = new TreeMap<String, Vsan>();

    private static final Logger _log = LoggerFactory.getLogger(Vsan.class);
    
    String vsanId;
    String vsanName;
    String vsanWwn;
    List<FCProtocolEndpoint> localEndpoints = new ArrayList<FCProtocolEndpoint>();     // key = wwpn of endpoint
    Zoneset activeZoneset = null;
    List<Zoneset> inactiveZonesets = new ArrayList<Zoneset>();
    public void print() {
        _log.info("*************** VSAN " + vsanId + ":   ********************");
        for (FCProtocolEndpoint ep : localEndpoints) {
            for (FCProtocolEndpoint cp : ep.connections.values()) {
                _log.info(ep.iface.name + " " + vsanId + " local " 
                        + formatWwn(ep.wwpn) + " remote " + formatWwn(cp.wwpn) + " " + formatWwn(cp.wwnn));
            }
        }
        if (activeZoneset != null) activeZoneset.print();
        for (Zoneset z : getInactiveZonesets()) {
            z.print();
        }
    }
    
    public Vsan(String vsanId, String vsanName) {
        this.vsanId = vsanId;
        this.vsanName = vsanName;
        vsanIdToVsan.put(vsanId, this);
    }

    public Zoneset getActiveZoneset() {
        return activeZoneset;
    }

    public void setActiveZoneset(Zoneset activeZoneset) {
        this.activeZoneset = activeZoneset;
    }

    public List<Zoneset> getInactiveZonesets() {
        if ( inactiveZonesets == null) {
            inactiveZonesets = new ArrayList<Zoneset>();
        }
        return inactiveZonesets;
    }
    
    public String formatWwn(String s) {
        char[] c = s.toCharArray();
        StringBuilder buf = new StringBuilder();
        for (int i=0; i < c.length && i < 16; i++) {
            buf.append(c[i]);
            if ((i&1)==1 && i < 15) buf.append(":");
        }
        return buf.toString().toLowerCase();
    }

}
