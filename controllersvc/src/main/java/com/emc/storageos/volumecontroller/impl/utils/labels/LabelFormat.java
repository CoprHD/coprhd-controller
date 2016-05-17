package com.emc.storageos.volumecontroller.impl.utils.labels;

import java.util.Collection;

/**
 * An interface for matching against a label format and providing
 * the "next" label.
 *
 * @see CountingSuffix
 *
 * @author Ian Bibby
 */
public interface LabelFormat {
    /**
     * Match against a single label.
     *
     * @param label A single label.
     * @return true, if the label's format is a match.  False otherwise.
     */
    boolean matches(String label);

    /**
     * Match against a collection of labels.
     *
     * @param label Collection of labels.
     * @return true, if all the label's formats are a match.  False otherwise.
     */
    boolean matches(Collection<String> label);

    /**
     * Return the next label based on the matching format.
     *
     * @see CountingSuffix
     * @return The next label.
     */
    String next();
}
