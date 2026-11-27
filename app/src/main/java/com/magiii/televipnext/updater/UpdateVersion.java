package com.magiii.televipnext.updater;

import java.util.Locale;

/**
 * Pure Java version comparison and release URL validation helpers.
 */
public final class UpdateVersion {

    public static final String ALLOWED_DOWNLOAD_PREFIX =
            "https://github.com/doandat943/TeleVip-Next-LSPosed/releases/download/";

    private UpdateVersion() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1).trim();
        }
        int plusIdx = s.indexOf('+');
        if (plusIdx >= 0) {
            s = s.substring(0, plusIdx).trim();
        }
        return s;
    }

    public static boolean isNewer(String remote, String current) {
        return compare(remote, current) > 0;
    }

    public static int compare(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.equals(nb)) return 0;

        String[] partsA = splitCoreAndPre(na);
        String[] partsB = splitCoreAndPre(nb);

        int coreCmp = compareDotNumeric(partsA[0], partsB[0]);
        if (coreCmp != 0) return coreCmp;

        String preA = partsA[1];
        String preB = partsB[1];
        if (preA.isEmpty() && preB.isEmpty()) return 0;
        if (preA.isEmpty()) return 1;
        if (preB.isEmpty()) return -1;
        return comparePreRelease(preA, preB);
    }

    public static boolean isAllowedDownloadUrl(String url) {
        return url != null && url.startsWith(ALLOWED_DOWNLOAD_PREFIX) && url.length() > ALLOWED_DOWNLOAD_PREFIX.length();
    }

    public static String sanitizeVersionToken(String raw) {
        if (raw == null) return "latest";
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isEmpty() ? "latest" : cleaned;
    }

    private static String[] splitCoreAndPre(String s) {
        int dash = s.indexOf('-');
        if (dash < 0) return new String[]{s, ""};
        return new String[]{s.substring(0, dash), s.substring(dash + 1)};
    }

    private static int compareDotNumeric(String a, String b) {
        String[] sa = a.isEmpty() ? new String[0] : a.split("\\.");
        String[] sb = b.isEmpty() ? new String[0] : b.split("\\.");
        int len = Math.max(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            int va = i < sa.length ? parseIntSafe(sa[i]) : 0;
            int vb = i < sb.length ? parseIntSafe(sb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int comparePreRelease(String a, String b) {
        String[] sa = a.split("\\.");
        String[] sb = b.split("\\.");
        int len = Math.max(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            if (i >= sa.length) return -1;
            if (i >= sb.length) return 1;
            String pa = sa[i];
            String pb = sb[i];
            boolean numA = isDigits(pa);
            boolean numB = isDigits(pb);
            if (numA && numB) {
                int ia = parseIntSafe(pa);
                int ib = parseIntSafe(pb);
                if (ia != ib) return Integer.compare(ia, ib);
            } else if (numA != numB) {
                return numA ? -1 : 1;
            } else {
                int cmp = pa.toLowerCase(Locale.ROOT).compareTo(pb.toLowerCase(Locale.ROOT));
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
