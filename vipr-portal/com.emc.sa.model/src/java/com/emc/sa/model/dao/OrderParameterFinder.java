/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.google.common.collect.Lists;

public class OrderParameterFinder extends ModelFinder<OrderParameter> {

    public OrderParameterFinder(DBClientWrapper client) {
        super(OrderParameter.class, client);
    }

    public List<OrderParameter> findByOrderId(URI orderId) {
        if (orderId == null) {
            return Lists.newArrayList();
        }
        
        List<NamedElement> orderParameterIds = client.findBy(OrderParameter.class, OrderParameter.ORDER_ID, orderId);

        List<OrderParameter> orderParameters = findByIds(toURIs(orderParameterIds));
        
        SortedIndexUtils.sort(orderParameters);
        
        return orderParameters;
    }
    
}
