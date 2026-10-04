package sg.hawkscc.api.sca.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal robots.txt evaluation: collects the Allow/Disallow rules of the group for {@code *} (or for a group whose
 * user-agent token appears in our User-Agent) and applies longest-match precedence. Wildcards are not supported;
 * a rule containing {@code *} or {@code $} is matched on its literal prefix before the wildcard.
 */
public final class RobotsPolicy {

    private record Rule(boolean allow, String prefix) {
    }

    private final List<Rule> rules;

    private RobotsPolicy(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsPolicy allowAll() {
        return new RobotsPolicy(List.of());
    }

    public static RobotsPolicy parse(String robotsTxt, String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        List<Rule> star = new ArrayList<>();
        List<Rule> specific = new ArrayList<>();
        List<String> groupAgents = new ArrayList<>();
        boolean inRules = false;

        for (String rawLine : robotsTxt.split("\\R")) {
            String line = rawLine.replaceAll("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "user-agent" -> {
                    if (inRules) {
                        groupAgents.clear();
                        inRules = false;
                    }
                    groupAgents.add(value.toLowerCase(Locale.ROOT));
                }
                case "allow", "disallow" -> {
                    inRules = true;
                    if (value.isEmpty()) {
                        continue; // "Disallow:" with no path allows everything
                    }
                    int wildcard = indexOfWildcard(value);
                    Rule rule = new Rule(key.equals("allow"), wildcard >= 0 ? value.substring(0, wildcard) : value);
                    for (String agent : groupAgents) {
                        if (agent.equals("*")) {
                            star.add(rule);
                        } else if (!agent.isEmpty() && ua.contains(agent)) {
                            specific.add(rule);
                        }
                    }
                }
                default -> {
                    // sitemap, crawl-delay, etc. are ignored
                }
            }
        }
        return new RobotsPolicy(specific.isEmpty() ? star : specific);
    }

    private static int indexOfWildcard(String value) {
        int star = value.indexOf('*');
        int dollar = value.indexOf('$');
        if (star < 0) {
            return dollar;
        }
        return dollar < 0 ? star : Math.min(star, dollar);
    }

    /** @param pathAndQuery e.g. "/SingaporeCricketAssoc/teamBatting.do?teamId=1" */
    public boolean isAllowed(String pathAndQuery) {
        Rule best = null;
        for (Rule rule : rules) {
            if (pathAndQuery.startsWith(rule.prefix())
                    && (best == null || rule.prefix().length() > best.prefix().length()
                            || (rule.prefix().length() == best.prefix().length() && rule.allow()))) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }
}
