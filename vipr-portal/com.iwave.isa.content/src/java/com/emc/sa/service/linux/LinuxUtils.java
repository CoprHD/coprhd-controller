/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.model.MountPoint;
import com.iwave.ext.linux.model.MultiPathEntry;

public class LinuxUtils {
    private static Pattern LOOSE_WWN_PATTERN = Pattern.compile("[0-9A-F:]+");

    public static LinuxSystemCLI convertHost(Host host) {
        LinuxSystemCLI cli = new LinuxSystemCLI();
        cli.setHost(host.getHostName());
        cli.setPort(host.getPortNumber());
        cli.setUsername(host.getUsername());
        cli.setPassword(host.getPassword());
        cli.setHostId(host.getId());
        return cli;
    }

    public static MountPoint getMountPoint(URI hostId, Map<String, MountPoint> results, BlockObjectRestRep volume) {
        String volumeMountPoint = KnownMachineTags.getBlockVolumeMountPoint(hostId, volume);
        if (results.containsKey(volumeMountPoint)) {
            return results.get(volumeMountPoint);
        }
        else {
            ExecutionUtils.fail("failTask.LinuxUtils.getMountPoint", volume.getId(), volumeMountPoint, volume.getName(), volume.getId());
            return null; // we will never get here - .fail() will throw an exception
        }
    }

    public static String getDeviceForEntry(MultiPathEntry entry) {
        return String.format("/dev/mapper/%s", entry.getName());
    }

    public static String normalizeWWN(String wwn) {
        if (StringUtils.isBlank(wwn)) {
            return null;
        }
        // Save without spaces and lowercase
        wwn = StringUtils.upperCase(StringUtils.trim(wwn));
        if (!LOOSE_WWN_PATTERN.matcher(wwn).matches()) {
            return null;
        }

        // Make a series of hex chars
        wwn = StringUtils.replace(wwn, ":", "");
        // Left pad with zeros to make 16 chars, trim any excess
        wwn = StringUtils.substring(StringUtils.leftPad(wwn, 16, '0'), 0, 16);

        StrBuilder sb = new StrBuilder();
        for (int i = 0; i < wwn.length(); i += 2) {
            sb.appendSeparator(':');
            sb.append(StringUtils.substring(wwn, i, i + 2));
        }
        return sb.toString();
    }

    public static List<String> normalizeWWNs(List<String> wwns) {
        List<String> normalized = Lists.newArrayList();
        for (String wwn : wwns) {
            normalized.add(normalizeWWN(wwn));
        }
        return normalized;
    }

}
