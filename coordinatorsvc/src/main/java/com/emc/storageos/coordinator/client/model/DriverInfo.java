package com.emc.storageos.coordinator.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class DriverInfo implements CoordinatorSerializable {

    public static final String ID = "global";
    public static final String KIND = "storagesystemdriver";
    public static final String ATTR = "driver";
    public static final String ENCODING_SEPARATOR = "\0";

    private List<String> drivers;
    private String finishNode;

    public DriverInfo() {
        this.drivers = Collections.unmodifiableList(new ArrayList<String>());
        this.finishNode = null;
    }

    public DriverInfo(String finishNode, List<String> drivers) {
        this.finishNode = finishNode;
        List<String> tmp = new ArrayList<String>(drivers);
        Collections.sort(tmp);
        this.drivers = Collections.unmodifiableList(tmp);
    }

    public String getFinishNode() {
        return this.finishNode;
    }

    public List<String> getDrivers() {
        return drivers;
    }

    @Override
    public String encodeAsString() {
        StringBuilder builder = new StringBuilder();
        if (finishNode != null && !finishNode.isEmpty()) {
            builder.append(finishNode);
        }
        builder.append(ENCODING_SEPARATOR);

        if (drivers == null || drivers.isEmpty()) {
            builder.append(",");
        } else {
            for (String driver : drivers) {
                builder.append(driver).append(",");
            }
        }
        builder.append(ENCODING_SEPARATOR);
        return builder.toString();
    }

    @Override
    public DriverInfo decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null || infoStr.isEmpty()) {
            return null;
        }
        String[] fields = infoStr.split(ENCODING_SEPARATOR);
        return new DriverInfo(fields[0], Arrays.asList(fields[1].split(",")));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(ID, KIND, ATTR);
    }
}
