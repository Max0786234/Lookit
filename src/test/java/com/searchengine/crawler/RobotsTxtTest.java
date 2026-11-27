package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsTxtTest {

    private static final String AGENT = "LookitBot";

    @Test
    void specificGroupOverridesWildcard() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /

                User-agent: lookitbot
                Disallow: /private
                Crawl-delay: 2.5
                """, AGENT);
        assertTrue(r.isAllowed("https://e.com/public"));
        assertFalse(r.isAllowed("https://e.com/private/x"));
        assertEquals(2.5, r.crawlDelaySeconds().orElseThrow());
    }

    @Test
    void wildcardGroupAppliesWhenNoSpecificGroup() {
        RobotsTxt r = RobotsTxt.parse("User-agent: Googlebot\nDisallow: /\n\nUser-agent: *\nDisallow: /tmp/\n", AGENT);
        assertTrue(r.isAllowed("https://e.com/"));
        assertFalse(r.isAllowed("https://e.com/tmp/file"));
        assertTrue(r.crawlDelaySeconds().isEmpty());
    }

    @Test
    void longestMatchWinsAndAllowWinsTies() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Disallow: /docs/
                Allow: /docs/public/
                Allow: /page
                Disallow: /page
                """, AGENT);
        assertFalse(r.isAllowed("https://e.com/docs/secret"));
        assertTrue(r.isAllowed("https://e.com/docs/public/a"));
        assertTrue(r.isAllowed("https://e.com/page"));
    }

    @Test
    void wildcardsAndEndAnchor() {
        RobotsTxt r = RobotsTxt.parse("User-agent: *\nDisallow: /*.pdf$\nDisallow: /*?sessionid=\n", AGENT);
        assertFalse(r.isAllowed("https://e.com/a/b.pdf"));
        assertTrue(r.isAllowed("https://e.com/a/b.pdf.html"));
        assertFalse(r.isAllowed("https://e.com/x?sessionid=42"));
        assertTrue(r.isAllowed("https://e.com/x?page=2"));
    }

    @Test
    void groupedUserAgentsShareRulesAndCommentsAreIgnored() {
        RobotsTxt r = RobotsTxt.parse("""
                # comment
                User-agent: OtherBot
                User-agent: LookitBot   # us
                Disallow: /shared   # trailing comment
                """, AGENT);
        assertFalse(r.isAllowed("https://e.com/shared/page"));
    }

    @Test
    void emptyDisallowAllowsAllAndRobotsFileIsAlwaysAllowed() {
        assertTrue(RobotsTxt.parse("User-agent: *\nDisallow:\n", AGENT).isAllowed("https://e.com/anything"));
        assertTrue(RobotsTxt.parse("User-agent: *\nDisallow: /\n", AGENT).isAllowed("https://e.com/robots.txt"));
    }

    @Test
    void unreachableRobotsPolicy() {
        assertTrue(RobotsTxt.forStatus(404).isAllowed("https://e.com/x"));
        assertFalse(RobotsTxt.forStatus(503).isAllowed("https://e.com/x"));
        assertFalse(RobotsTxt.forStatus(0).isAllowed("https://e.com/x"));
    }
}
