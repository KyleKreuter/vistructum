package de.kylekreuter.vistructum.core.update;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginVersion {

    private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)(?:-(.+))?");

    private PluginVersion() {
    }

    /** Whether {@code candidate} is a later release than {@code running}; a pre-release is older than its release. */
    public static boolean isNewer(String candidate, String running) {
        Matcher next = VERSION.matcher(candidate.trim());
        Matcher current = VERSION.matcher(running.trim());
        if (!next.matches() || !current.matches()) {
            return false;
        }
        int[] a = numbers(next.group(1));
        int[] b = numbers(current.group(1));
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return next.group(2) == null && current.group(2) != null;
    }

    private static int[] numbers(String dotted) {
        return Arrays.stream(dotted.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }
}
