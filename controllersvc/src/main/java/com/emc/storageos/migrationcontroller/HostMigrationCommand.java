package com.emc.storageos.migrationcontroller;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.adapter.LinuxHostDiscoveryAdapter;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Volume;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.command.ListMultiPathEntriesCommand;
import com.iwave.ext.linux.command.MultipathCommand;
import com.iwave.ext.linux.command.MultipathException;
import com.iwave.ext.linux.command.powerpath.PowerPathException;
import com.iwave.ext.linux.command.powerpath.PowerPathHDSInquiry;
import com.iwave.ext.linux.command.powerpath.PowerPathInquiry;
import com.iwave.ext.linux.command.powerpath.PowerPathInvistaInquiry;
import com.iwave.ext.linux.command.powerpath.PowermtCheckRegistrationCommand;
import com.iwave.ext.linux.command.powerpath.PowermtConfigCommand;
import com.iwave.ext.linux.command.powerpath.PowermtRestoreCommand;
import com.iwave.ext.linux.command.powerpath.PowermtSaveCommand;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PathInfo;
import com.iwave.ext.linux.model.PowerPathDevice;

public class HostMigrationCommand {
    private static final Logger _log = LoggerFactory.getLogger(HostMigrationCommand.class);

    private static final int HUS_VM_PARTIAL_WWN_LENGTH = 12;

    private static final int SUFFIX_LENGTH = 4;
    private static final int HUS_PREFIX_LENGTH = 4;
    private static final int PARTIAL_PREFIX_LENGTH = 5;

    public static final String LINUX_WWN_PREFIX = "3";

    public static final int FIND_MIGRATION_MAX_TRIES = 60;

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

    public static MultiPathEntry FindMultiPathEntryForVolume(Host host, Volume volume) throws Exception {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        ListMultiPathEntriesCommand command = new ListMultiPathEntriesCommand();
        cli.executeCommand(command);

        List<MultiPathEntry> entries = command.getResults();
        MultiPathEntry entry = findMultiPathEntry(volume, entries);
        if (entry == null) {
            String message = String.format("FindMultiPathEntryForVolume.illegalState.couldNotFindEntry",
                    volume.getWWN().toLowerCase());
            throw new Exception(message);
        }
        _log.info("find.multipath.wwn", entry.toString());
        checkStatus(entry);
        return entry;
    }

    public static void updatePowerPathEntries(Host host) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        PowermtConfigCommand configCommand = new PowermtConfigCommand();
        cli.executeCommand(configCommand);
        PowermtRestoreCommand restoreCommand = new PowermtRestoreCommand();
        cli.executeCommand(restoreCommand);
        PowermtSaveCommand saveCommand = new PowermtSaveCommand();
        cli.executeCommand(saveCommand);
    }

    public static void updateMultiPathEntries(Host host) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        MultipathCommand command = new MultipathCommand();
        cli.executeCommand(command);
    }

    public static String migrationCommand(Host host, String srcDevice, String tgtDevice) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        String args = String.format("if=%s of=%s & echo $!", srcDevice, tgtDevice);
        MigrateVolumeCommand command = new MigrateVolumeCommand(args);
        cli.executeCommand(command);
        try {
            CommandOutput output = command.getOutput();
            if (output.getStderr() != null) {
                String string = "migration failed";
                return string;
            }
            return output.getStdout();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static MigrationInfo findMigration(Host host, String migrationName, String migrationPid) throws Exception {
        MigrationInfo migrationInfo = null;
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        String args = String.format("%s ", migrationPid);
        FindMigrationCommand command = new FindMigrationCommand(args);

        cli.executeCommand(command);
        migrationInfo = command.getResults();
        if (migrationInfo == null) {
            String message = String.format("can't find migraiton for migration name = %s", migrationName);
            throw new Exception(message);
        }
        return migrationInfo;
    }


    public static List<MigrationInfo> findMigrations(Host host, List<String> migrationNames, Migration migration)
            throws Exception {

        List<MigrationInfo> migrationInfoList = new ArrayList<MigrationInfo>();
        for (String migrationName : migrationNames) {
            try {
                // First look in the device migrations and if not found, then
                // look in the extent migrations.
                String migrationPid = migration.getMigrationPid();
                MigrationInfo migrationInfo = findMigration(host, migrationName, migrationPid);
                migrationInfo.setIsHostMigration(true);
                migrationInfoList.add(migrationInfo);
            } catch (Exception vae) {
                _log.info("Migration {} not found with host migrations");
                vae.printStackTrace();
            }
        }
        return migrationInfoList;
    }

    public static String cancelMigrationsCommand(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        CancelMigrationCommand command = new CancelMigrationCommand(args);
        cli.executeCommand(command);
        try {
            CommandOutput output = command.getOutput();
            if (output.getStderr() != null) {
                String string = "cancel migration failed";
                return string;
            }
            return output.getStdout();
        } catch (Exception e) {
            return e.getMessage();
        }

    }

    public static String deleteHostDevice(Host host, String targetName) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        deleteDeviceCommand command = new deleteDeviceCommand(targetName);
        cli.executeCommand(command);
        try {
            CommandOutput output = command.getOutput();
            if (output.getStderr() != null) {
                String string = "delete device failed";
                return string;
            }
            return output.getStdout();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static String removeCommittedOrCanceledMigrations(Host host, String args) {
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        removeCanceledMigrationsCommand command = new removeCanceledMigrationsCommand(args);
        cli.executeCommand(command);
        try {
            CommandOutput output = command.getOutput();
            if (output.getStderr() != null) {
                String string = "remove canceled migration record failed";
                return string;
            }
            return output.getStdout();
        } catch (Exception e) {
            return e.getMessage();
        }

    }

    /**
     * Commits the completed migrations with the passed names and tears down the
     * old devices and unclaims the storage volumes.
     * */
    public static String doCommitMigrationsCommand(Host host, String generalVolumeName, Migration migration,
            List<String> migrationNames,
            boolean cleanup, boolean remove, boolean rename) throws Exception {
        List<MigrationInfo> migrationInfoList = findMigrations(host, migrationNames, migration);
        // Verify that the migrations have completed successfully and can be
        // committed.

        for (MigrationInfo migrationInfo : migrationInfoList) {
            if (migrationInfo.getStatus() != "complete") {
                throw MigrationControllerException.exceptions
                        .cantCommitedMigrationNotCompletedSuccessfully(migrationInfo.getName());
            }
        }
        String srcDevice = migration.getSrcDev();
        String tgtDevice = migration.getTgtDev();
        String args = String.format("srcDevice=%s tgtDevice=%s", srcDevice, tgtDevice);
        LinuxSystemCLI cli = LinuxHostDiscoveryAdapter.createLinuxCLI(host);
        commitMigrationsCommand command = new commitMigrationsCommand(args);
        cli.executeCommand(command);
        try {
            CommandOutput output = command.getOutput();
            if (output.getStderr() != null) {
                String string = "commit Migration failed";
                return string;
            }
            return output.getStdout();
        } catch (Exception e) {
            return e.getMessage();
        }
    }
    private static void checkStatus(MultiPathEntry entry) {
        for (PathInfo path : entry.getPaths()) {
            if (path.isFailed()) {
                _log.warn("find.multipath.wwn.failed", entry.getWwid(), path.getDevice());
            }
        }
    }

    private static MultiPathEntry findMultiPathEntry(Volume volume, List<MultiPathEntry> multipathEntries) {
        for (MultiPathEntry entry : multipathEntries) {
            String entryWwn = stripWwnPrefix(entry.getWwid());
            _log.debug("FindMultiPathEntryForVolume.checking", entry.getName(), entryWwn, volume.getWWN());
            if (wwnMatches(entryWwn, volume.getWWN())) {
                return entry;
            }
        }
        _log.debug("FindMultiPathEntryForVolume.noEntries", volume.getWWN());
        return null;
    }

    private static String stripWwnPrefix(String wwn) {
        return wwn.startsWith(LINUX_WWN_PREFIX) ? wwn.substring(1) : wwn;
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

}
