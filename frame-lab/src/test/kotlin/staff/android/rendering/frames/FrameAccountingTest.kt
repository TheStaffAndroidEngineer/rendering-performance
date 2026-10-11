package staff.android.rendering.frames

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FrameAccountingTest {
    private val subject: FrameAccounting = if (System.getProperty("frame.solution") == "learner") LearnerFrames else ReferenceFrames
    private val key = FrameKey("feed", 1)
    private fun expected(start: Long=100, end: Long=116) = ExpectedFrame(key,start,end)
    private fun actual(start: Long=101,end: Long=114,outcome: Outcome=Outcome.Presented) = ActualFrame(key,start,end,outcome)
    @Test fun signedSlackAndElapsedAreDifferentValues() {
        assertEquals(FrameObservation(key,FrameStatus.OnTime,1,13,-2),subject.account(listOf(expected()),listOf(actual())).observations.single())
    }
    @Test fun exactDeadlineIsOnTime() {
        assertEquals(FrameStatus.OnTime,subject.account(listOf(expected()),listOf(actual(end=116))).observations.single().status)
    }
    @Test fun lateStartCanMissDeadlineDespiteShortWork() {
        val row=subject.account(listOf(expected()),listOf(actual(start=115,end=119))).observations.single()
        assertEquals(FrameObservation(key,FrameStatus.Late,15,4,3),row)
    }
    @Test fun longWorkAfterOnTimeStartCanMissDeadline() {
        val row=subject.account(listOf(expected()),listOf(actual(start=100,end=121))).observations.single()
        assertEquals(FrameObservation(key,FrameStatus.Late,0,21,5),row)
    }
    @Test fun changedDeadlineUsesEachExpectedRecord() {
        val next=FrameKey("feed",2)
        val plans=listOf(expected(),ExpectedFrame(next,200,208))
        val results=listOf(actual(start=100,end=110),ActualFrame(next,200,210,Outcome.Presented))
        assertEquals(listOf(FrameStatus.OnTime,FrameStatus.Late),subject.account(plans,results).observations.map { it.status })
    }
    @Test fun sameTokenOnDifferentSurfacesIsNotTheSameFrame() {
        val video=FrameKey("video",1)
        val report=subject.account(listOf(expected(),ExpectedFrame(video,100,110)),listOf(ActualFrame(video,100,112,Outcome.Presented),actual()))
        assertEquals(listOf(FrameStatus.OnTime,FrameStatus.Late),report.observations.map { it.status })
        assertEquals(listOf(key,video),report.observations.map { it.key })
    }
    @Test fun actualInputOrderDoesNotChangeMatching() {
        val next=FrameKey("feed",2);val plans=listOf(expected(),ExpectedFrame(next,200,216))
        val records=listOf(actual(),ActualFrame(next,205,220,Outcome.Presented))
        assertEquals(subject.account(plans,records),subject.account(plans,records.reversed()))
    }
    @Test fun missingEvidenceIsUnknownRatherThanFast() {
        assertEquals(FrameObservation(key,FrameStatus.Missing,null,null,null),subject.account(listOf(expected()),emptyList()).observations.single())
    }
    @Test fun droppedBufferDoesNotInventPresentationOverrun() {
        assertEquals(FrameObservation(key,FrameStatus.Dropped,1,25,null),subject.account(listOf(expected()),listOf(actual(end=126,outcome=Outcome.Dropped))).observations.single())
    }
    @Test fun unmatchedActualIsRetainedAfterExpectedOrder() {
        val other=FrameKey("other",7)
        val report=subject.account(listOf(expected()),listOf(ActualFrame(other,5,9,Outcome.Presented)))
        assertEquals(listOf(FrameObservation(key,FrameStatus.Missing,null,null,null),FrameObservation(other,FrameStatus.Unmatched,null,4,null)),report.observations)
    }
    @Test fun denominatorIncludesOnlyMatchedPresentedObservations() {
        val plans=(1L..4).map { ExpectedFrame(FrameKey("feed",it),100,116) }
        val records=listOf(actual(),actual(start=110,end=120).copy(key=FrameKey("feed",2)),actual(outcome=Outcome.Dropped).copy(key=FrameKey("feed",3)))
        val result=subject.account(plans,records)
        assertEquals(2,result.matchedPresented);assertEquals(1,result.late);assertEquals(0.5,result.lateFraction)
    }
    @Test fun zeroMatchedPresentationsHaveNoPercentage() {
        assertNull(subject.account(listOf(expected()),emptyList()).lateFraction)
        assertEquals(emptyList<FrameObservation>(),subject.account(emptyList(),emptyList()).observations)
    }
    @Test fun duplicateKeysAreAmbiguousAndRejected() {
        assertThrows(IllegalArgumentException::class.java) { subject.account(listOf(expected(),expected()),listOf(actual())) }
        assertThrows(IllegalArgumentException::class.java) { subject.account(listOf(expected()),listOf(actual(),actual())) }
    }
    @Test fun invalidUnmatchedRowsAreStillRejected() {
        assertThrows(IllegalArgumentException::class.java) { subject.account(emptyList(),listOf(actual(start=20,end=10))) }
        assertThrows(IllegalArgumentException::class.java) { subject.account(listOf(expected(start=-1)),emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { subject.account(listOf(expected().copy(key=FrameKey("",0))),emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { subject.account(emptyList(),listOf(actual().copy(key=FrameKey("feed",-1)))) }
    }
    @Test fun earlyStartIsNotNegativeWaiting() {
        assertEquals(0L,subject.account(listOf(expected()),listOf(actual(start=95))).observations.single().startDelayNanos)
    }
    @Test fun largeValidTimestampsDoNotOverflowByAddingADuration() {
        val start=Long.MAX_VALUE-100;val plan=expected(start,Long.MAX_VALUE-10)
        val record=actual(start+5,Long.MAX_VALUE)
        assertEquals(FrameObservation(key,FrameStatus.Late,5,95,10),subject.account(listOf(plan),listOf(record)).observations.single())
    }
}
