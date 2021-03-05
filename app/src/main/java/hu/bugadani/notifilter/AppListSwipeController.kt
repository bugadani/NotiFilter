package hu.bugadani.notifilter

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min
import kotlin.math.max

/**
 * Based on (article) https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
 * Based on (source) https://github.com/FanFataL/swipe-controller-demo/blob/master/app/src/main/java/pl/fanfatal/swipecontrollerdemo/SwipeController.java
 */
class AppListSwipeController : ItemTouchHelper.Callback() {
    internal enum class MenuState {
        CLOSED, OPEN
    }

    private var swipeBack: Boolean = true
    private var menuState: MenuState = MenuState.CLOSED

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(ItemTouchHelper.LEFT)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = menuState != MenuState.CLOSED
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
            if (menuState == MenuState.CLOSED) {
                setTouchListener(
                    c,
                    recyclerView,
                    viewHolder as AppListItemAdapter.ViewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

        with(viewHolder as AppListItemAdapter.ViewHolder) {
            val clamped_dX = max(dX, -viewHolder.backgroundButtons.width.toFloat())
            if (menuState == MenuState.CLOSED) {
                if (dX != 0f) {
                    background.visibility = View.VISIBLE
                } else {
                    background.visibility = View.GONE
                }
                foreground.translationX = clamped_dX
            } else {
                foreground.translationX = min(clamped_dX, -viewHolder.backgroundButtons.width.toFloat())
            }
            foreground.translationY = dY
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
        recyclerView.setOnTouchListener { _, event ->
            swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP

            if (swipeBack) {
                if (dX.toInt() < -viewHolder.backgroundButtons.width) {
                    menuState = MenuState.OPEN
                }
                if (menuState != MenuState.CLOSED) {
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
        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                recyclerView.setOnTouchListener { _, _ -> false }
                swipeBack = false
                menuState = MenuState.CLOSED

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