package com.magiii.televipnext.features.otherFeatures;

import com.magiii.televipnext.language.Keys;
import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShowProfileIdDcTest {

    @Test
    public void formatTitleId_matchesNagramXfStyleWithoutMinus100() {
        assertEquals("6298774840", ShowProfileIdDc.formatTitleId(6298774840L, 0L));
        assertEquals("5716741926", ShowProfileIdDc.formatTitleId(0L, 5716741926L));
        assertEquals("123456789", ShowProfileIdDc.formatTitleId(0L, 123456789L));
        assertEquals("", ShowProfileIdDc.formatTitleId(0L, 0L));
    }

    @Test
    public void formatRawCopyId_returnsBotApiOrUserId() {
        assertEquals("6298774840", ShowProfileIdDc.formatRawCopyId(6298774840L, 0L, false));
        assertEquals("-1005716741926", ShowProfileIdDc.formatRawCopyId(0L, 5716741926L, false));
        assertEquals("-123456789", ShowProfileIdDc.formatRawCopyId(0L, 123456789L, true));
        assertEquals("", ShowProfileIdDc.formatRawCopyId(0L, 0L, false));
    }

    @Test
    public void formatDcLine_matchesNagramXfDataCenters() {
        assertEquals("DC1 Pluto, Miami", ShowProfileIdDc.formatDcLine(1));
        assertEquals("DC2 Venus, Amsterdam", ShowProfileIdDc.formatDcLine(2));
        assertEquals("DC3 Aurora, Miami", ShowProfileIdDc.formatDcLine(3));
        assertEquals("DC4 Vesta, Amsterdam", ShowProfileIdDc.formatDcLine(4));
        assertEquals("DC5 Flora, Singapore", ShowProfileIdDc.formatDcLine(5));
        assertEquals("DC203", ShowProfileIdDc.formatDcLine(203));
        assertEquals("ID", ShowProfileIdDc.formatDcLine(0));
        assertEquals("ID", ShowProfileIdDc.formatDcLine(-1));
    }

    @Test
    public void formatApproxDateShort_matchesShortUtcFormat() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(2023, Calendar.AUGUST, 6, 17, 23, 0);
        assertEquals("~ 06.08.23 at 17:23", ShowProfileIdDc.formatApproxDateShort(cal.getTimeInMillis()));
    }

    @Test
    public void formatChatJoinDate_includesFullDateYearAndTime() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar cal = Calendar.getInstance(utc);
        cal.set(2026, Calendar.NOVEMBER, 7, 12, 34, 0);
        long sec = cal.getTimeInMillis() / 1000L;
        assertEquals("07.11.2026 at 12:34", ShowProfileIdDc.formatChatJoinDate(sec, Locale.US, utc));
        assertEquals("07.11.2026, 12:34", ShowProfileIdDc.formatChatJoinDate(sec, Locale.forLanguageTag("vi"), utc));
    }

    @Test
    public void resolveChatHintKey_distinguishesCreatorLeftAndJoined() {
        assertEquals(Keys.ProfileCreatedChatSelfHint, ShowProfileIdDc.resolveChatHintKey(true, false));
        assertEquals(Keys.ProfileCreatedChatSelfHint, ShowProfileIdDc.resolveChatHintKey(true, true));
        assertEquals(Keys.ProfileCreatedChatHint, ShowProfileIdDc.resolveChatHintKey(false, true));
        assertEquals(Keys.ProfileJoinedChatHint, ShowProfileIdDc.resolveChatHintKey(false, false));
    }

    @Test
    public void stripNativeIdDcText_handlesStandaloneAndSuffixedStrings() {
        assertEquals("", ShowProfileIdDc.stripNativeIdDcText("ID: 6298774840, DC: 5"));
        assertEquals("online", ShowProfileIdDc.stripNativeIdDcText("online · ID: 6298774840, DC: 5"));
        assertEquals("online", ShowProfileIdDc.stripNativeIdDcText("online, ID: 6298774840"));
        assertNull(ShowProfileIdDc.stripNativeIdDcText("online"));
        assertNull(ShowProfileIdDc.stripNativeIdDcText("Uploaded May 28"));
    }
}
