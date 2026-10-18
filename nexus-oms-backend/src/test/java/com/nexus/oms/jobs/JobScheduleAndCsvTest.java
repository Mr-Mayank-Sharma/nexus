package com.nexus.oms.jobs;

import com.nexus.oms.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobScheduleAndCsvTest {

    @Test
    void aSixFieldCronIsAccepted_andAnEmptyOneMeansOnDemand() {
        assertDoesNotThrow(() -> JobSchedule.validate("0 0 6 * * *", "UTC"));
        assertDoesNotThrow(() -> JobSchedule.validate(null, "UTC"));
        assertDoesNotThrow(() -> JobSchedule.validate("  ", "UTC"));
    }

    @Test
    void anInvalidCronExplainsTheFormat() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> JobSchedule.validate("every day", "UTC"));
        assertTrue(e.getMessage().contains("six fields"), e.getMessage());
    }

    @Test
    void aJobMayNotRunMoreOftenThanEveryFiveMinutes() {
        assertThrows(BadRequestException.class, () -> JobSchedule.validate("0 * * * * *", "UTC"));       // every minute
        assertThrows(BadRequestException.class, () -> JobSchedule.validate("0 */2 * * * *", "UTC"));     // every 2 minutes
        assertDoesNotThrow(() -> JobSchedule.validate("0 */5 * * * *", "UTC"));
        assertDoesNotThrow(() -> JobSchedule.validate("0 0 * * * *", "UTC"));
    }

    @Test
    void anUnknownTimeZoneIsRefusedWithAnExample() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> JobSchedule.validate("0 0 6 * * *", "Mars/Base"));
        assertTrue(e.getMessage().contains("America/New_York"), e.getMessage());
    }

    @Test
    void nextIsComputedInTheJobsZone_andReturnedAsUtc() {
        // 06:00 in New York is 10:00 UTC in October (EDT, UTC-4)
        LocalDateTime next = JobSchedule.next("0 0 6 * * *", "America/New_York", LocalDateTime.of(2026, 10, 18, 0, 0));
        assertEquals(LocalDateTime.of(2026, 10, 18, 10, 0), next);
        // asked again after that moment, it is the following day
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0), JobSchedule.next("0 0 6 * * *", "America/New_York", next));
        assertNull(JobSchedule.next(null, "UTC", LocalDateTime.now()));
    }

    @Test
    void csvCellsThatLookLikeFormulasAreNeutralised() {
        assertEquals("'=HYPERLINK(\"x\")".replace("\"", "\"\""), JobCsv.cell("=HYPERLINK(\"x\")").replaceAll("^\"|\"$", ""));
        assertEquals("'+1+1", JobCsv.cell("+1+1"));
        assertEquals("'@SUM(A1)", JobCsv.cell("@SUM(A1)"));
        assertEquals("'-cmd", JobCsv.cell("-cmd"));
    }

    @Test
    void realNumbersAreNotMangled_andQuotingFollowsTheRules() {
        assertEquals("-5", JobCsv.cell(-5));
        assertEquals("12.5", JobCsv.cell("12.5"));
        assertEquals("\"a,b\"", JobCsv.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", JobCsv.cell("say \"hi\""));
        assertEquals("", JobCsv.cell(null));
    }

    @Test
    void theErrorsFileHasAHeaderAndOneLinePerFailedRowWithItsReason() {
        NxJobRunIssue i = new NxJobRunIssue();
        i.setRowRef("SKU-9");
        i.setReason("Nexus holds 3 but Shopify holds 5");
        i.setRaw("node-1");
        String csv = JobCsv.errors(List.of(i));
        assertEquals("row,reason,data\r\nSKU-9,Nexus holds 3 but Shopify holds 5,node-1\r\n", csv);
    }
}
