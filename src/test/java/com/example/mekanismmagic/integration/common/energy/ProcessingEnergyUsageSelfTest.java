package com.example.mekanismmagic.integration.common.energy;

/** Offline checks for completed-world-tick energy accounting. */
public final class ProcessingEnergyUsageSelfTest {
    private ProcessingEnergyUsageSelfTest() {
    }

    public static void main(String[] args) {
        currentTickIsHiddenAndMultipleEventsAccumulate();
        refundsOnlyCancelTheCurrentTicksProcessing();
        idleTicksAndSkippedReadsExpireUsage();
        clockRollbackStartsANewTimeline();
        extremeClocksDoNotOverflowOrCollideWithInitialState();
        amountsSaturateAndNeverBecomeNegative();
        clearDiscardsBothPendingAndCompletedHistory();
    }

    private static void currentTickIsHiddenAndMultipleEventsAccumulate() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        expect(0, usage.lastCompletedTick(40), "new meter");
        usage.recordExtraction(40, 100);
        usage.recordExtraction(40, 35);
        expect(0, usage.lastCompletedTick(40), "current tick must remain hidden");
        expect(135, usage.lastCompletedTick(41), "multiple work events must accumulate");
        expect(135, usage.lastCompletedTick(41), "reading must not consume the sample");
        usage.recordExtraction(41, 17);
        expect(135, usage.lastCompletedTick(41), "next work must not replace the completed sample");
        expect(17, usage.lastCompletedTick(42), "following completed tick");
    }

    private static void refundsOnlyCancelTheCurrentTicksProcessing() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        usage.recordExtraction(10, 100);
        usage.recordExtraction(10, 40);
        usage.recordRefund(10, 25);
        expect(115, usage.lastCompletedTick(11), "refund must subtract from summed processing");
        usage.recordRefund(11, 60);
        expect(115, usage.lastCompletedTick(11), "later refund must not rewrite yesterday's usage");
        usage.recordExtraction(11, 9);
        expect(9, usage.lastCompletedTick(12), "unmatched refund must not become credit for later work");
        usage.recordExtraction(12, 30);
        usage.recordRefund(12, 100);
        usage.recordExtraction(12, 7);
        expect(7, usage.lastCompletedTick(13), "excess refund must clamp at zero");
    }

    private static void idleTicksAndSkippedReadsExpireUsage() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        usage.recordExtraction(100, 240);
        expect(240, usage.lastCompletedTick(101), "first completed tick");
        expect(0, usage.lastCompletedTick(102), "one idle tick must clear usage");
        usage.recordExtraction(500, 600);
        expect(0, usage.lastCompletedTick(700), "opening a GUI later must not revive old usage");
        usage.recordExtraction(700, 20);
        expect(20, usage.lastCompletedTick(701), "work after an idle gap");
        expect(0, usage.lastCompletedTick(10_000), "large idle gap");
    }

    private static void clockRollbackStartsANewTimeline() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        usage.recordExtraction(100, 100);
        expect(100, usage.lastCompletedTick(101), "sample before rollback");
        expect(0, usage.lastCompletedTick(20), "rollback must forget the old timeline");
        usage.recordExtraction(20, 13);
        expect(13, usage.lastCompletedTick(21), "new timeline after a read detects rollback");
        usage.recordExtraction(4, 29);
        expect(0, usage.lastCompletedTick(4), "rollback first observed during extraction");
        expect(29, usage.lastCompletedTick(5), "extraction in restarted timeline");
        usage.recordRefund(2, 1);
        expect(0, usage.lastCompletedTick(3), "rollback first observed during refund");
    }

    private static void extremeClocksDoNotOverflowOrCollideWithInitialState() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        usage.recordExtraction(Long.MIN_VALUE, 8);
        expect(0, usage.lastCompletedTick(Long.MIN_VALUE), "minimum clock current tick");
        expect(8, usage.lastCompletedTick(Long.MIN_VALUE + 1), "minimum clock is a valid sampled tick");
        usage.recordExtraction(Long.MAX_VALUE - 1, 30);
        expect(30, usage.lastCompletedTick(Long.MAX_VALUE), "maximum clock completed tick");
        usage.recordExtraction(Long.MAX_VALUE, 12);
        expect(30, usage.lastCompletedTick(Long.MAX_VALUE), "maximum clock current sample remains hidden");
        expect(0, usage.lastCompletedTick(Long.MIN_VALUE), "clock wrap must reset without overflowing");
        usage.recordExtraction(Long.MIN_VALUE, 7);
        expect(7, usage.lastCompletedTick(Long.MIN_VALUE + 1), "work after clock wrap");
    }

    private static void amountsSaturateAndNeverBecomeNegative() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        usage.recordExtraction(8, -100);
        usage.recordRefund(8, -100);
        usage.recordExtraction(8, 0);
        usage.recordRefund(8, 0);
        expect(0, usage.lastCompletedTick(9), "nonpositive amounts must not invent usage");
        usage.recordExtraction(9, Long.MAX_VALUE - 5);
        usage.recordExtraction(9, 20);
        expect(Long.MAX_VALUE, usage.lastCompletedTick(10), "aggregate overflow must saturate");
        usage.recordExtraction(10, Long.MAX_VALUE);
        usage.recordRefund(10, 17);
        expect(Long.MAX_VALUE - 17, usage.lastCompletedTick(11), "refund from saturated usage");
        usage.recordExtraction(11, 3);
        usage.recordRefund(11, Long.MAX_VALUE);
        usage.recordRefund(11, Long.MIN_VALUE);
        expect(0, usage.lastCompletedTick(12), "huge or negative refunds must not underflow");
    }

    private static void clearDiscardsBothPendingAndCompletedHistory() {
        ProcessingEnergyUsage usage = new ProcessingEnergyUsage();
        usage.recordExtraction(20, 10);
        expect(10, usage.lastCompletedTick(21), "completed history before clear");
        usage.recordExtraction(21, 90);
        usage.clear();
        expect(0, usage.lastCompletedTick(21), "clear completed history");
        expect(0, usage.lastCompletedTick(22), "clear pending history");
        usage.recordExtraction(22, 7);
        expect(7, usage.lastCompletedTick(23), "meter remains usable after clear");
    }

    private static void expect(long expected, long actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }
}
