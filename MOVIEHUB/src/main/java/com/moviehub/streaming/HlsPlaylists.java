package com.moviehub.streaming;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites an HLS master playlist before it reaches the player:
 * <ul>
 *   <li>drops the variants above the plan's quality (the player cannot pick what it never sees);</li>
 *   <li>for external streams, makes relative URIs absolute so the media playlists and segments load straight
 *       from the video host.</li>
 * </ul>
 */
public final class HlsPlaylists {

    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=\\d+x(\\d+)");
    private static final Pattern URI_ATTRIBUTE = Pattern.compile("URI=\"([^\"]+)\"");

    private HlsPlaylists() {
    }

    /**
     * @param content    the master playlist
     * @param maxQuality highest picture height allowed; 0 = no limit
     * @param base       URL of the playlist for an external stream (URIs made absolute), or null to keep them
     * @return the playlist, and the variant URIs that were removed
     */
    public static Result filterMaster(String content, int maxQuality, URI base) {
        List<String> lines = content.lines().toList();
        List<Variant> variants = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("#EXT-X-STREAM-INF")) {
                int uriLine = nextUriLine(lines, i + 1);
                if (uriLine >= 0) {
                    variants.add(new Variant(i, uriLine, height(lines.get(i))));
                }
            }
        }
        Set<Integer> dropped = new TreeSet<>();
        if (maxQuality > 0 && !variants.isEmpty()) {
            List<Variant> allowed = variants.stream().filter(v -> v.height() <= maxQuality).toList();
            // Nothing small enough: keep the smallest one rather than nothing.
            int keepAnyway = allowed.isEmpty()
                    ? variants.stream().min((a, b) -> Integer.compare(a.height(), b.height())).get().tagLine()
                    : -1;
            for (Variant v : variants) {
                if (v.height() > maxQuality && v.tagLine() != keepAnyway) {
                    dropped.add(v.tagLine());
                    dropped.add(v.uriLine());
                }
            }
        }

        StringBuilder out = new StringBuilder();
        List<String> removed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (dropped.contains(i)) {
                if (!line.startsWith("#")) {
                    removed.add(line.strip());
                }
                continue;
            }
            out.append(rewrite(line, base)).append('\n');
        }
        return new Result(out.toString(), removed);
    }

    /** Whether the content is a master playlist (lists variants) rather than a media playlist. */
    public static boolean isMaster(String content) {
        return content.contains("#EXT-X-STREAM-INF");
    }

    public record Result(String playlist, List<String> removedUris) {
    }

    private record Variant(int tagLine, int uriLine, int height) {
    }

    private static String rewrite(String line, URI base) {
        if (base == null || line.isBlank()) {
            return line;
        }
        if (!line.startsWith("#")) {
            return base.resolve(line.strip()).toString();
        }
        Matcher m = URI_ATTRIBUTE.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("URI=\"" + base.resolve(m.group(1)) + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static int nextUriLine(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return i;
            }
        }
        return -1;
    }

    /** Picture height of a variant; unknown resolution counts as 0 (kept). */
    private static int height(String tag) {
        Matcher m = RESOLUTION.matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
