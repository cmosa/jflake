package com.mobinlife.jflake;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.SortedMap;
import java.util.TreeMap;

import com.mobinlife.jflake.configuration.RedisGeneratorIDConfiguration;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

/**
 * GeneratorID provider using a Redis HASH. Will lease a generator ID and regularly update the lease expiration date.
 * Upon id lease, will look for expired leases and atomically take one if applicable. 
 * @author Christophe
 *
 */
public class RedisGeneratorIDProvider implements GeneratorIDProvider {

	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private int leaseExpirationTime;
	private int maxLeaseRetries;
	private int leaseRenewalFrequency;
	private JedisPool jedisPool;
	private String redisHost;
	private int redisPort;
	private String redisPassword;
	
	private String redisHashKey;
	private Integer generatorID = null;

	public RedisGeneratorIDProvider(RedisGeneratorIDConfiguration config){
		if(config == null){
			throw new IllegalArgumentException("config cannot be null");
		}
		this.jedisPool = config.getJedisPool();
		this.redisHashKey = config.getRedisHashKey();
		this.leaseExpirationTime = config.getLeaseExpirationTime();
		this.maxLeaseRetries = config.getMaxLeaseRetries();
		this.leaseRenewalFrequency = config.getLeaseRenewalFrequency();
		this.redisHost = config.getRedisHost();
		this.redisPort = config.getRedisPort();
		this.redisPassword = config.getRedisPassword();
	}
	
	public int getId() {
		if(generatorID != null){
			return generatorID;
		} else {
			return leaseNewId();
		}
	}
	
	private int leaseNewId(){
		Jedis jedis;
		if(jedisPool != null) {
			jedis = jedisPool.getResource();
		} else {
			jedis = new Jedis(redisHost, redisPort);
			if(redisPassword != null){
				jedis.auth(redisPassword);
			}
		}
		try {
			int tries = 0;
			while(tries <= maxLeaseRetries){
				tries++;
				long now = System.currentTimeMillis();
				jedis.watch("redisHashKey");
				Map<String, String> leasesAsString = jedis.hgetAll(redisHashKey);
				SortedMap<Integer, Long> leases = getSortedLeases(leasesAsString);
				
				int leaseToBook=-1;
				if(leases.size() == 0) {
					// first lease
					leaseToBook = 1;
				} else {
					for(Integer leaseId : leases.keySet()){
						if(now > leases.get(leaseId)){
							// this lease is expired
							leaseToBook = leaseId;
						}
					}
					
					if(leaseToBook == -1){ // no expired lease was found
						leaseToBook = leases.lastKey()+1;
					}
				}
				
				// booking the lease within a transaction (optimistic locking)
				Transaction transaction = jedis.multi();
				transaction.hset(redisHashKey, String.valueOf(leaseToBook), String.valueOf(now+leaseExpirationTime));
				if(transaction.exec()!=null){
					generatorID = leaseToBook;
					
					scheduleLeaseRenewal();
					
					return generatorID;
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(jedisPool != null){
				jedisPool.returnResource(jedis);
			} else {
				jedis.disconnect();
			}
		}
	
		return -1;
	}
	
	private void scheduleLeaseRenewal(){
		scheduler.scheduleWithFixedDelay(new Runnable() {
			
			public void run() {
				Jedis jedis;
				if(jedisPool != null) {
					jedis = jedisPool.getResource();
				} else {
					jedis = new Jedis(redisHost, redisPort);
					if(redisPassword != null){
						jedis.auth(redisPassword);
					}
				}
				try {
					jedis.hset(redisHashKey, String.valueOf(generatorID)
							,  String.valueOf(System.currentTimeMillis()+leaseExpirationTime));
				} catch (Exception e) {
					// failed to renew lease
				} finally {
					if(jedisPool != null){
						jedisPool.returnResource(jedis);
					} else {
						jedis.disconnect();
					}
				}
				
			}
		}, leaseRenewalFrequency, leaseRenewalFrequency, TimeUnit.SECONDS);
	}
	
	private SortedMap<Integer, Long> getSortedLeases(Map<String, String> leasesAsString){
		SortedMap<Integer, Long> leases = new TreeMap<Integer, Long>();
		for(Entry<String, String> leaseEntry : leasesAsString.entrySet()){
			try {
				leases.put(Integer.parseInt(leaseEntry.getKey()), Long.parseLong(leaseEntry.getValue()));
			} catch (NumberFormatException e){
				// ignore wrong entry in the HASH
			}
		}
		return leases;
	}

}
