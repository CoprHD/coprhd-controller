/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.aix.command;

public class ListVIOIQNsCommand extends ListIQNsCommand {

    public ListVIOIQNsCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in `lsdev | grep ^iscsi | awk '{ print $1 }'`; do ");
        sb.append("  lsdev -dev $host -vpd | grep -E -i -w 'initiator_name'; ");
        sb.append("done; ");
        setCommand(sb.toString());
    }
}