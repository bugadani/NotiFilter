package hu.bugadani.notifilter

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Based on (article) https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
 * Based on (source) https://github.com/FanFataL/swipe-controller-demo/blob/master/app/src/main/java/pl/fanfatal/swipecontrollerdemo/SwipeController.java
 */
class AppListSwipeController() : ItemTouchHelper.Callback() {
    private var canReRegister: Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }

    override fun initialDx(viewHolder: RecyclerView.ViewHolder) : Float {
        viewHolder as AppListItemAdapter.ViewHolder
        Log.d("SwipeController", "Initial dX: ${viewHolder.lastDx}")
        return viewHolder.lastDx
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
        viewHolder as AppListItemAdapter.ViewHolder
        val clamped_dX = dX.coerceIn(-viewHolder.backgroundButtons.width.toFloat(), 0f)

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (canReRegister) {
                setTouchListener(
                    c,
                    recyclerView,
                    viewHolder,
                    clamped_dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

        if (!viewHolder.menuOpen || isCurrentlyActive) {
            viewHolder.background.visibility = if (dX != 0f) {
                View.VISIBLE
            } else {
                View.GONE
            }
            viewHolder.foreground.translationX = dX
        } else {
            viewHolder.foreground.translationX = -viewHolder.backgroundButtons.width.toFloat()
        }
        viewHolder.foreground.translationY = dY

        if (!isCurrentlyActive) {
            viewHolder.lastDx = viewHolder.foreground.translationX
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
            val swipeBack =
                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP

            if (swipeBack) {
                Log.d("SwipeController", "end: $dX")
                if (dX.toInt() <= -viewHolder.backgroundButtons.width) {
                    viewHolder.menuOpen = true
                    canReRegister = false
                    setTouchDownListener(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                } else {
                    viewHolder.menuOpen = false
                }
            }

            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchDownListener(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: AppListItemAdapter.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int, isCurrentlyActive: Boolean
    ) {
        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                canReRegister = true

                if (hitTest(recyclerView, viewHolder.foreground, event.x, event.y)) {
                    Log.d("SwipeController", "Open menu touched")
                    setTouchListener(c, recyclerView, viewHolder,
                        -viewHolder.backgroundButtons.width.toFloat(), dY, actionState, false)
                } else {
                    Log.d("SwipeController", "List touched")

                    viewHolder.menuOpen = false

                    recyclerView.setOnTouchListener { _, _ -> false }

                    startRecoveryAnimation(
                        viewHolder,
                        -viewHolder.backgroundButtons.width.toFloat()
                    )
                }
            }
            false
        }
    }

    private fun hitTest(parent: View, child: View, x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        val ploc = IntArray(2)
        parent.getLocationInWindow(ploc)
        child.getLocationInWindow(loc)
        val left = loc[0] - ploc[0]
        val top = loc[1] - ploc[1]
        return x >= left && x <= left + child.width && y >= top && y <= top + child.height
    }
}