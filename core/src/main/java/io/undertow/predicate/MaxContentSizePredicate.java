package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * Predicate that returns true if the Content-Size of a request is above a
 * given value.
 *
 * @author Stuart Douglas
 */
class MaxContentSizePredicate implements Predicate {

    private final long maxSize;

    public MaxContentSizePredicate(final long maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final HttpString length = value.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
        if(length == null) {
            return false;
        }
        return length.toLong() > maxSize;
    }
}
