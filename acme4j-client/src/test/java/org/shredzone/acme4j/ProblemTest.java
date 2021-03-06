/*
 * acme4j - Java ACME client
 *
 * Copyright (C) 2017 Richard "Shred" Körber
 *   http://acme4j.shredzone.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.shredzone.acme4j;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.net.URI;

import org.junit.Test;
import org.shredzone.acme4j.toolbox.JSON;
import org.shredzone.acme4j.toolbox.TestUtils;

/**
 * Unit tests for {@link Problem}.
 */
public class ProblemTest {

    @Test
    public void testProblem() {
        JSON original = TestUtils.getJsonAsObject("problem");

        Problem problem = new Problem(original);

        assertThat(problem.getType(), is(URI.create("urn:ietf:params:acme:error:connection")));
        assertThat(problem.getDetail(), is("connection refused"));
        assertThat(problem.asJSON().toString(), is(sameJSONAs(original.toString())));
        assertThat(problem.toString(), is(sameJSONAs(original.toString())));
    }

}
