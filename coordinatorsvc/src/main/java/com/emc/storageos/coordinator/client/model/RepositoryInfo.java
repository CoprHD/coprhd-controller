/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.emc.storageos.services.util.Strings;

import com.emc.storageos.coordinator.exceptions.DecodingException;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.InvalidRepositoryInfoException;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;

public class RepositoryInfo implements CoordinatorSerializable {
    public static final String CONFIG_KIND = "upgradetargetconfig";
    public static final String CONFIG_ID = "global";

    private static final String ENCODING_INVALID = "";
    private static final String ENCODING_SEPARATOR = "\0";

    private final SoftwareVersion _current;
    private final List<SoftwareVersion> _versions;

    public RepositoryInfo() {
        _current = null;
        _versions = null;
    }

    public RepositoryInfo(final SoftwareVersion current, final List<SoftwareVersion> available)
            throws InvalidRepositoryInfoException {
        if (current == null || available == null || !available.contains(current)) {
            throw CoordinatorException.fatals.invalidRepoInfoError("current=" + Strings.repr(current) + " versions="
                    + Strings.repr(available));

        }

        List<SoftwareVersion> versions = new ArrayList<SoftwareVersion>(available);
        Collections.sort(versions);
        _versions = Collections.unmodifiableList(versions);
        _current = current;
    }

    public List<SoftwareVersion> getVersions() {
        return _versions;
    }

    public SoftwareVersion getCurrentVersion() {
        return _current;
    }

    public boolean hasSameVersions(final RepositoryInfo state) {
        return _versions.equals(state._versions);
    }

    @Override
    public String toString() {
        return "current=" + Strings.repr(_current) + " available=" + Strings.repr(_versions);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        final RepositoryInfo state = (RepositoryInfo) object;
        return (_current == null ? state._current == null : _current.equals(state._current)) &&
                hasSameVersions(state);
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        return builder.append(this._current).append(this._versions).toHashCode();
    }

    @Override
    public String encodeAsString() {
        final StringBuilder s = new StringBuilder();

        s.append(_current != null ? _current : ENCODING_INVALID);

        for (SoftwareVersion v : _versions) {
            s.append(ENCODING_SEPARATOR);
            s.append(v);
        }
        return s.toString();
    }

    @Override
    public RepositoryInfo decodeFromString(String infoStr) throws DecodingException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length == 0) {
            throw CoordinatorException.fatals.decodingError("Empty string");
        }

        try {
            final SoftwareVersion current = new SoftwareVersion(strings[0]);
            final List<SoftwareVersion> versions = new ArrayList<SoftwareVersion>();
            for (int i = 1; i < strings.length; i++) {
                versions.add(new SoftwareVersion(strings[i]));
            }
            return new RepositoryInfo(current, versions);
        } catch (InvalidSoftwareVersionException e) {
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        } catch (InvalidRepositoryInfoException e) {
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "repositoryInfo");
    }
}
