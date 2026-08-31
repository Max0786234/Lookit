# Database Systems and Indexing

Databases are specialized systems for storing and retrieving data efficiently. Search engines rely on databases for persistent storage and fast access.

## Relational Databases

Relational databases organize data into tables with rows and columns. SQL is the standard language for querying relational databases.

### ACID Properties

Relational databases guarantee:
- Atomicity: Transactions complete fully or not at all
- Consistency: Data remains valid after transactions
- Isolation: Concurrent transactions don't interfere
- Durability: Completed transactions persist

### Indexing in Relational Databases

Databases use B-tree and hash indices to speed lookups:
- B-tree indices: Efficient for range queries and sorted access
- Hash indices: Efficient for equality queries
- Full-text indices: Specialized for text search

PostgreSQL, MySQL, and Oracle all use these techniques.

## Inverted Indices

Search engines use inverted indices, a structure optimized for text search:

Traditional (forward) index:
```
document 1 -> [term1, term2, term3]
document 2 -> [term2, term4]
```

Inverted index:
```
term1 -> [document 1]
term2 -> [document 1, document 2]
term3 -> [document 1]
term4 -> [document 2]
```

This allows fast lookups: given a term, instantly find all documents containing it.

## NoSQL Databases

For applications needing flexibility or massive scale:
- Document stores (MongoDB): Store documents in JSON-like format
- Key-value stores (Redis): Fast in-memory storage
- Graph databases (Neo4j): Represent relationships
- Time-series databases: Optimize for timestamped data

Each trades some ACID guarantees for increased scalability or flexibility.

## Caching

Caches store frequently accessed data in fast, expensive memory:

### Query Caching
Store results of expensive database queries. When the same query comes again, return the cached result.

### Page Caching
Store rendered pages in Redis or Memcached to avoid recomputing them.

### Cache Invalidation
The challenge: when data changes, invalidate the cache. Strategies include:
- Time-based expiration: Cache expires after N seconds
- Event-based invalidation: Explicitly remove cache entries when data changes
- Dependency tracking: Track which cache entries depend on which data

## Scaling Databases

### Replication
Copy data to multiple servers:
- Read replicas handle query load
- Write replicas provide failover
- Eventual consistency: Replicas may temporarily have different data

### Sharding
Split data across multiple servers:
- Each shard handles a subset of keys
- Reduces load per server
- Increases complexity: queries may need to touch multiple shards

Production search engines use both replication and sharding to handle massive scale.
