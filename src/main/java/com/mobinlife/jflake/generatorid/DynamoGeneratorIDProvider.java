package com.mobinlife.jflake.generatorid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableStatus;
import com.mobinlife.jflake.generatorid.configuration.DynamoGeneratorIDConfiguration;

/**
 * Generator ID provider based on a dynamo table
 * Atomically leases available IDs from a table, with an expiration time, and will update the lease expiration
 * time as long as it is running. 
 * @author Christophe
 *
 */
public class DynamoGeneratorIDProvider implements GeneratorIDProvider {

	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private int leaseExpirationTime;
	private int maxLeaseRetries;
	private int leaseRenewalFrequency;
	
	private AmazonDynamoDB dynamoClient;
	private String dynamoTable;	
	private String dynamoHashKeyName;
	private String dynamoLeaseExpirationAttributeName;
	
	private Integer generatorID = null;
	
	public DynamoGeneratorIDProvider(DynamoGeneratorIDConfiguration config){
		if(config == null){
			throw new IllegalArgumentException("config cannot be null");
		}
		
		
		this.dynamoClient = config.getDynamoClient();
		this.dynamoTable = config.getDynamoTable();
		this.leaseExpirationTime = config.getLeaseExpirationTime();
		this.maxLeaseRetries = config.getMaxLeaseRetries();
		this.leaseRenewalFrequency = config.getLeaseRenewalFrequency();
		this.dynamoHashKeyName = config.getDynamoHashKeyName();
		this.dynamoLeaseExpirationAttributeName = config.getDynamoLeaseExpirationAttributeName();
		
		if(!tableExists()){
			throw new IllegalArgumentException("Dynamo table " + dynamoTable + " does not exist");
		}
		
	}
	
	public int getId() {
		if(generatorID != null){
			return generatorID;
		} else {
			return leaseNewId();
		}
	}
	

	
	private int leaseNewId(){
		
		int tries = 0;
		while(tries <= maxLeaseRetries){
			tries++;
			long now = System.currentTimeMillis();
			long bookedLeaseOriginalExpirationDate = -1;
			
			SortedMap<Integer, Long> leases = getSortedLeasesFromDynamo();
			
			int leaseToBook=-1;
			if(leases.size() == 0) {
				// first lease
				leaseToBook = 1;
			} else {
				for(Integer leaseId : leases.keySet()){
					if(now > leases.get(leaseId)){
						// this lease is expired
						leaseToBook = leaseId;
						bookedLeaseOriginalExpirationDate = leases.get(leaseId);
					}
				}
				
				if(leaseToBook == -1){ // no expired lease was found
					leaseToBook = leases.lastKey()+1;
				}
			}
			
			// booking the lease with Dynamo optimistic lock
			if(bookedLeaseOriginalExpirationDate == -1){ 
				// new entry
				if(bookNewLease(leaseToBook, now+leaseExpirationTime)){
					generatorID = leaseToBook;
					scheduleLeaseRenewal();
					return generatorID;
				}
			} else {
				// lease an expired entry
				if(bookExpiredLease(leaseToBook, now+leaseExpirationTime, bookedLeaseOriginalExpirationDate)){
					generatorID = leaseToBook;
					scheduleLeaseRenewal();
					return generatorID;
				}
			}
		}
		
		return -1;
	}
	
	private void scheduleLeaseRenewal(){
		scheduler.scheduleWithFixedDelay(new Runnable() {
			public void run() {
				try {
					renewLease();
				} catch (Exception e) {
					// failed to renew lease
				}
			}
		}, leaseRenewalFrequency, leaseRenewalFrequency, TimeUnit.SECONDS);
	}
	
	private void renewLease(){
		Map<String, ExpectedAttributeValue> expected = new HashMap<String, ExpectedAttributeValue>();
		expected.put(dynamoHashKeyName, new ExpectedAttributeValue().withExists(true));
		
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put(dynamoHashKeyName, new AttributeValue().withN(String.valueOf(generatorID)));
		item.put(dynamoLeaseExpirationAttributeName
				, new AttributeValue()
					.withN(String.valueOf(System.currentTimeMillis()+leaseExpirationTime)));
		
		PutItemRequest putItemRequest = new PutItemRequest()
			.withTableName(dynamoTable)
			.withExpected(expected)
			.withItem(item);
		
		
		dynamoClient.putItem(putItemRequest);
	
	}
	
	private boolean bookExpiredLease(int leaseToBook, long expirationTime, long bookedLeaseOriginalExpirationDate){
		
		Map<String, ExpectedAttributeValue> expected = new HashMap<String, ExpectedAttributeValue>();
		expected.put(dynamoHashKeyName, new ExpectedAttributeValue()
									.withExists(true)
									.withValue(new AttributeValue().withN(String.valueOf(leaseToBook))));
		expected.put(dynamoLeaseExpirationAttributeName
				, new ExpectedAttributeValue()
					.withValue(new AttributeValue()
							.withN(String.valueOf(bookedLeaseOriginalExpirationDate))));
		
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put(dynamoHashKeyName, new AttributeValue().withN(String.valueOf(leaseToBook)));
		item.put(dynamoLeaseExpirationAttributeName, new AttributeValue().withN(String.valueOf(expirationTime)));
		
		PutItemRequest putItemRequest = new PutItemRequest()
			.withTableName(dynamoTable)
			.withExpected(expected)
			.withItem(item);
		
		try {
			dynamoClient.putItem(putItemRequest);
		} catch (Exception e){
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean bookNewLease(int leaseToBook, long expirationTime){
		
		Map<String, ExpectedAttributeValue> expected = new HashMap<String, ExpectedAttributeValue>();
		expected.put(dynamoHashKeyName, new ExpectedAttributeValue().withExists(false));
		expected.put(dynamoLeaseExpirationAttributeName, new ExpectedAttributeValue().withExists(false));
		
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put(dynamoHashKeyName, new AttributeValue().withN(String.valueOf(leaseToBook)));
		item.put(dynamoLeaseExpirationAttributeName, new AttributeValue().withN(String.valueOf(expirationTime)));
		
		PutItemRequest putItemRequest = new PutItemRequest()
			.withTableName(dynamoTable)
			.withExpected(expected)
			.withItem(item);
		
		try {
			dynamoClient.putItem(putItemRequest);
		} catch (Exception e){
			return false;
		}
		
		return true;
	}
	
	private boolean tableExists(){
		DescribeTableRequest request = new DescribeTableRequest();
		request.setTableName(dynamoTable);
		DescribeTableResult result;
        try {
            result = dynamoClient.describeTable(request);
        } catch (ResourceNotFoundException e) {
            return false;
        } catch (AmazonClientException e) {
        	
        	throw new RuntimeException("Dynamo error when checking for table existence");
        }

        String tableStatus = result.getTable().getTableStatus();

        return TableStatus.ACTIVE.name().equals(tableStatus);
	}
	
	private SortedMap<Integer, Long> getSortedLeasesFromDynamo(){
		
		ScanRequest scanRequest = new ScanRequest()
			.withTableName(dynamoTable);
		
		ScanResult scanResult = dynamoClient.scan(scanRequest);
		if(scanResult != null){
			if(scanResult.getItems() != null){
				List<Map<String, AttributeValue>> currentLeases = scanResult.getItems();
				if(scanResult.getLastEvaluatedKey() != null && scanResult.getLastEvaluatedKey().size() > 0){
					currentLeases.addAll(scanFurther(scanResult));
				}
				return sortLeases(currentLeases);
			} 
		}
		return null;
	}
	
	private SortedMap<Integer, Long> sortLeases(List<Map<String, AttributeValue>> currentLeases){
		SortedMap<Integer, Long> sortedLeases = new TreeMap<Integer, Long>();

		for(Map<String, AttributeValue> item : currentLeases){
			sortedLeases.put(Integer.parseInt(item.get(dynamoHashKeyName).getN())
					, Long.parseLong(item.get(dynamoLeaseExpirationAttributeName).getN()));
		}
		
		return sortedLeases;
	}
	
	List<Map<String, AttributeValue>> scanFurther(ScanResult incomingScanResult){
		
		ScanRequest scanRequest = new ScanRequest()
			.withTableName(dynamoTable)
			.withExclusiveStartKey(incomingScanResult.getLastEvaluatedKey());
		ScanResult scanResult = dynamoClient.scan(scanRequest);
		List<Map<String, AttributeValue>> result = incomingScanResult.getItems();
		if(scanResult != null){
			if(scanResult.getItems() != null){
				result.addAll(scanResult.getItems());
				if(scanResult.getLastEvaluatedKey() != null && scanResult.getLastEvaluatedKey().size() > 0){
					result.addAll(scanFurther(scanResult));
				}
			}
		}
		return result;
	}

}
