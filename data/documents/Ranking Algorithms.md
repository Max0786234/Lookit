# Ranking Algorithms

Ranking algorithms determine the order in which search results appear. The quality of a ranking algorithm directly impacts user satisfaction and the usefulness of a search engine.

## Term Frequency (TF)

The simplest ranking metric: how many times a query term appears in a document.

Advantages: Fast and intuitive
Disadvantages: Doesn't account for term importance or document length

Formula: TF(term, doc) = frequency of term in doc

## TF-IDF

Term Frequency - Inverse Document Frequency improves upon simple frequency by weighing rare terms higher.

The intuition: A term appearing in many documents is less valuable for distinguishing relevant documents. A term appearing in few documents is more valuable.

Formula: TF-IDF = TF(term, doc) * IDF(term)
where IDF(term) = log(total documents / documents containing term)

## BM25

Okapi BM25 is the modern standard for ranking. It's used by most production search engines including Elasticsearch and Lucene.

BM25 improves on TF-IDF by:
1. Normalizing for document length (longer documents naturally have higher term frequencies)
2. Using a saturation function (term frequency has diminishing returns)
3. Tuning parameters for field-specific behavior

## PageRank

Google's original ranking algorithm for web search. PageRank uses link structure:
- A page is important if many other important pages link to it
- Links from authoritative sites count more than links from random sites

This addresses a key weakness of content-based ranking: combating spam by considering link popularity.

## Modern Search Ranking

Production search engines combine multiple signals:
- Content relevance (TF-IDF, BM25)
- Link popularity (PageRank variants)
- User signals (click-through rate, dwell time)
- Freshness (recency of content)
- Personalization (user history, preferences)
- Context (search location, device)
- Quality signals (domain reputation, content quality)

Machine learning models increasingly learn optimal ranking by combining these signals based on user feedback.

## Challenges in Ranking

### Ambiguity
User queries are often ambiguous. "Java" could mean:
- The Java programming language
- The Indonesian island
- Coffee (slang)

Good ranking requires understanding context and query intent.

### Spam
Website owners try to manipulate rankings through:
- Keyword stuffing
- Link farms
- Doorway pages
- Cloaking

Search engines use various techniques to detect and penalize spam.

### Diversity
Users benefit from diverse results. A query for "Java" should return results about programming, the island, and coffee culture rather than only about programming.
