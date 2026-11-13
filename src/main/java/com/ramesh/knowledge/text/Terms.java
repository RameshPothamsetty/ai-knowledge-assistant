package com.ramesh.knowledge.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenisation shared by the embedding model and the keyword scorer, so a term
 * that counts for one also counts for the other. Tokens are lower-cased,
 * stripped of a small set of stop words and lightly stemmed.
 */
public final class Terms {

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "have",
            "in", "is", "it", "its", "of", "on", "or", "that", "the", "this", "to", "was",
            "were", "will", "with", "what", "which", "who", "how", "when", "where", "why"
    );

    private Terms() {
    }

    public static List<String> of(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = stem(matcher.group());
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
        }
        return List.copyOf(terms);
    }

    /**
     * Very small suffix stripping so "documents" and "document" share a token.
     */
    public static String stem(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && (token.endsWith("ses") || token.endsWith("xes"))) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
