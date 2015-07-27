/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.RecentService;
import com.emc.sa.model.util.CreationTimeComparator;
import com.emc.storageos.db.client.URIUtil;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class ComparatorTest {
    
    private static final Logger _logger = Logger.getLogger(CatalogServiceTest.class);
    
    @Test
    public void testCreationTimeComparator() throws Exception {
        
        _logger.info("Starting CreationTimeComparator test");
        
        List<RecentService> recentServices = Lists.newArrayList();
        
        Calendar cal1 = Calendar.getInstance();
        
        RecentService rs1 = new RecentService();
        rs1.setCreationTime(cal1);
        rs1.setUserId("myUserId");
        URI csId1 = URIUtil.createId(CatalogService.class);
        rs1.setCatalogServiceId(csId1);
        recentServices.add(rs1);
        
        Calendar cal2 = Calendar.getInstance();
        cal2.add(Calendar.HOUR_OF_DAY, 1);

        RecentService rs2 = new RecentService();
        rs2.setCreationTime(cal2);
        rs2.setUserId("myUserId");
        URI csId2 = URIUtil.createId(CatalogService.class);
        rs2.setCatalogServiceId(csId2);
        recentServices.add(rs2);
        
        Calendar cal3 = Calendar.getInstance();
        cal3.add(Calendar.HOUR_OF_DAY, 2);
        
        RecentService rs3 = new RecentService();
        rs3.setCreationTime(cal3);
        rs3.setUserId("myUserId");
        URI csId3 = URIUtil.createId(CatalogService.class);
        rs1.setCatalogServiceId(csId3);
        recentServices.add(rs3);    
        
        Collections.shuffle(recentServices);
        
        Collections.sort(recentServices, CreationTimeComparator.OLDEST_FIRST);
        
        Assert.assertEquals(rs1, recentServices.get(0));
        Assert.assertEquals(rs2, recentServices.get(1));
        Assert.assertEquals(rs3, recentServices.get(2));
        
        List<RecentService> newList = Lists.newArrayList();
        newList.add(rs2);
        newList.add(rs3);
        newList.add(rs1);
        
        Collections.sort(newList, new CreationTimeComparator());
        
        Assert.assertEquals(rs1, newList.get(0));
        Assert.assertEquals(rs2, newList.get(1));
        Assert.assertEquals(rs3, newList.get(2));        
        
        Collections.sort(newList, CreationTimeComparator.NEWEST_FIRST);
         
        Assert.assertEquals(rs3, newList.get(0));
        Assert.assertEquals(rs2, newList.get(1));
        Assert.assertEquals(rs1, newList.get(2));                
        
    }
    
}
