package com.najm.vid

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.PowerManager
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.*

/** An in-process paint pass of this app's own content, never a screen capture. */
internal object GlassPass {
    var active=false
    fun skip(view: View)=active && view.background is GlassDrawable
}

internal data class GlassFrame(val bitmap: Bitmap,val width: Int,val height: Int,val serial: Long)

/** Captures the content plane once when it changes, excluding all glass controls. */
class GlassHost(c: Context) : FrameLayout(c) {
    private var dirty=true
    private var buffers=arrayOfNulls<Bitmap>(2)
    private var bufferIndex=0
    private var capturing=false
    internal var frame: GlassFrame?=null
        private set
    internal var captureCount=0L
        private set
    init { setWillNotDraw(false);clipChildren=false;clipToPadding=false }
    fun refreshBackdrop(){dirty=true;postInvalidateOnAnimation()}
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int){super.onSizeChanged(w,h,oldw,oldh);dirty=true}
    private fun glassControl(view: View): Boolean {
        var current: View?=view
        while(current!=null && current!==this){
            if(current.background is GlassDrawable || current is LiquidNavigation)return true
            current=current.parent as? View
        }
        return false
    }
    override fun onDescendantInvalidated(child: View,target: View){
        if(!capturing && !GlassPass.active && !glassControl(target))dirty=true
        super.onDescendantInvalidated(child,target)
        if(!capturing && !GlassPass.active)invalidate()
    }
    override fun dispatchDraw(canvas: Canvas){
        if(!GlassPass.active && dirty && width>0 && height>0)captureBackdrop()
        super.dispatchDraw(canvas)
    }
    private fun captureBackdrop(){
        val scale=minOf(.55f,640f/width,1600f/height)
        val w=(width*scale).roundToInt().coerceAtLeast(1)
        val h=(height*scale).roundToInt().coerceAtLeast(1)
        bufferIndex=1-bufferIndex
        var bitmap=buffers[bufferIndex]
        if(bitmap==null || bitmap.width!=w || bitmap.height!=h){
            bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);buffers[bufferIndex]=bitmap
        }
        val target=Canvas(bitmap);target.drawColor(Ui.bg,PorterDuff.Mode.SRC)
        target.scale(w.toFloat()/width,h.toFloat()/height)
        capturing=true;GlassPass.active=true
        try{background?.draw(target);super.dispatchDraw(target)}finally{GlassPass.active=false;capturing=false}
        captureCount++;frame=GlassFrame(bitmap,width,height,captureCount);dirty=false
        invalidateMaterials(this)
    }
    private fun invalidateMaterials(group: ViewGroup){
        for(i in 0 until group.childCount){val child=group.getChildAt(i)
            if(child.background is GlassDrawable || child is LiquidNavigation)child.invalidate()
            else if(child is ViewGroup)invalidateMaterials(child)
        }
    }
    override fun onDetachedFromWindow(){super.onDetachedFromWindow();frame=null;buffers=arrayOfNulls(2)}
}

internal object GlassTouch {
    fun dispatch(view: View,event: MotionEvent){
        var owner: View?=view
        while(owner!=null && owner.background !is GlassDrawable)owner=owner.parent as? View
        val target=owner?:return
        val glass=target.background as GlassDrawable
        val location=IntArray(2);target.getLocationOnScreen(location)
        val x=(event.rawX-location[0]).coerceIn(0f,target.width.toFloat())
        val y=(event.rawY-location[1]).coerceIn(0f,target.height.toFloat())
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->glass.contact(target,x,y,true)
            MotionEvent.ACTION_MOVE->glass.moveContact(x,y)
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->glass.contact(target,x,y,false)
        }
    }
    fun motion(c: Context)=ValueAnimator.areAnimatorsEnabled() && !(c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
}

class LiquidTextView(c: Context) : AppCompatTextView(c){
    override fun draw(canvas: Canvas){if(!GlassPass.skip(this))super.draw(canvas)}
    override fun onTouchEvent(event: MotionEvent): Boolean {GlassTouch.dispatch(this,event);return super.onTouchEvent(event)}
    override fun performClick()=super.performClick()
}
class LiquidEditText(c: Context) : AppCompatEditText(c){
    override fun draw(canvas: Canvas){if(!GlassPass.skip(this))super.draw(canvas)}
    override fun onTouchEvent(event: MotionEvent): Boolean {GlassTouch.dispatch(this,event);return super.onTouchEvent(event)}
    override fun performClick()=super.performClick()
}
class LiquidRadioButton(c: Context) : RadioButton(c){
    override fun draw(canvas: Canvas){if(!GlassPass.skip(this))super.draw(canvas)}
    override fun onTouchEvent(event: MotionEvent): Boolean {GlassTouch.dispatch(this,event);return super.onTouchEvent(event)}
    override fun performClick()=super.performClick()
}

/** A continuous selection lens, rather than rebuilding three static selected pills. */
class LiquidNavigation(c: Context,private val onSelection: (Int)->Unit) : FrameLayout(c){
    val cells=Ui.row(c)
    private val lens=GlassDrawable(c,30,GlassTone.SELECTED)
    private var selected=0
    private var center=0f
    private var stretch=0f
    private var animator: ValueAnimator?=null
    private var downX=0f
    private var dragging=false
    private val slop=ViewConfiguration.get(c).scaledTouchSlop
    internal val selectedCenter get()=center
    init {
        tag="liquid_nav";background=Ui.glass(c,36);elevation=Ui.dp(c,5).toFloat()
        setPadding(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5))
        setWillNotDraw(false);lens.callback=this
        addView(cells,LayoutParams(-1,-1))
    }
    private fun slot()=(width-paddingLeft-paddingRight)/3f
    private fun target(index: Int)=width-paddingRight-slot()*(index+.5f)
    fun select(index: Int,animated: Boolean=true){
        val changed=selected!=index;selected=index
        if(width==0)return
        animator?.cancel()
        val from=if(center==0f)target(index)else center
        val to=target(index)
        if(!animated || !changed || !GlassTouch.motion(context)){center=to;stretch=0f;invalidate();return}
        animator=ValueAnimator.ofFloat(0f,1f).apply{
            duration=360;interpolator=OvershootInterpolator(.65f)
            addUpdateListener{val t=it.animatedValue as Float;center=from+(to-from)*t;stretch=sin(t.coerceIn(0f,1f)*PI).toFloat()*Ui.dp(context,12);invalidate()}
            start()
        }
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int){super.onSizeChanged(w,h,oldw,oldh);center=target(selected)}
    override fun draw(canvas: Canvas){if(!GlassPass.active)super.draw(canvas)}
    override fun onDraw(canvas: Canvas){
        super.onDraw(canvas)
        val half=(slot()*.47f+stretch).coerceAtMost(width*.48f)
        lens.bounds=Rect((center-half).roundToInt(),paddingTop,(center+half).roundToInt(),height-paddingBottom)
        lens.draw(canvas)
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=event.x;dragging=false}
            MotionEvent.ACTION_MOVE->if(abs(event.x-downX)>slop){dragging=true;parent?.requestDisallowInterceptTouchEvent(true);return true}
        }
        return super.onInterceptTouchEvent(event)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN)lens.contact(this,event.x,event.y,true,scaleView=false)
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL)lens.contact(this,event.x,event.y,false,scaleView=false)
        return super.dispatchTouchEvent(event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!dragging)return super.onTouchEvent(event)
        when(event.actionMasked){
            MotionEvent.ACTION_MOVE->{animator?.cancel();center=event.x.coerceIn(target(2),target(0));stretch=Ui.dp(context,7).toFloat();lens.moveContact(event.x,event.y);invalidate()}
            MotionEvent.ACTION_UP->{dragging=false;stretch=0f;val index=((width-paddingRight-event.x)/slot()).toInt().coerceIn(0,2);onSelection(index);performClick()}
            MotionEvent.ACTION_CANCEL->{dragging=false;select(selected,false)}
        }
        return true
    }
    override fun verifyDrawable(who: android.graphics.drawable.Drawable)=who===lens || super.verifyDrawable(who)
    override fun performClick()=super.performClick()
    override fun onDetachedFromWindow(){animator?.cancel();super.onDetachedFromWindow()}
}
