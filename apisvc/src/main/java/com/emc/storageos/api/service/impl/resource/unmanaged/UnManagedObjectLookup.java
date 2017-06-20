package com.emc.storageos.api.service.impl.resource.unmanaged;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.model.property.PropertyInfo;

public class UnManagedObjectLookup extends StrLookup<DataObject> {

    protected static Logger _log = LoggerFactory.getLogger(UnManagedObjectLookup.class);

    protected DataObject _dataObject = null;
    protected ReportInfoCache _reportInfoCache = null;
    protected DbClient _dbClient = null;
    protected CoordinatorClient _coordinator = null;
    public static final String NETWORK_VIRTUAL_IP = "network_vip";
    public static final String NETWORK_STANDALONE_IP = "network_standalone_ipaddr";
    protected String _applicationBaseUrl = ""; 

    UnManagedObjectLookup(DataObject dataObject, ReportInfoCache reportInfoCache, DbClient dbClient, CoordinatorClient coordinator) {
        this._dataObject = dataObject;
        this._reportInfoCache = reportInfoCache;
        this._dbClient = dbClient;
        this._coordinator = coordinator;
        
        PropertyInfo props = _coordinator.getPropertyInfo();
        if (props != null) {
            String applicationHost = getApplicationHost(props.getAllProperties());
            if (StringUtils.isNotBlank(applicationHost)) {
                _applicationBaseUrl = String.format("https://%s", applicationHost);
            }
        }
        _log.info("_applicationBaseUrl is " + _applicationBaseUrl);
    }

    /**
     * Gets the host name or IP for the application from the coordinator properties. This will use the virtual IP
     * if available, otherwise it uses the standalone network IP.
     * 
     * @param properties the coordinator properties.
     * @return the application host.
     */
    private static String getApplicationHost(Map<String, String> properties) {
        String virtualIp = properties.get(NETWORK_VIRTUAL_IP);
        String standaloneIp = properties.get(NETWORK_STANDALONE_IP);
        if (StringUtils.isNotBlank(virtualIp) && !StringUtils.equals(virtualIp, "0.0.0.0")) {
            return virtualIp;
        }
        else if (StringUtils.isNotBlank(standaloneIp)) {
            return standaloneIp;
        }
        else {
            return null;
        }
    }

    @Override
    public String lookup(String key) {
        Object val = DataObjectUtils.getPropertyValue(_dataObject.getClass(), _dataObject, key);
        return val.toString();
    }

    protected String renderSimpleStringSet(Set<String> set) {
        StringBuffer output = new StringBuffer();
        for (String item : set) {
            output.append(String.format(Templates.TEMPLATE_LI, item));
        }
        if (output.length() > 0) {
            return String.format(Templates.TEMPLATE_UL, output.toString());
        }
        return Templates.EMPTY_STRING;
    }
}
