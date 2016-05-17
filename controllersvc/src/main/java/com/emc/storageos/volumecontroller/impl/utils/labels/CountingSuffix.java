package com.emc.storageos.volumecontroller.impl.utils.labels;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

/**
 * Supports label formats with a "-X" suffix, where X is a number representing a
 * count of something.
 *
 * For example, given a label "foo-0", calling #next would return "foo-1".
 *
 * @author Ian Bibby
 */
public class CountingSuffix implements LabelFormat {

    private static final String HYPEN_AND_NUMBER_SUFFIX = "^(.*-)(\\d+)$";
    private static final int MAIN_GROUP = 1;
    private static final int COUNT_GROUP = 2;

    private Pattern pattern;
    private Collection<String> labels;
    private String label;
    private Integer lastCount;

    public CountingSuffix() {
        pattern = Pattern.compile(HYPEN_AND_NUMBER_SUFFIX);
    }

    @Override
    public boolean matches(String label) {
        return matches(asList(label));
    }

    @Override
    public boolean matches(Collection<String> labels) {
        for (String l : labels) {
            if (!pattern.matcher(l).matches()) {
                return false;
            }
        }

        this.labels = labels;

        return true;
    }

    @Override
    public String next() {
        if (lastCount == null) {
            findNextCount();
        }

        return label + (++lastCount);
    }

    private void findNextCount() {
        SortedSet<String> sortedLabels = sortLabels(labels);
        String highestLabel = sortedLabels.last();

        Matcher matcher = pattern.matcher(highestLabel);
        matcher.matches();

        label = matcher.group(MAIN_GROUP);
        // Regex will ensure "count" is a number.
        lastCount = Integer.valueOf(matcher.group(COUNT_GROUP));
    }

    private SortedSet<String> sortLabels(Collection<String> labels) {
        return new TreeSet<>(labels);
    }

}
