package com.github.diegopacheco.sandbox.java.netflixoss.dyno.msa.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.diegopacheco.sandbox.java.netflixoss.dyno.msa.dynomite.DynomiteNodeInfo;
import com.github.diegopacheco.sandbox.java.netflixoss.dyno.msa.dynomite.DynomiteSeedsParser;
import com.github.diegopacheco.sandbox.java.netflixoss.dyno.msa.dynomite.HostSupplierFactory;
import com.github.diegopacheco.sandbox.java.netflixoss.dyno.msa.dynomite.TokenMapSupplierFactory;
import com.google.inject.Singleton;
import com.netflix.config.ConfigurationManager;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.RetryNTimes;
import com.netflix.dyno.contrib.ArchaiusConnectionPoolConfiguration;
import com.netflix.dyno.jedis.DynoDualWriterClient;
import com.netflix.dyno.jedis.DynoDualWriterClient.Dial;
import com.netflix.dyno.jedis.DynoJedisClient;

@Singleton
public class DynoManager {
	
	private static final Logger logger = LoggerFactory.getLogger(DynoManager.class);

	private DynoJedisClient dyno;
	
	public DynoManager() {
		
		ConfigurationManager.getConfigInstance().setProperty("dyno.dynomiteCluster.retryPolicy","RetryNTimes:3:true");
		
		String seeds = System.getenv("DYNOMITE_SEEDS");
		logger.info("Using Seeds: " + seeds );
		
		String seedsDW = System.getenv("DYNOMITE_DW_SEEDS");
		logger.info("Using Dual Write Seeds: " + seedsDW );
		
		List<DynomiteNodeInfo> nodes = DynomiteSeedsParser.parse(seeds);
		TokenMapSupplier tms = TokenMapSupplierFactory.build(nodes);
		HostSupplier hs = HostSupplierFactory.build(nodes);
		
		List<DynomiteNodeInfo> nodesDW = DynomiteSeedsParser.parse(seedsDW);
		HostSupplier hsDW = HostSupplierFactory.build(nodesDW);
		
//		setOSProperty("EC2_AVAILABILITY_ZONE", "rack1");
//		System.setProperty("EC2_AVAILABILITY_ZONE","rack1");
//		ConfigurationManager.getConfigInstance().setProperty("EC2_AVAILABILITY_ZONE", "rack1");
//		
//		setOSProperty("EC2_REGION", "dc");
//		System.setProperty("EC2_REGION","dc");
//		ConfigurationManager.getConfigInstance().setProperty("EC2_REGION", "dc");
		
		 Dial customerDial = new DynoDualWriterClient.Dial() {
		        @Override
		        public boolean isInRange(String key) {
		            return true;            
		        }
		        @Override
		        public boolean isInRange(byte[] key) {
		            return true;
		        }
		        @Override
		        public void setRange(int range) {}
		};

		ConfigurationManager.getConfigInstance().setProperty("dyno.dynomiteCluster.dualwrite.enabled", "true");
		ConfigurationManager.getConfigInstance().setProperty("dyno.dynomiteCluster.dualwrite.cluster", "dynomiteCluster");
		ConfigurationManager.getConfigInstance().setProperty("dyno.dynomiteCluster.dualwrite.percentage", "100");
		
		DynoJedisClient dynoClient = new DynoDualWriterClient.Builder().withApplicationName("dynomiteCluster")
				.withDynomiteClusterName("dynomiteCluster")
				.withCPConfig(
						new ConnectionPoolConfigurationImpl("dynomiteCluster")
							.withTokenSupplier(tms)
							.setMaxConnsPerHost(1)
							.setConnectTimeout(2000)
						    .setRetryPolicyFactory(new RetryNTimes.RetryFactory(3,true))
							.setLocalRack("rack1")
							.setLocalZoneAffinity(true)
				)
				.withHostSupplier(hs)
				.withDualWriteClusterName("dynomiteCluster")
				.withDualWriteHostSupplier(hsDW)
				.withDualWriteDial(customerDial)
				.build();
		
		this.dyno = dynoClient;
		
	}
	
	public DynoJedisClient getClient(){
		return dyno;
	}

	//	@SuppressWarnings({"unchecked"})
//	public static void setOSProperty(String key, String value) {
//	    try {
//	        Map<String, String> env = System.getenv();
//	        Class<?> cl = env.getClass();
//	        Field field = cl.getDeclaredField("m");
//	        field.setAccessible(true);
//	        Map<String, String> writableEnv = (Map<String, String>) field.get(env);
//	        writableEnv.put(key, value);
//	    } catch (Exception e) {
//	        throw new IllegalStateException("Failed to set environment variable", e);
//	    }
//	}
	
}
