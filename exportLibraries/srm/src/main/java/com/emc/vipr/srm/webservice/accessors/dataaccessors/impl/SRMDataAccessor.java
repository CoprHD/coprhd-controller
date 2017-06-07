package com.emc.vipr.srm.webservice.accessors.dataaccessors.impl;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.vipr.srm.common.utils.SRMFilters;
import com.emc.vipr.srm.exception.ViprSRMException;
import com.emc.vipr.srm.webservice.accessors.Aggregation;
import com.emc.vipr.srm.webservice.accessors.Aggregations;
import com.emc.vipr.srm.webservice.accessors.DatabaseAccessor;
import com.emc.vipr.srm.webservice.accessors.DatabaseAccessorService;
import com.emc.vipr.srm.webservice.accessors.DistinctPropertyValues;
import com.emc.vipr.srm.webservice.accessors.PropertyRecord;
import com.emc.vipr.srm.webservice.accessors.TimeSerie;
import com.emc.vipr.srm.webservice.accessors.TimeSerieValue;
import com.emc.vipr.srm.webservice.accessors.ViprSrmWSException;
import com.emc.vipr.srm.webservice.accessors.dataaccessors.ISRMDataAccessor;

public class SRMDataAccessor implements ISRMDataAccessor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(SRMDataAccessor.class);
    private static final String BRACES_INCLUDER = "({0})";

    private static DatabaseAccessorService databaseAccessorService;

    public static void setDatabaseAccessorService(
            DatabaseAccessorService databaseAccessorService) {
        SRMDataAccessor.databaseAccessorService = databaseAccessorService;
    }

    /**
     * Gets DataBase accessor to access APG information.
     * 
     * @return APGDatabase Accessor instance
     * @throws ViprSRMException
     */
    private static DatabaseAccessor getDatabaseAccessorPort()
            throws ViprSRMException {
        if (null == databaseAccessorService) {
            throw new ViprSRMException(
                    "SRM Webservice accessor spring initialization failed, check application init process...");
        }
        return databaseAccessorService.getDatabaseAccessorPort();
    }

    /**
     * This method retrieves data from APG WS by querying the passed in filter
     * and selecting the passed in properties
     * 
     * @param filterStr
     *            the APG filter to be queries
     * @param properties
     *            the properties whose value needs to be retrieved.
     * @return the data the format of List<Map<String, String>> where the each
     *         map contains the key as the property and value as the property
     *         value.
     * @throws ViprSrmWSException
     *             if there is any error while retrieving the data.
     */
    @SuppressWarnings("unused")
    private List<List<String>> retrieveData(final String filterStr,
            final List<String> properties) throws ViprSrmWSException {
        List<List<String>> detailList = new ArrayList<List<String>>();
        final String filter = SRMFilters.FILTER_VSTATUS_ACTIVE
                + MessageFormat.format(BRACES_INCLUDER, filterStr);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter to get RowData from APG DB : [{}]", filter);
        }
        try {
            List<PropertyRecord> values = getDatabaseAccessorPort()
                    .getDistinctPropertyRecords(filter, null, null, null, null,
                            properties);

            for (PropertyRecord record : values) {
                List<String> propValues = record.getValue();
                if (CollectionUtils.isNotEmpty(propValues)) {
                    detailList.add(propValues);
                }
            }
            values.clear();
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format(
                    "Error while retrieving the data through webservice for filter {0} and properties {1}",
                    filter, properties), e.getMessage());
        }
        return detailList;
    }

    /**
     * This method retrieves data from APG WS by querying the passed in filter
     * and selecting the passed in properties
     * 
     * @param filterStr
     *            the APG filter to be queries
     * @param properties
     *            the properties whose value needs to be retrieved.
     * @return the data the format of List<Map<String, String>> where the each
     *         map contains the key as the property and value as the property
     *         value.
     * @throws ViprSrmWSException
     *             if there is any error while retrieving the data.
     */
    public static List<Map<String, String>> retrieveDataWithMultipleKey(
            final String filterStr, final List<String> properties)
            throws ViprSrmWSException {
        long start = System.currentTimeMillis();
        List<Map<String, String>> detailList = new ArrayList<Map<String, String>>();
        final String filter = SRMFilters.FILTER_VSTATUS_ACTIVE
                + MessageFormat.format(BRACES_INCLUDER, filterStr);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter to get MultipleDetails from APG DB : [{}]",
                    filter);
        }
        try {
            List<PropertyRecord> values = getDatabaseAccessorPort()
                    .getDistinctPropertyRecords(filter, null, null, null, null,
                            properties);

            for (PropertyRecord record : values) {
                List<String> propValues = record.getValue();

                if (CollectionUtils.isNotEmpty(propValues)
                        && properties.size() == propValues.size()) {
                    Iterator<String> propIterator = propValues.iterator();
                    Iterator<String> propertiesIterator = properties.iterator();
                    HashMap<String, String> map = new HashMap<String, String>();
                    while (propertiesIterator.hasNext()
                            && propIterator.hasNext()) {
                        map.put(propertiesIterator.next(), propIterator.next());
                    }
                    detailList.add(map);
                }
            }
            values.clear();
            long totalQueryExeutionTime = System.currentTimeMillis() - start;
            LOGGER.info("SRM query execution time  : {} ms", totalQueryExeutionTime);
            System.out.println("SRM query execution time  : " + totalQueryExeutionTime +" ms");
        } catch (Exception e) {
            System.out.println(e);
            LOGGER.error(MessageFormat.format(
                    "Error while retrieving the data through webservice for filter {0} and properties {1}",
                    filter, properties), e.getMessage());
        }
        return detailList;
    }

    /**
     * Gets Distinct Values for the Properties on the combinations of Filter and
     * subFilter
     * 
     * @param filterStr
     *            Base Filter string
     * @param subFilters
     *            Sub Filter list
     * @param properties
     *            List of properties
     * @return Distinct property values
     * @throws ViprSRMException
     */
    public static Set<String> retrieveDataValues(final String filterStr,
            final List<String> subFilters, final String property)
            throws ViprSRMException {
        Set<String> detailList = new HashSet<String>();
        final String filter = SRMFilters.FILTER_VSTATUS_ACTIVE
                + MessageFormat.format(BRACES_INCLUDER, filterStr);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter : {} # Properties : {}", filter, property);
        }

        List<DistinctPropertyValues> opValues = getDatabaseAccessorPort()
                .getDistinctPropertyValues(filter, subFilters, null, null, null,
                        null, Arrays.asList(property), null);

        Aggregations agg = new Aggregations();
        agg.setSpacial(Aggregation.LAST);
        List<TimeSerie> series = getDatabaseAccessorPort().getAggregatedData(filter, null, 3, -1, null, null, 3600*24, agg);
        System.out.println(getMostRecentValue(series));
        System.out.println(series);
        for (DistinctPropertyValues opv : opValues) {
            detailList.addAll(opv.getValue());
        }
        return detailList;
    }
    
    /**
     * 
     * @param filterStr
     * @param subFilters
     * @return
     * @throws ViprSrmWSException
     * @throws ViprSRMException
     */
    public static Float getAggregateData(final String filterStr, final List<String> subFilters) throws ViprSrmWSException, ViprSRMException {
        Aggregations agg = new Aggregations();
        agg.setSpacial(Aggregation.LAST);
        List<TimeSerie> series = getDatabaseAccessorPort().getAggregatedData(filterStr, subFilters, 3, -1, null, null, 3600*24, agg);
        return getMostRecentValue(series);
    }
    
    /**
     * Retrieves the most recent float value from the list of {@code timeseries} by timestamp.
     * @param timeseries
     * @return
     * @throws ViprSRMException 
     */
    private static Float getMostRecentValue(List<TimeSerie> timeseries) throws ViprSRMException {
        
        final Iterator<TimeSerie> iterTSV = timeseries.iterator();
        Float value = null;
        while (iterTSV.hasNext()) {
            final List<TimeSerieValue> tvs = iterTSV.next().getTv();
            if (CollectionUtils.isEmpty(tvs)) {
                throw new ViprSRMException("No timeseries values returned for query");
            }

            int timestamp = Integer.MIN_VALUE;
            for (final TimeSerieValue tv : tvs) {
                if (tv.getT() > timestamp) {
                    if (CollectionUtils.isEmpty(tv.getV())) {
                        continue;
                    }
                    timestamp = tv.getT();
                    /*
                     * The first value in this container is the data, second
                     * value is the count (not used).
                     */
                    value = tv.getV().get(0).floatValue();
                }
            }
        }

        return value;
    }

}
