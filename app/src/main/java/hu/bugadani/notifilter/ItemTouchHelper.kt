package hu.bugadani.notifilter

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.*
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.*
import java.util.*
import kotlin.math.abs
import kotlin.math.sign

/**
 * This is a cut-down version of ItemTouchHelper from androidx.
 *
 * Modifications:
 *  - no drag support
 *  - Requires API level >= 21
 *  - removed SimpleCallback
 */
class ItemTouchHelper
/**
 * Creates an ItemTouchHelper that will work with the given Callback.
 *
 *
 * You can attach ItemTouchHelper to a RecyclerView via
 * [.attachToRecyclerView]. Upon attaching, it will add an item decoration,
 * an onItemTouchListener and a Child attach / detach listener to the RecyclerView.
 *
 * @param mCallback The Callback which controls the behavior of this touch helper.
 */(
    /**
     * Developer callback which controls the behavior of ItemTouchHelper.
     */
    var mCallback: Callback
) :
    RecyclerView.ItemDecoration(), OnChildAttachStateChangeListener {
    /**
     * Views, whose state should be cleared after they are detached from RecyclerView.
     * This is necessary after swipe dismissing an item. We wait until animator finishes its job
     * to clean these views.
     */
    val mPendingCleanup: MutableList<View> = ArrayList()

    /**
     * Re-use array to calculate dx dy for a ViewHolder
     */
    private val mTmpPosition = FloatArray(2)

    /**
     * Currently selected view holder
     */
    var mSelected: ViewHolder? = null

    /**
     * The reference coordinates for the action start. For drag & drop, this is the time long
     * press is completed vs for swipe, this is the initial touch point.
     */
    var mInitialTouchX = 0f
    var mInitialTouchY = 0f

    /**
     * Set when ItemTouchHelper is assigned to a RecyclerView.
     */
    private var mSwipeEscapeVelocity = 0f

    /**
     * Set when ItemTouchHelper is assigned to a RecyclerView.
     */
    private var mMaxSwipeVelocity = 0f

    /**
     * The diff between the last event and initial touch.
     */
    var mDx = 0f
    var mDy = 0f

    /**
     * The coordinates of the selected view at the time it is selected. We record these values
     * when action starts so that we can consistently position it even if LayoutManager moves the
     * View.
     */
    private var mSelectedStartX = 0f
    private var mSelectedStartY = 0f

    /**
     * The pointer we are tracking.
     */
    var mActivePointerId: Int =
        ACTIVE_POINTER_ID_NONE

    /**
     * Current mode.
     */
    private var mActionState: Int = ACTION_STATE_IDLE

    /**
     * The direction flags obtained from unmasking
     * [Callback.getAbsoluteMovementFlags] for the current
     * action state.
     */
    var mSelectedFlags = 0

    /**
     * When a View is dragged or swiped and needs to go back to where it was, we create a Recover
     * Animation and animate it to its location using this custom Animator, instead of using
     * framework Animators.
     * Using framework animators has the side effect of clashing with ItemAnimator, creating
     * jumpy UIs.
     */
    var mRecoverAnimations: MutableList<RecoverAnimation> = ArrayList()
    private var mSlop = 0
    var mRecyclerView: RecyclerView? = null

    /**
     * Used for detecting fling swipe
     */
    var mVelocityTracker: VelocityTracker? = null

    /**
     * If drag & drop is supported, we use child drawing order to bring them to front.
     */
    private var mChildDrawingOrderCallback: ChildDrawingOrderCallback? = null

    /**
     * This keeps a reference to the child dragged by the user. Even after user stops dragging,
     * until view reaches its final position (end of recover animation), we keep a reference so
     * that it can be drawn above other children.
     */
    var mOverdrawChild: View? = null

    /**
     * We cache the position of the overdraw child to avoid recalculating it each time child
     * position callback is called. This value is invalidated whenever a child is attached or
     * detached.
     */
    private var mOverdrawChildPosition = -1

    init {
        mCallback.setup(this)
    }

    private val mOnItemTouchListener: OnItemTouchListener = object :
        OnItemTouchListener {
        override fun onInterceptTouchEvent(
            recyclerView: RecyclerView,
            event: MotionEvent
        ): Boolean {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "intercept: x:" + event.x + ",y:" + event.y + ", " + event
                )
            }
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                mActivePointerId = event.getPointerId(0)
                mInitialTouchX = event.x
                mInitialTouchY = event.y
                obtainVelocityTracker()
                if (mSelected == null) {
                    val animation = findAnimation(event)
                    if (animation != null) {
                        if (mCallback.hitTest(recyclerView, animation.mViewHolder, event.x, event.y)) {
                            mInitialTouchX -= animation.mX
                            mInitialTouchY -= animation.mY
                            endRecoverAnimation(animation.mViewHolder, true)
                            if (mPendingCleanup.remove(mCallback.itemView(animation.mViewHolder))) {
                                mCallback.clearView(mRecyclerView!!, animation.mViewHolder)
                            }
                            select(animation.mViewHolder, animation.mActionState)
                            updateDxDy(event, mSelectedFlags, 0)
                        }
                    }
                }
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mActivePointerId = ACTIVE_POINTER_ID_NONE
                select(null, ACTION_STATE_IDLE)
            } else if (mActivePointerId != ACTIVE_POINTER_ID_NONE) {
                // in a non scroll orientation, if distance change is above threshold, we
                // can select the item
                val index = event.findPointerIndex(mActivePointerId)
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "pointer index $index"
                    )
                }
                if (index >= 0) {
                    checkSelectForSwipe(action, event, index)
                }
            }
            if (mVelocityTracker != null) {
                mVelocityTracker!!.addMovement(event)
            }
            return mSelected != null
        }

        override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "on touch: x:$mInitialTouchX,y:$mInitialTouchY, :$event"
                )
            }
            if (mVelocityTracker != null) {
                mVelocityTracker!!.addMovement(event)
            }
            if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
                return
            }
            val action = event.actionMasked
            val activePointerIndex = event.findPointerIndex(mActivePointerId)
            if (activePointerIndex >= 0) {
                checkSelectForSwipe(action, event, activePointerIndex)
            }

            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    // Find the index of the active pointer and fetch its position
                    if (activePointerIndex >= 0) {
                        updateDxDy(event, mSelectedFlags, activePointerIndex)
                        mRecyclerView!!.invalidate()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (mVelocityTracker != null) {
                        mVelocityTracker!!.clear()
                    }
                    select(null, ACTION_STATE_IDLE)
                    mActivePointerId = ACTIVE_POINTER_ID_NONE
                }
                MotionEvent.ACTION_UP -> {
                    select(null, ACTION_STATE_IDLE)
                    mActivePointerId = ACTIVE_POINTER_ID_NONE
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == mActivePointerId) {
                        // This was our active pointer going up. Choose a new
                        // active pointer and adjust accordingly.
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        mActivePointerId = event.getPointerId(newPointerIndex)
                        updateDxDy(event, mSelectedFlags, pointerIndex)
                    }
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (!disallowIntercept) {
                return
            }
            select(null, ACTION_STATE_IDLE)
        }
    }

    /**
     * When user started to drag scroll. Reset when we don't scroll
     */
    private var mDragScrollStartTimeInMs: Long = 0

    /**
     * Attaches the ItemTouchHelper to the provided RecyclerView. If TouchHelper is already
     * attached to a RecyclerView, it will first detach from the previous one. You can call this
     * method with `null` to detach it from the current RecyclerView.
     *
     * @param recyclerView The RecyclerView instance to which you want to add this helper or
     * `null` if you want to remove ItemTouchHelper from the current
     * RecyclerView.
     */
    fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (mRecyclerView === recyclerView) {
            return  // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks()
        }
        mRecyclerView = recyclerView
        if (recyclerView != null) {
            val resources = recyclerView.resources
            mSwipeEscapeVelocity = resources
                .getDimension(R.dimen.item_touch_helper_swipe_escape_velocity)
            mMaxSwipeVelocity = resources
                .getDimension(R.dimen.item_touch_helper_swipe_escape_max_velocity)
            setupCallbacks()
        }
    }

    private fun setupCallbacks() {
        val vc = ViewConfiguration.get(mRecyclerView!!.context)
        mSlop = vc.scaledTouchSlop
        mRecyclerView!!.addItemDecoration(this)
        mRecyclerView!!.addOnItemTouchListener(mOnItemTouchListener)
        mRecyclerView!!.addOnChildAttachStateChangeListener(this)
    }

    private fun destroyCallbacks() {
        mRecyclerView!!.removeItemDecoration(this)
        mRecyclerView!!.removeOnItemTouchListener(mOnItemTouchListener)
        mRecyclerView!!.removeOnChildAttachStateChangeListener(this)
        // clean all attached
        val recoverAnimSize = mRecoverAnimations.size
        for (i in recoverAnimSize - 1 downTo 0) {
            val recoverAnimation = mRecoverAnimations[0]
            mCallback.clearView(mRecyclerView!!, recoverAnimation.mViewHolder)
        }
        mRecoverAnimations.clear()
        mOverdrawChild = null
        mOverdrawChildPosition = -1
        releaseVelocityTracker()
    }

    private fun getSelectedDxDy(outPosition: FloatArray) {
        val itemView = mCallback.itemView(mSelected!!)

        if (mSelectedFlags and (LEFT or RIGHT) != 0) {
            outPosition[0] = mSelectedStartX + mDx - itemView.left
        } else {
            outPosition[0] = itemView.translationX
        }
        if (mSelectedFlags and (UP or DOWN) != 0) {
            outPosition[1] = mSelectedStartY + mDy - itemView.top
        } else {
            outPosition[1] = itemView.translationY
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {
        var dx = 0f
        var dy = 0f
        if (mSelected != null) {
            getSelectedDxDy(mTmpPosition)
            dx = mTmpPosition[0]
            dy = mTmpPosition[1]
        }
        mCallback.onDrawOver(
            c, parent, mSelected,
            mRecoverAnimations, mActionState, dx, dy
        )
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: State) {
        // we don't know if RV changed something so we should invalidate this index.
        mOverdrawChildPosition = -1
        var dx = 0f
        var dy = 0f
        if (mSelected != null) {
            getSelectedDxDy(mTmpPosition)
            dx = mTmpPosition[0]
            dy = mTmpPosition[1]
        }
        mCallback.onDraw(
            c, parent, mSelected,
            mRecoverAnimations, mActionState, dx, dy
        )
    }

    /**
     * Starts dragging or swiping the given View. Call with null if you want to clear it.
     *
     * @param selected    The ViewHolder to drag or swipe. Can be null if you want to cancel the
     * current action, but may not be null if actionState is ACTION_STATE_DRAG.
     * @param actionState The type of action
     */
    fun select(selected: ViewHolder?, actionState: Int) {
        if (selected === mSelected && actionState == mActionState) {
            return
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE
        val prevActionState = mActionState
        // prevent duplicate animations
        endRecoverAnimation(selected, true)
        mActionState = actionState
        if (actionState == ACTION_STATE_DRAG) {
            requireNotNull(selected) { "Must pass a ViewHolder when dragging" }

            // we remove after animation is complete. this means we only elevate the last drag
            // child but that should perform good enough as it is very hard to start dragging a
            // new child before the previous one settles.
            mOverdrawChild = selected.itemView
        }
        val actionStateMask =
            ((1 shl DIRECTION_FLAG_COUNT + DIRECTION_FLAG_COUNT * actionState)
                    - 1)
        var preventLayout = false
        if (mSelected != null) {
            val prevSelected: ViewHolder = mSelected as ViewHolder
            if (prevSelected.itemView.parent != null) {
                val swipeDir =
                    if (prevActionState == ACTION_STATE_DRAG) 0 else swipeIfNecessary(
                        prevSelected
                    )
                releaseVelocityTracker()
                // find where we should animate to
                val targetTranslateX: Float
                val targetTranslateY: Float
                when (swipeDir) {
                    LEFT, RIGHT, START, END -> {
                        targetTranslateY = 0f
                        targetTranslateX = sign(mDx) * mRecyclerView!!.width
                    }
                    UP, DOWN -> {
                        targetTranslateX = 0f
                        targetTranslateY = sign(mDy) * mRecyclerView!!.height
                    }
                    else -> {
                        targetTranslateX = 0f
                        targetTranslateY = 0f
                    }
                }
                val animationType =
                    if (prevActionState == ACTION_STATE_DRAG) {
                        ANIMATION_TYPE_DRAG
                    } else if (swipeDir > 0) {
                        ANIMATION_TYPE_SWIPE_SUCCESS
                    } else {
                        ANIMATION_TYPE_SWIPE_CANCEL
                    }
                getSelectedDxDy(mTmpPosition)
                val currentTranslateX = mTmpPosition[0]
                val currentTranslateY = mTmpPosition[1]
                startRecoverAnimation(
                    prevSelected,
                    prevActionState, currentTranslateX, currentTranslateY,
                    targetTranslateX, targetTranslateY, swipeDir, animationType
                )
                preventLayout = true
            } else {
                removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView)
                mCallback.clearView(mRecyclerView!!, prevSelected)
            }
            mSelected = null
        }
        if (selected != null) {
            mSelectedFlags =
                (mCallback.getAbsoluteMovementFlags(mRecyclerView!!, selected) and actionStateMask
                        shr mActionState * DIRECTION_FLAG_COUNT)
            mSelectedStartX = selected.itemView.left.toFloat()
            mSelectedStartY = selected.itemView.top.toFloat()
            mSelected = selected
            if (actionState == ACTION_STATE_DRAG) {
                mCallback.itemView(mSelected!!).performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        val rvParent = mRecyclerView!!.parent
        rvParent?.requestDisallowInterceptTouchEvent(mSelected != null)
        if (!preventLayout) {
            mRecyclerView!!.layoutManager!!.requestSimpleAnimationsInNextLayout()
        }
        mCallback.onSelectedChanged(mSelected, mActionState)
        mRecyclerView!!.invalidate()
    }

    fun startRecoverAnimation(
        viewHolder: ViewHolder,
        actionState: Int,
        currentTranslateX: Float,
        currentTranslateY: Float,
        targetTranslateX: Float,
        targetTranslateY: Float,
        swipeDir: Int,
        animationType: Int
    ) {
        val rv = object : RecoverAnimation(
            mCallback, viewHolder,
            actionState, currentTranslateX, currentTranslateY,
            targetTranslateX, targetTranslateY
        ) {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                if (mOverridden) {
                    return
                }
                if (swipeDir <= 0) {
                    // this is a drag or failed swipe. recover immediately
                    mCallback.clearView(mRecyclerView!!, viewHolder)
                    // full cleanup will happen on onDrawOver
                } else {
                    // wait until remove animation is complete.
                    mPendingCleanup.add(viewHolder.itemView)
                    mIsPendingCleanup = true
                    if (swipeDir > 0) {
                        // Animation might be ended by other animators during a layout.
                        // We defer callback to avoid editing adapter during a layout.
                        postDispatchSwipe(this, swipeDir)
                    }
                }
                // removed from the list after it is drawn for the last time
                if (mOverdrawChild === viewHolder.itemView) {
                    removeChildDrawingOrderCallbackIfNecessary(viewHolder.itemView)
                }
            }
        }
        val duration = mCallback.getAnimationDuration(
            mRecyclerView!!, animationType,
            targetTranslateX - currentTranslateX, targetTranslateY - currentTranslateY
        )
        rv.setDuration(duration)
        mRecoverAnimations.add(rv)
        rv.start()
    }

    fun postDispatchSwipe(anim: RecoverAnimation, swipeDir: Int) {
        // wait until animations are complete.
        mRecyclerView!!.post(object : Runnable {
            override fun run() {
                if (mRecyclerView != null && mRecyclerView!!.isAttachedToWindow
                    && !anim.mOverridden
                    && anim.mViewHolder.adapterPosition != NO_POSITION
                ) {
                    val animator = mRecyclerView!!.itemAnimator
                    // if animator is running or we have other active recover animations, we try
                    // not to call onSwiped because DefaultItemAnimator is not good at merging
                    // animations. Instead, we wait and batch.
                    if ((animator == null || !animator.isRunning(null))
                        && !hasRunningRecoverAnim()
                    ) {
                        mCallback.onSwiped(anim.mViewHolder, swipeDir)
                    } else {
                        mRecyclerView!!.post(this)
                    }
                }
            }
        })
    }

    fun hasRunningRecoverAnim(): Boolean {
        val size = mRecoverAnimations.size
        for (i in 0 until size) {
            if (!mRecoverAnimations[i].mEnded) {
                return true
            }
        }
        return false
    }

    override fun onChildViewAttachedToWindow(view: View) {}
    override fun onChildViewDetachedFromWindow(view: View) {
        removeChildDrawingOrderCallbackIfNecessary(view)
        val holder = mRecyclerView!!.getChildViewHolder(view) ?: return
        if (mSelected != null && holder === mSelected) {
            select(null, ACTION_STATE_IDLE)
        } else {
            endRecoverAnimation(holder, false) // this may push it into pending cleanup list.
            if (mPendingCleanup.remove(holder.itemView)) {
                mCallback.clearView(mRecyclerView!!, holder)
            }
        }
    }

    /**
     * Returns the animation type or 0 if cannot be found.
     */
    fun endRecoverAnimation(viewHolder: ViewHolder?, override: Boolean) {
        val recoverAnimSize = mRecoverAnimations.size
        for (i in recoverAnimSize - 1 downTo 0) {
            val anim = mRecoverAnimations[i]
            if (anim.mViewHolder === viewHolder) {
                anim.mOverridden = anim.mOverridden or override
                if (!anim.mEnded) {
                    anim.cancel()
                }
                mRecoverAnimations.removeAt(i)
                return
            }
        }
    }

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView,
        state: State
    ) {
        outRect.setEmpty()
    }

    @SuppressLint("Recycle")
    fun obtainVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
        }
        mVelocityTracker = VelocityTracker.obtain()
    }

    private fun releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    private fun findSwipedView(motionEvent: MotionEvent): ViewHolder? {
        val lm = mRecyclerView!!.layoutManager
        if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
            return null
        }
        val pointerIndex = motionEvent.findPointerIndex(mActivePointerId)
        val dx = motionEvent.getX(pointerIndex) - mInitialTouchX
        val dy = motionEvent.getY(pointerIndex) - mInitialTouchY
        val absDx = abs(dx)
        val absDy = abs(dy)
        if (absDx < mSlop && absDy < mSlop) {
            return null
        }
        if (absDx > absDy && lm!!.canScrollHorizontally()) {
            return null
        } else if (absDy > absDx && lm!!.canScrollVertically()) {
            return null
        }
        val child = findChildView(motionEvent) ?: return null
        return mRecyclerView!!.getChildViewHolder(child)
    }

    /**
     * Checks whether we should select a View for swiping.
     */
    fun checkSelectForSwipe(
        action: Int,
        motionEvent: MotionEvent,
        pointerIndex: Int
    ) {
        if (mSelected != null || action != MotionEvent.ACTION_MOVE || mActionState == ACTION_STATE_DRAG || !mCallback.isItemViewSwipeEnabled()
        ) {
            return
        }
        if (mRecyclerView!!.scrollState == SCROLL_STATE_DRAGGING) {
            return
        }
        val vh = findSwipedView(motionEvent) ?: return
        val movementFlags = mCallback.getAbsoluteMovementFlags(mRecyclerView!!, vh)
        val swipeFlags = (movementFlags and ACTION_MODE_SWIPE_MASK
                shr DIRECTION_FLAG_COUNT * ACTION_STATE_SWIPE)
        if (swipeFlags == 0) {
            return
        }

        // mDx and mDy are only set in allowed directions. We use custom x/y here instead of
        // updateDxDy to avoid swiping if user moves more in the other direction
        val x = motionEvent.getX(pointerIndex)
        val y = motionEvent.getY(pointerIndex)

        // Calculate the distance moved
        val dx = x - mInitialTouchX
        val dy = y - mInitialTouchY
        // swipe target is chose w/o applying flags so it does not really check if swiping in that
        // direction is allowed. This why here, we use mDx mDy to check slope value again.
        val absDx = abs(dx)
        val absDy = abs(dy)
        if (absDx < mSlop && absDy < mSlop) {
            return
        }
        if (absDx > absDy) {
            if (dx < 0 && swipeFlags and LEFT == 0) {
                return
            }
            if (dx > 0 && swipeFlags and RIGHT == 0) {
                return
            }
        } else {
            if (dy < 0 && swipeFlags and UP == 0) {
                return
            }
            if (dy > 0 && swipeFlags and DOWN == 0) {
                return
            }
        }
        mDy = 0f
        mDx = mDy
        mActivePointerId = motionEvent.getPointerId(0)
        select(vh, ACTION_STATE_SWIPE)
    }

    fun findChildView(event: MotionEvent): View? {
        // first check elevated views, if none, then call RV
        val x = event.x
        val y = event.y
        if (mSelected != null) {
            val selectedView = mCallback.itemView(mSelected!!)
            if (hitTest(
                    selectedView,
                    x,
                    y,
                    mSelectedStartX + mDx,
                    mSelectedStartY + mDy
                )
            ) {
                return selectedView
            }
        }
        for (i in mRecoverAnimations.indices.reversed()) {
            val anim = mRecoverAnimations[i]
            val view = mCallback.itemView(anim.mViewHolder)
            if (hitTest(view, x, y, anim.mX, anim.mY)) {
                return view
            }
        }
        return mRecyclerView!!.findChildViewUnder(x, y)
    }

    fun findAnimation(event: MotionEvent): RecoverAnimation? {
        if (mRecoverAnimations.isEmpty()) {
            return null
        }
        val target = findChildView(event)
        return findAnimationForView(target)
    }

    fun findAnimationForView(view: View?): RecoverAnimation? {
        for (i in mRecoverAnimations.indices.reversed()) {
            val anim = mRecoverAnimations[i]
            if (mCallback.itemView(anim.mViewHolder) === view) {
                return anim
            }
        }
        return null
    }

    fun updateDxDy(ev: MotionEvent, directionFlags: Int, pointerIndex: Int) {
        val x = ev.getX(pointerIndex)
        val y = ev.getY(pointerIndex)

        // Calculate the distance moved
        mDx = x - mInitialTouchX
        mDy = y - mInitialTouchY
        if (directionFlags and LEFT == 0) {
            mDx = 0f.coerceAtLeast(mDx)
        }
        if (directionFlags and RIGHT == 0) {
            mDx = 0f.coerceAtMost(mDx)
        }
        if (directionFlags and UP == 0) {
            mDy = 0f.coerceAtLeast(mDy)
        }
        if (directionFlags and DOWN == 0) {
            mDy = 0f.coerceAtMost(mDy)
        }
    }

    private fun swipeIfNecessary(viewHolder: ViewHolder): Int {
        if (mActionState == ACTION_STATE_DRAG) {
            return 0
        }
        val originalMovementFlags = mCallback.getMovementFlags(mRecyclerView!!, viewHolder)
        val absoluteMovementFlags = mCallback.convertToAbsoluteDirection(
            originalMovementFlags,
            ViewCompat.getLayoutDirection(mRecyclerView!!)
        )
        val flags = (absoluteMovementFlags
                and ACTION_MODE_SWIPE_MASK) shr ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT
        if (flags == 0) {
            return 0
        }
        val originalFlags = (originalMovementFlags
                and ACTION_MODE_SWIPE_MASK) shr ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT
        var swipeDir: Int
        if (abs(mDx) > abs(mDy)) {
            if (checkHorizontalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
                // if swipe dir is not in original flags, it should be the relative direction
                return if (originalFlags and swipeDir == 0) {
                    // convert to relative
                    Callback.convertToRelativeDirection(
                        swipeDir,
                        ViewCompat.getLayoutDirection(mRecyclerView!!)
                    )
                } else swipeDir
            }
            if (checkVerticalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
                return swipeDir
            }
        } else {
            if (checkVerticalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
                return swipeDir
            }
            if (checkHorizontalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
                // if swipe dir is not in original flags, it should be the relative direction
                return if (originalFlags and swipeDir == 0) {
                    // convert to relative
                    Callback.convertToRelativeDirection(
                        swipeDir,
                        ViewCompat.getLayoutDirection(mRecyclerView!!)
                    )
                } else swipeDir
            }
        }
        return 0
    }

    private fun checkHorizontalSwipe(viewHolder: ViewHolder, flags: Int): Int {
        if (flags and (LEFT or RIGHT) != 0) {
            val dirFlag: Int =
                if (mDx > 0) RIGHT else LEFT
            if (mVelocityTracker != null && mActivePointerId > -1) {
                mVelocityTracker!!.computeCurrentVelocity(
                    PIXELS_PER_SECOND,
                    mCallback.getSwipeVelocityThreshold(mMaxSwipeVelocity)
                )
                val xVelocity = mVelocityTracker!!.getXVelocity(mActivePointerId)
                val yVelocity = mVelocityTracker!!.getYVelocity(mActivePointerId)
                val velDirFlag: Int = if (xVelocity > 0f) RIGHT else LEFT
                val absXVelocity = abs(xVelocity)
                if (velDirFlag and flags != 0 && dirFlag == velDirFlag && absXVelocity >= mCallback.getSwipeEscapeVelocity(
                        mSwipeEscapeVelocity
                    ) && absXVelocity > abs(yVelocity)
                ) {
                    return velDirFlag
                }
            }
            val threshold = mRecyclerView!!.width * mCallback.getSwipeThreshold(viewHolder)
            if (flags and dirFlag != 0 && abs(mDx) > threshold) {
                return dirFlag
            }
        }
        return 0
    }

    private fun checkVerticalSwipe(viewHolder: ViewHolder, flags: Int): Int {
        if (flags and (UP or DOWN) != 0) {
            val dirFlag: Int =
                if (mDy > 0) DOWN else UP
            if (mVelocityTracker != null && mActivePointerId > -1) {
                mVelocityTracker!!.computeCurrentVelocity(
                    PIXELS_PER_SECOND,
                    mCallback.getSwipeVelocityThreshold(mMaxSwipeVelocity)
                )
                val xVelocity = mVelocityTracker!!.getXVelocity(mActivePointerId)
                val yVelocity = mVelocityTracker!!.getYVelocity(mActivePointerId)
                val velDirFlag: Int = if (yVelocity > 0f) DOWN else UP
                val absYVelocity = abs(yVelocity)
                if (velDirFlag and flags != 0 && velDirFlag == dirFlag && absYVelocity >= mCallback.getSwipeEscapeVelocity(
                        mSwipeEscapeVelocity
                    ) && absYVelocity > abs(xVelocity)
                ) {
                    return velDirFlag
                }
            }
            val threshold = mRecyclerView!!.height * mCallback
                .getSwipeThreshold(viewHolder)
            if (flags and dirFlag != 0 && abs(mDy) > threshold) {
                return dirFlag
            }
        }
        return 0
    }

    fun removeChildDrawingOrderCallbackIfNecessary(view: View) {
        if (view === mOverdrawChild) {
            mOverdrawChild = null
            // only remove if we've added
            if (mChildDrawingOrderCallback != null) {
                mRecyclerView!!.setChildDrawingOrderCallback(null)
            }
        }
    }

    /**
     * This class is the contract between ItemTouchHelper and your application. It lets you control
     * which touch behaviors are enabled per each ViewHolder and also receive callbacks when user
     * performs these actions.
     *
     *
     * To control which actions user can take on each view, you should override
     * [.getMovementFlags] and return appropriate set
     * of direction flags. ([.LEFT], [.RIGHT], [.START], [.END],
     * [.UP], [.DOWN]). You can use
     * [.makeMovementFlags] to easily construct it.
     *
     *
     * Upon receiving this callback, you should move the item from the old position
     * (`dragged.getAdapterPosition()`) to new position (`target.getAdapterPosition()`)
     * in your adapter and also call [RecyclerView.Adapter.notifyItemMoved].
     * To control where a View can be dropped, you can override
     * [.canDropOver]. When a
     * dragging View overlaps multiple other views, Callback chooses the closest View with which
     * dragged View might have changed positions. Although this approach works for many use cases,
     * if you have a custom LayoutManager, you can override
     * [.chooseDropTarget] to select a
     * custom drop target.
     *
     *
     * When a View is swiped, ItemTouchHelper animates it until it goes out of bounds, then calls
     * [.onSwiped]. At this point, you should update your
     * adapter (e.g. remove the item) and call related Adapter#notify event.
     */
    abstract class Callback {
        private lateinit var mOwner: ItemTouchHelper

        protected fun startRecoveryAnimation(viewHolder: ViewHolder, initial: Float) {
            val running = mOwner.findAnimationForView(viewHolder.itemView)

            if (running != null) {
                running.mOverridden = true
            }

            mOwner.startRecoverAnimation(
                viewHolder,
                ACTION_STATE_SWIPE,
                initial,
                0f,
                0f,
                0f,
                LEFT,
                ANIMATION_TYPE_SWIPE_CANCEL
            )
        }

        abstract fun hitTest(parent: View, child: ViewHolder, x: Float, y: Float): Boolean

        open fun itemView(child: ViewHolder): View {
            return child.itemView
        }

        /**
         * Should return a composite flag which defines the enabled move directions in each state
         * (idle, swiping, dragging).
         *
         *
         * Instead of composing this flag manually, you can use [.makeMovementFlags]
         * or [.makeFlag].
         *
         *
         * This flag is composed of 3 sets of 8 bits, where first 8 bits are for IDLE state, next
         * 8 bits are for SWIPE state and third 8 bits are for DRAG state.
         * Each 8 bit sections can be constructed by simply OR'ing direction flags defined in
         * [ItemTouchHelper].
         *
         *
         * For example, if you want it to allow swiping LEFT and RIGHT but only allow starting to
         * swipe by swiping RIGHT, you can return:
         * <pre>
         * makeFlag(ACTION_STATE_IDLE, RIGHT) | makeFlag(ACTION_STATE_SWIPE, LEFT | RIGHT);
        </pre> *
         * This means, allow right movement while IDLE and allow right and left movement while
         * swiping.
         *
         * @param recyclerView The RecyclerView to which ItemTouchHelper is attached.
         * @param viewHolder   The ViewHolder for which the movement information is necessary.
         * @return flags specifying which movements are allowed on this ViewHolder.
         * @see .makeMovementFlags
         * @see .makeFlag
         */
        abstract fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder
        ): Int

        /**
         * Converts a given set of flags to absolution direction which means [.START] and
         * [.END] are replaced with [.LEFT] and [.RIGHT] depending on the layout
         * direction.
         *
         * @param flags           The flag value that include any number of movement flags.
         * @param layoutDirection The layout direction of the RecyclerView.
         * @return Updated flags which includes only absolute direction values.
         */
        open fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
            var flags = flags
            val masked = flags and RELATIVE_DIR_FLAGS
            if (masked == 0) {
                return flags // does not have any relative flags, good.
            }
            flags = flags and masked.inv() //remove start / end

            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
                // no change. just OR with 2 bits shifted mask and return
                flags =
                    flags or (masked shr 2) // START is 2 bits after LEFT, END is 2 bits after RIGHT.
            } else {
                // add START flag as RIGHT
                flags = flags or (masked shr 1 and RELATIVE_DIR_FLAGS.inv())
                // first clean start bit then add END flag as LEFT
                flags = flags or (masked shr 1 and RELATIVE_DIR_FLAGS shr 2)
            }

            return flags
        }

        fun getAbsoluteMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder
        ): Int {
            val flags = getMovementFlags(recyclerView, viewHolder)
            return convertToAbsoluteDirection(flags, ViewCompat.getLayoutDirection(recyclerView))
        }

        /**
         * Returns whether ItemTouchHelper should start a swipe operation if a pointer is swiped
         * over the View.
         *
         *
         * Default value returns true but you may want to disable this if you want to start
         * swiping on a custom view touch using [.startSwipe].
         *
         * @return True if ItemTouchHelper should start swiping an item when user swipes a pointer
         * over the View, false otherwise. Default value is `true`.
         * @see .startSwipe
         */
        fun isItemViewSwipeEnabled(): Boolean {
            return true
        }

        /**
         * Returns the fraction that the user should move the View to be considered as swiped.
         * The fraction is calculated with respect to RecyclerView's bounds.
         *
         *
         * Default value is .5f, which means, to swipe a View, user must move the View at least
         * half of RecyclerView's width or height, depending on the swipe direction.
         *
         * @param viewHolder The ViewHolder that is being dragged.
         * @return A float value that denotes the fraction of the View size. Default value
         * is .5f .
         */
        fun getSwipeThreshold(viewHolder: ViewHolder): Float {
            return .5f
        }

        /**
         * Defines the minimum velocity which will be considered as a swipe action by the user.
         *
         *
         * You can increase this value to make it harder to swipe or decrease it to make it easier.
         * Keep in mind that ItemTouchHelper also checks the perpendicular velocity and makes sure
         * current direction velocity is larger then the perpendicular one. Otherwise, user's
         * movement is ambiguous. You can change the threshold by overriding
         * [.getSwipeVelocityThreshold].
         *
         *
         * The velocity is calculated in pixels per second.
         *
         *
         * The default framework value is passed as a parameter so that you can modify it with a
         * multiplier.
         *
         * @param defaultValue The default value (in pixels per second) used by the
         * ItemTouchHelper.
         * @return The minimum swipe velocity. The default implementation returns the
         * `defaultValue` parameter.
         * @see .getSwipeVelocityThreshold
         * @see .getSwipeThreshold
         */
        fun getSwipeEscapeVelocity(defaultValue: Float): Float {
            return defaultValue
        }

        /**
         * Defines the maximum velocity ItemTouchHelper will ever calculate for pointer movements.
         *
         *
         * To consider a movement as swipe, ItemTouchHelper requires it to be larger than the
         * perpendicular movement. If both directions reach to the max threshold, none of them will
         * be considered as a swipe because it is usually an indication that user rather tried to
         * scroll then swipe.
         *
         *
         * The velocity is calculated in pixels per second.
         *
         *
         * You can customize this behavior by changing this method. If you increase the value, it
         * will be easier for the user to swipe diagonally and if you decrease the value, user will
         * need to make a rather straight finger movement to trigger a swipe.
         *
         * @param defaultValue The default value(in pixels per second) used by the ItemTouchHelper.
         * @return The velocity cap for pointer movements. The default implementation returns the
         * `defaultValue` parameter.
         * @see .getSwipeEscapeVelocity
         */
        fun getSwipeVelocityThreshold(defaultValue: Float): Float {
            return defaultValue
        }

        /**
         * Called when a ViewHolder is swiped by the user.
         *
         *
         * If you are returning relative directions ([.START] , [.END]) from the
         * [.getMovementFlags] method, this method
         * will also use relative directions. Otherwise, it will use absolute directions.
         *
         *
         * If you don't support swiping, this method will never be called.
         *
         *
         * ItemTouchHelper will keep a reference to the View until it is detached from
         * RecyclerView.
         * As soon as it is detached, ItemTouchHelper will call
         * [.clearView].
         *
         * @param viewHolder The ViewHolder which has been swiped by the user.
         * @param direction  The direction to which the ViewHolder is swiped. It is one of
         * [.UP], [.DOWN],
         * [.LEFT] or [.RIGHT]. If your
         * [.getMovementFlags]
         * method
         * returned relative flags instead of [.LEFT] / [.RIGHT];
         * `direction` will be relative as well. ([.START] or [                   ][.END]).
         */
        abstract fun onSwiped(viewHolder: ViewHolder, direction: Int)

        /**
         * Called when the ViewHolder swiped by the ItemTouchHelper is changed.
         *
         *
         * If you override this method, you should call super.
         *
         * @param viewHolder  The new ViewHolder that is being swiped or dragged. Might be null if
         * it is cleared.
         * @param actionState One of [ItemTouchHelper.ACTION_STATE_IDLE],
         * [ItemTouchHelper.ACTION_STATE_SWIPE] or
         * [ItemTouchHelper.ACTION_STATE_DRAG].
         * @see .clearView
         */
        fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {

        }

        fun onDraw(
            c: Canvas, parent: RecyclerView, selected: ViewHolder?,
            recoverAnimationList: List<RecoverAnimation>,
            actionState: Int, dX: Float, dY: Float
        ) {
            val recoverAnimSize = recoverAnimationList.size
            for (i in 0 until recoverAnimSize) {
                val anim = recoverAnimationList[i]
                anim.update()
                val count = c.save()
                onChildDraw(
                    c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
                    false
                )
                c.restoreToCount(count)
            }
            if (selected != null) {
                val count = c.save()
                onChildDraw(c, parent, selected, dX, dY, actionState, true)
                c.restoreToCount(count)
            }
        }

        fun onDrawOver(
            c: Canvas, parent: RecyclerView, selected: ViewHolder?,
            recoverAnimationList: MutableList<RecoverAnimation>,
            actionState: Int, dX: Float, dY: Float
        ) {
            val recoverAnimSize = recoverAnimationList.size
            for (i in 0 until recoverAnimSize) {
                val anim = recoverAnimationList[i]
                val count = c.save()
                onChildDrawOver(
                    c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
                    false
                )
                c.restoreToCount(count)
            }
            if (selected != null) {
                val count = c.save()
                onChildDrawOver(c, parent, selected, dX, dY, actionState, true)
                c.restoreToCount(count)
            }
            var hasRunningAnimation = false
            for (i in recoverAnimSize - 1 downTo 0) {
                val anim = recoverAnimationList[i]
                if (anim.mEnded && !anim.mIsPendingCleanup) {
                    recoverAnimationList.removeAt(i)
                } else if (!anim.mEnded) {
                    hasRunningAnimation = true
                }
            }
            if (hasRunningAnimation) {
                parent.invalidate()
            }
        }

        /**
         * Called by the ItemTouchHelper when the user interaction with an element is over and it
         * also completed its animation.
         *
         *
         * This is a good place to clear all changes on the View that was done in
         * [.onSelectedChanged],
         * [.onChildDraw] or
         * [.onChildDrawOver].
         *
         * @param recyclerView The RecyclerView which is controlled by the ItemTouchHelper.
         * @param viewHolder   The View that was interacted by the user.
         */
        fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {

        }

        /**
         * Called by ItemTouchHelper on RecyclerView's onDraw callback.
         *
         *
         * If you would like to customize how your View's respond to user interactions, this is
         * a good place to override.
         *
         *
         * Default implementation translates the child by the given `dX`,
         * `dY`.
         * ItemTouchHelper also takes care of drawing the child after other children if it is being
         * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
         * is
         * achieved via [android.view.ViewGroup.getChildDrawingOrder] and on L
         * and after, it changes View's elevation value to be greater than all other children.)
         *
         * @param c                 The canvas which RecyclerView is drawing its children
         * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
         * @param viewHolder        The ViewHolder which is being interacted by the User or it was
         * interacted and simply animating to its original position
         * @param dX                The amount of horizontal displacement caused by user's action
         * @param dY                The amount of vertical displacement caused by user's action
         * @param actionState       The type of interaction on the View. Is either [                          ][.ACTION_STATE_DRAG] or [.ACTION_STATE_SWIPE].
         * @param isCurrentlyActive True if this view is currently being controlled by the user or
         * false it is simply animating back to its original state.
         * @see .onChildDrawOver
         */
        open fun onChildDraw(
            c: Canvas, recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
        ) {

        }

        /**
         * Called by ItemTouchHelper on RecyclerView's onDraw callback.
         *
         *
         * If you would like to customize how your View's respond to user interactions, this is
         * a good place to override.
         *
         *
         * Default implementation translates the child by the given `dX`,
         * `dY`.
         * ItemTouchHelper also takes care of drawing the child after other children if it is being
         * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
         * is
         * achieved via [android.view.ViewGroup.getChildDrawingOrder] and on L
         * and after, it changes View's elevation value to be greater than all other children.)
         *
         * @param c                 The canvas which RecyclerView is drawing its children
         * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
         * @param viewHolder        The ViewHolder which is being interacted by the User or it was
         * interacted and simply animating to its original position
         * @param dX                The amount of horizontal displacement caused by user's action
         * @param dY                The amount of vertical displacement caused by user's action
         * @param actionState       The type of interaction on the View. Is either [                          ][.ACTION_STATE_DRAG] or [.ACTION_STATE_SWIPE].
         * @param isCurrentlyActive True if this view is currently being controlled by the user or
         * false it is simply animating back to its original state.
         * @see .onChildDrawOver
         */
        open fun onChildDrawOver(
            c: Canvas, recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
        ) {

        }

        /**
         * Called by the ItemTouchHelper when user action finished on a ViewHolder and now the View
         * will be animated to its final position.
         *
         *
         * Default implementation uses ItemAnimator's duration values. If
         * `animationType` is [.ANIMATION_TYPE_DRAG], it returns
         * [RecyclerView.ItemAnimator.getMoveDuration], otherwise, it returns
         * [RecyclerView.ItemAnimator.getRemoveDuration]. If RecyclerView does not have
         * any [RecyclerView.ItemAnimator] attached, this method returns
         * `DEFAULT_DRAG_ANIMATION_DURATION` or `DEFAULT_SWIPE_ANIMATION_DURATION`
         * depending on the animation type.
         *
         * @param recyclerView  The RecyclerView to which the ItemTouchHelper is attached to.
         * @param animationType The type of animation. Is one of [.ANIMATION_TYPE_DRAG],
         * [.ANIMATION_TYPE_SWIPE_CANCEL] or
         * [.ANIMATION_TYPE_SWIPE_SUCCESS].
         * @param animateDx     The horizontal distance that the animation will offset
         * @param animateDy     The vertical distance that the animation will offset
         * @return The duration for the animation
         */
        fun getAnimationDuration(
            recyclerView: RecyclerView, animationType: Int,
            animateDx: Float, animateDy: Float
        ): Long {
            val itemAnimator = recyclerView.itemAnimator
            return if (itemAnimator == null) {
                if (animationType == ANIMATION_TYPE_DRAG) DEFAULT_DRAG_ANIMATION_DURATION else DEFAULT_SWIPE_ANIMATION_DURATION
            } else {
                if (animationType == ANIMATION_TYPE_DRAG) itemAnimator.moveDuration else itemAnimator.removeDuration
            }
        }

        fun setup(owner: ItemTouchHelper) {
            mOwner = owner
        }

        companion object {
            const val DEFAULT_DRAG_ANIMATION_DURATION: Long = 200
            const val DEFAULT_SWIPE_ANIMATION_DURATION: Long = 250
            const val RELATIVE_DIR_FLAGS: Int =
                (START or END
                        or (START or END shl DIRECTION_FLAG_COUNT)
                        or (START or END shl 2 * DIRECTION_FLAG_COUNT))
            const val ABS_HORIZONTAL_DIR_FLAGS: Int =
                (LEFT or RIGHT
                        or (LEFT or RIGHT shl DIRECTION_FLAG_COUNT)
                        or (LEFT or RIGHT shl 2 * DIRECTION_FLAG_COUNT))

            /**
             * Replaces a movement direction with its relative version by taking layout direction into
             * account.
             *
             * @param flags           The flag value that include any number of movement flags.
             * @param layoutDirection The layout direction of the View. Can be obtained from
             * [ViewCompat.getLayoutDirection].
             * @return Updated flags which uses relative flags ([.START], [.END]) instead
             * of [.LEFT], [.RIGHT].
             * @see .convertToAbsoluteDirection
             */
            fun convertToRelativeDirection(flags: Int, layoutDirection: Int): Int {
                var flags = flags
                val masked = flags and ABS_HORIZONTAL_DIR_FLAGS
                if (masked == 0) {
                    return flags // does not have any abs flags, good.
                }
                flags = flags and masked.inv() //remove left / right.
                if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
                    // no change. just OR with 2 bits shifted mask and return
                    flags =
                        flags or (masked shl 2) // START is 2 bits after LEFT, END is 2 bits after RIGHT.
                    return flags
                } else {
                    // add RIGHT flag as START
                    flags =
                        flags or (masked shl 1 and ABS_HORIZONTAL_DIR_FLAGS.inv())
                    // first clean RIGHT bit then add LEFT flag as END
                    flags =
                        flags or (masked shl 1 and ABS_HORIZONTAL_DIR_FLAGS shl 2)
                }
                return flags
            }

            /**
             * Convenience method to create movement flags.
             *
             *
             * For instance, if you want to let your items be drag & dropped vertically and swiped
             * left to be dismissed, you can call this method with:
             * `makeMovementFlags(UP | DOWN, LEFT);`
             *
             * @param swipeFlags The directions in which the item can be swiped.
             * @return Returns an integer composed of the given drag and swipe flags.
             */
            fun makeMovementFlags(swipeFlags: Int): Int {
                return makeFlag(ACTION_STATE_IDLE, swipeFlags) or makeFlag(
                    ACTION_STATE_SWIPE,
                    swipeFlags
                )
            }

            /**
             * Shifts the given direction flags to the offset of the given action state.
             *
             * @param actionState The action state you want to get flags in. Should be one of
             * [.ACTION_STATE_IDLE], [.ACTION_STATE_SWIPE] or
             * [.ACTION_STATE_DRAG].
             * @param directions  The direction flags. Can be composed from [.UP], [.DOWN],
             * [.RIGHT], [.LEFT] [.START] and [.END].
             * @return And integer that represents the given directions in the provided actionState.
             */
            fun makeFlag(actionState: Int, directions: Int): Int {
                return directions shl actionState * DIRECTION_FLAG_COUNT
            }
        }
    }

    open class RecoverAnimation(
        val mCallback: Callback,
        val mViewHolder: ViewHolder,
        val mActionState: Int,
        val mStartDx: Float,
        val mStartDy: Float,
        val mTargetX: Float,
        val mTargetY: Float
    ) :
        Animator.AnimatorListener {
        private val mValueAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f)
        var mIsPendingCleanup = false
        var mX = 0f
        var mY = 0f

        // if user starts touching a recovering view, we put it into interaction mode again,
        // instantly.
        var mOverridden = false
        var mEnded = false
        private var mFraction = 0f

        init {
            mValueAnimator.addUpdateListener { animation -> setFraction(animation.animatedFraction) }
            mValueAnimator.setTarget(mCallback.itemView(mViewHolder))
            mValueAnimator.addListener(this)
            setFraction(0f)
        }

        fun setDuration(duration: Long) {
            mValueAnimator.duration = duration
        }

        fun start() {
            mViewHolder.setIsRecyclable(false)
            mValueAnimator.start()
        }

        fun cancel() {
            mValueAnimator.cancel()
        }

        fun setFraction(fraction: Float) {
            mFraction = fraction
        }

        /**
         * We run updates on onDraw method but use the fraction from animator callback.
         * This way, we can sync translate x/y values w/ the animators to avoid one-off frames.
         */
        fun update() {
            mX = if (mStartDx == mTargetX) {
                mCallback.itemView(mViewHolder).translationX
            } else {
                mStartDx + mFraction * (mTargetX - mStartDx)
            }
            mY = if (mStartDy == mTargetY) {
                mCallback.itemView(mViewHolder).translationY
            } else {
                mStartDy + mFraction * (mTargetY - mStartDy)
            }
        }

        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            if (!mEnded) {
                mViewHolder.setIsRecyclable(true)
            }
            mEnded = true
        }

        override fun onAnimationCancel(animation: Animator) {
            setFraction(1f) //make sure we recover the view's state.
        }

        override fun onAnimationRepeat(animation: Animator) {}
    }

    companion object {
        /**
         * Up direction, used for swipe & drag control.
         */
        const val UP = 1

        /**
         * Down direction, used for swipe & drag control.
         */
        const val DOWN = 1 shl 1

        /**
         * Left direction, used for swipe & drag control.
         */
        const val LEFT = 1 shl 2

        /**
         * Right direction, used for swipe & drag control.
         */
        const val RIGHT = 1 shl 3
        // If you change these relative direction values, update Callback#convertToAbsoluteDirection,
        // Callback#convertToRelativeDirection.
        /**
         * Horizontal start direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
         * direction. Used for swipe & drag control.
         */
        const val START: Int = LEFT shl 2

        /**
         * Horizontal end direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
         * direction. Used for swipe & drag control.
         */
        const val END: Int = RIGHT shl 2

        /**
         * ItemTouchHelper is in idle state. At this state, either there is no related motion event by
         * the user or latest motion events have not yet triggered a swipe or drag.
         */
        const val ACTION_STATE_IDLE = 0

        /**
         * A View is currently being swiped.
         */
        const val ACTION_STATE_SWIPE = 1

        /**
         * A View is currently being dragged.
         */
        const val ACTION_STATE_DRAG = 2

        /**
         * Animation type for views which are swiped successfully.
         */
        const val ANIMATION_TYPE_SWIPE_SUCCESS = 1 shl 1

        /**
         * Animation type for views which are not completely swiped thus will animate back to their
         * original position.
         */
        const val ANIMATION_TYPE_SWIPE_CANCEL = 1 shl 2

        /**
         * Animation type for views that were dragged and now will animate to their final position.
         */
        const val ANIMATION_TYPE_DRAG = 1 shl 3

        private const val TAG = "ItemTouchHelper"
        private const val DEBUG = false
        private const val ACTIVE_POINTER_ID_NONE = -1

        const val DIRECTION_FLAG_COUNT = 8

        private const val ACTION_MODE_IDLE_MASK = (1 shl DIRECTION_FLAG_COUNT) - 1

        const val ACTION_MODE_SWIPE_MASK: Int = ACTION_MODE_IDLE_MASK shl DIRECTION_FLAG_COUNT

        /**
         * The unit we are using to track velocity
         */
        private const val PIXELS_PER_SECOND = 1000
        private fun hitTest(child: View, x: Float, y: Float, left: Float, top: Float): Boolean {
            return x >= left && x <= left + child.width && y >= top && y <= top + child.height
        }
    }
}