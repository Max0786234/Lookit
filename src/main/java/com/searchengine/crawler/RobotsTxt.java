package com.searchengine.crawler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * robots.txt rules for one user agent, following RFC 9309:
 * <ul>
 *   <li>the group whose User-agent token matches ours (case-insensitive) wins, else the {@code *}
 *       group; consecutive User-agent lines share one group;</li>
 *   <li>the longest matching Allow/Disallow path wins, Allow wins ties; {@code *} matches any
 *       sequence and a trailing {@code $} anchors the end;</li>
 *   <li>an empty Disallow allows everything; {@code /robots.txt} itself is always allowed.</li>
 * </ul>
 * Crawl-delay is not part of the RFC but widely used, so it is honoured when present.
 */
public final class RobotsTxt {
    public static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of(), OptionalDouble.empty());

    private record Rule(boolean allow, String path, Pattern pattern) {
    }

    private final List<Rule> rules;
    private final OptionalDouble crawlDelaySeconds;

    private RobotsTxt(List<Rule> rules, OptionalDouble crawlDelaySeconds) {
        this.rules = rules;
        this.crawlDelaySeconds = crawlDelaySeconds;
    }

    /** What to do when robots.txt itself could not be fetched, per RFC 9309 §2.3.1. */
    public static RobotsTxt forStatus(int status) {
        // 4xx: no restrictions. 5xx / network failure: assume complete disallow.
        return status >= 400 && status < 500 ? ALLOW_ALL : parse("User-agent: *\nDisallow: /", "*");
    }

    public static RobotsTxt parse(String content, String userAgentToken) {
        String agent = userAgentToken.toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        Double specificDelay = null;
        Double wildcardDelay = null;
        boolean specificSeen = false;

        boolean inAgents = false;   // still reading the User-agent lines of a group
        boolean groupIsOurs = false;
        boolean groupIsStar = false;
        for (String rawLine : content.split("\\r?\\n|\\r")) {
            String line = rawLine.replaceFirst("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!inAgents) {
                    groupIsOurs = false;
                    groupIsStar = false;
                    inAgents = true;
                }
                String token = value.toLowerCase(Locale.ROOT);
                if (token.equals("*")) {
                    groupIsStar = true;
                } else if (token.split("/")[0].trim().equals(agent)) {
                    groupIsOurs = true;
                    specificSeen = true;
                }
                continue;
            }
            inAgents = false;
            List<Rule> target = groupIsOurs ? specific : groupIsStar ? wildcard : null;
            if (target == null) {
                continue;
            }
            switch (key) {
                case "allow", "disallow" -> {
                    if (!value.isEmpty()) {
                        target.add(new Rule(key.equals("allow"), value, compile(value)));
                    }
                }
                case "crawl-delay" -> {
                    try {
                        double d = Double.parseDouble(value);
                        if (groupIsOurs) {
                            specificDelay = d;
                        } else {
                            wildcardDelay = d;
                        }
                    } catch (NumberFormatException ignored) {
                        // malformed delay: ignore the line
                    }
                }
                default -> {
                }
            }
        }
        List<Rule> chosen = specificSeen ? specific : wildcard;
        Double delay = specificSeen ? specificDelay : wildcardDelay;
        return new RobotsTxt(List.copyOf(chosen), delay == null ? OptionalDouble.empty() : OptionalDouble.of(delay));
    }

    private static Pattern compile(String path) {
        StringBuilder re = new StringBuilder();
        boolean anchored = path.endsWith("$");
        String body = anchored ? path.substring(0, path.length() - 1) : path;
        for (String part : body.split("\\*", -1)) {
            if (!re.isEmpty()) {
                re.append(".*");
            }
            re.append(Pattern.quote(part));
        }
        return Pattern.compile(re + (anchored ? "$" : ".*"), Pattern.DOTALL);
    }

    public boolean isAllowed(String url) {
        String path;
        try {
            URI uri = URI.create(url);
            path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (path.equals("/robots.txt")) {
            return true;
        }
        Rule best = null;
        for (Rule r : rules) {
            if (r.pattern.matcher(path).matches()) {
                if (best == null || r.path.length() > best.path.length()
                        || (r.path.length() == best.path.length() && r.allow && !best.allow)) {
                    best = r;
                }
            }
        }
        return best == null || best.allow;
    }

    public OptionalDouble crawlDelaySeconds() {
        return crawlDelaySeconds;
    }
}
