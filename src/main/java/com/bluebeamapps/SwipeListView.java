/*
 * Copyright (C) 2016 Bluebeamapps.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bluebeamapps;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * A ListView with items that can be swiped. Notice that using a selector
 * with this ListView will not work, a background must be set on the
 * ViewGroup that will be inflated by the adapter. A background must also be
 * set on the ListView.
 */
public class SwipeListView extends ListView {

    private static final int SWIPE_VELOCITY = 1200;

    private OnItemClickListener mOnItemClickListener = null;
    private OnListItemSwipeListener mSwipeListener = null;

    /**
     * Slows down the swiping speed.
     */
    private float mSpeedDamp = 0.75f;

    /**
     * Swipe velocity.
     */
    private float mXVelocity;

    private int mXDown, mYDown, mXPosDown;

    /**
     * Distance the pointer has traveled.
     */
    private int mDelta;

    /**
     * Minimum distance the pointer has to travel before a swipe is detected.
     */
    private int mMinDistance;

    private int mHeaderCount = 0, mFooterCount = 0;

    /**
     * View type to be ignored, can be set via setIgnoreViewTypeSwipe().
     */
    private int mViewTypeIgnore = -1;

    /**
     * Whether or not the user is swiping an item.
     */
    private boolean mIsSwiping = false;

    /**
     * Whether or not the user is scrolling the list.
     */
    private boolean mScrolling = false;

    /**
     * Blocks touch operation while a swipe out animation is playing.
     */
    private boolean mBlockTouch = false;

    private VelocityTracker mVelocityTracker = null;

    /**
     * The ListView child that is currently being touched.
     */
    private View mTouchedView;

    public static interface OnListItemSwipeListener {
        public void onListItemSwiped(AdapterView<?> parent, View view, int position, long id);
    }

    public SwipeListView(Context context) {
        super(context);
        init(context);
    }

    public SwipeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mMinDistance = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, metrics);
    }

    public void setOnListItemSwipeListener(OnListItemSwipeListener l) {
        mSwipeListener = l;
    }

    public boolean isSwiping() {
        return mIsSwiping;
    }

    /**
     * Ignores swiping for a certain view type.
     */
    public void setIgnoreViewTypeSwipe(int viewType) {
        mViewTypeIgnore = viewType;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mBlockTouch) {
                    break;
                }

                int pos = pointToPosition(x, (int) event.getY());

			    /* Make sure we have not hit a header or footer. */
                mHeaderCount = getHeaderViewsCount();
                mFooterCount = getFooterViewsCount();

                if (pos < mHeaderCount || pos >= getCount() - mFooterCount) {
                    break;
                }

                if (pos != INVALID_POSITION && mSwipeListener != null) {//Will be -1 if we hit a divider.
                    //Checks whether or not the view type of the passed position should be ignored.
                    if (getAdapter().getItemViewType(pos) == mViewTypeIgnore) {
                        break;//Ignore this view type.
                    }

                    mTouchedView = getChildAt(pos - getFirstVisiblePosition());
				
				    /* Record some values. */
                    mXPosDown = mTouchedView.getLeft();
                    mXDown = x;
                    mYDown = y;
                }

			    /* Doing this on ACTION_UP will cause the app to crash when a quick succession
			      of touches is detected.  */
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                }

                mVelocityTracker = VelocityTracker.obtain();

                break;

            case MotionEvent.ACTION_MOVE:
                if (mBlockTouch) {
                    break;
                }

                if (mTouchedView != null && mSwipeListener != null) {//Make sure a view was touched.
                    mDelta = x - mXDown;
                    int deltaY = y - mYDown;

                    mVelocityTracker.addMovement(event);

                    if (Math.abs(deltaY) >= mMinDistance && !mIsSwiping) {
					    /* Detected a scroll, swiping won't occur. */
                        mScrolling = true;
                    } else if (Math.abs(mDelta) >= mMinDistance && !mIsSwiping && !mScrolling) {
					    /* Start the swiping operation if the pointer has traveled
					    * the minimum distance. Refresh the X down value and delta.*/
                        mIsSwiping = true;
                        mXDown = x;
                        mDelta = x - mXDown;

                        animateSelectorAlpha(255, 0);//Fade the selector.
                    }

                    if (mIsSwiping) {
					    /* Moves the view. */
                        int newXPos = mXPosDown + (int) (mDelta * mSpeedDamp);
                        mTouchedView.setTranslationX(newXPos);
                        mTouchedView.setPressed(false);

                        mVelocityTracker.computeCurrentVelocity(1000);
                        mXVelocity = mVelocityTracker.getXVelocity();

                        float alpha = 1.0f - ((float) Math.abs(mDelta) / (float) mTouchedView.getWidth());
                        mTouchedView.setAlpha(alpha < 0.4f ? 0.4f : alpha);

                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mIsSwiping) {
				    /* Temporarily disable listener when a swipe occurs. We must let
				    * the ListView handle this MotionEvent. Using setPressed(false)
				    * will not work at all.*/
                    setOnItemClickListener(null);
				
				    /* User swiped fast. */
                    boolean swipedFast = Math.abs(mXVelocity) >= SWIPE_VELOCITY &&
                            Math.abs(mDelta) >= (mMinDistance * 2);
				
				    /* User swiped until a good portion of the view was out of sight. */
                    boolean swipedFull = Math.abs(mDelta) >= (mTouchedView.getWidth() / 2.5);

                    if (swipedFast || swipedFull) {
                        mBlockTouch = true;
                        animateOut();
                    } else {
                        animateBackIn();
                    }

                    animateSelectorAlpha(0, 255);//Restore selector's alpha.

                    mTouchedView = null;
                    mIsSwiping = false;
                }

                mScrolling = false;

                break;
        }

        return super.onTouchEvent(event);
    }

    private void animateSelectorAlpha(int from, int to) {
        Drawable selector = getSelector();
        ObjectAnimator animator = ObjectAnimator.ofInt(selector, "alpha", from, to);
        animator.setDuration(500);
        animator.start();
    }

    private void animateBackIn() {
		/* Call listener only when animation ends. */
        final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setOnItemClickListener(mOnItemClickListener);//Restores listener.
            }
        };
		
		/* Animates the translation and alpha back to their original values. */
        mTouchedView.animate().translationX(0).alpha(1.0f).setListener(listener);
    }

    private void animateOut() {
		/* Decide what side the view should be animated to. */
        int finalX = mDelta > 0 ? mTouchedView.getRight() : -mTouchedView.getWidth();
			
		/* Animates the view back to its position. */
        final View touchedView = mTouchedView;//mTouchedView will be null soon.
        final int position = getPositionForView(touchedView);
		
		/* Call listener only when animation ends. */
        final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setOnItemClickListener(mOnItemClickListener);//Restores listener.

                if (mSwipeListener != null) {
                    mSwipeListener.onListItemSwiped(SwipeListView.this, touchedView, position,
                            getAdapter().getItemId(position));
					
					/* Restores the view state. */
                    if (touchedView != null) {
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                touchedView.setTranslationX(0);
                                touchedView.setAlpha(1.0f);
                            }
                        }, 50);//Using post delayed to avoid some flickering.
                    }

                    mBlockTouch = false;//Enforce a bit of sanity on the user.
                }
            }
        };

        touchedView.animate().translationX(finalX).setListener(listener);//Animate.
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        if (listener != null) {
            mOnItemClickListener = listener;//Records listener.
        }

        super.setOnItemClickListener(listener);
    }

}