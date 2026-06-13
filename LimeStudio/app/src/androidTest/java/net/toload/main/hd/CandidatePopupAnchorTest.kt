package net.toload.main.hd.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class CandidatePopupAnchorTest {
    @Test
    fun popupBaseXAlwaysReservesDismissWidth() {
        assertEquals(121, CandidateView.popupBaseX(100, 21))
    }
    @Test
    fun expandedPopupDoesNotReserveLegacyBottomCloseButtonHeight() {
        assertEquals(240, CandidateView.popupContentHeight(240))
    }
    @Test
    fun expandedPopupFirstRowStartsAfterLeadingDismissButton() {
        assertEquals(21, CandidateExpandedView.rowStartX(0, 21))
        assertEquals(0, CandidateExpandedView.rowStartX(1, 21))
    }
    @Test
    fun expandedPopupFirstRowReservesTrailingCollapseButton() {
        assertEquals(452, CandidateExpandedView.rowEndX(0, 500, 48))
        assertEquals(500, CandidateExpandedView.rowEndX(1, 500, 48))
    }
    @Test
    fun expandedPopupRowsUseLiveCandidateBarHeightAndPadding() {
        assertEquals(34, CandidateExpandedView.rowHeight(30, 4))
        assertEquals(27, CandidateExpandedView.rowBaseline(34, 28f, 24f))
        assertEquals(2f, CandidateExpandedView.rowLineTop(0, 34, 4), 0f)
        assertEquals(32f, CandidateExpandedView.rowLineBottom(0, 34, 4), 0f)
    }
    @Test
    fun expandedPopupUsesLiveCandidateTextSizeAndMinimumWordWidth() {
        assertEquals(25.2f, CandidateView.liveCandidateTextSize(28f), 0.01f)
        var paint: Paint = Paint()
        paint.setTextSize(28f)
        var punctuationWidth: Int = ((paint.measureText("。") as Int) + 8)
        assertEquals(punctuationWidth, CandidateExpandedView.wordWidth(paint, "d", 4))
    }
    @Test
    fun expandedPopupUsesFrameLayoutParamsForScrollableContent() {
        var params: ViewGroup.LayoutParams = CandidateView.popupFrameContentLayoutParams(240)
        assertTrue((params is FrameLayout.LayoutParams))
        assertTrue((params is ViewGroup.MarginLayoutParams))
        assertEquals(240, params.height)
    }
    @Test
    fun popupHeightShrinksToContentOnlyWhenKeyboardViewIsHidden() {
        assertEquals(90, CandidateView.popupHeight(120, 90, true))
        assertEquals(120, CandidateView.popupHeight(120, 90, false))
    }
    @Test
    fun visibleKeyboardPopupHeightCoversCandidateBarAndKeyboard() {
        assertEquals(520, CandidateView.visibleKeyboardPopupHeight(400, 120))
    }
    @Test
    fun bottomAlignedPopupUsesNoAnchorYOffset() {
        assertEquals(0, CandidateView.popupYOffset(30, 90, true))
        assertEquals(0, CandidateView.popupYOffset(30, 90, false))
    }
    @Test
    fun visibleKeyboardPopupYStartsBelowCandidateBar() {
        assertEquals(630, CandidateView.visibleKeyboardPopupY(600, 30))
    }
    @Test
    fun composingPopupDoesNotShowWhileExpanded() {
        assertTrue(CandidateView.shouldShowComposingPopup(false, true))
        assertFalse(CandidateView.shouldShowComposingPopup(true, true))
        assertFalse(CandidateView.shouldShowComposingPopup(false, false))
    }
    @Test
    fun rightActionAcceptsButtonAndParentClicks() {
        var rightButton: View = View(InstrumentationRegistry.getInstrumentation().getTargetContext())
        var rightParent: View = View(InstrumentationRegistry.getInstrumentation().getTargetContext())
        var other: View = View(InstrumentationRegistry.getInstrumentation().getTargetContext())
        assertTrue(CandidateInInputViewContainer.isRightActionClick(rightButton, rightButton, rightParent))
        assertTrue(CandidateInInputViewContainer.isRightActionClick(rightParent, rightButton, rightParent))
        assertFalse(CandidateInInputViewContainer.isRightActionClick(other, rightButton, rightParent))
    }
    @Test
    fun candidateStripRightEdgeTapDoesNotStealLastCandidate() {
        assertFalse(CandidateView.isExpandEdgeTap(455, 500, 48, 640))
        assertFalse(CandidateView.isExpandEdgeTap(451, 500, 48, 640))
        assertFalse(CandidateView.isExpandEdgeTap(455, 500, 48, 500))
    }
    @Test
    fun inputContainerRightEdgeActionIsLimitedToCandidateRow() {
        assertTrue(CandidateInInputViewContainer.isRightEdgeActionTap(1235f, 80f, 1280, 120, 96))
        assertFalse(CandidateInInputViewContainer.isRightEdgeActionTap(1183f, 80f, 1280, 120, 96))
        assertFalse(CandidateInInputViewContainer.isRightEdgeActionTap(1235f, 121f, 1280, 120, 96))
    }
    @Test
    fun rightActionShowsDownBeforeExpandAndUpAfterExpand() {
        assertFalse(CandidateInInputViewContainer.shouldShowCollapseGlyph(false, false, false))
        assertTrue(CandidateInInputViewContainer.shouldShowCollapseGlyph(false, true, false))
        assertFalse(CandidateInInputViewContainer.shouldShowCollapseGlyph(true, true, false))
    }
    @Test
    fun idleToolsWaitForRevealDelayAndNoComposition() {
        assertFalse(CandidateInInputViewContainer.shouldShowIdleTools(false, true, false))
        assertFalse(CandidateInInputViewContainer.shouldShowIdleTools(true, false, false))
        assertFalse(CandidateInInputViewContainer.shouldShowIdleTools(true, true, true))
        assertTrue(CandidateInInputViewContainer.shouldShowIdleTools(true, true, false))
    }
    @Test
    fun activeChromeStaysVisibleDuringDelayedEmptyTransition() {
        assertTrue(CandidateInInputViewContainer.shouldShowActiveChrome(false, false, false))
        assertTrue(CandidateInInputViewContainer.shouldShowActiveChrome(true, false, false))
        assertFalse(CandidateInInputViewContainer.shouldShowActiveChrome(true, true, true))
    }
}
