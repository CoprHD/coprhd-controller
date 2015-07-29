/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.asString;
import static com.emc.vipr.client.core.util.ResourceUtils.name;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;

import jobs.vipr.ConnectedBlockVirtualPoolsCall;
import models.SizeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import play.data.validation.Required;
import play.data.validation.Validation;
import util.MessagesUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;

import com.emc.storageos.model.vpool.ProtectionCopyPolicy;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;

public class RPCopyForm {
    public static final int JOURNAL_MIN_SIZE_IN_GB = 10;
    public static final String JOURNAL_SIZE_MIN = "min";
    public static final String JOURNAL_DEFAULT_MULTIPLIER = "0.25";

    @Required
    public String virtualArray;
    public String virtualArrayName;
    public String virtualPool;
    public String virtualPoolName;
    public String journalVArray;
    public String journalVArrayName;
    public String journalVPool;
    public String journalVPoolName;
    public String journalSize;
    public SizeUnit journalSizeUnit;
    public String formattedJournalSize;

    public boolean isEnabled() {
        return StringUtils.isNotBlank(virtualArray);
    }

    public void validate(String formName) {
        Validation.valid(formName, this);
        if (!isValidJournalSize(journalSize, journalSizeUnit)) {
            Validation.addError(formName + ".journalSize", "validation.invalid");
        }
    }

    public void load(VirtualPoolProtectionVirtualArraySettingsParam copy) {
        String none = MessagesUtils.get("common.none");
        virtualArray = asString(copy.getVarray());
        virtualArrayName = StringUtils.defaultString(name(VirtualArrayUtils.getVirtualArray(virtualArray)), none);
        virtualPool = asString(copy.getVpool());
        virtualPoolName = StringUtils.defaultString(name(VirtualPoolUtils.getBlockVirtualPool(virtualPool)), none);
        journalVArray = asString(copy.getCopyPolicy().getJournalVarray());
        journalVArrayName = StringUtils.defaultString(name(VirtualArrayUtils.getVirtualArray(journalVArray)), none);
        journalVPool = asString(copy.getCopyPolicy().getJournalVpool());
        journalVPoolName = StringUtils.defaultString(name(VirtualPoolUtils.getBlockVirtualPool(journalVPool)), none);
        journalSizeUnit = parseJournalSizeUnit(copy.getCopyPolicy().getJournalSize());
        journalSize = parseJournalSize(copy.getCopyPolicy().getJournalSize(), journalSizeUnit);
        formattedJournalSize = getFormattedJournalSize();
    }

    public VirtualPoolProtectionVirtualArraySettingsParam write() {
        VirtualPoolProtectionVirtualArraySettingsParam param = new VirtualPoolProtectionVirtualArraySettingsParam();
        param.setCopyPolicy(new ProtectionCopyPolicy(getFormattedJournalSize()));
        param.setCopyPolicy(new ProtectionCopyPolicy(getFormattedJournalSize(), uri(journalVArray), uri(journalVPool)));
        param.setVarray(uri(virtualArray));
        param.setVpool(uri(virtualPool));
        return param;
    }

    public String getFormattedJournalSize() {
        return formatJournalSize(journalSize, journalSizeUnit);
    }

    public ConnectedBlockVirtualPoolsCall recoverPointVirtualPools() {
        List<URI> varrayIds = StringUtils.isNotBlank(virtualArray) ? uris(virtualArray) : uris();
        return new ConnectedBlockVirtualPoolsCall(varrayIds);
    }

    public ConnectedBlockVirtualPoolsCall recoverPointJournalVirtualPools() {
        List<URI> varrayIds = StringUtils.isNotBlank(journalVArray) ? uris(journalVArray) : uris();
        return new ConnectedBlockVirtualPoolsCall(varrayIds);
    }

    public static SizeUnit parseJournalSizeUnit(String value) {
        if (StringUtils.isBlank(value) || StringUtils.equalsIgnoreCase(value, JOURNAL_SIZE_MIN)) {
            // Defaults the dropdown to GB even if the value is empty or 'min'
            return SizeUnit.GB;
        }

        for (SizeUnit unit : SizeUnit.values()) {
            if (StringUtils.endsWithIgnoreCase(value, unit.name())) {
                return unit;
            }
        }
        return SizeUnit.Bytes;
    }

    public static String parseJournalSize(String value, SizeUnit unit) {
        String unitStr = StringUtils.lowerCase(unit.name());
        String journalSize = StringUtils.replace(StringUtils.lowerCase(value), unitStr, "");
        return StringUtils.trim(journalSize);
    }

    public static String formatJournalSize(String value, SizeUnit unit) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        else if (StringUtils.equalsIgnoreCase(value, JOURNAL_SIZE_MIN)) {
            return JOURNAL_SIZE_MIN;
        }
        else if (unit == SizeUnit.x) {
            // Check for a valid double
            double val = NumberUtils.toDouble(value, -1);
            if (val < 0) {
                return null;
            }
            return value + unit.name();
        }
        else {
            // Check for a valid integer
            int val = NumberUtils.toInt(value, -1);
            if (val < 0) {
                return null;
            }

            if (convertToGB(val, unit) < JOURNAL_MIN_SIZE_IN_GB) {
                // invalid journal size
                return null;
            }

            return (unit != SizeUnit.Bytes) ? value + unit.name() : value;
        }
    }

    public static double convertToGB(int value, SizeUnit unit) {
        double sizeInGB = 0.0;
        if (unit == null || SizeUnit.Bytes.equals(unit)) {
            sizeInGB = value / 1024 / 1024;
        }
        else if (SizeUnit.MB.equals(unit)) {
            sizeInGB = value / 1024;
        }
        else if (SizeUnit.GB.equals(unit)) {
            sizeInGB = value;
        }
        else if (SizeUnit.TB.equals(unit)) {
            sizeInGB = value * 1024;
        }
        return sizeInGB;
    }

    public static boolean isValidJournalSize(String value, SizeUnit unit) {
        if (StringUtils.isNotBlank(value)) {
            return formatJournalSize(value, unit) != null;
        }
        else {
            return true;
        }
    }
}
