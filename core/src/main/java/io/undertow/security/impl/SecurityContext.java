/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.security.impl;


import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationState;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.AttachmentKey;
import org.xnio.IoFuture;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
public class SecurityContext {

    public static final RuntimePermission PERMISSION = new RuntimePermission("MODIFY_UNDERTOW_SECURITY_CONTEXT");

    public static AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

    private final List<AuthenticationMechanism> authMechanisms = new ArrayList<AuthenticationMechanism>();
    private final IdentityManager identityManager;

    // TODO - We also need to supply a login method that allows app to supply a username and password.
    // Maybe this will need to be a custom mechanism that doesn't exchange tokens with the client but will then
    // be configured to either associate with the connection, the session or some other arbitrary whatever.
    //
    // Do we want multiple to be supported or just one?  Maybe extend the AuthenticationMechanism to allow
    // it to be identified and called.

    private AuthenticationState authenticationState = AuthenticationState.NOT_REQUIRED;
    private Principal authenticatedPrincipal;
    private String mechanismName;
    private Account account;

    public SecurityContext(final IdentityManager identityManager) {
        this.identityManager = identityManager;
        if(System.getSecurityManager() != null) {
            System.getSecurityManager().checkPermission(PERMISSION);
        }
    }

    /**
     * Performs authentication on the request. If the auth succeeds then the next handler will be invoked, otherwise the
     * completion handler will be called.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify the
     * exchange.
     *
     * @param exchange The exchange
     * @param nextHandler The next handler to invoke once auth succeeds
     */
    public void authenticate(final HttpServerExchange exchange, final HttpHandler nextHandler) {
        // TODO - A slight variation will be required if called from a servlet, in that case by being called authentication will
        // automatically become required, also will need to cope with control returning to the caller should it be successful.

        new RequestAuthenticator(authMechanisms.iterator(), exchange, nextHandler).authenticate();
    }

    public void setAuthenticationRequired() {
        authenticationState = AuthenticationState.REQUIRED;
    }

    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    public Principal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    /**
     * @return The name of the mechanism used to authenticate the request.
     */
    public String getMechanismName() {
        return mechanismName;
    }

    public boolean isUserInGroup(String group) {
        return identityManager.isUserInGroup(account, group);
    }

    IdentityManager getIdentityManager() {
        // Mechanisms can access this through the AuthenticationMechanism Util class.
        return identityManager;
    }

    public void addAuthenticationMechanism(final AuthenticationMechanism handler) {
        authMechanisms.add(handler);
    }

    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        return Collections.unmodifiableList(authMechanisms);
    }

    private class RequestAuthenticator {

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpServerExchange exchange;
        private final HttpHandler nextHandler;

        private RequestAuthenticator(final Iterator<AuthenticationMechanism> handlerIterator, final HttpServerExchange exchange, final HttpHandler nextHandler) {
            this.mechanismIterator = handlerIterator;
            this.exchange = exchange;
            this.nextHandler = nextHandler;
        }

        void authenticate() {
            // need a redesign :(
        }
    }
}
