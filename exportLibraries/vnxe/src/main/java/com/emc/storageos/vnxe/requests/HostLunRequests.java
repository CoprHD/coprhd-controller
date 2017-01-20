/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.HostLun;

public class HostLunRequests extends KHRequests<HostLun> {

    private static final Logger _logger = LoggerFactory.getLogger(HostLunRequests.class);
    private static String URL = "/api/types/hostLUN/instances";
    private static final String URL_HOSTLUN = "/api/instances/hostLUN/";
    public static String ID_SEQUENCE_LUN = "prod";
    public static String ID_SEQUENCE_SNAP = "snap";
    private static final String FIELDS = "host,type,hlu,lun,snap,isReadOnly";

    public HostLunRequests(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    public HostLun getHostLun(String lunId, String hostId, String idCharSequence) {
        _logger.info("Finding hostLun for lunId: {}, hostId: {}", lunId, hostId);

        StringBuilder queryFilter = new StringBuilder(VNXeConstants.LUN_FILTER);
        if (_client.isUnity()) {
            queryFilter.append("\"");
            queryFilter.append(lunId);
            queryFilter.append("\"");
        } else {
            queryFilter.append(lunId);
        }
        setFilter(queryFilter.toString());

        HostLun result = null;
        List<HostLun> hostLuns = getDataForObjects(HostLun.class);
        for (HostLun hostLun : hostLuns) {
            String lunHostId = hostLun.getHost().getId();
            if (hostId.equals(lunHostId) && hostLun.getId().contains(idCharSequence)) {
                result = hostLun;
                _logger.info("Found hostLun {}", hostLun.getId());
                break;
            }
        }

        return result;
    }

    public List<HostLun> getByLunId(String lunId) {
        _logger.info("Finding hostLun for lunId: {}, hostId: {}", lunId);
        setFilter(VNXeConstants.LUN_FILTER + lunId);
        return getDataForObjects(HostLun.class);
    }
    
    public HostLun getSnapHostLun(String snapId, String hostId) {
        _logger.info("Finding hostLun for snapId: {}, hostId: {}", snapId, hostId);
        String filter = VNXeConstants.SNAP_FILTER_V31 + "\"" + snapId + "\"";
        setFilter(filter);
        HostLun result = null;
        List<HostLun> hostLuns = getDataForObjects(HostLun.class);
        for (HostLun hostLun : hostLuns) {
            String lunHostId = hostLun.getHost().getId();
            if (hostId.equals(lunHostId)) {
                result = hostLun;
                _logger.info("Found hostLun");
                break;
            }
        }

        return result;
    }

    /**
     * Get a HostLun's detail using its id.
     *
     * @param hostLunId
     * @return HostLun
     */
    public HostLun getHostLun(String hostLunId) {
        _url = URL_HOSTLUN + hostLunId;
        return getDataForOneObject(HostLun.class);
    }

}
