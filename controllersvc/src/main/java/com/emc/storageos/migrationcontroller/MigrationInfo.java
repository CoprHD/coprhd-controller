package com.emc.storageos.migrationcontroller;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

public class MigrationInfo {
    // Enumerates the migration attributes we are interested in
    // There must be a setter method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum MigrationAttribute {
        STATUS("status"),
        SOURCE_NAME("source"),
        TARGET_NAME("target"),
        PERCENTAGE_DONE("percentage-done"),
        START_TIME("start-time");

        private final String _name;

        /**
         * Constructor.
         * 
         * @param name The attribute name.
         */
        MigrationAttribute(String name) {
            _name = name;
        }

        /**
         * Getter for the name for the attribute.
         * 
         * @return The name for the attribute.
         */
        public String getAttributeName() {
            return _name;
        }

        /**
         * Returns the enum whose name matches the passed name, else null when
         * not found.
         * 
         * @param name The name to match.
         * 
         * @return The enum whose name matches the passed name, else null when
         *         not found.
         */
        public static MigrationAttribute valueOfAttribute(String name) {
            MigrationAttribute[] migrationAtts = values();
            for (int i = 0; i < migrationAtts.length; i++) {
                if (migrationAtts[i].getAttributeName().equals(name)) {
                    return migrationAtts[i];
                }
            }
            return null;
        }
    };

    // Enumerates the migration status values
    public static enum MigrationStatus {
        IN_PROGRESS("in-progress"),
        COMPLETE("complete"),
        PAUSED("paused"),
        CANCELLED("cancelled"),
        COMMITTED("committed"),
        READY("ready"),
        ERROR("error"),
        PARTIALLY_COMMITTED("partially-committed"),
        PARTIALLY_CANCELLED("partially-cancelled"),
        QUEUED("queued");

        // The value for the status.
        private final String _status;

        /**
         * Constructor.
         * 
         * @param status The VPlex status value.
         */
        MigrationStatus(String status) {
            _status = status;
        }

        /**
         * Getter for the VPlex value for the status.
         * 
         * @return The VPlex value for the status.
         */
        public String getStatusValue() {
            return _status;
        }
    }

    // A reference to the source volume info associated with the migration
    // when the migration is created.
    private VolumeInfo generalVolumeInfo;

    // The status of the migration.
    private String status;

    // The name of the source device/extent.
    private String sourceName;

    // The name of the target device/extent.
    private String targetName;

    // The percentage done for the migration.
    private String percentageDone;

    // The start time of the migration.
    private String startTime;

    // Flag indicates if the migration is a device migration (true), or
    // extent migration (false).
    private boolean isDeviceMigration;

    /**
     * Setter for the virtual volume info for the migration.
     * 
     * @return The virtual volume info for the migration.
     */
    public VolumeInfo getGeneralVolumeInfo() {
        return generalVolumeInfo;
    }

    /**
     * Setter for the virtual volume info for the migration.
     * 
     * @param volumeInfo The virtual volume info for the migration.
     */
    public void setVirtualVolumeInfo(VolumeInfo volumeInfo) {
        generalVolumeInfo = volumeInfo;
    }

    /**
     * Getter for the migration status.
     * 
     * @return The migration status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Setter for the migration status.
     * 
     * @param strVal The migration status.
     */
    public void setStatus(String strVal) {
        status = strVal;
    }

    /**
     * Getter for the name of the migration source device/extent.
     * 
     * @return The name of the migration source.
     */
    public String getSource() {
        return sourceName;
    }

    /**
     * Setter for the name of the migration source device/extent.
     * 
     * @param strVal The name of the migration source.
     */
    public void setSource(String strVal) {
        sourceName = strVal;
    }

    /**
     * Getter for the name of the migration target device/extent.
     * 
     * @return The name of the migration target.
     */
    public String getTarget() {
        return targetName;
    }

    /**
     * Setter for the name of the migration target device/extent.
     * 
     * @param strVal The name of the migration target.
     */
    public void setTarget(String strVal) {
        targetName = strVal;
    }

    /**
     * Getter for the percentage done.
     * 
     * @return The percentage done.
     */
    public String getPercentageDone() {
        return percentageDone;
    }

    /**
     * Setter for the percentage done.
     * 
     * @param strVal The percentage done.
     */
    public void setPercentageDone(String strVal) {
        percentageDone = strVal;
    }

    /**
     * Getter for the migration start time.
     * 
     * @return The migration start time.
     */
    public String getStartTime() {
        return startTime;
    }

    /**
     * Setter for the migration start time.
     * 
     * @param strVal The migration start time.
     */
    public void setStartTime(String strVal) {
        startTime = strVal;
    }

    /**
     * Getter for the is device migration flag.
     * 
     * @return true for a device migration, false for an extent migration.
     */
    public boolean getIsDeviceMigration() {
        return isDeviceMigration;
    }

    /**
     * Setter for the is device migration flag.
     * 
     * @param boolVal true for a device migration, false for an extent migration.
     */
    public void setIsDeviceMigration(boolean boolVal) {
        isDeviceMigration = boolVal;
    }

    /**
     * {@inheritDoc}
     */

    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (MigrationAttribute att : MigrationAttribute.values()) {
            attFilters.add(att.getAttributeName());
        }
        return attFilters;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("MigrationInfo ( ");
        str.append(super.toString());
        str.append(", status: ").append(status);
        str.append(", sourceName: ").append(sourceName);
        str.append(", targetName: ").append(targetName);
        str.append(", percentageDone: ").append(percentageDone);
        str.append(", startTime: ").append(startTime);
        str.append(", isDeviceMigration: ").append(String.valueOf(isDeviceMigration));
        str.append(" )");

        return str.toString();
    }
}
