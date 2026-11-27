---
title: Lookit
emoji: 🔎
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
short_description: A search engine built from scratch in Java
---

# Lookit

A search engine written from scratch in Java 21: Porter-stemmed inverted index with memory-mapped
segments, BM25F ranking with WAND pruning, a query language, MiniLM embeddings with an HNSW index,
hybrid rank fusion, spelling correction, autocomplete and a polite web crawler.

The demo opens with **5,183 scientific abstracts** (BEIR SciFact) already indexed. Try a question in
plain English, switch between Keyword / Semantic / Hybrid, or add web pages from the *Add web pages*
tab. Pages you crawl are kept until the Space restarts.

Source, evaluation and design notes: https://github.com/Max0786234/Lookit
