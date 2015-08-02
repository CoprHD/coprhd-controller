/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import java.util.List;
import java.util.Stack;

public class ScaleIOQueryAllSDSCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryAllSDSResult> {

    // Query-all-SDS returned 6 SDS nodes.
    //
    // Protection Domain: Name: PD-1 ID: b1a5a2ef00000000
    // SDS ID: f415b57100000002 Name: lglbg040 IP: 10.247.78.40 Port: 7072
    // SDS ID: f415b57200000003 Name: lglbg041 IP: 10.247.78.41 Port: 7072
    // SDS ID: f415b57300000004 Name: lglbg042 IP: 10.247.78.42 Port: 7072
    //
    // Protection Domain: Name: PD-2 ID: b1a5a35300000001
    // SDS ID: f415b57500000005 Name: unnamed IP: 10.247.78.43 Port: 7072
    // SDS ID: f415b57600000006 Name: unnamed IP: 10.247.78.44 Port: 7072
    // SDS ID: f415b57700000007 Name: unnamed IP: 10.247.78.47 Port: 7072

    private static final String PROTECTION_DOMAIN = "ProtectionDomain";
    private static final String PROTECTION_DOMAIN_1_30 = "ProtectionDomain_1_30";
    private static final String SDS = "SDS";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("Protection Domain:\\s+Name:\\s+(\\S+)\\s+ID:\\s+(\\w+)", PROTECTION_DOMAIN),
            new ParsePattern("Protection Domain\\s+(\\w+)\\s+Name:\\s+(\\S+)", PROTECTION_DOMAIN_1_30),
            new ParsePattern("SDS ID:\\s+(\\w+)\\s+Name:\\s+(\\S+)\\s+IP:\\s+(\\S+)\\s+Port:\\s+(\\d+)", SDS),
            new ParsePattern("SDS ID:\\s+(\\w+)\\s+Name:\\s+(\\S+)\\s+State:.*?IP:\\s+(\\S+)\\s+Port:\\s+(\\d+)", SDS)
    };

    private Stack<String> lastProtectionDomain;

    public ScaleIOQueryAllSDSCommand() {
        addArgument("--query_all_sds");
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); // No need to check not null condition here
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOQueryAllSDSResult();
        lastProtectionDomain = new Stack<String>();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        String protectionDomainId;
        String protectionDomainName;
        switch (spec.getPropertyName()) {
            case PROTECTION_DOMAIN:
                protectionDomainName = capturedStrings.get(0);
                protectionDomainId = capturedStrings.get(1);
                lastProtectionDomain.push(protectionDomainId);
                results.addProtectionDomain(protectionDomainId, protectionDomainName);
                break;
            case PROTECTION_DOMAIN_1_30:
                protectionDomainId = capturedStrings.get(0);
                protectionDomainName = capturedStrings.get(1);
                lastProtectionDomain.push(protectionDomainId);
                results.addProtectionDomain(protectionDomainId, protectionDomainName);
                break;
            case SDS:
                String sdsId = capturedStrings.get(0);
                String sdsName = capturedStrings.get(1);
                String sdsIP = capturedStrings.get(2);
                String sdsPort = capturedStrings.get(3);
                protectionDomainId = lastProtectionDomain.peek();
                results.addSDS(protectionDomainId, sdsId, sdsName, sdsIP, sdsPort);
                break;
        }
    }
}
