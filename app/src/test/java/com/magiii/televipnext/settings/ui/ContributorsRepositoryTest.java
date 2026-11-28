package com.magiii.televipnext.settings.ui;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContributorsRepositoryTest {

    @Test
    public void parseValidPayloadSortsDescendingAndBreaksTiesCaseInsensitively() {
        String json = "["
                + "{\"login\":\"zoe\",\"type\":\"User\",\"html_url\":\"https://github.com/zoe\",\"contributions\":5},"
                + "{\"login\":\"alice\",\"type\":\"User\",\"html_url\":\"https://github.com/alice\",\"contributions\":5},"
                + "{\"login\":\"mustafa1dev\",\"type\":\"User\",\"html_url\":\"https://github.com/mustafa1dev\",\"contributions\":122},"
                + "{\"login\":\"doandat943\",\"type\":\"User\",\"html_url\":\"https://github.com/doandat943\",\"contributions\":62}"
                + "]";

        List<ContributorsRepository.Contributor> result = ContributorsRepository.parseContributorsJson(json);
        assertEquals(4, result.size());
        assertEquals("mustafa1dev", result.get(0).login);
        assertEquals(122, result.get(0).contributions);
        assertEquals("doandat943", result.get(1).login);
        assertEquals(62, result.get(1).contributions);
        assertEquals("alice", result.get(2).login);
        assertEquals("zoe", result.get(3).login);
    }

    @Test
    public void filtersBotsBlankLoginsAndNonPositiveContributions() {
        String json = "["
                + "{\"login\":\"dependabot[bot]\",\"type\":\"Bot\",\"contributions\":42},"
                + "{\"login\":\"renovate[BOT]\",\"type\":\"User\",\"contributions\":10},"
                + "{\"login\":\"ghost-org\",\"type\":\"Organization\",\"contributions\":99},"
                + "{\"login\":\"   \",\"type\":\"User\",\"contributions\":8},"
                + "{\"login\":\"zeroUser\",\"type\":\"User\",\"contributions\":0},"
                + "{\"login\":\"negUser\",\"type\":\"User\",\"contributions\":-3},"
                + "{\"login\":\"ronaldiputra09\",\"type\":\"User\",\"html_url\":\"https://github.com/ronaldiputra09\",\"contributions\":1}"
                + "]";

        List<ContributorsRepository.Contributor> result = ContributorsRepository.parseContributorsJson(json);
        assertEquals(1, result.size());
        assertEquals("ronaldiputra09", result.get(0).login);
        assertEquals("https://github.com/ronaldiputra09", result.get(0).url);
        assertEquals(1, result.get(0).contributions);
    }

    @Test
    public void deduplicatesCaseInsensitivelyAndValidatesHtmlUrl() {
        String json = "["
                + "{\"login\":\"Alice\",\"html_url\":\"javascript:alert(1)\",\"contributions\":3},"
                + "{\"login\":\"alice\",\"html_url\":\"https://github.com/alice\",\"contributions\":9},"
                + "{\"login\":\"bob\",\"html_url\":\"https://evil.example/bob\",\"contributions\":4}"
                + "]";

        List<ContributorsRepository.Contributor> result = ContributorsRepository.parseContributorsJson(json);
        assertEquals(2, result.size());
        assertEquals("alice", result.get(0).login);
        assertEquals(9, result.get(0).contributions);
        assertEquals("https://github.com/alice", result.get(0).url);
        assertEquals("bob", result.get(1).login);
        assertEquals("https://github.com/bob", result.get(1).url);
    }

    @Test
    public void handlesNullEmptyAndMalformedJsonGracefully() {
        assertTrue(ContributorsRepository.parseContributorsJson(null).isEmpty());
        assertTrue(ContributorsRepository.parseContributorsJson("").isEmpty());
        assertTrue(ContributorsRepository.parseContributorsJson("   ").isEmpty());
        assertTrue(ContributorsRepository.parseContributorsJson("{not json").isEmpty());
        assertTrue(ContributorsRepository.parseContributorsJson("{\"message\":\"API rate limit exceeded\"}").isEmpty());
    }

    @Test
    public void shareSegmentsKeepSmallDistinctSetAndAggregateTailForLargeSet() {
        assertTrue(ContributorsRepository.computeShareSegments(null, 6, 0.03f).isEmpty());
        assertTrue(ContributorsRepository.computeShareSegments(new ArrayList<>(), 6, 0.03f).isEmpty());

        List<ContributorsRepository.Contributor> two = new ArrayList<>();
        two.add(new ContributorsRepository.Contributor("mustafa1dev", "https://github.com/mustafa1dev", 122));
        two.add(new ContributorsRepository.Contributor("doandat943", "https://github.com/doandat943", 62));
        List<ContributorsRepository.ShareSegment> segTwo = ContributorsRepository.computeShareSegments(two, 6, 0.03f);
        assertEquals(2, segTwo.size());
        assertEquals(0, segTwo.get(0).contributorIndex);
        assertEquals(1, segTwo.get(1).contributorIndex);
        assertEquals(1.0f, segTwo.get(0).fraction + segTwo.get(1).fraction, 0.001f);

        // Two contributors where #2 is 1%: do not collapse #2 into a lone -1 segment
        List<ContributorsRepository.Contributor> twoSkewed = new ArrayList<>();
        twoSkewed.add(new ContributorsRepository.Contributor("top", "https://github.com/top", 99));
        twoSkewed.add(new ContributorsRepository.Contributor("tiny", "https://github.com/tiny", 1));
        List<ContributorsRepository.ShareSegment> segSkewed =
                ContributorsRepository.computeShareSegments(twoSkewed, 6, 0.03f);
        assertEquals(2, segSkewed.size());
        assertEquals(0, segSkewed.get(0).contributorIndex);
        assertEquals(1, segSkewed.get(1).contributorIndex);

        // 25 contributors: at most 6 distinct + 1 tail segment summing to 1.0f
        List<ContributorsRepository.Contributor> list25 = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            list25.add(new ContributorsRepository.Contributor("user" + i, "https://github.com/user" + i, (25 - i) * 4));
        }
        List<ContributorsRepository.ShareSegment> seg25 = ContributorsRepository.computeShareSegments(list25, 6, 0.03f);
        assertEquals(7, seg25.size());
        assertEquals(-1, seg25.get(6).contributorIndex);
        float sum = 0f;
        for (ContributorsRepository.ShareSegment s : seg25) {
            sum += s.fraction;
        }
        assertEquals(1.0f, sum, 0.001f);
    }

    @Test
    public void sharePercentHandlesBoundaryAndNormalValues() {
        assertEquals(0, ContributorsRepository.computeSharePercent(0, 100));
        assertEquals(0, ContributorsRepository.computeSharePercent(10, 0));
        assertEquals(1, ContributorsRepository.computeSharePercent(1, 1000));
        assertEquals(66, ContributorsRepository.computeSharePercent(122, 184));
        assertEquals(100, ContributorsRepository.computeSharePercent(100, 100));
    }
}
