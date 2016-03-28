/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.VNXeIscsiNode;
import com.emc.storageos.vnxe.models.VNXeIscsiPortal;

public class IscsiPortalListRequest extends KHRequests<VNXeIscsiPortal> {
    private static final Logger _logger = LoggerFactory.getLogger(IscsiPortalListRequest.class);
    private static final String URL = "/api/types/iscsiPortal/instances";
    private static final String FIELDS = "ipAddress";

    public IscsiPortalListRequest(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    /**
     * Get iscsiPortals, without detailed iscsiNode, e.g. no iqn name
     * 
     * @return
     */
    public List<VNXeIscsiPortal> get() {
        return getDataForObjects(VNXeIscsiPortal.class);

    }

    /**
     * Get all iscsiPortals, with detailed iscsiNode.
     * 
     * @return
     */
    public List<VNXeIscsiPortal> getDetails() {
        List<VNXeIscsiPortal> result = new ArrayList<VNXeIscsiPortal>();
        List<VNXeIscsiPortal> portals = get();

        if (portals != null && !portals.isEmpty()) {
            for (VNXeIscsiPortal portal : portals) {
                // get iscsiNode, so that we could get iqn name for each iscsi port
                VNXeIscsiNode node = portal.getIscsiNode();
                if (node != null) {
                    String nodeId = node.getId();
                    IscsiNodeRequests nodeReq = new IscsiNodeRequests(getClient());
                    VNXeIscsiNode detailedNode = nodeReq.get(nodeId);
                    portal.setIscsiNode(detailedNode);
                    result.add(portal);
                }
            }
        }
        return result;
    }

    /**
     * Get iscsiPort based on the IscsiNode Id
     * 
     * @param nodeId iscsiNode id
     * @return
     */
    public VNXeIscsiPortal getByIscsiNode(String nodeId) {
        StringBuilder builder = new StringBuilder(VNXeConstants.ISCSINODE_FILTER);
        builder.append("\"");
        builder.append(nodeId);
        builder.append("\"");
        setFilter(builder.toString());

        VNXeIscsiPortal result = null;
        List<VNXeIscsiPortal> portalList = get();
        // it should just return 1
        if (portalList != null && !portalList.isEmpty()) {
            result = portalList.get(0);
        } else {
            _logger.info("No iscsiPortal found using the iscsiNode Id: {}", nodeId);
        }
        return result;
    }

}
