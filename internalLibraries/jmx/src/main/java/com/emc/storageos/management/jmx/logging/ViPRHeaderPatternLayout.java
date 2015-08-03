/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.jmx.logging;

import org.apache.log4j.PatternLayout;

public final class ViPRHeaderPatternLayout extends PatternLayout {

    private IdentifierManager identifierManager = IdentifierManager.getInstance();
    private final String NEW_LINE = System.getProperty("line.separator");
    public static final char HEADER_START_INDICATOR = '@';
    public static final int HEADER_START_LENGTH = 90;

    @Override
    public String getHeader() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < HEADER_START_LENGTH; i++) {
            sb.append(HEADER_START_INDICATOR);
        }
        sb.append(System.currentTimeMillis());
        sb.append(NEW_LINE);

        addLine(sb, "Product", identifierManager.findProductIdent());
        addLine(sb, "Product Base", identifierManager.findProductBase());
        addLine(sb, "Git Repository", identifierManager.findGitRepo());
        addLine(sb, "Git Branch", identifierManager.findGitBranch());
        addLine(sb, "Git Revision", identifierManager.findGitRevision());
        addLine(sb, "Platform", identifierManager.findPlatform());
        addLine(sb, "Kernel Version", identifierManager.findKernelVersion());
        addLine(sb, "IP Address", identifierManager.findIpAddress());
        return sb.toString();
    }

    private void addLine(StringBuffer sb, String title, String value) {
        sb.append(title);
        sb.append(": ");
        sb.append(value);
        sb.append(NEW_LINE);
    }
}
