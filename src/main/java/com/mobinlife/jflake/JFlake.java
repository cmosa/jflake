package com.mobinlife.jflake;

import java.util.BitSet;

/**
 * JFlake is a generator of unique 64 bits IDs. 
 * It needs to be initialized with a 9 bits generator ID (0 <= generatorId <= 511) 
 * @author Christophe
 *
 */
public class JFlake {

	private final static int ID_SIZE = 64;
	private final static long EPOCH_TRANSLATION = 1388534400000L; // 
	private final static int MAX_SEQUENCE_VALUE = 0x1fff; // 8191 - 13 bits
	private final static int MAX_GENERATORID_VALUE = 0x1ff; // 511 - 9 bits
	private final static int TIME_BITS = 41;
	private final static int GENERATOR_BITS = 9;
	private final static int SEQUENCE_BITS = 13;

	private final ThreadLocal<BitSet> threadLocalBitSet = new ThreadLocal<BitSet>() {
        @Override
        public BitSet initialValue() {
            return new BitSet(ID_SIZE);
        }
    };
    
    private final Object threadLock = new Object();
    private volatile int sequenceNumber;
    private volatile long timestamp;
    private final int generatorId;
   
    /**
     * 
     * @param generatorId Generator identifier - 24bits
     */
    private JFlake(int generatorId){
    	this.generatorId = generatorId;
    }
    
    public static JFlake getUniqueIDGenerator(int generatorId) throws Exception{
    	if(generatorId > MAX_GENERATORID_VALUE) {
    		throw new Exception("Generator ID is 9 bits and cannot be more than " + MAX_GENERATORID_VALUE);
    	}
    	return new JFlake(generatorId);
    }
    
    public long getId() throws Exception{
    	if(sequenceNumber == MAX_SEQUENCE_VALUE){
    		throw new Exception("No id available");
    	}

    	long now;

    	synchronized(threadLock) {
    		// Translation with a new origin gives us more room in the future
    		now = System.currentTimeMillis()-EPOCH_TRANSLATION;
    		if(now != timestamp) {
    			timestamp = now;
    			sequenceNumber = 0;
    		} else {
    			sequenceNumber++;
    		}
			
			BitSet bitSet = threadLocalBitSet.get();
    		int currentIndex =  ID_SIZE-2;

			for(int i = TIME_BITS-1; i >= 0 ; i--){
				bitSet.set(currentIndex--, (((now >> i)&1)==1));
			}
			for(int i = GENERATOR_BITS-1; i >= 0 ; i--){
				bitSet.set(currentIndex--, (((generatorId >> i)&1)==1));
			}
			for(int i = SEQUENCE_BITS-1; i >= 0 ; i--){
				bitSet.set(currentIndex--, (((sequenceNumber >> i)&1)==1));
			}
			
			long value = 0L;
			for (int i = 0; i < ID_SIZE; ++i) {
				value += bitSet.get(i) ? (1L << i) : 0;
			}

			return value;
    	}
    }

}
