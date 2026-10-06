package com.lingua.core
import kotlin.test.*
import java.time.LocalDate
class CalendarTest {
    private fun day(date: String,completed: Boolean)=StudyDailyRecord(date,"ko",1.0,1,0,1,0,completed,1,1)
    @Test fun `streak requires completed days and can end yesterday`() {
        val records=listOf(day("2026-10-01",true),day("2026-10-02",true),day("2026-10-03",false))
        val summary=StudyCalendarAnalytics.summarize(records,LocalDate.parse("2026-10-03"))
        assertEquals(2,summary.currentStreak); assertEquals(2,summary.longestStreak); assertEquals(3,summary.totalStudyDays); assertEquals(66,summary.monthCompletionRate)
    }
    @Test fun `longest survives a broken current streak across month boundary`() {
        val summary=StudyCalendarAnalytics.summarize(listOf(day("2026-09-29",true),day("2026-09-30",true),day("2026-10-01",true),day("2026-10-04",true)),LocalDate.parse("2026-10-06"))
        assertEquals(0,summary.currentStreak); assertEquals(3,summary.longestStreak); assertEquals(2,summary.monthStudyDays)
    }
    @Test fun `empty calendar has zero metrics`() { assertEquals(CalendarSummary(0,0,0,0,0),StudyCalendarAnalytics.summarize(emptyList(),LocalDate.parse("2026-10-06"))) }
}
