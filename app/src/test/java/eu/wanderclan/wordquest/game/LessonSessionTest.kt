package eu.wanderclan.wordquest.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSessionTest {
    private fun exercise(wordId: String) = ChoiceExercise(
        ExerciseType.PICTURE_TO_WORD,
        wordId,
        Prompt.Picture("x"),
        listOf(Option.Text(wordId, wordId), Option.Text("other-1", "other one"), Option.Text("other-2", "other two")),
    )

    private val request = LessonRequest.UnitLesson("u", 1)
    private fun session(vararg words: String) = LessonSession("s-1", Lesson(request, words.map(::exercise), emptyList()))

    private fun LessonSession.right() = answer((current as ChoiceExercise).answerIndex).next()
    private fun LessonSession.wrong() = answer(((current as ChoiceExercise).answerIndex + 1) % 3).next()

    @Test
    fun `all right gives three stars and a correct outcome for every word`() {
        var s = session("a", "b", "c")
        repeat(3) { s = s.right() }
        assertTrue(s.isFinished)
        val result = s.result()
        assertEquals(3, result.stars)
        assertEquals("s-1", result.sessionId)
        assertEquals(request, result.request)
        assertEquals(listOf(WordOutcome("a", true), WordOutcome("b", true), WordOutcome("c", true)), result.outcomes)
    }

    @Test
    fun `a wrong answer comes back at the end of the lesson`() {
        var s = session("a", "b")
        s = s.wrong()
        assertEquals(listOf("a", "b", "a"), s.queue.map { it.wordId })
        s = s.right()
        assertEquals("a", s.current!!.wordId)
        s = s.right()
        assertTrue(s.isFinished)
        assertEquals(2, s.result().stars)
        assertEquals(listOf(WordOutcome("a", false), WordOutcome("b", true)), s.result().outcomes)
    }

    @Test
    fun `a word is re-queued at most twice`() {
        var s = session("a")
        s = s.wrong() // re-queued once
        s = s.wrong() // re-queued twice
        s = s.wrong() // not again
        assertTrue(s.isFinished)
        assertEquals(3, s.queue.size)
        assertEquals(3, s.mistakes)
        assertEquals(1, s.result().stars)
        assertEquals(listOf(WordOutcome("a", false)), s.result().outcomes)
    }

    @Test
    fun `star thresholds`() {
        assertEquals(3, Scoring.stars(0))
        assertEquals(2, Scoring.stars(1))
        assertEquals(2, Scoring.stars(2))
        assertEquals(1, Scoring.stars(3))
        assertEquals(1, Scoring.stars(20))
    }

    @Test
    fun `progress counts the answered question and grows with re-queued ones`() {
        var s = session("a", "b")
        assertEquals(0f, s.progress)
        s = s.answer(0)
        assertEquals(0.5f, s.progress)
        s = s.next().wrong()
        assertEquals(2f / 3f, s.progress)
        assertFalse(s.isFinished)
    }

    @Test(expected = IllegalStateException::class)
    fun `a question cannot be answered twice`() {
        session("a").answer(0).answer(1)
    }
}
