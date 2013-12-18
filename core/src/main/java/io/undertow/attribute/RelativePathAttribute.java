package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.QueryParameterUtils;

/**
 * The relative path
 *
 * @author Stuart Douglas
 */
public class RelativePathAttribute implements ExchangeAttribute {

    public static final String RELATIVE_PATH_SHORT = "%R";
    public static final String RELATIVE_PATH = "%{RELATIVE_PATH}";

    public static final ExchangeAttribute INSTANCE = new RelativePathAttribute();

    private RelativePathAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRelativePath().toString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        int pos = newValue.indexOf('?');
        if (pos == -1) {
            exchange.setRelativePath(HttpString.fromString(newValue));
            exchange.setRequestURI(exchange.getResolvedPath().concat(newValue));
            exchange.setRequestPath(exchange.getResolvedPath().concat(newValue));
        } else {
            final String path = newValue.substring(0, pos);
            exchange.setRelativePath(HttpString.fromString(path));
            exchange.setRequestURI(exchange.getResolvedPath().concat(newValue));
            exchange.setRequestPath(exchange.getResolvedPath().concat(newValue));

            final String newQueryString = newValue.substring(pos);
            exchange.setQueryString(HttpString.fromString(newQueryString));
            exchange.getQueryParameters().putAll(QueryParameterUtils.parseQueryString(newQueryString.substring(1)));
        }
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Relative Path";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            return token.equals(RELATIVE_PATH) || token.equals(RELATIVE_PATH_SHORT) ? INSTANCE : null;
        }
    }
}
