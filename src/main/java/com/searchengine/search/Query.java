package com.searchengine.search;

import java.util.List;

/**
 * Query AST. Terms are already analyzed (stemmed etc.). A {@code null} field means "all default
 * fields", which the {@link Searcher} expands using its field boosts.
 */
public sealed interface Query {

    record Term(String field, String term) implements Query {
    }

    /** {@code positions[i]} is the analyzer position of {@code terms[i]}; gaps encode stopwords. */
    record Phrase(String field, List<String> terms, List<Integer> positions) implements Query {
        public Phrase {
            if (terms.size() != positions.size() || terms.isEmpty()) {
                throw new IllegalArgumentException("phrase needs one position per term");
            }
            terms = List.copyOf(terms);
            positions = List.copyOf(positions);
        }
    }

    /** Matches terms starting with an analyzed-but-unstemmed prefix. */
    record Prefix(String field, String prefix) implements Query {
    }

    record Bool(List<Clause> clauses) implements Query {
        public Bool {
            clauses = List.copyOf(clauses);
        }
    }

    record Clause(Occur occur, Query query) {
    }

    enum Occur { MUST, SHOULD, MUST_NOT }
}
