/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.List;

public class ScaleIOQueryClusterCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryClusterResult> {

    // Mode: Cluster, Cluster State: Normal, Tie-Breaker State: Connected
    // Primary IP: 10.247.78.40 Secondary IP: 10.247.78.41 Tie-Breaker IP: 10.247.78.42

    private static final String CLUSTER_STATUSES = "ClusterStatuses";
    private static final String CLUSTER_IPS = "ClusterIPs";
    private static final String PRIMARY_IP = "PrimaryIP";
    private static final String SECONDARY_IP = "SecondaryIP";
    private static final String TIE_BREAKER = "TieBreaker";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("\\s+Mode:\\s+(\\w+),\\s+Cluster State:\\s+(\\w+),\\s+Tie-Breaker State:\\s+(\\w+)", CLUSTER_STATUSES),
            new ParsePattern("\\s+Primary IP:\\s+(\\S+)\\s+Secondary IP:\\s+(\\S+)\\s+Tie-Breaker IP:\\s+(\\S+)", CLUSTER_IPS),
            new ParsePattern("\\s+Primary IP:\\s+(\\S+)", PRIMARY_IP),
            new ParsePattern("\\s+Secondary IP:\\s+(\\S+)", SECONDARY_IP),
            new ParsePattern("\\s+Tie-Breaker IP:\\s+(\\S+)", TIE_BREAKER)
    };

    public ScaleIOQueryClusterCommand() {
        addArgument("--query_cluster");
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); // No need to check not null condition here
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOQueryClusterResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case CLUSTER_STATUSES:
                String mode = capturedStrings.get(0);
                String state = capturedStrings.get(1);
                String tieBreakerState = capturedStrings.get(2);
                results.setClusterMode(mode);
                results.setClusterState(state);
                results.setTieBreakerState(tieBreakerState);
                break;
            case CLUSTER_IPS:
                String primaryIP = capturedStrings.get(0);
                String secondaryIP = capturedStrings.get(1);
                String tieBreakerIP = capturedStrings.get(2);
                results.setIPs(primaryIP, secondaryIP, tieBreakerIP);
                break;
            case PRIMARY_IP:
                results.setPrimaryIP(capturedStrings.get(0));
                break;
            case SECONDARY_IP:
                results.setSecondaryIP(capturedStrings.get(0));
                break;
            case TIE_BREAKER:
                results.setTieBreakerIP(capturedStrings.get(0));
                break;
        }
    }
}
