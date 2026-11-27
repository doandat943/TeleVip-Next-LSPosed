package com.magiii.televipnext.updater;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateVersionTest {

    @Test
    public void normalizeStripsLeadingVAndWhitespace() {
        assertEquals("1.2.4", UpdateVersion.normalize(" v1.2.4 "));
        assertEquals("1.2.5", UpdateVersion.normalize("V1.2.5+42"));
        assertEquals("", UpdateVersion.normalize(null));
    }

    @Test
    public void compareHandlesSemverAndPrerelease() {
        assertTrue(UpdateVersion.isNewer("v1.2.5", "1.2.4"));
        assertTrue(UpdateVersion.isNewer("1.3.0", "1.2.9"));
        assertTrue(UpdateVersion.isNewer("2.0", "1.9.9"));
        assertTrue(UpdateVersion.isNewer("1.2.5", "1.2.5-beta1"));
        assertTrue(UpdateVersion.isNewer("1.2.5-rc2", "1.2.5-rc1"));

        assertFalse(UpdateVersion.isNewer("v1.2.4", "1.2.4"));
        assertFalse(UpdateVersion.isNewer("1.2.3", "1.2.4"));
        assertFalse(UpdateVersion.isNewer("1.2.4-beta1", "1.2.4"));
        assertEquals(0, UpdateVersion.compare("v1.2", "1.2.0"));
    }

    @Test
    public void allowedDownloadUrlEnforcesRepoPrefix() {
        assertTrue(UpdateVersion.isAllowedDownloadUrl(
                "https://github.com/doandat943/TeleVip-Next-LSPosed/releases/download/v1.2.4/TeleVip-Next-v1.2.4.apk"));
        assertFalse(UpdateVersion.isAllowedDownloadUrl("https://evil.com/TeleVip.apk"));
        assertFalse(UpdateVersion.isAllowedDownloadUrl("http://github.com/doandat943/TeleVip-Next-LSPosed/releases/download/v1.2.4/a.apk"));
        assertFalse(UpdateVersion.isAllowedDownloadUrl(null));
    }

    @Test
    public void sanitizeVersionTokenRemovesUnsafeChars() {
        assertEquals("v1.2.4", UpdateVersion.sanitizeVersionToken("v1.2.4"));
        assertEquals("v1.2.4_.._foo", UpdateVersion.sanitizeVersionToken("v1.2.4/..\\foo"));
        assertEquals("latest", UpdateVersion.sanitizeVersionToken(""));
    }
}
