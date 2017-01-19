/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm;

import java.io.Serializable;

/**
 * This class is a container for two related objects. One (or even both...) of
 * the objects can be <tt>null</tt>.
 * 
 * @param <A>
 *            Any Object.
 * @param <B>
 *            Any Object.
 */
public class Pair<A, B> implements Serializable {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 6046962697228098232L;

    /**
     * The first element.
     */
    private A firstElement;

    /**
     * The second element.
     */
    private B secondElement;

    /**
     * Empty constructor.
     */
    public Pair() {

    }

    /**
     * Constructor.
     * 
     * @param firstElement
     *            The first element in this pair.
     * @param secondElement
     *            The second element in this pair.
     */
    public Pair(A firstElement, B secondElement) {
        this.firstElement = firstElement;
        this.secondElement = secondElement;
    }

    /**
     * Gets the first element.
     * 
     * @return first element.
     */
    public A getFirstElement() {
        return this.firstElement;
    }

    /**
     * Gets the second element.
     * 
     * @return second element.
     */
    public B getSecondElement() {
        return this.secondElement;
    }

    /**
     * Sets the first element.
     * 
     * @param firstElement
     *            first element.
     */
    public void setFirstElement(A firstElement) {
        this.firstElement = firstElement;
    }

    /**
     * Sets the second element.
     * 
     * @param secondElement
     *            second element.
     */
    public void setSecondElement(B secondElement) {
        this.secondElement = secondElement;
    }

    /**
     * Returns <tt>true</tt> if both elements of the pair are equal according to
     * their <tt>equals</tt> method. If both elements are <tt>null</tt>, this
     * method will return <tt>true</tt>.
     * 
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Pair)) {
            return false;
        }
        Pair duet = (Pair) obj;

        return equalsAcceptNull(firstElement, duet.getFirstElement())
                && equalsAcceptNull(secondElement, duet.getSecondElement());
    }

    /**
     * Returns <code>A.hashCode() ^ B.hashCode()</code>. If either A or B is
     * null, its hash code will be 0.
     * 
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return (firstElement == null ? 0 : firstElement.hashCode())
                ^ (secondElement == null ? 0 : this.secondElement.hashCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append(firstElement != null ? firstElement.toString() : "null");
        builder.append(",");
        builder.append(secondElement != null ? secondElement.toString() : "null");
        builder.append("]");
        return builder.toString();
    }

    /**
     * Compares two objects using the <tt>equals</tt> method. If both objects
     * are <tt>null</tt> it will be assumed that they are equal.
     * 
     * @param obj1
     *            The first object.
     * @param obj2
     *            The second object.
     * @return <tt>true</tt> if objects are equal (including if both are null),
     *         false otherwise.
     */
    private boolean equalsAcceptNull(Object obj1, Object obj2) {

        // If both objects are null, return true...
        if (obj1 == null && obj2 == null) {
            return true;
        }

        // Else, make sure that none of them are null and use the equals
        // method...
        return obj1 != null && obj2 != null && obj1.equals(obj2);
    }
}