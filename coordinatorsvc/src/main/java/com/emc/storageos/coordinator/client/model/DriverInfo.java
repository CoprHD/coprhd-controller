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

    private final List<String> drivers;

    public DriverInfo(List<String> drivers) {
        List<String> tmp = new ArrayList<String>(drivers);
        Collections.sort(tmp);
        this.drivers = Collections.unmodifiableList(tmp);
    }

    public List<String> getDrivers() {
        return drivers;
    }

    @Override
    public String encodeAsString() {
        if (drivers == null || drivers.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String driver : drivers) {
            builder.append(driver).append(ENCODING_SEPARATOR);
        }
        return builder.toString();
    }

    @Override
    public DriverInfo decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null || infoStr.isEmpty()) {
            return null;
        }
        String[] drivers = infoStr.split(ENCODING_SEPARATOR);
        return new DriverInfo(Arrays.asList(drivers));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(ID, KIND, ATTR);
    }
}
