package com.emc.storageos.volumecontroller.impl.utils.labels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Factory class for acquiring a matching {@link LabelFormat} implementation for a
 * given label.
 *
 * @author Ian Bibby
 */
public class LabelFormatFactory {
    private static final Logger log = LoggerFactory.getLogger(LabelFormatFactory.class);

    private List<LabelFormat> labelFormats = new ArrayList<>();

    /**
     * Default constructor.
     */
    public LabelFormatFactory() {
        // TODO Switch to injecting these as dependencies.
        labelFormats.add(new CountingSuffix());
    }

    /**
     * Return a {@link LabelFormat} implementation appropriate for the format of the given label.
     *
     * @param label A single label.
     * @return An instance of LabelFormat.
     */
    public LabelFormat getLabelFormat(final String label) {
        return getLabelFormat(asList(label));
    }

    /**
     * Return a {@link LabelFormat} implementation appropriate for the format of the given labels.
     *
     * @param labels A collection of labels.
     * @return An instance of LabelFormat.
     */
    public LabelFormat getLabelFormat(final Collection<String> labels) {

        for (LabelFormat labelFormat : labelFormats) {
            if (labelFormat.matches(labels)) {
                return labelFormat;
            }
        }

        DefaultLabelFormat defaultLabelFormat = new DefaultLabelFormat();
        defaultLabelFormat.matches(labels);
        return defaultLabelFormat;
    }
}
