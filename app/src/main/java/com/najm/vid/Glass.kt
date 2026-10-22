package com.najm.vid

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/** Decorative scene, also used when a control has no live content host. */
object GlassScene {
    private val cache=android.util.LruCache<String,Bitmap>(4)
    fun texture(w: Int, h: Int): Bitmap {
        val key="$w:$h"
        cache.get(key)?.let{return it}
        val width=384
        val height=(width*h.toFloat()/w.coerceAtLeast(1)).toInt().coerceIn(256,1024)
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { draw(Canvas(it),width.toFloat(),height.toFloat());cache.put(key,it) }
    }
    fun draw(c: Canvas,w: Float,h: Float){
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,w,h,intArrayOf(Color.rgb(249,246,241),Color.rgb(240,230,218),Color.rgb(248,245,240)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,p)
        val x=w*.92f;val y=h*.37f;val r=w*.38f
        p.shader=RadialGradient(x,y,r*1.30f,intArrayOf(0x36FF8B35,0x00FF8B35),null,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r*1.30f,p)
        p.shader=RadialGradient(x-r*.38f,y-r*.5f,r*1.65f,intArrayOf(0xFFFFD09A.toInt(),0xFFFFAB59.toInt(),0xFFF87C20.toInt()),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,p)
        p.shader=LinearGradient(x-r,y-r,x+r,y+r,intArrayOf(0xACFFFFFF.toInt(),0x04FFFFFF,0x48F17B31),null,Shader.TileMode.CLAMP)
        p.style=Paint.Style.STROKE;p.strokeWidth=w*.004f;c.drawCircle(x,y,r-w*.002f,p)
        p.style=Paint.Style.FILL
        p.shader=RadialGradient(w*.02f,h*.81f,w*.65f,intArrayOf(0xCFFFFFFF.toInt(),0x10FFFFFF),null,Shader.TileMode.CLAMP)
        c.drawCircle(-w*.16f,h*.88f,w*.65f,p)
        p.shader=null
    }
}

class SceneDrawable : Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas){
        if(bounds.isEmpty)return
        val bitmap=GlassScene.texture(bounds.width(),bounds.height())
        canvas.drawBitmap(bitmap,null,bounds,paint)
    }
    override fun setAlpha(alpha: Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter: ColorFilter?){paint.colorFilter=colorFilter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.OPAQUE
}

enum class GlassTone { CLEAR, FIELD, SMOKED, SELECTED }

/** A live content lens with local touch lighting; glyphs are drawn separately. */
class GlassDrawable(private val c: Context,private val corner: Int=24,private val tone: GlassTone=GlassTone.CLEAR) : Drawable(){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect=RectF();private val clip=Path();private val location=IntArray(2);private val hostLocation=IntArray(2)
    private var lens: GlassLens?=null
    private var attempted=false
    private var animator: android.animation.ValueAnimator?=null
    internal val usesGpuLens get()=lens!=null
    internal var usesLiveSource=false
        private set
    internal var pressure=0f
        private set
    private var touchX=-1f;private var touchY=-1f
    private var globalAlpha=255
    override fun onBoundsChange(bounds: Rect){
        rect.set(bounds.left+.8f,bounds.top+.8f,bounds.right-.8f,bounds.bottom-.8f)
        val radius=Ui.dp(c,corner).toFloat().coerceAtMost(rect.height()/2)
        clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW)
    }
    internal fun moveContact(x: Float,y: Float){touchX=x;touchY=y;invalidateSelf()}
    internal fun contact(owner: View,x: Float,y: Float,pressed: Boolean,scaleView: Boolean=true){
        touchX=x;touchY=y;animator?.cancel()
        val to=if(pressed)1f else 0f
        if(GlassTouch.motion(c)){
            animator=android.animation.ValueAnimator.ofFloat(pressure,to).apply{
                duration=if(pressed)110 else 300
                interpolator=android.view.animation.DecelerateInterpolator()
                addUpdateListener{pressure=it.animatedValue as Float;invalidateSelf()};start()
            }
            if(scaleView)owner.animate().scaleX(if(pressed)1.025f else 1f).scaleY(if(pressed)1.025f else 1f).setDuration(if(pressed)110 else 280).start()
        }else{pressure=to;invalidateSelf()}
    }
    override fun draw(canvas: Canvas){
        if(rect.isEmpty || GlassPass.active)return
        val v=callback as? View
        var parent: View?=v
        while(parent!=null && parent !is GlassHost)parent=parent.parent as? View
        val host=parent as? GlassHost
        val frame=host?.frame
        val root=v?.rootView
        val w=(frame?.width?:root?.width?:c.resources.displayMetrics.widthPixels).coerceAtLeast(1)
        val h=(frame?.height?:root?.height?:c.resources.displayMetrics.heightPixels).coerceAtLeast(1)
        val radius=Ui.dp(c,corner).toFloat().coerceAtMost(rect.height()/2)
        val texture=frame?.softBitmap?:GlassScene.texture(w,h)
        usesLiveSource=frame!=null
        v?.getLocationInWindow(location);host?.getLocationInWindow(hostLocation)
        val x=(location[0]-if(host!=null)hostLocation[0]else 0).toFloat()
        val y=(location[1]-if(host!=null)hostLocation[1]else 0).toFloat()
        canvas.save();canvas.clipPath(clip)
        p.style=Paint.Style.FILL;p.alpha=globalAlpha
        if(!attempted){attempted=true;if(Build.VERSION.SDK_INT>=33)lens=runCatching{AgslGlassLens()}.getOrNull()}
        if(lens!=null && canvas.isHardwareAccelerated){
            p.shader=lens!!.shader(texture,w,h,x,y,rect,radius,c.resources.displayMetrics.density,tone.ordinal,pressure,touchX,touchY)
            canvas.drawRect(rect,p);p.shader=null
        }else{
            val save=canvas.save();canvas.translate(-x,-y)
            canvas.drawBitmap(texture,null,RectF(0f,0f,w.toFloat(),h.toFloat()),p);canvas.restoreToCount(save)
            p.color=if(tone==GlassTone.SMOKED)0xEF242321.toInt() else 0xCFFFFFFB.toInt()
            canvas.drawRoundRect(rect,radius,radius,p)
        }
        // Keep the rim quiet; the optical edge comes primarily from the shader.
        p.style=Paint.Style.STROKE;p.strokeWidth=c.resources.displayMetrics.density*.65f
        p.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
            if(tone==GlassTone.SMOKED)intArrayOf(0x66FFFFFF,0x08FFFFFF,0x24FFFFFF)
            else intArrayOf(0xD4FFFFFF.toInt(),0x18FFFFFF,0x35B7A797),floatArrayOf(0f,.58f,1f),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,p);p.shader=null;p.style=Paint.Style.FILL;canvas.restore()
    }
    override fun getOutline(outline: Outline){outline.setRoundRect(bounds,Ui.dp(c,corner).toFloat());outline.alpha=.16f}
    override fun setAlpha(alpha: Int){globalAlpha=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter: ColorFilter?){p.colorFilter=colorFilter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

private interface GlassLens {
    fun shader(bitmap: Bitmap,w: Int,h: Int,x: Float,y: Float,rect: RectF,radius: Float,density: Float,tone: Int,pressure: Float,touchX: Float,touchY: Float): Shader
}

@RequiresApi(33)
private class AgslGlassLens : GlassLens {
    private val effect=RuntimeShader("""
        uniform shader scene;
        uniform float2 origin;
        uniform float2 size;
        uniform float2 offset;
        uniform float2 scale;
        uniform float2 touch;
        uniform float radius;
        uniform float density;
        uniform float tone;
        uniform float pressure;
        half4 main(float2 xy) {
            float2 p=xy-origin-size*0.5;
            float2 q=abs(p)-(size*0.5-radius);
            float2 outside=max(q,0.0);
            float d=length(outside)+min(max(q.x,q.y),0.0)-radius;
            float2 n;
            if(max(q.x,q.y)>0.0)n=normalize(outside+float2(0.0001))*sign(p);
            else n=q.x>q.y?float2(sign(p.x),0.0):float2(0.0,sign(p.y));
            float edge=1.0-smoothstep(0.0,12.0*density,-d);
            float bend=edge*edge*(8.5+pressure*3.0)*density;
            float2 uv=(xy+offset-n*bend)*scale;
            float2 blur=float2(0.65+0.45*(1.0-edge));
            float2 horizontal=float2(blur.x,0.0);
            float2 vertical=float2(0.0,blur.y);
            half4 color=scene.eval(uv)*0.25;
            color+=scene.eval(uv+horizontal)*0.125;
            color+=scene.eval(uv-horizontal)*0.125;
            color+=scene.eval(uv+vertical)*0.125;
            color+=scene.eval(uv-vertical)*0.125;
            color+=scene.eval(uv+blur)*0.0625;
            color+=scene.eval(uv-blur)*0.0625;
            color+=scene.eval(uv+float2(blur.x,-blur.y))*0.0625;
            color+=scene.eval(uv+float2(-blur.x,blur.y))*0.0625;
            float luma=dot(float3(color.rgb),float3(0.2126,0.7152,0.0722));
            float tint=clamp((0.84-luma)/max(1.0-luma,0.01),0.32,0.84);
            if(tone>0.5 && tone<1.5)tint=max(tint,0.58);
            if(tone>1.5 && tone<2.5)color.rgb=mix(color.rgb,half3(0.115,0.109,0.101),0.91);
            else color.rgb=mix(color.rgb,half3(1.0,0.985,0.965),tint);
            float2 delta=(xy-touch)/density;
            float contact=exp(-dot(delta,delta)/2600.0)*pressure;
            float2 light=normalize(float2(-0.55,-0.85)+(touch-origin-size*0.5)/max(size,1.0)*pressure*0.6);
            float specular=pow(max(dot(n,light),0.0),5.0)*edge*0.13;
            color.rgb+=half3(specular+contact*0.09);
            return half4(clamp(color.rgb,0.0,1.0),1.0);
        }
    """.trimIndent())
    private var texture: Bitmap?=null
    private var generation=0
    override fun shader(bitmap: Bitmap,w: Int,h: Int,x: Float,y: Float,rect: RectF,radius: Float,density: Float,tone: Int,pressure: Float,touchX: Float,touchY: Float): Shader{
        if(texture!==bitmap || generation!=bitmap.generationId){
            // RuntimeShader inputs otherwise default to nearest, regardless of Paint filtering.
            val source=BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply{setFilterMode(BitmapShader.FILTER_MODE_LINEAR)}
            effect.setInputShader("scene",source);texture=bitmap;generation=bitmap.generationId
        }
        effect.setFloatUniform("origin",rect.left,rect.top)
        effect.setFloatUniform("size",rect.width(),rect.height())
        effect.setFloatUniform("offset",x,y)
        effect.setFloatUniform("scale",bitmap.width.toFloat()/w,bitmap.height.toFloat()/h)
        effect.setFloatUniform("radius",radius)
        effect.setFloatUniform("density",density)
        effect.setFloatUniform("tone",tone.toFloat())
        effect.setFloatUniform("pressure",pressure)
        effect.setFloatUniform("touch",if(touchX<0)rect.centerX()else touchX,if(touchY<0)rect.centerY()else touchY)
        return effect
    }
}

/** Original vector brand mark, drawn at any density without a bitmap. */
class BrandMark(c: Context) : View(c){
    override fun onDraw(canvas: Canvas){
        val s=minOf(width,height).toFloat();canvas.save();canvas.translate((width-s)/2,(height-s)/2);canvas.scale(s/48,s/48)
        val star=Path()
        for(i in 0 until 10){val a=Math.toRadians(-90.0+i*36.0);val r=if(i%2==0)21.0 else 10.5;val x=24f+(kotlin.math.cos(a)*r).toFloat();val y=24f+(kotlin.math.sin(a)*r).toFloat();if(i==0)star.moveTo(x,y)else star.lineTo(x,y)}
        star.close()
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{shader=LinearGradient(5f,4f,40f,42f,0xFFFFBB6B.toInt(),0xFFEF7014.toInt(),Shader.TileMode.CLAMP)}
        canvas.drawPath(star,p);p.shader=null;p.color=0xFFFDF7EF.toInt()
        val play=Path().apply{moveTo(21f,17f);lineTo(31f,24f);lineTo(21f,31f);close()};canvas.drawPath(play,p);canvas.restore()
    }
}
