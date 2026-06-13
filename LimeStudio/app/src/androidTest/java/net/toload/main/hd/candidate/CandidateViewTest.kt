package net.toload.main.hd.candidate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.content.res.Resources
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.R
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.voice.DictationState
import java.util.ArrayList
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class CandidateViewTest {
    @Test
    fun shouldShowLimeToastWhenAnchorIsAttachedEvenIfCandidateRowIsHidden() {
        assertTrue(CandidateView.shouldShowLimeToast(true, "大易"))
    }
    @Test
    fun shouldNotShowLimeToastWithoutAttachedAnchorOrText() {
        assertFalse(CandidateView.shouldShowLimeToast(false, "大易"))
        assertFalse(CandidateView.shouldShowLimeToast(true, null))
        assertFalse(CandidateView.shouldShowLimeToast(true, ""))
    }
    @Test
    fun candidateActionButtonsStayTransparentOnThemedRow() {
        var darkCandidateBackground: Int = Color.rgb(16, 16, 16)
        assertEquals(darkCandidateBackground, CandidateInInputViewContainer.actionRowBackgroundColor(darkCandidateBackground))
        assertEquals(Color.TRANSPARENT, CandidateInInputViewContainer.actionButtonBackgroundColor())
        assertEquals(Color.TRANSPARENT, CandidateInInputViewContainer.dismissButtonBackgroundColor())
    }
    @Test
    fun dictationDisplayTextReflectsStateAndPartialText() {
        var resources: Resources = InstrumentationRegistry.getInstrumentation().getTargetContext().getResources()
        assertEquals(resources.getString(R.string.dictation_status_listening), CandidateView.dictationDisplayText(resources, DictationState.LISTENING, null))
        assertEquals("這是測試", CandidateView.dictationDisplayText(resources, DictationState.PARTIAL, "這是測試"))
        assertEquals(resources.getString(R.string.dictation_status_finalizing), CandidateView.dictationDisplayText(resources, DictationState.FINALIZING, null))
        assertEquals(resources.getString(R.string.dictation_status_error), CandidateView.dictationDisplayText(resources, DictationState.ERROR, null))
        assertEquals("", CandidateView.dictationDisplayText(resources, DictationState.IDLE, null))
    }
    @Test
    fun setSuggestionsWithoutHighlightLeavesNoSelectedCandidate() {
        var candidateView: CandidateView = CandidateView(InstrumentationRegistry.getInstrumentation().getTargetContext(), null)
        var composing: Mapping = Mapping()
        composing.setWord("salt")
        composing.setComposingCodeRecord()
        var suggestions: MutableList<Mapping?> = ArrayList()
        suggestions.add(composing)
        candidateView.setSuggestionsWithoutHighlight(suggestions, false, "1234567890")
        assertEquals(1, candidateView.mSelectedIndex)
    }
}
