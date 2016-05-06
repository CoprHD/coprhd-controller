package com.emc.storageos.migrationcontroller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.adapter.LinuxHostDiscoveryAdapter;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Volume;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.command.MultipathCommand;
import com.iwave.ext.linux.command.MultipathException;
import com.iwave.ext.linux.command.powerpath.PowerPathException;
import com.iwave.ext.linux.command.powerpath.PowerPathHDSInquiry;
import com.iwave.ext.linux.command.powerpath.PowerPathInquiry;
import com.iwave.ext.linux.command.powerpath.PowerPathInvistaInquiry;
import com.iwave.ext.linux.command.powerpath.PowermtCheckRegistrationCommand;
import com.iwave.ext.linux.model.PowerPathDevice;

public class HostMigrationCommand {
    private static final Logger _log = LoggerFactory.getLogger(HostMigrationCommand.class);

    private static final int HUS_VM_PARTIAL_WWN_LENGTH = 12;

    private static final int SUFFIX_LENGTH = 4;
    private static final int HUS_PREFIX_LENGTH = 4;
    private static final int PARTIAL_PREFIX_LENGTH = 5;

    private HostMigrationCommand() {

    }

    public static PowerPathDevice FindPowerPathEntryForVolume(Host host, Volume volume) throws Exception {
        PowerPathDevice entry = findPowerPathEntry(host, volume);
        if (entry == null) {
            String message = String.format("FindPowerPathEntryForVolume.illegalState.noEntries",
                    volume.getWWN().toLowerCase());
            throw new Exception(message);
        }
        _log.info("find.powerpath.wwn", entry);
        return entry;
    }

    private static boolean wwnMatches(String actual, String partial) {
        if (actual == null || partial == null) {
            return actual == null && partial == null;
        }
        int actualLength = actual.length();
        int partialLength = partial.length();
        if (actualLength == partialLength) {
            return actual.equalsIgnoreCase(partial);
        }
        else if (actualLength > partialLength) {
            return actual.toLowerCase().endsWith(partial.toLowerCase());
        }
        // This would only happen is for some reason the partial string was longer
        return false;
    }

    private static PowerPathDevice findPowerPathEntry(Host host, Volume volume) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        PowerPathInquiry command = new PowerPathInquiry();
        cli.executeCommand(command);
        List<PowerPathDevice> entries = command.getResults();
        for (PowerPathDevice device : entries) {
            String deviceWwn = device.getWwn();
            _log.debug("FindPowerPathEntryForVolume.checking", device.getDevice(), deviceWwn, volume.getWWN());
            if (wwnMatches(deviceWwn, volume.getWWN())) {
                return device;
            }
        }
        cli.executeCommand(new PowerPathInvistaInquiry());
        entries = command.getResults();
        for (PowerPathDevice device : entries) {
            String deviceWwn = device.getWwn();
            _log.debug("FindPowerPathEntryForVolume.checking", device.getDevice(), deviceWwn, volume.getWWN());
            if (wwnMatches(deviceWwn, volume.getWWN())) {
                return device;
            }
        }

        cli.executeCommand(new PowerPathHDSInquiry());
        entries = command.getResults();
        for (PowerPathDevice device : entries) {
            String deviceWwn = device.getWwn();
            _log.debug("FindPowerPathEntryForVolume.checking", device.getDevice(), deviceWwn, volume.getWWN());
            if (wwnHDSMatches(deviceWwn, volume.getWWN())) {
                return device;
            }
        }

        _log.debug("FindMultiPathEntryForVolume.noEntries", volume.getWWN());
        return null;
    }

    private static String convertAsciiHDSWwn(String wwn) {
        if (wwn.length() % 2 != 0) {
            return null; // can't convert if not an even number
        }

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < wwn.length(); i += 2) {
            String str = wwn.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static boolean isHusVmPartialWwn(String wwn) {
        return (wwn.length() == HUS_VM_PARTIAL_WWN_LENGTH);
    }

    private static String createHusPartialWwn(String wwn) {
        if (wwn.length() < SUFFIX_LENGTH + HUS_PREFIX_LENGTH) {
            return wwn; // return original wwn if length not long enough to make partial
        }
        String prefix = wwn.substring(wwn.length() - (SUFFIX_LENGTH + HUS_PREFIX_LENGTH), wwn.length() - SUFFIX_LENGTH);
        String sufix = wwn.substring(wwn.length() - SUFFIX_LENGTH);

        return (prefix + "0000" + sufix);
    }

    private static String createPartialWwn(String wwn) {
        if (wwn.length() < SUFFIX_LENGTH + PARTIAL_PREFIX_LENGTH) {
            return wwn; // return original wwn if length not long enough to make partial
        }
        String prefix = wwn.substring(wwn.length() - (SUFFIX_LENGTH + PARTIAL_PREFIX_LENGTH), wwn.length() - SUFFIX_LENGTH);
        String sufix = wwn.substring(wwn.length() - SUFFIX_LENGTH);

        return ("000" + prefix + "0000" + sufix);
    }

    public static boolean wwnHDSMatches(String actualWwn, String volumwwn) {
        String convertedWwn = convertAsciiHDSWwn(actualWwn);

        if (convertedWwn == null) {
            return false; // convert failed
        }

        String useableWwn = convertedWwn;

        // check if we need to created HUS compatible wwn
        if (isHusVmPartialWwn(volumwwn)) {
            useableWwn = createHusPartialWwn(convertedWwn); // 12 char long
        } else {
            useableWwn = createPartialWwn(convertedWwn); // 16 char long
        }

        return wwnMatches(useableWwn, volumwwn);
    }

    public static String checkForPowerPath(Host host) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        PowermtCheckRegistrationCommand command = new PowermtCheckRegistrationCommand();
        try {
            cli.executeCommand(command);
            return null;
        } catch (PowerPathException e) {
            return e.getMessage();
        }
    }

    public static String checkForMultipath(Host host) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        MultipathCommand command = new MultipathCommand();
        command.addArgument("-l");
        cli.executeCommand(command);
        try {
            CommandOutput output = command.getOutput();
            if (output.getExitValue() != 0) {
                String string = "CheckForMultipath.noMultipath";
                return string;
            }
            return null;
        } catch (MultipathException e) {
            return e.getMessage();
        }
    }
    public static String hostIscsiConnetTarget(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        IscsiConnectTargetCommand command = new IscsiConnectTargetCommand(args);
        cli.executeCommand(command);
        _log.info("host conncet to target");
        return command.getResults();
    }

    public static String hostIscsiDisconnetTarget(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        IscsiDisconnectTargetCommand command = new IscsiDisconnectTargetCommand(args);
        cli.executeCommand(command);
        _log.info("host disconncet to target");
        return command.getResults();
    }

    public static String hostDoMigration(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        DoMigrationDDCommand command = new DoMigrationDDCommand(args);
        cli.executeCommand(command);
        _log.info("host do dd migration");
        return command.getResults();
    }
}
