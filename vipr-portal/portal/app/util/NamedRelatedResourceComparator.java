/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Comparator;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;

public class NamedRelatedResourceComparator implements Comparator<NamedRelatedResourceRep> {
    @Override
    public int compare(NamedRelatedResourceRep first, NamedRelatedResourceRep second) {
        String firstName = first.getName();
        String secondName = second.getName();
        int result = ObjectUtils.compare(StringUtils.lowerCase(firstName), StringUtils.lowerCase(secondName));
        if (result == 0) {
            result = ObjectUtils.compare(firstName, secondName);
        }
        return result;
    }
}
