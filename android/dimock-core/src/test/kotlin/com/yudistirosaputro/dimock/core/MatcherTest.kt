package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.Matcher
import com.yudistirosaputro.dimock.core.model.BodyMatch
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {

    private fun request(
        method: String = "GET",
        host: String = "api.example.com",
        path: String = "/v1/portfolio/summary",
        query: Map<String, List<String>> = emptyMap(),
        headers: Map<String, List<String>> = emptyMap(),
        body: String? = null,
    ) = RequestSnapshot(method, "https://$host$path", host, path, query, headers, body)

    @Test
    fun `empty match matches everything`() {
        assertTrue(Matcher.matches(Match(), request()))
    }

    @Test
    fun `method is case-insensitive exact`() {
        assertTrue(Matcher.matches(Match(method = "get"), request(method = "GET")))
        assertFalse(Matcher.matches(Match(method = "POST"), request(method = "GET")))
    }

    @Test
    fun `path glob star stays within one segment`() {
        assertTrue(Matcher.matches(Match(path = "/v1/*/summary"), request(path = "/v1/portfolio/summary")))
        assertFalse(Matcher.matches(Match(path = "/v1/*"), request(path = "/v1/portfolio/summary")))
    }

    @Test
    fun `path glob double star crosses segments`() {
        assertTrue(Matcher.matches(Match(path = "/v1/**"), request(path = "/v1/portfolio/summary")))
        assertTrue(Matcher.matches(Match(path = "/v1/orders**"), request(path = "/v1/orders")))
        assertTrue(Matcher.matches(Match(path = "/v1/orders**"), request(path = "/v1/orders/42/items")))
    }

    @Test
    fun `path regex with re prefix`() {
        assertTrue(Matcher.matches(Match(path = "re:^/v1/auth/.*$"), request(path = "/v1/auth/login")))
        assertFalse(Matcher.matches(Match(path = "re:^/v1/auth/.*$"), request(path = "/v2/auth/login")))
    }

    @Test
    fun `path never matches against the query string`() {
        assertTrue(Matcher.matches(Match(path = "/v1/orders"), request(path = "/v1/orders", query = mapOf("status" to listOf("open")))))
    }

    @Test
    fun `host glob`() {
        assertTrue(Matcher.matches(Match(host = "*.example.com"), request(host = "api.example.com")))
        assertFalse(Matcher.matches(Match(host = "api.example.com"), request(host = "cdn.example.com")))
    }

    @Test
    fun `all listed query pairs must be present`() {
        val req = request(query = mapOf("grant_type" to listOf("password"), "scope" to listOf("read")))
        assertTrue(Matcher.matches(Match(query = mapOf("grant_type" to "password")), req))
        assertFalse(Matcher.matches(Match(query = mapOf("grant_type" to "refresh")), req))
        assertFalse(Matcher.matches(Match(query = mapOf("missing" to "x")), req))
    }

    @Test
    fun `header names are case-insensitive`() {
        val req = request(headers = mapOf("X-Client" to listOf("android")))
        assertTrue(Matcher.matches(Match(headers = mapOf("x-client" to "android")), req))
        assertFalse(Matcher.matches(Match(headers = mapOf("x-client" to "ios")), req))
    }

    @Test
    fun `body jsonpath equals on a GraphQL operationName`() {
        val body = """{"operationName":"GetPortfolio","variables":{"id":7}}"""
        val portfolio = Match(body = listOf(BodyMatch("$.operationName", "GetPortfolio")))
        val orders = Match(body = listOf(BodyMatch("$.operationName", "GetOrders")))
        assertTrue(Matcher.matches(portfolio, request(method = "POST", path = "/graphql", body = body)))
        assertFalse(Matcher.matches(orders, request(method = "POST", path = "/graphql", body = body)))
    }

    @Test
    fun `body jsonpath supports nested keys and array index and numbers`() {
        val body = """{"variables":{"ids":[3,5]},"flag":true}"""
        assertTrue(Matcher.matches(Match(body = listOf(BodyMatch("$.variables.ids[1]", "5"))), request(body = body)))
        assertTrue(Matcher.matches(Match(body = listOf(BodyMatch("$.flag", "true"))), request(body = body)))
        assertFalse(Matcher.matches(Match(body = listOf(BodyMatch("$.variables.ids[9]", "5"))), request(body = body)))
    }

    @Test
    fun `body match against a non-JSON or absent body never matches`() {
        val m = Match(body = listOf(BodyMatch("$.a", "1")))
        assertFalse(Matcher.matches(m, request(body = "not json")))
        assertFalse(Matcher.matches(m, request(body = null)))
    }
}
