package com.mobinlife.jflake.configuration;

import java.util.Properties;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;

public class DynamoGeneratorIDConfiguration implements GeneratorIDConfiguration {
	
	/**
	 * Generic configurations
	 */
	private int leaseExpirationTime = 3600000; // 1 hour
	private int maxLeaseRetries = 10; // 10 times
	private int leaseRenewalFrequency = 5; // 5 seconds
	
	/**
	 * Dynamo specific configurations
	 */
	private AmazonDynamoDB dynamoClient = null;
	
	private String dynamoTable; 
	private String dynamoHashKeyName = "id";
	private String dynamoLeaseExpirationAttributeName = "exp";

	/**
	 * Constructor with a DynamoDB client instance
	 * @param AmazonDynamoDB
	 * @param properties
	 */
	public DynamoGeneratorIDConfiguration(AmazonDynamoDB dynamoClient, Properties properties){
		if(dynamoClient == null){
			throw new IllegalArgumentException("dynamoClient client cannot be null");
		}
		if(properties.get("dynamoTable") == null){
			throw new IllegalArgumentException("dynamoTable cannot be null");
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
		if(properties.get("dynamoHashKeyName") != null){
			dynamoHashKeyName = properties.getProperty("dynamoHashKeyName");
		}
		if(properties.get("dynamoLeaseExpirationAttributeName") != null){
			dynamoLeaseExpirationAttributeName = properties.getProperty("dynamoLeaseExpirationAttributeName");
		}
		
		this.dynamoClient = dynamoClient;
		this.dynamoTable = properties.getProperty("dynamoTable");
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

	
	public AmazonDynamoDB getDynamoClient(){
		return dynamoClient;
	}


	public String getDynamoTable() {
		return dynamoTable;
	}

	public String getDynamoHashKeyName() {
		return dynamoHashKeyName;
	}

	public String getDynamoLeaseExpirationAttributeName() {
		return dynamoLeaseExpirationAttributeName;
	}
	
	

}
