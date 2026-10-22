package com.najm.vid

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/** The glass samples only our own decorative scene; text and media stay sharp. */
object GlassScene {
    private val cache=android.util.LruCache<String,Bitmap>(4)
    fun texture(w: Int, h: Int): Bitmap {
        val key="$w:$h"
        cache.get(key)?.let{return it}
        val width=256
        val height=(width*h.toFloat()/w.coerceAtLeast(1)).toInt().coerceIn(256,1024)
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { draw(Canvas(it),width.toFloat(),height.toFloat());cache.put(key,it) }
    }
    fun draw(c: Canvas,w: Float,h: Float){
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,w,h,intArrayOf(Color.rgb(249,246,241),Color.rgb(240,230,218),Color.rgb(248,245,240)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,p)
        val x=w*.92f;val y=h*.37f;val r=w*.46f
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

/** Cached scene plus a small GPU lens, no continuous animation or screen capture. */
class GlassDrawable(private val c: Context,private val corner: Int=24,private val tone: GlassTone=GlassTone.CLEAR) : Drawable(){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect=RectF();private val clip=Path();private val location=IntArray(2)
    private var lens: GlassLens?=null
    private var attempted=false
    internal val usesGpuLens get()=lens!=null
    private var globalAlpha=255
    override fun onBoundsChange(bounds: Rect){
        rect.set(bounds.left+.8f,bounds.top+.8f,bounds.right-.8f,bounds.bottom-.8f)
        val radius=Ui.dp(c,corner).toFloat().coerceAtMost(rect.height()/2)
        clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW)
    }
    override fun draw(canvas: Canvas){
        if(rect.isEmpty)return
        val v=callback as? View
        val root=v?.rootView
        val w=(root?.width?:c.resources.displayMetrics.widthPixels).coerceAtLeast(1)
        val h=(root?.height?:c.resources.displayMetrics.heightPixels).coerceAtLeast(1)
        val radius=Ui.dp(c,corner).toFloat().coerceAtMost(rect.height()/2)
        val texture=GlassScene.texture(w,h)
        v?.getLocationInWindow(location)
        canvas.save();canvas.clipPath(clip)
        p.style=Paint.Style.FILL;p.alpha=globalAlpha
        if(!attempted){attempted=true;if(Build.VERSION.SDK_INT>=33)lens=runCatching{AgslGlassLens()}.getOrNull()}
        if(tone!=GlassTone.SMOKED){
            if(lens!=null && canvas.isHardwareAccelerated){
                p.shader=lens!!.shader(texture,w,h,location[0].toFloat(),location[1].toFloat(),rect,radius)
                canvas.drawRect(rect,p);p.shader=null
            }else{
                val save=canvas.save()
                canvas.translate(-location[0].toFloat(),-location[1].toFloat())
                canvas.drawBitmap(texture,null,RectF(0f,0f,w.toFloat(),h.toFloat()),p);canvas.restoreToCount(save)
            }
        }
        val colors=when(tone){
            GlassTone.SMOKED->intArrayOf(0xFF383532.toInt(),0xFF22211F.toInt(),0xFF2B2825.toInt())
            GlassTone.SELECTED->intArrayOf(0xDBFFFFFF.toInt(),0x70FFE5CA,0x96FFFFFF.toInt())
            GlassTone.FIELD->intArrayOf(0xEFFFFFFF.toInt(),0xBDFFF9F1.toInt(),0xD9FFFFFF.toInt())
            GlassTone.CLEAR->intArrayOf(0xBAFFFFFF.toInt(),0x72FFF9F1,0xADFFFFFF.toInt())
        }
        p.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,colors,floatArrayOf(0f,.6f,1f),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,p);p.shader=null
        // A narrow white upper edge and warm lower edge: intentionally subdued.
        p.style=Paint.Style.STROKE;p.strokeWidth=Ui.dp(c,1).coerceAtLeast(1).toFloat()*.6f
        p.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
            if(tone==GlassTone.SMOKED)intArrayOf(0x82FFFFFF.toInt(),0x08FFFFFF,0x3CFFFFFF)
            else intArrayOf(0xEAFFFFFF.toInt(),0x38FFFFFF,0x68D5AE89),floatArrayOf(0f,.58f,1f),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,p);p.shader=null
        if(tone!=GlassTone.FIELD){
            val inner=RectF(rect).apply{inset(Ui.dp(c,2).toFloat(),Ui.dp(c,2).toFloat())}
            p.strokeWidth=1f;p.shader=LinearGradient(inner.left,inner.top,inner.left,inner.bottom,
                intArrayOf(0x65FFFFFF,0x00FFFFFF,0x20D08B4F),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(inner,(radius-Ui.dp(c,2)).coerceAtLeast(1f),(radius-Ui.dp(c,2)).coerceAtLeast(1f),p);p.shader=null
        }
        p.style=Paint.Style.FILL;canvas.restore()
    }
    override fun getOutline(outline: Outline){outline.setRoundRect(bounds,Ui.dp(c,corner).toFloat());outline.alpha=.12f}
    override fun setAlpha(alpha: Int){globalAlpha=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter: ColorFilter?){p.colorFilter=colorFilter;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

private interface GlassLens {
    fun shader(bitmap: Bitmap,w: Int,h: Int,x: Float,y: Float,rect: RectF,radius: Float): Shader
}

@RequiresApi(33)
private class AgslGlassLens : GlassLens {
    private val effect=RuntimeShader("""
        uniform shader scene;
        uniform float2 origin;
        uniform float2 size;
        uniform float2 offset;
        uniform float2 scale;
        uniform float radius;
        half4 main(float2 xy) {
            float2 p=xy-origin-size*0.5;
            float2 q=abs(p)-(size*0.5-radius);
            float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
            float edge=1.0-smoothstep(0.0,12.0,-d);
            float2 normal=normalize(p+float2(0.001));
            float2 uv=(xy+offset-normal*edge*5.5)*scale;
            half4 color=scene.eval(uv)*0.4;
            float2 tap=float2(1.1,0.0);
            color+=scene.eval(uv+tap)*0.15;
            color+=scene.eval(uv-tap)*0.15;
            color+=scene.eval(uv+tap.yx)*0.15;
            color+=scene.eval(uv-tap.yx)*0.15;
            return color;
        }
    """.trimIndent())
    private var texture: Bitmap?=null
    override fun shader(bitmap: Bitmap,w: Int,h: Int,x: Float,y: Float,rect: RectF,radius: Float): Shader{
        if(texture!==bitmap){effect.setInputShader("scene",BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));texture=bitmap}
        effect.setFloatUniform("origin",rect.left,rect.top)
        effect.setFloatUniform("size",rect.width(),rect.height())
        effect.setFloatUniform("offset",x,y)
        effect.setFloatUniform("scale",bitmap.width.toFloat()/w,bitmap.height.toFloat()/h)
        effect.setFloatUniform("radius",radius)
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
