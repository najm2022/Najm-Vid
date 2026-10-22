package com.najm.vid

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale

object Ui {
    val bg = Color.rgb(247,242,235)
    val card = Color.rgb(253,249,244)
    val raised = Color.rgb(239,229,217)
    val line = Color.rgb(215,198,180)
    val mint = Color.rgb(184,78,13) // Text-safe orange; retained alias for existing callers.
    val accent = Color.rgb(255,137,34)
    val ink = Color.rgb(35,33,30)
    val white = ink // Existing content uses this token for foreground text.
    val muted = Color.rgb(105,95,85)
    val danger = Color.rgb(158,48,29)
    private var regularFont: Typeface?=null
    private var boldFont: Typeface?=null
    fun font(c: Context,bold: Boolean=false): Typeface {
        if(regularFont==null)regularFont=Typeface.createFromAsset(c.assets,"fonts/NotoSansArabic-Regular.ttf")
        if(boldFont==null)boldFont=Typeface.createFromAsset(c.assets,"fonts/NotoSansArabic-SemiBold.ttf")
        return (if(bold)boldFont else regularFont)!!
    }
    fun glass(c: Context, radius: Int=24, tone: GlassTone=GlassTone.CLEAR) = GlassDrawable(c,radius,tone)
    fun surface(c: Context,radius: Int=24) = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xEDFFFCF8.toInt(),0xD9F9F3EB.toInt())).apply{cornerRadius=dp(c,radius).toFloat();setStroke(dp(c,1).coerceAtLeast(1),0xBFFFFFFF.toInt())}
    fun dp(c: Context, v: Int) = (v*c.resources.displayMetrics.density).toInt()
    fun shape(c: Context, color: Int = card, radius: Int = 24, border: Boolean = false) = GradientDrawable().apply {
        setColor(color);cornerRadius=dp(c,radius).toFloat();if(border)setStroke(dp(c,1),line)
    }
    fun text(c: Context, value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) = LiquidTextView(c).apply {
        text=value;textSize=size;setTextColor(color);typeface=font(c,bold)
        gravity=Gravity.START or Gravity.CENTER_VERTICAL;includeFontPadding=false
        textDirection=View.TEXT_DIRECTION_FIRST_STRONG_RTL
    }
    fun column(c: Context, padding: Int=0) = object: LinearLayout(c){override fun draw(canvas: Canvas){if(!GlassPass.skip(this))super.draw(canvas)}}.apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(c,padding),dp(c,padding),dp(c,padding),dp(c,padding)) }
    fun row(c: Context) = object: LinearLayout(c){override fun draw(canvas: Canvas){if(!GlassPass.skip(this))super.draw(canvas)}}.apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
    fun space(c: Context, height: Int) = View(c).apply { layoutParams=LinearLayout.LayoutParams(1,dp(c,height)) }
    fun button(c: Context, label: String, primary: Boolean=true, action: () -> Unit): TextView = text(c,label,14f,if(primary)Color.WHITE else ink,true).apply {
        gravity=Gravity.CENTER;background=glass(c,26,if(primary)GlassTone.SMOKED else GlassTone.CLEAR)
        setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));minHeight=dp(c,52);isClickable=true;isFocusable=true
        setOnClickListener { action() };contentDescription=label
    }
    fun pill(c: Context, label: String, selected: Boolean=false, action: (() -> Unit)?=null) = text(c,label,12f,if(selected)Color.WHITE else muted,selected).apply {
        gravity=Gravity.CENTER;background=glass(c,24,if(selected)GlassTone.SMOKED else GlassTone.FIELD)
        minHeight=dp(c,48);setPadding(dp(c,12),dp(c,9),dp(c,12),dp(c,9));if(action!=null){isClickable=true;isFocusable=true;setOnClickListener{action()}}
    }
    fun iconButton(c: Context, name: String, label: String, action: () -> Unit) = Glyph(c,name,ink).apply {
        contentDescription=label;background=glass(c,24);setPadding(dp(c,13),dp(c,13),dp(c,13),dp(c,13))
        layoutParams=LinearLayout.LayoutParams(dp(c,48),dp(c,48));isClickable=true;isFocusable=true;setOnClickListener{action()}
    }
    fun action(c: Context,label: String,icon: String="arrow",tag: String?=null,callback: ()->Unit): LinearLayout {
        val row=row(c)
        row.addView(Glyph(c,icon,Color.WHITE).apply{
            background=glass(c,28,GlassTone.SMOKED);contentDescription=label
            setPadding(dp(c,15),dp(c,15),dp(c,15),dp(c,15));isClickable=true;isFocusable=true;setOnClickListener{callback()}
        },LinearLayout.LayoutParams(dp(c,52),dp(c,52)).apply{marginEnd=dp(c,7)})
        row.addView(button(c,label,action=callback).apply{this.tag=tag},LinearLayout.LayoutParams(0,-2,1f))
        return row
    }
    fun sheet(a: AppCompatActivity): Pair<BottomSheetDialog,LinearLayout> {
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(a.currentFocus?.windowToken,0)
        val dialog=BottomSheetDialog(a,R.style.Theme_NajmVid_Sheet)
        val content=column(a,22)
        val handle=View(a).apply { background=shape(a,line,4) }
        content.addView(handle,LinearLayout.LayoutParams(dp(a,38),dp(a,4)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(a,22)})
        val scroll=ScrollView(a).apply { isFillViewport=true;addView(content);isVerticalScrollBarEnabled=false }
        scroll.setOnScrollChangeListener{_,_,_,_,_->(scroll.parent as? GlassHost)?.refreshBackdrop()}
        val host=GlassHost(a).apply{background=surface(a,28);addView(scroll,FrameLayout.LayoutParams(-1,-2))}
        dialog.setContentView(host)
        dialog.setOnShowListener { dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let{sheet->
                sheet.background=shape(a,card,28)
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).apply{skipCollapsed=true;state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED}
            }
            dialog.window?.let{androidx.core.view.WindowCompat.getInsetsController(it,it.decorView).isAppearanceLightNavigationBars=true} }
        return dialog to content
    }
    fun toast(c: Context, message: String) = Toast.makeText(c,message,Toast.LENGTH_SHORT).show()
    fun bytes(value: Long): String = if(value>=1_073_741_824) String.format(Locale.US,"%.1f GB",value/1_073_741_824.0) else String.format(Locale.US,"%.1f MB",value/1_048_576.0)
}

class Glyph(c: Context, private val symbol: String, var tint: Int = Ui.muted) : View(c) {
    override fun draw(canvas: Canvas){if(!GlassPass.skip(this))super.draw(canvas)}
    override fun onTouchEvent(event: MotionEvent): Boolean {GlassTouch.dispatch(this,event);return super.onTouchEvent(event)}
    override fun performClick()=super.performClick()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val avail=minOf(width-paddingLeft-paddingRight,height-paddingTop-paddingBottom).toFloat()
        canvas.save();canvas.translate(paddingLeft+(width-paddingLeft-paddingRight-avail)/2,paddingTop+(height-paddingTop-paddingBottom-avail)/2);canvas.scale(avail/24,avail/24)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        fun line(x:Float,y:Float,a:Float,b:Float)=canvas.drawLine(x,y,a,b,p)
        val path=Path()
        when(symbol){
            "home"->{path.moveTo(3f,10f);path.lineTo(12f,3f);path.lineTo(21f,10f);path.lineTo(21f,21f);path.lineTo(15f,21f);path.lineTo(15f,14f);path.lineTo(9f,14f);path.lineTo(9f,21f);path.lineTo(3f,21f);path.close();canvas.drawPath(path,p)}
            "download"->{line(12f,3f,12f,15f);line(7f,10f,12f,15f);line(17f,10f,12f,15f);path.moveTo(4f,16f);path.lineTo(4f,21f);path.lineTo(20f,21f);path.lineTo(20f,16f);canvas.drawPath(path,p)}
            "settings"->{canvas.drawCircle(12f,12f,7f,p);canvas.drawCircle(12f,12f,2.5f,p);for(i in 0..7){canvas.save();canvas.rotate(i*45f,12f,12f);line(12f,2f,12f,4f);canvas.restore()}}
            "paste"->{canvas.drawRoundRect(5f,5f,19f,22f,2f,2f,p);canvas.drawRoundRect(9f,2f,15f,7f,1f,1f,p);line(9f,12f,15f,12f);line(9f,16f,15f,16f)}
            "check"->{path.moveTo(5f,12f);path.lineTo(10f,17f);path.lineTo(20f,6f);canvas.drawPath(path,p)}
            "close"->{line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
            "play"->{path.moveTo(8f,5f);path.lineTo(20f,12f);path.lineTo(8f,19f);path.close();canvas.drawPath(path,p)}
            "share"->{canvas.drawCircle(6f,12f,2.3f,p);canvas.drawCircle(18f,5f,2.3f,p);canvas.drawCircle(18f,19f,2.3f,p);line(8f,11f,16f,6f);line(8f,13f,16f,18f)}
            "retry"->{canvas.drawArc(4f,4f,20f,20f,10f,290f,false,p);path.moveTo(14f,3f);path.lineTo(19f,4f);path.lineTo(18f,9f);canvas.drawPath(path,p)}
            "audio"->{line(9f,17f,9f,5f);line(9f,5f,19f,3f);line(19f,3f,19f,15f);canvas.drawOval(3f,16f,9f,21f,p);canvas.drawOval(13f,14f,19f,19f,p)}
            "arrow"->{line(4f,12f,20f,12f);line(13f,5f,20f,12f);line(13f,19f,20f,12f)}
            "folder"->{path.moveTo(3f,7f);path.lineTo(3f,20f);path.lineTo(21f,20f);path.lineTo(21f,7f);path.lineTo(12f,7f);path.lineTo(10f,4f);path.lineTo(3f,4f);path.close();canvas.drawPath(path,p)}
            else->{canvas.drawCircle(12f,12f,9f,p);line(12f,7f,12f,13f);line(12f,17f,12f,17.1f)}
        }
        canvas.restore()
    }
}

class OrbitView(c: Context) : View(c) {
    override fun onDraw(canvas: Canvas) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG);val x=width*.5f;val y=height*.5f;val r=minOf(width,height)*.37f
        p.shader=RadialGradient(x-r*.4f,y-r*.45f,r*1.5f,intArrayOf(0xFFFFDCAC.toInt(),0xFFFFAC55.toInt(),0xFFF98023.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawCircle(x,y,r,p);p.shader=null
        p.color=0xAFFFFFFF.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=Ui.dp(context,1).toFloat();canvas.drawCircle(x,y,r,p)
        p.style=Paint.Style.FILL;p.color=0xEDFFFFFF.toInt();val path=Path().apply{moveTo(x-r*.16f,y-r*.30f);lineTo(x+r*.34f,y);lineTo(x-r*.16f,y+r*.30f);close()};canvas.drawPath(path,p)
    }
}

object Artwork {
    private val cache=android.util.LruCache<String,Bitmap>(20)
    fun load(a: AppCompatActivity, image: ImageView, item: DownloadItem) {
        val key=if(item.thumbnail.isNotBlank())item.thumbnail else item.uri
        if(key.isBlank())return
        image.tag=key
        cache.get(key)?.let{image.setImageBitmap(it);return}
        a.lifecycleScope.launch {
            val bitmap=withContext(Dispatchers.IO){runCatching {
                if(key.startsWith("content:")){
                    val retriever=android.media.MediaMetadataRetriever()
                    try{retriever.setDataSource(a,android.net.Uri.parse(key));retriever.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,180)}finally{retriever.release()}
                }else{
                    val url=URL(key);require(url.protocol in listOf("http","https"))
                    val connection=url.openConnection().apply{connectTimeout=6000;readTimeout=6000}
                    val data=connection.getInputStream().use { input ->
                        val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);var total=0
                        while(true){val n=input.read(buf);if(n<0)break;total+=n;require(total<=4_194_304);out.write(buf,0,n)};out.toByteArray()
                    }
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(data,0,data.size,bounds)
                    var sample=1;while(bounds.outWidth/sample>768||bounds.outHeight/sample>768)sample*=2
                    BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply{inSampleSize=sample})
                }
            }.getOrNull()}
            if(bitmap!=null){cache.put(key,bitmap);if(image.tag==key)image.setImageBitmap(bitmap)}
        }
    }
}
