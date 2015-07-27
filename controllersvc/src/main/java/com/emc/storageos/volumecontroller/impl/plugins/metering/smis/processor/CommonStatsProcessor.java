/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;

public abstract class CommonStatsProcessor extends Processor {
    
    protected ZeroRecordGenerator _zeroRecordGenerator;
    
    protected PartitionManager _partitionManager;
    
    private static final String DEFAULT_PROVIDER_TIME = "19691231190000.000000-300";
    
    /**
     * To construct CIMObjectPath, keyword symm got from Statistics call need to
     * be changed to Symmetrix. This looks ugly, i.e. replacing strings, but
     * currently what we gain out of this , is huge performance, hence lies this
     * logic.
     * 
     * @param nativeGuid
     * @param keyMap 
     * @return String
     */
    protected String translatedAttributes(String nativeGuid, Map<String, Object> keyMap) {
        
        if (keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                && Boolean.valueOf(keyMap.get(Constants.IS_NEW_SMIS_PROVIDER).toString())) {
            nativeGuid = nativeGuid.replaceAll(Constants.SMIS_80_STYLE, Constants.SMIS_PLUS_REGEX);
        }else{
            if (nativeGuid.contains(_symm)) {
                nativeGuid = nativeGuid.replace(_symm, _symmetrix);
            } else if (nativeGuid.contains(_clar)) {
                nativeGuid = nativeGuid.replace(_clar, _clariion);
            }
        }
        return nativeGuid;
    }
    
    /**
     * Return the nativeGuid of the system from portStat Instance.
     * 
     * @param statInstanceId
     * @param keyMap 
     * @return
     */
    protected String getSystemNativeGuidFromMetric(String statInstanceId, Map<String, Object> keyMap) {
        statInstanceId = normalizeNativeGuidForVIPR(statInstanceId, keyMap);
        int secondIndex = StringUtils.ordinalIndexOf(statInstanceId, Constants._plusDelimiter, 2);
        String nativeGuid = translatedAttributes(statInstanceId.substring(0, secondIndex), keyMap);
        return nativeGuid;
    }
    
    @Override
    public void setPrerequisiteObjects(List<Object> inputArgs) throws SMIPluginException {
        // TODO Auto-generated method stub
    }
    
    /**
     * Inject zerorecordGenerator.
     * @param recordGenerator
     */
    public void setZeroRecordGenerator(ZeroRecordGenerator recordGenerator) {
        _zeroRecordGenerator = recordGenerator;
    }
    
    /**
     * Inject partitionManager.    
     * @param partitionManager
     */
    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }
    
    /**
     * Converts the CIM property StatisticTime to msec since the epoch.
     * @param statisticTime - CIM propertiy in CIM_BlockStatisticalData
     * @return Long time in milliseconds in format similar to System.getMillis()
     */
    public Long convertCIMStatisticTime(String statisticTime) {
		if (statisticTime == null || statisticTime.equals("")
				|| statisticTime.equals(DEFAULT_PROVIDER_TIME))
			return 0L;
        String[] parts = statisticTime.split("[\\.\\+\\-]");
        Integer year = Integer.parseInt(parts[0].substring(0,4), 10)-1900;
        Integer month = Integer.parseInt(parts[0].substring(4,6), 10)-1;
        Integer day = Integer.parseInt(parts[0].substring(6,8), 10);
        Integer hour = Integer.parseInt(parts[0].substring(8,10), 10);
        Integer min = Integer.parseInt(parts[0].substring(10,12), 10);
        Integer sec = Integer.parseInt(parts[0].substring(12,14), 10);
        Integer msec = Integer.parseInt(parts[1].substring(0,3), 10);
        @SuppressWarnings("deprecation")
        Date date = new Date(year, month, day, hour, min, sec);
        Long millis = date.getTime() + msec;
        date = new Date(millis);
        return millis;
    }
    
    /**
     * Normalize nativeGuid for VIPR consumption, newer SMIs provider 8.x has
     * different delimiters.
     * @param nativeGuid
     * @param keyMap
     * @return
     */
    protected String normalizeNativeGuidForVIPR(String nativeGuid, Map<String, Object> keyMap)
    {
        if (keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                && Boolean.valueOf(keyMap.get(Constants.IS_NEW_SMIS_PROVIDER).toString())) {
            nativeGuid = nativeGuid.replaceAll(Constants.SMIS_80_STYLE, Constants.SMIS_PLUS_REGEX);
        }
        return nativeGuid;
    }

}
