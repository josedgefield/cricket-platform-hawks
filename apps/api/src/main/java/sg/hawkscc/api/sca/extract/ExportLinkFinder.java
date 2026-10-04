package sg.hawkscc.api.sca.extract;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Finds the URL behind a page's "CSV" / "Export" button, if it has a real downloadable URL. Many CricClubs-style
 * pages build the CSV in the browser from the visible table (e.g. a DataTables "CSV" button); in that case there is
 * no URL and this returns empty, so the caller reads the same table instead.
 */
public final class ExportLinkFinder {

    private static final Pattern EXPORT_WORD = Pattern.compile("\\b(csv|export|excel|download)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_IN_SCRIPT = Pattern.compile(
            "['\"]([^'\"\\s]*(?:csv|export|download)[^'\"\\s]*)['\"]", Pattern.CASE_INSENSITIVE);

    private ExportLinkFinder() {
    }

    public static Optional<URI> find(Document doc) {
        // 1. Anchors whose href or label says CSV/export.
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").trim();
            if (href.isEmpty() || href.startsWith("#") || href.toLowerCase(Locale.ROOT).startsWith("javascript:")
                    || href.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                continue;
            }
            if (looksLikeExport(href) || looksLikeExport(label(a))) {
                return toUri(a.absUrl("href"));
            }
        }
        // 2. Forms submitted by an export button.
        for (Element form : doc.select("form[action]")) {
            if (looksLikeExport(form.attr("action")) || !form.select("button, input[type=submit]").stream()
                    .filter(b -> looksLikeExport(label(b))).toList().isEmpty()) {
                if (form.attr("method").isBlank() || form.attr("method").equalsIgnoreCase("get")) {
                    return toUri(form.absUrl("action"));
                }
            }
        }
        // 3. Buttons/links whose onclick navigates to an export URL.
        for (Element el : doc.select("[onclick]")) {
            if (!looksLikeExport(label(el)) && !looksLikeExport(el.attr("onclick"))) {
                continue;
            }
            Matcher m = URL_IN_SCRIPT.matcher(el.attr("onclick"));
            while (m.find()) {
                String candidate = m.group(1);
                if (candidate.contains("/") || candidate.contains(".do") || candidate.endsWith(".csv")) {
                    return toUri(resolve(doc, candidate));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeExport(String text) {
        return text != null && EXPORT_WORD.matcher(text).find();
    }

    private static String label(Element el) {
        return String.join(" ", el.text(), el.attr("title"), el.attr("aria-label"), el.attr("value"),
                el.attr("class"), el.id());
    }

    private static String resolve(Document doc, String relative) {
        try {
            return URI.create(doc.location()).resolve(relative).toString();
        } catch (IllegalArgumentException e) {
            return relative;
        }
    }

    private static Optional<URI> toUri(String url) {
        try {
            URI uri = URI.create(url);
            return uri.isAbsolute() ? Optional.of(uri) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
