package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.vnxe.models.PoolTier;
import com.emc.storageos.vnxe.models.VNXePool;

public class PoolListRequestTest {
	private static KHClient _client;
    private static String host = EnvConfig.get("sanity", "vnxe.host");
    private static String userName = EnvConfig.get("sanity", "vnxe.username");
    private static String password = EnvConfig.get("sanity", "vnxe.password");

	@BeforeClass
    public static void setup() throws Exception {
		_client = new KHClient(host, userName, password);
	}
	
	@Test
	public void getTest() {
		PoolListRequest req = new PoolListRequest(_client);
        List<VNXePool> list = req.get();
        for (VNXePool pool : list) {
        	Integer raidLevel = pool.getRaidType();
        	System.out.print(pool.getRaidTypeEnum().name());
           String name = pool.getName();
           System.out.println(name);
           System.out.println(raidLevel);
           List<PoolTier> tiers = pool.getTiers();
           for (PoolTier tier : tiers) {
               long size = tier.getSizeTotal();
               System.out.println("total size:");
               System.out.println(size);
           }
        }
        System.out.println(list.size());
	}

}
