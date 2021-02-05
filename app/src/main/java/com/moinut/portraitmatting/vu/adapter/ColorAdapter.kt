package com.moinut.portraitmatting.vu.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.moinut.portraitmatting.R

class ColorAdapter(context: Context, listener: OnColorClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mContext: Context = context
    private var mOnItemClickListener: OnColorClickListener = listener

    interface OnColorClickListener {
        fun onItemClick(view: View?, color: Int)
    }

    private val mColorList = intArrayOf(
        Color.parseColor("#9e9e9e"),
        Color.parseColor("#ffffff"),
        Color.parseColor("#673ab7"),
        Color.parseColor("#5677fc"),
        Color.parseColor("#8bc34a"),
        Color.parseColor("#ffeb3b"),
        Color.parseColor("#ff9800"),
        Color.parseColor("#e51c23")
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ColorViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_color, parent, false))
    }

    override fun getItemCount(): Int {
        return mColorList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val colorHolder = holder as ColorViewHolder
        colorHolder.mColor = mColorList[position]
        colorHolder.mButton!!.setBackgroundColor(mColorList[position])
        colorHolder.mButton!!.setOnClickListener {
            mOnItemClickListener.onItemClick(it, colorHolder.mColor!!)
        }
    }

    inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var mButton: ImageButton? = null
        var mColor: Int? = null

        init {
            mButton = itemView.findViewById(R.id.buttonColor)
        }
    }
}