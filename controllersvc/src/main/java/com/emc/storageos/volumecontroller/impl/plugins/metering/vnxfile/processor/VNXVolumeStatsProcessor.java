/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.VolumeSetStats;
import com.emc.nas.vnxfile.xmlapi.VolumeSetStats.Sample;
import com.emc.nas.vnxfile.xmlapi.VolumeSetStats.Sample.Item;


/**
 * VNXVolumeStatsProcessor is responsible to process the result received from
 * XML API Server after getting the Volume Statistics Information.
 */
public class VNXVolumeStatsProcessor extends VNXFileProcessor {

	/**
	 * Logger instance.
	 */
	private final Logger _logger = LoggerFactory
			.getLogger(VNXVolumeStatsProcessor.class);

	@SuppressWarnings("unchecked")
	@Override
	public void processResult(Operation operation, Object resultObj,
			Map<String, Object> keyMap) throws BaseCollectionException {
		final PostMethod result = (PostMethod) resultObj;
		_logger.info("processing volumeStats response" + resultObj);
		try {
			ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
					.unmarshal(result.getResponseBodyAsStream());
			List<Object> volumeStats = getQueryStatsResponse(responsePacket);
			Iterator<Object> iterator = volumeStats.iterator();
			Map<String, String> volFileMap = (Map<String, String>) keyMap
					.get(VNXFileConstants.VOLFILESHAREMAP);
			List<Stat> statsList = (List<Stat>) keyMap.get(VNXFileConstants.STATS);
			// There are cases where we will get error for movers, we should ignore the error
			// and proceed with rest of the Mover stats.
			while (iterator.hasNext()) {
				VolumeSetStats volStats = (VolumeSetStats) iterator.next();
				List<Sample> sampleList = volStats.getSample();
				processVolumeSampleList(sampleList, keyMap, volFileMap, statsList);
			}
		} catch (final Exception ex) {
			_logger.error(
					"Exception occurred while processing the volume stats response due to {}",
					ex.getMessage());
		} finally {
			result.releaseConnection();
		}

	}
	/**
	 * Process the each Volume sample list and inject
	 * bwIn, bwOut for each fileshare. We also have a one-to-one
	 * mapping between volume to Fileshare.
	 * @param sampleList : List of Sample objects of a volume
	 * @param keyMap : keyMap
	 * @param volFileMap : Volume-to-Fileshare Mapping
	 * @param statsList : StatsList to update.
	 */
	private void processVolumeSampleList(List<Sample> sampleList,
			Map<String, Object> keyMap, Map<String, String> volFileMap,
			List<Stat> statsList) {

		Iterator<Sample> itemItr = sampleList.iterator();
		while (itemItr.hasNext()) {
			Sample volSample = itemItr.next();
			List<Item> volItems = volSample.getItem();
			Iterator<Item> volItemsItr = volItems.iterator();
			while (volItemsItr.hasNext()) {
				Item volItem = volItemsItr.next();
				if (volFileMap.containsKey(volItem.getVolume())) {
				    String fileSystem = volFileMap.get(volItem.getVolume());
				    // No filesystem found for volume then skip
				    if (null != fileSystem) {
				        injectBWInOut(fileSystem, keyMap, volItem, statsList);
				    }
				}
			}

		}

	}

	/**
	 * Inject bwIn & bwOut in stat object.
	 * @param fileShareId : FileshareId to update.
	 * @param keyMap : KeyMap
	 * @param volItem : VolItem contains the bytesRead/bytesWrite values.
	 * @param statsList : Stat List to update.
	 */
	private void injectBWInOut(String fileShareId, Map<String, Object> keyMap,
			Item volItem, List<Stat> statsList) {
		Iterator<Stat> statsItr = statsList.iterator();
		while (statsItr.hasNext()) {
			Stat stat = statsItr.next();
			if (null != fileShareId && fetchNativeId(stat.getNativeGuid()).equals(fileShareId)) {
				stat.setBandwidthOut(volItem.getBytesRead().longValue());
				stat.setBandwidthIn(volItem.getBytesWritten().longValue());
				break;
			}
		}
	}

	@Override
	protected void setPrerequisiteObjects(List<Object> inputArgs)
			throws BaseCollectionException {

	}

}
