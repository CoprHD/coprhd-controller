package com.emc.vipr.srm.common.utils;

import java.util.Map;
import java.util.Properties;

public final class SRMQueryResource {
    
    private static Properties srmFilterQueries;
    public static final String DYNAMIC_VALUE_KEY = "v_";
    public static final String SINGLEQUOTE = "'";
    public static final String ESCAPED_SINGLE_QUOTE = "\\\\'";
    public static final String DOUBLE_ESCAPED_SINGLE_QUOTE = "\\\\\\\\'";
    public static final String NOT_APPLICABLE = "N/A";
    
    // we support only '*' and '?' as wild-cards
    public static final String BACKSLASH = "\\";

    private static final String[] _repPatterns = {
            // NOTE !!! ORDER OF REPLACEMENTS IS IMPORTANT
            BACKSLASH, BACKSLASH + BACKSLASH, ".", BACKSLASH + ".", "(", BACKSLASH + "(", ")", BACKSLASH + ")", "[",
            BACKSLASH + "[", "]", BACKSLASH + "]", "^", BACKSLASH + "^", "$", BACKSLASH + "$", "'", BACKSLASH + "'" };

    private static final String[] _repPatternsIncludeWildcards = { "*", ".*", "?", "." };

    private static final String[] _repPatternsNoWildcards = { "*", BACKSLASH + "*", "?", BACKSLASH + "?" };
    
    static {
        srmFilterQueries = (Properties) ApplicationContextUtils
                .getBean("srmQueries");
    }

    public static String fetchSRMFilterQuery(String filterQuery, final Map<String, String> valueMap) {
        String srmFilter = null;
        if(null != filterQuery && srmFilterQueries.containsKey(filterQuery) && filterQuery.endsWith("Filter")) {
            srmFilter = srmFilterQueries.getProperty(filterQuery);
        } else {
            throw new IllegalArgumentException("Filter query not found, invalid query key or query is not a filter query");
        }
        if (null != valueMap && !valueMap.isEmpty()) {
            srmFilter = constructSRMFilter(srmFilter, valueMap);
        }
        return srmFilter;
    }

    public static String srmPropertiestoFetch(String propertiesKey) {
        String srmProperties = null;
        if(null != propertiesKey && srmFilterQueries.containsKey(propertiesKey) && propertiesKey.endsWith("Properties")) {
            srmProperties = srmFilterQueries.getProperty(propertiesKey);
        } else {
            throw new IllegalArgumentException("Invalid properties key or key is not a properties list key.");
        }
        return srmProperties;
    }

    /**
     * This method constructs the Filter by replacing the dynamic values in the filter.
     * 
     * @param key
     *            the key to the filter.
     * 
     * @param valueMap
     *            the map containing the dynamic keys and values to be added in the filter.
     * @return the dynamically constructed filter.
     */
    private static String constructSRMFilter(String filter, final Map<String, String> valueMap) {

        for (String valueKey : valueMap.keySet()) {
            final String dynamicKey = DYNAMIC_VALUE_KEY + valueKey;
            final String value = valueMap.get(valueKey);

            String modifiedValue = adjustRegexpString(value);
            if (modifiedValue.contains(SINGLEQUOTE)) {
                modifiedValue = modifiedValue.replaceAll(ESCAPED_SINGLE_QUOTE,
                        DOUBLE_ESCAPED_SINGLE_QUOTE);
            }
            filter = filter.replaceAll(dynamicKey, modifiedValue);
        }
        return filter;
    }
    
    /**
     * Replaces any '*'/'?' wildcard(s) with the correct regexp value, escaping all others (including brackets, backslash,
     * etc.)
     * 
     * @param s
     *            Original string suspected of containing a regexp value (may be null empty)
     * @return Adjusted string - may be same as input if no regexp adjustment required
     */
    private static final String adjustRegexpString(final String s) {
        return adjustRegexpString(s, true);
    }
    
    /**
     * Optionaly replaces any '*'/'?' wildcard(s) with the correct regexp value, escaping all others (including brackets,
     * backslash, etc.)
     * 
     * @param s
     *            Original string suspected of containing a regexp value (may be null empty)
     * @param replaceAll
     *            - Replaces any '*'/'?' wildcard(s) with the correct regexp value
     * @return Adjusted string - may be same as input if no regexp adjustment required
     */
    private static final String adjustRegexpString(final String s, final boolean replaceAll) {
        if ((null == s) || (s.length() <= 0))
            return s;

        String regExp = s;
        // replace wild-cards:
        for (int rIndex = 0; rIndex < _repPatterns.length; rIndex += 2) {
            final String org = _repPatterns[rIndex], rep = _repPatterns[rIndex + 1];
            regExp = replacePattern(regExp, org, rep);
        }

        final String[] repAdditional = replaceAll ? _repPatternsIncludeWildcards : _repPatternsNoWildcards;
        for (int rIndex = 0; rIndex < repAdditional.length; rIndex += 2) {
            final String org = repAdditional[rIndex], rep = repAdditional[rIndex + 1];
            regExp = replacePattern(regExp, org, rep);
        }

        return regExp;
    }

    /**
     * replaces a pattern with another in a given string
     * 
     * @param str
     * @param s
     * @param t
     * @return
     */
    private static final String replacePattern(final String str, final String s, final String t) {
        final int strLen = (null == str) ? 0 : str.length();
        if (strLen <= 0)
            return str;

        final int i = str.indexOf(s);
        if (i < 0)
            return str;

        final String prefix = str.substring(0, i), remPart = str.substring(i + s.length()), suffix = replacePattern(remPart,
                s, t);
        return prefix + t + suffix;
    }
}
