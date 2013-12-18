package io.undertow.predicate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
class PathMatchPredicate implements Predicate {

    private final String slashPath;
    private final String path;

    public PathMatchPredicate(final String path) {
        if (path.startsWith("/")) {
            this.slashPath = path;
            this.path = path.substring(1);
        } else {
            this.slashPath = "/" + path;
            this.path = path;
        }
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final HttpString relativePath = value.getRelativePath();
        if (relativePath.startsWith('/')) {
            return relativePath.equalToString(slashPath);
        } else {
            return relativePath.equalToString(path);
        }
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("path", String.class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("path");
        }

        @Override
        public String defaultParameter() {
            return "path";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String path = (String) config.get("path");
            return new PathMatchPredicate(path);
        }
    }
}
