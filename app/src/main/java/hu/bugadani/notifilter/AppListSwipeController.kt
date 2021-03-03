package hu.bugadani.notifilter

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min
import kotlin.math.max

/**
 * Based on (article) https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
 * Based on (source) https://github.com/FanFataL/swipe-controller-demo/blob/master/app/src/main/java/pl/fanfatal/swipecontrollerdemo/SwipeController.java
 */
class AppListSwipeController : ItemTouchHelper.Callback() {
    internal enum class MenuState {
        GONE, VISIBLE
    }

    private var swipeBack: Boolean = true
    private var menuState: MenuState = MenuState.GONE

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(0, ItemTouchHelper.LEFT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = menuState != MenuState.GONE
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            with(viewHolder as AppListItemAdapter.ViewHolder) {
                if (menuState != MenuState.GONE) {
                    foreground.translationX = min(dX, -viewHolder.backgroundButtons.width.toFloat())
                    foreground.translationY = dY
                } else {
                    // FIXME: no need to set this on every frame
                    setTouchListener(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }
        }

        if (menuState == MenuState.GONE) {
            with(viewHolder as AppListItemAdapter.ViewHolder) {
                if (dX != 0f) {
                    background.visibility = View.VISIBLE
                } else {
                    background.visibility = View.GONE
                }
                foreground.translationX = max(dX, -viewHolder.backgroundButtons.width.toFloat())
                foreground.translationY = dY
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: AppListItemAdapter.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        recyclerView.setOnTouchListener { v, event ->
            Log.d("SwipeController", "OnTouch")
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP

            if (swipeBack) {
                if (dX.toInt() < -viewHolder.backgroundButtons.width) {
                    menuState = MenuState.VISIBLE
                }
                if (menuState != MenuState.GONE) {
                    setTouchDownListener(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }

            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchDownListener(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int, isCurrentlyActive: Boolean
    ) {
        recyclerView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Log.d("SwipeController", "TouchDown")
                recyclerView.setOnTouchListener { v, event -> false }
                swipeBack = false
                menuState = MenuState.GONE
                // FIXME: it would be nice if this animated
                this@AppListSwipeController.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    0f,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
            false
        }
    }
}