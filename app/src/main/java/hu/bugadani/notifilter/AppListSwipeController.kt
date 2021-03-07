package hu.bugadani.notifilter

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import hu.bugadani.notifilter.AppListItemAdapter.ViewHolder

/**
 * Based on (article) https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
 * Based on (source) https://github.com/FanFataL/swipe-controller-demo/blob/master/app/src/main/java/pl/fanfatal/swipecontrollerdemo/SwipeController.java
 */
class AppListSwipeController : ItemTouchHelper.Callback() {
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
    }

    override fun onSwiped(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) {
        viewHolder as ViewHolder
        if (direction == ItemTouchHelper.LEFT) {
            Log.d("SwipeController", "Swiped left")
            openMenuItem(recyclerView, viewHolder)
        } else if (direction == ItemTouchHelper.RIGHT) {
            Log.d("SwipeController", "Swiped right")
            closeMenuItem(recyclerView, viewHolder)
        }
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
        viewHolder as ViewHolder
        val clamped_dX = dX.coerceIn(-viewHolder.background.width.toFloat(), 0f)

        viewHolder.foreground.translationX = if (!viewHolder.menuOpen || isCurrentlyActive) {
            clamped_dX
        } else {
            -viewHolder.background.width.toFloat()
        }
        viewHolder.foreground.translationY = dY
    }

    @SuppressLint("ClickableViewAccessibility")
    fun openMenuItem(recyclerView: RecyclerView, viewHolder: ViewHolder) {
        viewHolder.menuOpen = true

        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                if (hitTest(recyclerView, viewHolder, event.x, event.y)) {
                    Log.d("SwipeController", "Open menu touched")
                } else {
                    Log.d("SwipeController", "List touched")

                    closeMenuItem(recyclerView, viewHolder)
                }
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun closeMenuItem(recyclerView: RecyclerView, viewHolder: ViewHolder) {
        viewHolder.menuOpen = false

        recyclerView.setOnTouchListener { _, _ -> false }

        startRecoveryAnimation(
            viewHolder,
            -viewHolder.background.width.toFloat()
        )
    }

    override fun hitTest(
        parent: View,
        child: RecyclerView.ViewHolder,
        x: Float,
        y: Float
    ): Boolean {
        val child = (child as ViewHolder).foreground

        val loc = IntArray(2)
        val ploc = IntArray(2)
        parent.getLocationInWindow(ploc)
        child.getLocationInWindow(loc)
        val left = loc[0] - ploc[0]
        val top = loc[1] - ploc[1]
        return x >= left && x <= left + child.width && y >= top && y <= top + child.height
    }

    override fun itemView(child: RecyclerView.ViewHolder): View {
        return (child as ViewHolder).foreground
    }
}