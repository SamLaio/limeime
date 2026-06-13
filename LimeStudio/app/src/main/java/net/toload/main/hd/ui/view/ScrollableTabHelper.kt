package net.toload.main.hd.ui.view

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import net.toload.main.hd.R

object ScrollableTabHelper {
    private const val SCROLLBAR_SIZE_DP = 6

    @JvmStatic
    fun applyToNestedScrollView(activity: Activity?, scrollView: NestedScrollView) {
        applyBottomNavInset(activity, scrollView)
        applySafeScrollbarDrawables(scrollView)
        scrollView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.isScrollbarFadingEnabled = true
        installOverflowCheck(scrollView) {
            var canScroll = false
            if (scrollView.childCount > 0) {
                val child = scrollView.getChildAt(0)
                val viewportHeight = scrollView.height - scrollView.paddingTop - scrollView.paddingBottom
                canScroll = child.height > viewportHeight
            }
            setScrollbarVisibleWhenScrollable(scrollView, canScroll)
        }
    }

    private fun applySafeScrollbarDrawables(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        view.verticalScrollbarThumbDrawable =
            ContextCompat.getDrawable(view.context, R.drawable.settings_scrollbar_thumb)
        view.verticalScrollbarTrackDrawable =
            ContextCompat.getDrawable(view.context, R.drawable.settings_scrollbar_track)
    }

    @JvmStatic
    fun applyToRecyclerView(activity: Activity?, recyclerView: RecyclerView) {
        applyBottomNavInset(activity, recyclerView)
        recyclerView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.isScrollbarFadingEnabled = true
        installOverflowCheck(recyclerView) {
            setScrollbarVisibleWhenScrollable(recyclerView, canRecyclerViewScroll(recyclerView))
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                setScrollbarVisibleWhenScrollable(recyclerView, canRecyclerViewScroll(recyclerView))
            }
        })
    }

    @JvmStatic
    fun refreshRecyclerViewScrollbar(recyclerView: RecyclerView?) {
        if (recyclerView == null) return
        recyclerView.post {
            setScrollbarVisibleWhenScrollable(recyclerView, canRecyclerViewScroll(recyclerView))
        }
    }

    private fun applyBottomNavInset(activity: Activity?, scrollable: View) {
        val baseLeft = scrollable.paddingLeft
        val baseTop = scrollable.paddingTop
        val baseRight = scrollable.paddingRight
        val baseBottom = scrollable.paddingBottom
        if (scrollable is ViewGroup) {
            scrollable.clipToPadding = false
        }

        val bottomNav = activity?.findViewById<View>(R.id.main_bottom_nav) ?: return

        bottomNav.post {
            val navHeight = bottomNav.height
            if (navHeight <= 0) return@post
            scrollable.setPadding(baseLeft, baseTop, baseRight, baseBottom + navHeight)
        }
    }

    private fun installOverflowCheck(view: View, update: Runnable) {
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update.run() }
        view.post(update)

        val observer = view.viewTreeObserver
        observer.addOnGlobalLayoutListener { update.run() }
    }

    private fun setScrollbarVisibleWhenScrollable(view: View, canScroll: Boolean) {
        view.isVerticalScrollBarEnabled = canScroll
        view.isScrollbarFadingEnabled = !canScroll
        if (canScroll) {
            val density = view.resources.displayMetrics.density
            view.scrollBarSize = max(1, Math.round(SCROLLBAR_SIZE_DP * density))
        }
        view.invalidate()
    }

    private fun canRecyclerViewScroll(recyclerView: RecyclerView): Boolean {
        val adapter = recyclerView.adapter
        if (adapter == null || adapter.itemCount == 0) return false

        val layoutManager = recyclerView.layoutManager
        if (layoutManager is LinearLayoutManager) {
            if (adapter.itemCount > recyclerView.childCount) {
                return true
            }
            return layoutManager.findFirstCompletelyVisibleItemPosition() > 0 ||
                layoutManager.findLastCompletelyVisibleItemPosition() < adapter.itemCount - 1
        }

        return recyclerView.canScrollVertically(1) || recyclerView.canScrollVertically(-1)
    }
}
