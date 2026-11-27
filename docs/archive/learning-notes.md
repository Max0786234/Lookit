# Learning Notes

## What Is A Search Engine?

A search engine is not just a search box. A real search engine has multiple parts:

```text
Crawler -> Parser -> Indexer -> Ranking -> Query Engine -> API -> UI
```

In Phase 1, we are starting with the smallest useful version:

```text
Documents -> Indexer -> Query Engine
```

## Document

A document is one searchable item. Later this can be a web page from the crawler.

For now, a document has:

- `id`
- `title`
- `url`
- `content`

## Token

A token is a cleaned word from text.

Example:

```text
"Java, Java! Search Engine."
```

becomes:

```text
java, java, search, engine
```

## Inverted Index

Normal document storage looks like this:

```text
doc1 -> java search engine
doc2 -> spring boot java
```

An inverted index flips this:

```text
java   -> doc1, doc2
search -> doc1
spring -> doc2
```

This lets us jump directly to documents containing a query term.

Our current index stores posting data:

```text
term -> docId -> frequency + positions
```

Example:

```text
java -> doc1 frequency 3 positions [0, 3, 10]
```

`frequency` tells us how many times the word appears.

`positions` tells us where the word appears in the cleaned token list.

## Indexing Time Vs Search Time

The inverted index is created when documents are added:

```text
index.addDocument(document)
```

Search uses the already-built index:

```text
searchService.search(query)
```

This is the main tradeoff:

```text
More space during indexing -> faster search later
```

Without an index, every query would scan every document.

With an index, the query jumps directly to matching documents.

## Phrase Search

Phrase search means the order and distance of words matters.

Normal search:

```text
search engine
```

means:

```text
Find documents containing search or engine.
```

Exact phrase search:

```text
"search engine"
```

means:

```text
Find documents where search appears immediately before engine.
```

This is why we store positions.

Example:

```text
search -> positions [1, 8]
engine -> positions [2, 9]
```

The phrase matches twice because:

```text
1 -> 2
8 -> 9
```

## Ranking

If many documents match, ranking decides which one should appear first.

The first ranking method was simple term frequency:

```text
score = how many times query words appear in the document
```

## TF-IDF

TF-IDF means:

```text
Term Frequency * Inverse Document Frequency
```

It fixes a weakness in simple frequency ranking.

If a word appears many times in one document, it is important for that document.

But if the same word appears in every document, it is less useful for deciding relevance.

Current formula:

```text
tf = 1 + log(term frequency in document)
idf = log(total documents / documents containing term)
score = tf * idf
```

Example idea:

```text
java appears in many documents -> lower idf
backend appears in fewer documents -> higher idf
```

So TF-IDF gives more value to terms that are frequent in a document but rare across the collection.

Later we will add BM25, which improves this further by handling document length better.

## Current Tests

The project has a lightweight test runner:

```text
src/test/java/com/searchengine/SearchEngineTests.java
```

It checks:

- text tokenization
- inverted index frequency and positions
- term-frequency search ranking
- exact phrase search
- TF-IDF giving more weight to rarer terms

This matters because tests protect the search engine core before we add Spring Boot, PostgreSQL, Redis, and React around it.
