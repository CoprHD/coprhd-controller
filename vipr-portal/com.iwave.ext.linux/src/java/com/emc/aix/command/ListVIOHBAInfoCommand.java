/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

public class ListVIOHBAInfoCommand extends ListHBAInfoCommand {

    public ListVIOHBAInfoCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in `lsdev -type adapter -field name | grep ^fc`; do ");
        sb.append("  echo \"host: $host\" ; ");
        sb.append("  lsdev -dev $host -vpd | grep -E -i -w 'Network Address|Device Specific.\\(Z8\\)'; ");
        sb.append("done; ");
        setCommand(sb.toString());
    }
}
