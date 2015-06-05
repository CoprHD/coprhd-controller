/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import com.google.common.base.Strings;

import java.util.UUID;

/**
 * This generator will return the user specified label. The name will have
 * non-alphanumeric and whitespace characters removed. It will be truncated to the
 * maxLength if necessary.
 */
public class ExportMaskNameGenerator extends ResourceOnlyNameGenerator {

    public static final int maxLength = 64;
    public static final String INVALID_CHARS_REGEX = "\\s+|[^a-zA-Z0-9_#@\\+\\-]";

    @Override
    public String generate(String firstName, String secondName, String alternateName, char delimiter, int maxLength) {
        String alternateNameIfAny = (Strings.isNullOrEmpty(alternateName)) ? "" : alternateName.replaceAll(INVALID_CHARS_REGEX, "-");
        String firstNameToUse = (Strings.isNullOrEmpty(firstName)) ? "" : firstName.replaceAll(INVALID_CHARS_REGEX, "-");
        String secondNameToUse = (Strings.isNullOrEmpty(secondName)) ? "" : secondName.replaceAll(INVALID_CHARS_REGEX, "-");

        // At this point we would have some combination of the alternateName, firstName, and secondName.
        // The alternateName would come before the firstName. And the firstName would come before the secondName.
        String result = assembleName(firstNameToUse, secondNameToUse, alternateNameIfAny, delimiter);

        // If the resulting string is longer than the maxLength,
        // then truncate the export and/or the resource names,
        // so the result length is at most maxLength.
        if (result.length() > maxLength) {
            int firstNameToUseLength = firstNameToUse.length();
            int alternateNameLength = alternateNameIfAny.length();
            int whatsLeft = maxLength - secondNameToUse.length();

            if (firstNameToUseLength > 0) {
                whatsLeft--; // For delimiter
            }

            if (alternateNameLength > 0) {
                whatsLeft--; // For delimiter
            }

            if (whatsLeft < 0) {
                return secondNameToUse.substring(0, maxLength);
            }

            int whatsExtra = (firstNameToUseLength + alternateNameLength) - whatsLeft;
            // Truncate the name that is longer
            if ((whatsExtra > whatsLeft) || (firstNameToUseLength == alternateNameLength)) {
                int truncateLength = (alternateNameLength > 0) ? whatsLeft/2 : whatsLeft;
                String adjustedFirstName = (truncateLength > firstNameToUseLength) ? firstNameToUse : firstNameToUse.substring(0, truncateLength);
                String adjustedAlternateName = (alternateNameLength == 0 || truncateLength > alternateNameLength) ?
                        alternateNameIfAny : alternateNameIfAny.substring(0, truncateLength);
                result = assembleName(adjustedFirstName, secondNameToUse, adjustedAlternateName, delimiter);
            } else if (alternateNameLength > firstNameToUseLength) {
                String adjustedAlternateName = alternateNameIfAny.substring(0, alternateNameLength - whatsExtra);
                result = assembleName(firstNameToUse, secondNameToUse, adjustedAlternateName, delimiter);
            } else if (firstNameToUseLength > alternateNameLength) {
                String adjustedFistName = firstNameToUse.substring(0, firstNameToUseLength - whatsExtra);
                result = assembleName(adjustedFistName, secondNameToUse, alternateNameIfAny, delimiter);
            }
        }

        return result;
    }

    public String generate(String clusterName, String hostName, String alternateName) {
        return generate(clusterName, hostName, alternateName, '_', maxLength);
    }

    private String assembleName(String firstNameToUse, String secondNameToUse,
                                String alternateNameIfAny, char delimiter) {
        StringBuilder result = new StringBuilder();
        // Assemble the name using each piece if it is non-null/empty
        if (!Strings.isNullOrEmpty(alternateNameIfAny)) {
            result.append(alternateNameIfAny);
        }

        if (!Strings.isNullOrEmpty(firstNameToUse)) {
            // If result has some size, then we want to the delimiter
            // to prepended to the firstNameToUse
            if (result.length() > 0) {
                result.append(delimiter);
            }
            result.append(firstNameToUse);
        }

        if (!Strings.isNullOrEmpty(secondNameToUse)) {
            // If result has some size, then we want to the delimiter
            // to prepended to the secondNameToUse
            if (result.length() > 0) {
                result.append(delimiter);
            }
            result.append(secondNameToUse);
        }
        return result.toString();
    }
}
