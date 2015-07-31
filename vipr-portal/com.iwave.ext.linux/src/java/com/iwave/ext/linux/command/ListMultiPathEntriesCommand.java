/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PathInfo;
import com.iwave.ext.text.TextParser;

public class ListMultiPathEntriesCommand extends LinuxResultsCommand<List<MultiPathEntry>> {
    private static final Pattern ENTRY_PATTERN = Pattern
            .compile("(\\w+)\\s+(?:\\((.*)\\)\\s+)?(dm-\\w+)");
    private static final Pattern PATH_PATTERN = Pattern
            .compile("- (\\d+):(\\d+):(\\d+):(\\d+)\\s+(\\w+)\\s+\\d+:\\d+\\s+(.*)");

    public ListMultiPathEntriesCommand() {
        setCommand(CommandConstants.MULTIPATH);
        addArgument("-ll");
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();

        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();

            TextParser parser = new TextParser();
            parser.setRepeatPattern(ENTRY_PATTERN);
            for (String textBlock : parser.parseTextBlocks(stdout)) {
                MultiPathEntry entry = readEntry(textBlock);
                if (entry != null) {
                    results.add(entry);
                }
            }
        }
    }

    private MultiPathEntry readEntry(String textBlock) {
        Matcher m = ENTRY_PATTERN.matcher(textBlock);
        if (m.find()) {
            MultiPathEntry entry = new MultiPathEntry();
            entry.setName(m.group(1));
            // If there is no WWID group, it is the same as the name
            if (m.group(2) != null) {
                entry.setWwid(m.group(2));
            }
            else {
                entry.setWwid(entry.getName());
            }
            entry.setDmName(m.group(3));
            entry.setPaths(readPaths(textBlock));
            return entry;
        }
        else {
            return null;
        }
    }

    private List<PathInfo> readPaths(String textBlock) {
        List<PathInfo> paths = Lists.newArrayList();
        Matcher m = PATH_PATTERN.matcher(textBlock);
        while (m.find()) {
            PathInfo path = new PathInfo();
            path.setHost(Integer.parseInt(m.group(1)));
            path.setChannel(Integer.parseInt(m.group(2)));
            path.setId(Integer.parseInt(m.group(3)));
            path.setLun(Integer.parseInt(m.group(4)));
            path.setDevice(m.group(5));
            path.setStatus(m.group(6));
            paths.add(path);
        }
        return paths;
    }
}
