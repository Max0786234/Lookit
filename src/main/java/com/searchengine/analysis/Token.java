package com.searchengine.analysis;

/**
 * One analyzed token.
 *
 * @param term     normalised index term
 * @param position token position; removed tokens (stopwords) leave gaps so phrase offsets stay true
 * @param start    start char offset in the original text (inclusive)
 * @param end      end char offset in the original text (exclusive)
 */
public record Token(String term, int position, int start, int end) {
}
