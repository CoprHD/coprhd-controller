/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.vipr.model.catalog.SortedIndexRestRep;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SortedIndexUtils {
    
    public static void moveUp(SortedIndexDataObject sortableDataObject, ModelClient modelClient) {
        move(sortableDataObject, modelClient, true);
    }

    public static void moveDown(SortedIndexDataObject sortableDataObject, ModelClient modelClient) {
        move(sortableDataObject, modelClient, false);
    }    
    
    public static int getNextSortedIndex(SortedIndexDataObject value, ModelClient modelClient) {
        return getNextSortedIndex(getSortedSiblings(value, modelClient));
    }
    
    public static int getNextSortedIndex(List<? extends SortedIndexDataObject> values) {
        int nextSortedIndex = 1;
        int maxSortedIndex = getMaxSortedIndex(values);
        if (maxSortedIndex > 0)  {
            nextSortedIndex = maxSortedIndex + 1;
        }
        return nextSortedIndex;
    }
    
    public static int getMaxSortedIndex(SortedIndexDataObject value, ModelClient modelClient) {
        return getMaxSortedIndex(getSortedSiblings(value, modelClient));
    }
    
    public static int getMaxSortedIndex(List<? extends SortedIndexDataObject> values) {
        int maxSortedIndex = Integer.MIN_VALUE;
        if (values != null) {
            for (SortedIndexDataObject value : values) {
                if (value != null && value.getSortedIndex() != null && value.getSortedIndex().intValue() > maxSortedIndex) {
                    maxSortedIndex = value.getSortedIndex().intValue();
                }
            }
        }
        return maxSortedIndex;
    }    
    
    public static void sort(List<? extends SortedIndexDataObject> objects) {
        Collections.sort(objects, new SortedIndexComparator());
    }
    
    public static <T extends SortedIndexRestRep> List<T> createSortedList(Iterator<T> objects) {
        List<T> list = copyIterator(objects);
        Collections.sort(list, new SortedIndexRestRepComparator());
        return list;
    }   
    
    private static <T> List<T> copyIterator(Iterator<T> iter) {
        List<T> copy = new ArrayList<T>();
        while (iter.hasNext()) {
            copy.add(iter.next());
        }
        return copy;
    }    
    
    private static void move(SortedIndexDataObject sortableDataObject, ModelClient modelClient, boolean up) {
        List<SortedIndexDataObject> values = getSortedSiblings(sortableDataObject, modelClient);
        for (int i = 0; i < values.size(); i++) {
            SortedIndexDataObject value = values.get(i);
            if (value != null && value.getId() != null && value.getId().equals(sortableDataObject.getId())) {
                if (up == true && i != 0) {
                    SortedIndexDataObject previousValue = values.get(i - 1);
                    swap(previousValue, value);
                    save(previousValue, modelClient);
                    save(value, modelClient);
                }
                else if (i < (values.size() - 1)) {
                    SortedIndexDataObject nextValue = values.get(i + 1);
                    swap(value, nextValue);
                    save(value, modelClient);
                    save(nextValue, modelClient);
                }
            }
        }
    }    
    
    private static void swap(SortedIndexDataObject left, SortedIndexDataObject right) {
        Integer leftSortedIndex = left.getSortedIndex();
        left.setSortedIndex(right.getSortedIndex());
        right.setSortedIndex(leftSortedIndex);        
    }
        
    private static void fixSortedIndexValues(List<? extends SortedIndexDataObject> values, ModelClient modelClient) {
        if (values != null) {
            
            // Clean up any nulls
            for (SortedIndexDataObject value : values) {
                if (value != null) {
                    if (value.getSortedIndex() == null) {
                        value.setSortedIndex(getNextSortedIndex(values));
                        save(value, modelClient);
                    }
                }
            }

            // Clean up duplicate sorted indexes
            List<Integer> sortedIndexes = Lists.newArrayList();
            for (SortedIndexDataObject value : values) {
                if (value != null) {
                    if (sortedIndexes.contains(value.getSortedIndex()) == false) {
                        sortedIndexes.add(value.getSortedIndex());
                    }
                    else {
                        // Found Duplicate, reset sorted index
                        value.setSortedIndex(getNextSortedIndex(values));
                        save(value, modelClient);                 
                    }
                }
            }
            
        }
    }
    
    private static void save(SortedIndexDataObject value, ModelClient modelClient) {
        if (value != null && value instanceof DataObject && modelClient != null) {
            modelClient.save((DataObject)value);
        }           
    }
        
    private static List<SortedIndexDataObject> getSortedSiblings(SortedIndexDataObject sortableDataObject, ModelClient modelClient) {
        List<SortedIndexDataObject> results = Lists.newArrayList();
        
        if (sortableDataObject != null) {
            if (sortableDataObject instanceof CatalogCategory) {
                CatalogCategory catalogCategory = (CatalogCategory) sortableDataObject;
                if (catalogCategory.getCatalogCategoryId() != null) {
                    results.addAll(modelClient.catalogCategories().findSubCatalogCategories(catalogCategory.getCatalogCategoryId().getURI()));
                }
                else {
                    throw new IllegalStateException("getSortedSiblings: catalogCategoryId is required for CatalogCategory");
                }
            }
            else if (sortableDataObject instanceof CatalogService) {
                CatalogService catalogService = (CatalogService) sortableDataObject;
                if (catalogService.getCatalogCategoryId() != null) {
                    results.addAll(modelClient.catalogServices().findByCatalogCategory(catalogService.getCatalogCategoryId().getURI()));
                }
                else {
                    throw new IllegalStateException("getSortedSiblings: catalogCategoryId is required for CatalogService");
                }                
            }
            else if (sortableDataObject instanceof CatalogServiceField) {
                CatalogServiceField catalogServiceField = (CatalogServiceField) sortableDataObject;
                if (catalogServiceField.getCatalogServiceId() != null) {
                    results.addAll(modelClient.catalogServiceFields().findByCatalogService(catalogServiceField.getCatalogServiceId().getURI()));
                }
                else {
                    throw new IllegalStateException("getSortedSiblings: catalogServiceId is required for CatalogServiceField");
                }                
            }
            else if (sortableDataObject instanceof OrderParameter) {
                OrderParameter orderParameter = (OrderParameter) sortableDataObject;
                if (orderParameter.getOrderId() != null) {
                    results.addAll(modelClient.orderParameters().findByOrderId(orderParameter.getOrderId()));
                }
                else {
                    throw new IllegalStateException("getSortedSiblings: orderId is required for OrderParameter");
                }                
            }
            else {
                throw new IllegalStateException("Unknown SortedIndex Type");
            }
        }
        
        fixSortedIndexValues(results, modelClient);
        
        sort(results);
        
        return results;
    }
}
