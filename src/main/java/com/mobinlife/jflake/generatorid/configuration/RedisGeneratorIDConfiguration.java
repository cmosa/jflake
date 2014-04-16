package com.mobinlife.jflake.generatorid.configuration;

import java.util.Properties;

import redis.clients.jedis.JedisPool;

public class RedisGeneratorIDConfiguration implements GeneratorIDConfiguration {
	
	/**
	 * Generic configurations
	 */
	private int leaseExpirationTime = 3600000; // 1 hour
	private int maxLeaseRetries = 10; // 10 times
	private int leaseRenewalFrequency = 5; // 5 seconds
	
	/**
	 * Redis specific configurations
	 */
	private JedisPool jedisPool = null;
	private String redisHost = null;
	private int redisPort = -1;
	private String redisPassword = null;
	
	private String redisHashKey; 

	/**
	 * Constructor with a JedisPool instance if the application uses one
	 * @param jedisPool
	 * @param properties
	 */
	public RedisGeneratorIDConfiguration(JedisPool jedisPool, Properties properties){
		if(jedisPool == null){
			throw new IllegalArgumentException("jedisPool client cannot be null");
		}
		if(properties.get("redisHashKey") == null){
			throw new IllegalArgumentException("redisHashKey cannot be null");
		}
		
		
		
		if(properties.get("leaseExpirationTime") != null){
			try {
				leaseExpirationTime = Integer.parseInt(properties.getProperty("leaseExpirationTime"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("leaseExpirationTime has a wrong format (Expecting integer)");
			}
		}
		if(properties.get("maxLeaseRetries") != null){
			try {
				maxLeaseRetries = Integer.parseInt(properties.getProperty("maxLeaseRetries"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("maxLeaseRetries has a wrong format (Expecting integer)");
			}
		}
		if(properties.get("leaseRenewalFrequency") != null){
			try {
				leaseRenewalFrequency = Integer.parseInt(properties.getProperty("leaseRenewalFrequency"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("leaseRenewalFrequency has a wrong format (Expecting integer)");
			}
		}
		
		this.jedisPool = jedisPool;
		this.redisHashKey = properties.getProperty("redisHashKey");
	}
	
	/**
	 * Constructor without a pool. Needs redisHost, redisPort and optionally redisPassword in the Properties instance
	 * @param properties
	 */
	public RedisGeneratorIDConfiguration(Properties properties){
		if(properties.get("redisHashKey") == null){
			throw new IllegalArgumentException("redisHashKey cannot be null");
		}
		if(properties.get("redisPort") == null){
			throw new IllegalArgumentException("redisPort cannot be null");
		}
		if(properties.get("redisHost") == null){
			throw new IllegalArgumentException("redisHost cannot be null");
		}
		
		
		if(properties.get("leaseExpirationTime") != null){
			try {
				leaseExpirationTime = Integer.parseInt(properties.getProperty("leaseExpirationTime"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("leaseExpirationTime has a wrong format (Expecting integer)");
			}
		}
		if(properties.get("maxLeaseRetries") != null){
			try {
				maxLeaseRetries = Integer.parseInt(properties.getProperty("maxLeaseRetries"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("maxLeaseRetries has a wrong format (Expecting integer)");
			}
		}
		if(properties.get("leaseRenewalFrequency") != null){
			try {
				leaseRenewalFrequency = Integer.parseInt(properties.getProperty("leaseRenewalFrequency"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("leaseRenewalFrequency has a wrong format (Expecting integer)");
			}
		}
		
		
		this.redisHashKey = properties.getProperty("redisHashKey");
		this.redisHost = properties.getProperty("redisHost");
		try {
			this.redisPort = Integer.parseInt(properties.getProperty("redisPort"));
		} catch (NumberFormatException e){
			throw new IllegalArgumentException("redisPort has a wrong format (Expecting integer)");
		}
		if(properties.get("redisPassword") != null){
			this.redisPassword = properties.getProperty("redisPassword");
		}
	}
	
	public int getLeaseExpirationTime() {
		return leaseExpirationTime;
	}

	public int getMaxLeaseRetries() {
		return maxLeaseRetries;
	}

	public int getLeaseRenewalFrequency() {
		return leaseRenewalFrequency;
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public String getRedisHost() {
		return redisHost;
	}

	public int getRedisPort() {
		return redisPort;
	}

	public String getRedisPassword() {
		return redisPassword;
	}

	public String getRedisHashKey() {
		return redisHashKey;
	}
	
	

}
