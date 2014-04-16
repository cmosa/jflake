package com.mobinlife.jflake;

public interface GeneratorIDProvider {

	/**
	 * Get the generator ID leased, or -1 if no lease was available
	 * @return An id if a lease was possible, -1 otherwise
	 */
	public int getId();
	
}
