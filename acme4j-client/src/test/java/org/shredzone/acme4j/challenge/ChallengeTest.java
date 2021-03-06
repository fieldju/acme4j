/*
 * acme4j - Java ACME client
 *
 * Copyright (C) 2015 Richard "Shred" Körber
 *   http://acme4j.shredzone.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.shredzone.acme4j.challenge;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.shredzone.acme4j.toolbox.AcmeUtils.parseTimestamp;
import static org.shredzone.acme4j.toolbox.TestUtils.*;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;

import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Test;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeProtocolException;
import org.shredzone.acme4j.exception.AcmeRetryAfterException;
import org.shredzone.acme4j.provider.TestableConnectionProvider;
import org.shredzone.acme4j.toolbox.JSON;
import org.shredzone.acme4j.toolbox.JSONBuilder;
import org.shredzone.acme4j.toolbox.TestUtils;

/**
 * Unit tests for {@link Challenge}.
 */
public class ChallengeTest {
    private Session session;
    private URI resourceUri = URI.create("https://example.com/acme/some-resource");
    private URI locationUri = URI.create("https://example.com/acme/some-location");

    @Before
    public void setup() throws IOException {
        session = TestUtils.session();
    }

    /**
     * Test that a challenge is properly restored.
     */
    @Test
    public void testChallenge() throws Exception {
        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public void sendRequest(URI uri, Session session) {
                assertThat(uri, is(locationUri));
            }

            @Override
            public int accept(int... httpStatus) throws AcmeException {
                assertThat(httpStatus, isIntArrayContainingInAnyOrder(
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED));
                return HttpURLConnection.HTTP_ACCEPTED;
            }

            @Override
            public JSON readJsonResponse() {
                return getJsonAsObject("updateHttpChallengeResponse");
            }
        };

        Session session = provider.createSession();

        provider.putTestChallenge(Http01Challenge.TYPE, new Http01Challenge(session));

        Http01Challenge challenge = Challenge.bind(session, locationUri);

        assertThat(challenge.getType(), is(Http01Challenge.TYPE));
        assertThat(challenge.getStatus(), is(Status.VALID));
        assertThat(challenge.getLocation(), is(locationUri));
        assertThat(challenge.getToken(), is("IlirfxKKXAsHtmzK29Pj8A"));

        provider.close();
    }

    /**
     * Test that after unmarshalling, the challenge properties are set correctly.
     */
    @Test
    public void testUnmarshall() throws URISyntaxException, MalformedURLException {
        Challenge challenge = new Challenge(session);

        // Test default values
        assertThat(challenge.getType(), is(nullValue()));
        assertThat(challenge.getStatus(), is(Status.PENDING));
        assertThat(challenge.getLocation(), is(nullValue()));
        assertThat(challenge.getValidated(), is(nullValue()));

        // Unmarshall a challenge JSON
        challenge.unmarshall(getJsonAsObject("genericChallenge"));

        // Test unmarshalled values
        assertThat(challenge.getType(), is("generic-01"));
        assertThat(challenge.getStatus(), is(Status.VALID));
        assertThat(challenge.getLocation(), is(new URI("http://example.com/challenge/123")));
        assertThat(challenge.getValidated(), is(parseTimestamp("2015-12-12T17:19:36.336785823Z")));
        assertThat(challenge.getError(), is(notNullValue()));
        assertThat(challenge.getError().getType(), is(URI.create("urn:ietf:params:acme:error:connection")));
        assertThat(challenge.getError().getDetail(), is("connection refused"));
        assertThat(challenge.getJSON().get("type").asString(), is("generic-01"));
        assertThat(challenge.getJSON().get("uri").asURL(), is(new URL("http://example.com/challenge/123")));
        assertThat(challenge.getJSON().get("not-present").asString(), is(nullValue()));
        assertThat(challenge.getJSON().get("not-present-url").asURL(), is(nullValue()));
    }

    /**
     * Test that {@link Challenge#respond(JSONBuilder)} contains the type.
     */
    @Test
    public void testRespond() throws JoseException {
        String json = TestUtils.getJson("genericChallenge");

        Challenge challenge = new Challenge(session);
        challenge.unmarshall(JSON.parse(json));

        JSONBuilder cb = new JSONBuilder();
        challenge.respond(cb);

        assertThat(cb.toString(), sameJSONAs("{\"type\"=\"generic-01\"}"));
    }

    /**
     * Test that an exception is thrown on challenge type mismatch.
     */
    @Test(expected = AcmeProtocolException.class)
    public void testNotAcceptable() throws URISyntaxException {
        Http01Challenge challenge = new Http01Challenge(session);
        challenge.unmarshall(getJsonAsObject("dnsChallenge"));
    }

    /**
     * Test that a challenge can be triggered.
     */
    @Test
    public void testTrigger() throws Exception {
        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public void sendSignedRequest(URI uri, JSONBuilder claims, Session session) {
                assertThat(uri, is(resourceUri));
                assertThat(claims.toString(), sameJSONAs(getJson("triggerHttpChallengeRequest")));
                assertThat(session, is(notNullValue()));
            }

            @Override
            public int accept(int... httpStatus) throws AcmeException {
                assertThat(httpStatus, isIntArrayContainingInAnyOrder(
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED));
                return HttpURLConnection.HTTP_ACCEPTED;
            }

            @Override
            public JSON readJsonResponse() {
                return getJsonAsObject("triggerHttpChallengeResponse");
            }
        };

        Session session = provider.createSession();

        Http01Challenge challenge = new Http01Challenge(session);
        challenge.unmarshall(getJsonAsObject("triggerHttpChallenge"));

        challenge.trigger();

        assertThat(challenge.getStatus(), is(Status.PENDING));
        assertThat(challenge.getLocation(), is(locationUri));

        provider.close();
    }

    /**
     * Test that a challenge is properly updated.
     */
    @Test
    public void testUpdate() throws Exception {
        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public void sendRequest(URI uri, Session session) {
                assertThat(uri, is(locationUri));
            }

            @Override
            public int accept(int... httpStatus) throws AcmeException {
                assertThat(httpStatus, isIntArrayContainingInAnyOrder(
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED));
                return HttpURLConnection.HTTP_OK;
            }

            @Override
            public JSON readJsonResponse() {
                return getJsonAsObject("updateHttpChallengeResponse");
            }

            @Override
            public void handleRetryAfter(String message) throws AcmeException {
                // Just do nothing
            }
        };

        Session session = provider.createSession();

        Challenge challenge = new Http01Challenge(session);
        challenge.unmarshall(getJsonAsObject("triggerHttpChallengeResponse"));

        challenge.update();

        assertThat(challenge.getStatus(), is(Status.VALID));
        assertThat(challenge.getLocation(), is(locationUri));

        provider.close();
    }

    /**
     * Test that a challenge is properly updated, with Retry-After header.
     */
    @Test
    public void testUpdateRetryAfter() throws Exception {
        final Instant retryAfter = Instant.now().plus(Duration.ofSeconds(30));

        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public void sendRequest(URI uri, Session session) {
                assertThat(uri, is(locationUri));
            }

            @Override
            public int accept(int... httpStatus) throws AcmeException {
                assertThat(httpStatus, isIntArrayContainingInAnyOrder(
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED));
                return HttpURLConnection.HTTP_ACCEPTED;
            }

            @Override
            public JSON readJsonResponse() {
                return getJsonAsObject("updateHttpChallengeResponse");
            }


            @Override
            public void handleRetryAfter(String message) throws AcmeException {
                throw new AcmeRetryAfterException(message, retryAfter);
            }
        };

        Session session = provider.createSession();

        Challenge challenge = new Http01Challenge(session);
        challenge.unmarshall(getJsonAsObject("triggerHttpChallengeResponse"));

        try {
            challenge.update();
            fail("Expected AcmeRetryAfterException");
        } catch (AcmeRetryAfterException ex) {
            assertThat(ex.getRetryAfter(), is(retryAfter));
        }

        assertThat(challenge.getStatus(), is(Status.VALID));
        assertThat(challenge.getLocation(), is(locationUri));

        provider.close();
    }

    /**
     * Test that null is handled properly.
     */
    @Test
    public void testNullChallenge() throws Exception {
        try {
            Challenge.bind(session, null);
            fail("locationUri accepts null");
        } catch (NullPointerException ex) {
            // expected
        }

        try {
            Challenge.bind(null, locationUri);
            fail("session accepts null");
        } catch (NullPointerException ex) {
            // expected
        }
    }

    /**
     * Test that an exception is thrown on a bad location URI.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBadBind() throws Exception {
        TestableConnectionProvider provider = new TestableConnectionProvider() {
            @Override
            public void sendRequest(URI uri, Session session) {
                assertThat(uri, is(locationUri));
            }

            @Override
            public int accept(int... httpStatus) throws AcmeException {
                assertThat(httpStatus, isIntArrayContainingInAnyOrder(
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED));
                return HttpURLConnection.HTTP_ACCEPTED;
            }

            @Override
            public JSON readJsonResponse() {
                return getJsonAsObject("updateRegistrationResponse");
            }
        };

        Session session = provider.createSession();
        Challenge.bind(session, locationUri);

        provider.close();
    }

    /**
     * Test that unmarshalling something different like a challenge fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBadUnmarshall() {
        Challenge challenge = new Challenge(session);
        challenge.unmarshall(getJsonAsObject("updateRegistrationResponse"));
    }

}
