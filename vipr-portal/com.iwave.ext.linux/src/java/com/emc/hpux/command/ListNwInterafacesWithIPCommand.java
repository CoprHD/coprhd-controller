/*
 * Copyright (c) 2018 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

public class ListNwInterafacesWithIPCommand extends HpuxResultsCommand<Set<String>> {

    public ListNwInterafacesWithIPCommand() {
        setCommand("netstat -in | egrep -v \"Name\" | grep lan | awk '{print $1}'");
    }    

    @Override
    public void parseOutput() {
        results = Sets.newHashSet();
        String[] values = getOutput().getStdout().split("\\n");
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                results.add(StringUtils.trim(value));
            }
        }
    }
}