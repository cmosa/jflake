package com.mobinlife.jflake.generatorid.configuration;

/**
 * Generic configuration interface
 * @author Christophe
 *
 */
public interface GeneratorIDConfiguration {
	
	/**
	 * 
	 * @return lease expiration time in ms
	 */
	public int getLeaseExpirationTime();
	
	/**
	 * 
	 * @return how many times to try a new lease before failure
	 */
	public int getMaxLeaseRetries();
	
	/**
	 * 
	 * @return lease renewal frequency in seconds
	 */
	public int getLeaseRenewalFrequency();
}
