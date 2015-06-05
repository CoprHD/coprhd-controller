/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.support;

import java.util.Comparator;

import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.collections.comparators.ComparatorChain;
import org.apache.commons.collections.comparators.NullComparator;

import com.emc.storageos.model.DataObjectRestRep;

public class CreationTimeComparator implements Comparator<DataObjectRestRep> {
    
    private static final String CREATION_TIME = "creationTime";
    
    private ComparatorChain COMPARATOR;
    
    private boolean reverseOrder = false;
    
    public CreationTimeComparator() {
        
    }
    
    public CreationTimeComparator(boolean reverseOrder) {
        this.reverseOrder = reverseOrder;
    }
    
    @Override
    public int compare(DataObjectRestRep o1, DataObjectRestRep o2) {
        return getComparator().compare(o1, o2);
    }
    
    private ComparatorChain getComparator() {
        if (COMPARATOR == null) {
            COMPARATOR = new ComparatorChain();
            COMPARATOR.addComparator(new BeanComparator(CREATION_TIME, new NullComparator()), reverseOrder);
        }
        return COMPARATOR;
    }

    public static final CreationTimeComparator NEWEST_FIRST = new CreationTimeComparator(true);
    public static final CreationTimeComparator OLDEST_FIRST = new CreationTimeComparator();
}
