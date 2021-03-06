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
class AppListSwipeController : ItemTouchHelper.Callback() {
    private var canReRegister: Boolean = true

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
        if (direction == ItemTouchHelper.LEFT) {
            Log.d("SwipeController", "Swiped")
            registerHider(recyclerView, viewHolder as AppListItemAdapter.ViewHolder)
            viewHolder.menuOpen = true
            canReRegister = false
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
        viewHolder as AppListItemAdapter.ViewHolder
        val clamped_dX = dX.coerceIn(-viewHolder.background.width.toFloat(), 0f)

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

        viewHolder.foreground.translationX = if (!viewHolder.menuOpen || isCurrentlyActive) {
            clamped_dX
        } else {
            -viewHolder.background.width.toFloat()
        }
        viewHolder.foreground.translationY = dY
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
                if (dX.toInt() <= -viewHolder.background.width) {
                    viewHolder.menuOpen = true
                    canReRegister = false
                    registerHider(recyclerView, viewHolder)
                } else {
                    viewHolder.menuOpen = false
                }
            }

            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun registerHider(recyclerView: RecyclerView, viewHolder: AppListItemAdapter.ViewHolder) {
        recyclerView.setOnTouchListener { _, innerEvent ->
            if (innerEvent.action == MotionEvent.ACTION_DOWN) {
                if (hitTest(recyclerView, viewHolder, innerEvent.x, innerEvent.y)) {
                    Log.d("SwipeController", "Open menu touched")
                } else {
                    Log.d("SwipeController", "List touched")

                    viewHolder.menuOpen = false

                    recyclerView.setOnTouchListener { _, _ -> false }

                    startRecoveryAnimation(
                        viewHolder,
                        -viewHolder.background.width.toFloat()
                    )
                }

                canReRegister = true
            }
            false
        }
    }

    override fun hitTest(
        parent: View,
        child: RecyclerView.ViewHolder,
        x: Float,
        y: Float
    ): Boolean {
        val child = (child as AppListItemAdapter.ViewHolder).foreground

        val loc = IntArray(2)
        val ploc = IntArray(2)
        parent.getLocationInWindow(ploc)
        child.getLocationInWindow(loc)
        val left = loc[0] - ploc[0]
        val top = loc[1] - ploc[1]
        return x >= left && x <= left + child.width && y >= top && y <= top + child.height
    }

    override fun itemView(child: RecyclerView.ViewHolder): View {
        return (child as AppListItemAdapter.ViewHolder).foreground
    }
}