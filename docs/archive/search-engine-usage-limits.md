# Search Engine: Current Scope, Usage, and Limits

## What this project is

This repository is a learning-first search engine built in Java, with a Spring Boot API and a small frontend. It is designed to teach how a search engine works from the inside out rather than to behave like a production-grade web search engine such as Google or Bing.

The current system covers the core pipeline:

- document storage
- text preprocessing
- tokenization
- inverted index
- ranking
- query execution
- REST API
- simple browser UI

It is best understood as an educational prototype that demonstrates how search engines work.

## What you can search right now

At the moment, the app can search the documents that live in the local application data set. This includes:

- the default sample documents created when the app starts
- documents added through the API or app flow
- documents created by the crawler when you use the crawl endpoint on a website

Examples of things it can search are:

- java
- spring
- backend
- search engine
- retrieval
- engine
- information
- boot

These are not general web queries. They are queries against the documents the app knows about locally.

## What is not included yet

This is not a general-purpose browser search or internet-wide search engine. It does not:

- crawl the entire public web
- index all pages on the internet
- support arbitrary URL search on all websites
- maintain a giant production index
- handle distributed search infrastructure
- support user authentication, permissions, or multi-user data isolation
- behave like a real search product across an entire web corpus

The search is effectively limited to the project’s own document corpus and the pages you intentionally crawl.

## Search scope in practice

The current search behavior is limited to these kinds of data:

1. Seed documents stored in the app
2. Documents created through the document-create API
3. Simple crawler results from a seed page or small set of URLs

That means this is a local demo search engine, not a full browser or full-web search engine.

## Browser use case

The browser UI is meant to show the search flow to a user. It lets you:

- type a query
- call the backend search endpoint
- see ranked results
- inspect title, URL, and snippet

This is a great learning UI, but it is intentionally narrow. It is a frontend for a local search demo, not a general browsing platform.

## What the crawler can and cannot do

The crawler is intentionally simple and educational. It can:

- fetch a page from a URL
- extract title and text content
- strip HTML tags
- normalize links
- collect a small set of related URLs from a seed page

It cannot yet do:

- full-depth crawling of large websites
- rate-limited or polite crawling
- robots.txt enforcement
- scheduler-based crawling
- production-grade indexing of millions of pages
- robust extraction from complicated sites

In other words, the crawler is useful for learning the idea of crawl -> parse -> index, but not for running an enterprise search bot.

## Current ranking model

The project currently uses a BM25-style ranker and supporting logic. That means it can rank text documents by query relevance, but the ranking is still basic and suitable for learning.

This is not yet optimized for:

- cache-heavy production workloads
- personalization
- freshness ranking
- click-through analytics
- multi-field ranking
- advanced query rewrite

## Realistic search examples

Good searches for this project:

- java
- spring
- search engine
- backend
- workflow
- information retrieval
- java search engine

These searches work because the app stores documents matching those concepts.

Poor or unrealistic searches for this project:

- search the open web
- look up random websites across the internet
- query for live news or social content
- search a huge dataset without first indexing it in the app

The app does not currently do that.

## Important limits to keep in mind

The project has these intentionally limited boundaries:

- local, demo-scale search only
- small dataset and small crawler scope
- no full internet search
- no production indexing infrastructure
- no distributed or multi-node deployment
- no rich search features like autocomplete, faceting, or typo tolerance

## Recommended mental model

Think of this as a small, teachable search engine that shows the fundamentals well:

- index documents
- preprocess text
- rank matching results
- expose a query endpoint
- render a browser UI

It is not a production search engine replacement.

## Best way to use it today

Use this project when you want to:

- learn how an inverted index works
- test BM25-like ranking
- understand the API search flow
- prototype a basic document search UI
- experiment with local crawled content

Do not treat it as a global web search or browser replacement.

## Summary

The search in this project is intentionally constrained to documents the app knows about locally. You can search sample documents and manually added content, and you can do a small crawler-based exploration of seed pages. But this is not a general internet search engine and it is not meant to search the browser or the open web by default.
