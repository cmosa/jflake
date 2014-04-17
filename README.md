JFlake
======

JFlake is a  [Twitter Snowflake](https://github.com/twitter/snowflake/ "Snowflake") inspired lightweight library used to generate 64bits uniques IDs in a distributed environment, with no coordination necessary. 

It is composed of two major components: The JFlake class which generates IDs and the generator ID lease framework. Both can be used independently from each other.

Currently under development, version 0.1 - Use at your own risks

Origin
------

Like Twitter, we needed to go away from MySQL for id generation in our mobile games backends; all our application data is stored in other kind of datastores (think DynamoDB, Redis...). However, we have smaller requirements than twitter: 
* Our ID generation can be per application / environment 
* We do not want to use Zookepper
* We want to generate IDs locally and not over the network (each application instance is responsible for its own ID generation, there is no concept of ID generation application cluster)

Otherwise, we have the same concepts:
* 64bits ids (goodbye UUIDs)
* roughly time ordered (at 1ms precision)
* sortable
* performant (1.5 million ids per second on a standard Intel i7 core)
* uncoordinated (each generator is independent)

JFlake ID generator
-------------------

The JFlake class generates unique IDs in a thread safe manner. It is initialized with an id (called generator id) stored on maximum 9 bits, i.e. the generator ID must be between 0 and 511 inclusive.  

The id generated has three parts: 
* Time component: 41bits epoch timestamp in ms, with a 1388534400000ms translation so we have an origin on 1st January 2014 00:00 GMT, which gives us space to generate IDs until 7th September 2083
* Generator ID component: 9bits integer; A single application can have up to 512 generators simultaneously
* A sequence component: 13bits integer; A single generator can output 8192 IDs per ms 
* One bit is wasted for the sign as Java does not have an unsigned long primitive

Generator ID lease providers
----------------------------

In order to have unique Generator IDs, this framework implements a lease system using a datastore (currently implemented with DynamoDB or Redis, can be easily extended to any datastore where atomic operations are possible). 

### Principles
A GeneratorIDProvider class has to be initilazed with its configuration and must implement the getId() method. The getId() method returns the currently leased id if existing; if not, it will try to lease a new id in the datastore. 
When leasing an id, it will first look for expired leases, and if no expired lease was found, it will lease a new id. 

### DynamoDB implementation
To use the DynamoDB implementation, you must dedicate a DynamoDB table to the lease system. The table has only a Hash Key (no Range Key) with a default Hash Key name "id", which can be overrided via the configuration file. 

You must set the provisioned capactiy according to your usage pattern:
* leaseRenewalFrequency: the higher the frequency, the higher the write throughput will be
* maxLeaseRetries: can increase the burst capacity needed in some exceptional cases
* More generators will need both more read and write capacity 
* Write capacity needed can be estimated with this formula: `capacity = (GeneratorCount / leaseRenewalFrequencyInSeconds) + a safety capacity overhead`
* A read/write capacity of 10/2 will be enough for the majority of small to medium scale use cases — which gives a monthly cost of around $2 (us-east April 2014 pricing)
* Warning: if all leasers are created at the same time, they will renew leases at the same time and create bursts of writes

