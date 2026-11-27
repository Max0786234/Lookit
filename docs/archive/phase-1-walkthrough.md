# Phase 1 Walkthrough

## What We Built

This phase builds a tiny search engine in pure Java.

The flow is:

```text
Document -> TextProcessor -> InvertedIndex -> SearchService -> Ranked Results
```

## Important Classes

### `Document`

Represents one searchable item.

For now, documents are manually created in `App.java`.

Later, crawler pages from the internet will become `Document` objects.

### `TextProcessor`

Turns raw text into searchable tokens.

Example:

```text
"Java is a powerful language!"
```

becomes:

```text
java, powerful, language
```

It currently does three things:

- lowercases text
- removes punctuation
- removes common stopwords

### `Posting`

Stores information about one term inside one document.

For example:

```text
java appears 3 times in document 1 at positions 0, 3, and 10
```

is stored as:

```text
Posting(documentId = 1, frequency = 3, positions = [0, 3, 10])
```

The code does not store `frequency` as a separate variable anymore. It calculates frequency from:

```text
positions.size()
```

### `InvertedIndex`

This is the heart of the search engine.

It stores:

```text
term -> documents containing that term
```

Example:

```text
java -> doc1, doc2
search -> doc1, doc3
```

This is why search becomes fast. We do not scan all documents every time.

The current Java structure is:

```text
Map<String, Map<Integer, Posting>>
```

Meaning:

```text
term -> document id -> posting information
```

The posting information now includes word positions.

## Why Positions Matter

Positions cost extra space, but they allow better search features later.

Without positions:

```text
We know search and engine appear somewhere in doc1.
```

With positions:

```text
search appears at [1, 8]
engine appears at [2, 9]
```

Now we can detect that `search engine` appears as a phrase because `engine` appears immediately after `search`.

## Phrase Search Logic

Quoted queries use phrase search:

```text
"search engine"
```

The query engine:

```text
1. removes the quotes
2. tokenizes the phrase
3. gets postings for the first term
4. checks whether the next terms appear at position + 1, position + 2, etc.
```

For example:

```text
search positions = [1, 8]
engine positions = [2, 9]
```

When `search` starts at `1`, `engine` is found at `2`, so that is a phrase match.

When `search` starts at `8`, `engine` is found at `9`, so that is another phrase match.

The phrase score is currently:

```text
score = number of exact phrase matches
```

## TF-IDF Ranking Logic

Simple term frequency says:

```text
score = how many times the query term appears
```

TF-IDF says:

```text
score = term frequency weight * rarity weight
```

The current code calculates:

```text
tf = 1 + log(frequency)
idf = log(total documents / documents containing term)
tf-idf = tf * idf
```

This is a deeper ranking signal because it asks two questions:

```text
1. Is this word important inside this document?
2. Is this word rare enough across all documents to be meaningful?
```

That is why TF-IDF is an important information retrieval concept.

### `Ranker`

This interface represents a ranking algorithm.

We currently have two ranking implementations:

```text
TermFrequencyRanker
TfIdfRanker
```

Later we will add:

```text
Bm25Ranker
```

### `SearchService`

This is the query engine.

It does:

```text
1. tokenize the user query
2. find matching postings from the inverted index
3. calculate document scores
4. sort results by score
```

## Why We Started Without Spring Boot

Spring Boot is useful for APIs, but the real search engine logic should be clear first.

If you understand this phase, then later Spring Boot will simply expose this logic through HTTP endpoints.

## What To Try

After Java is installed, run the program and then change the sample documents inside `App.java`.

Try queries like:

```text
java
search engine
spring backend
ranking algorithm
```

Observe which document comes first and why.

## Test Runner

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-phase1.ps1
```

The test runner is intentionally simple. It uses normal Java methods and throws `AssertionError` if something is wrong.

This keeps the first phase focused on search logic before adding JUnit through Maven.
