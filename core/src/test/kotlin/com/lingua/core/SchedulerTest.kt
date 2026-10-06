package com.lingua.core
import kotlin.test.*
class SchedulerTest {
    @Test fun `success advances through intervals and caps at thirty days`() {
        val scheduler = IntervalReviewScheduler()
        var p = Progress()
        listOf(1,3,7,14,30,30).forEach { days -> p = scheduler.schedule(p, Recall.KNOW, 1000); assertEquals(1000L + days * 86_400_000L, p.nextReview) }
        assertEquals(6, p.correct)
    }
    @Test fun `failure resets interval and records error`() {
        val p = IntervalReviewScheduler().schedule(Progress(stage=5, streak=4), Recall.AGAIN, 1000)
        assertEquals(0,p.stage); assertEquals(0,p.streak); assertEquals(1,p.errors); assertEquals(61000,p.nextReview)
    }
    @Test fun `fuzzy shortens interval without counting a correct answer`() {
        val p = IntervalReviewScheduler().schedule(Progress(stage=3), Recall.FUZZY, 0)
        assertEquals(2,p.stage); assertEquals(0,p.correct); assertEquals(259200000,p.nextReview)
    }
    @Test fun `normalization accepts punctuation and spacing`() { assertEquals(StandardTokenizer().normalize("안녕하세요!"), StandardTokenizer().normalize(" 안녕하세요 ")) }
}
