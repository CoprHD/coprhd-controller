package com.emc.sa.service.vipr.customservices.tasks;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnsibleHelper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AnsibleHelper.class);

    public static String getOptions(final String key, final Map<String, List<String>> input) {
        if (input.get(key) != null) {
            return StringUtils.strip(input.get(key).get(0).toString(), "\"");
        }

        logger.info("key not defined. key:{}", key);

        return null;
    }

    /**
     * Ansible extra Argument format:
     * --extra_vars "key1=value1 key2=value2"
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static String makeExtraArg(final Map<String, List<String>> input) throws Exception {
        if (input == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            // TODO find a better way to fix this
            if (e.getValue() != null && !StringUtils.isEmpty(e.getValue().get(0))) {
                sb.append(e.getKey()).append("=").append(e.getValue().get(0).replace("\"", "")).append(" ");
            }
        }
        logger.info("extra vars:{}", sb.toString());

        return sb.toString().trim();
    }

    public static String parseOut(final String out) {
        final String regexString = Pattern.quote("output_start") + "(?s)(.*?)" + Pattern.quote("output_end");
        final Pattern pattern = Pattern.compile(regexString);
        final Matcher matcher = pattern.matcher(out);

        while (matcher.find()) {
            return matcher.group(1);
        }

        return out;
    }

}
