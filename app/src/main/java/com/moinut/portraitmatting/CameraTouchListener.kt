package com.moinut.portraitmatting

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View


class CameraTouchListener(context: Context?) : View.OnTouchListener {
    private val mGestureDetector: GestureDetector

    private val mScaleGestureDetector: ScaleGestureDetector
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        mScaleGestureDetector.onTouchEvent(event)
        if (!mScaleGestureDetector.isInProgress) {
            mGestureDetector.onTouchEvent(event)
        }
        return true
    }

    interface CustomTouchListener {

        fun zoom(delta: Float)

        fun click(x: Float, y: Float)

        fun doubleClick(x: Float, y: Float)

        fun longClick(x: Float, y: Float)
    }

    private var mCustomTouchListener: CustomTouchListener? = null
    fun setCustomTouchListener(customTouchListener: CustomTouchListener?) {
        mCustomTouchListener = customTouchListener
    }

    var onScaleGestureListener: OnScaleGestureListener = object : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val delta = detector.scaleFactor
            if (mCustomTouchListener != null) {
                mCustomTouchListener!!.zoom(delta)
            }
            return true
        }
    }
    var onGestureListener: SimpleOnGestureListener = object : SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (mCustomTouchListener != null) {
                mCustomTouchListener!!.longClick(e.x, e.y)
            }
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (mCustomTouchListener != null) {
                mCustomTouchListener!!.click(e.x, e.y)
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (mCustomTouchListener != null) {
                mCustomTouchListener!!.doubleClick(e.x, e.y)
            }
            return true
        }
    }

    init {
        mGestureDetector = GestureDetector(context, onGestureListener)
        mScaleGestureDetector = ScaleGestureDetector(context, onScaleGestureListener)
    }
}