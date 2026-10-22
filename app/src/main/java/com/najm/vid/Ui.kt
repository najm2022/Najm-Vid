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
    val bg = Color.rgb(10,17,18)
    val card = Color.rgb(20,30,31)
    val raised = Color.rgb(27,40,40)
    val line = Color.rgb(41,57,55)
    val mint = Color.rgb(178,244,205)
    val white = Color.rgb(241,246,242)
    val muted = Color.rgb(143,162,155)
    val danger = Color.rgb(255,172,159)
    fun dp(c: Context, v: Int) = (v*c.resources.displayMetrics.density).toInt()
    fun shape(c: Context, color: Int = card, radius: Int = 24, border: Boolean = false) = GradientDrawable().apply {
        setColor(color);cornerRadius=dp(c,radius).toFloat();if(border)setStroke(dp(c,1),line)
    }
    fun text(c: Context, value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) = TextView(c).apply {
        text=value;textSize=size;setTextColor(color);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
        gravity=Gravity.START or Gravity.CENTER_VERTICAL;includeFontPadding=false
        textDirection=View.TEXT_DIRECTION_FIRST_STRONG_RTL
    }
    fun column(c: Context, padding: Int=0) = LinearLayout(c).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(c,padding),dp(c,padding),dp(c,padding),dp(c,padding)) }
    fun row(c: Context) = LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
    fun space(c: Context, height: Int) = View(c).apply { layoutParams=LinearLayout.LayoutParams(1,dp(c,height)) }
    fun button(c: Context, label: String, primary: Boolean=true, action: () -> Unit): TextView = text(c,label,14f,if(primary)bg else mint,true).apply {
        gravity=Gravity.CENTER;background=shape(c,if(primary)mint else raised,16)
        setPadding(dp(c,14),0,dp(c,14),0);minHeight=dp(c,52);isClickable=true;isFocusable=true
        setOnClickListener { action() };contentDescription=label
    }
    fun pill(c: Context, label: String, selected: Boolean=false, action: (() -> Unit)?=null) = text(c,label,11f,if(selected)mint else muted,selected).apply {
        gravity=Gravity.CENTER;background=shape(c,if(selected)Color.rgb(35,63,48) else raised,12)
        setPadding(dp(c,12),dp(c,9),dp(c,12),dp(c,9));if(action!=null){isClickable=true;isFocusable=true;setOnClickListener{action()}}
    }
    fun iconButton(c: Context, name: String, label: String, action: () -> Unit) = Glyph(c,name,muted).apply {
        contentDescription=label;background=shape(c,raised,14);setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12))
        layoutParams=LinearLayout.LayoutParams(dp(c,48),dp(c,48));isClickable=true;isFocusable=true;setOnClickListener{action()}
    }
    fun sheet(a: AppCompatActivity): Pair<BottomSheetDialog,LinearLayout> {
        val dialog=BottomSheetDialog(a,R.style.Theme_NajmVid_Sheet)
        val content=column(a,22)
        val handle=View(a).apply { background=shape(a,line,4) }
        content.addView(handle,LinearLayout.LayoutParams(dp(a,38),dp(a,4)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(a,22)})
        val scroll=ScrollView(a).apply { isFillViewport=true;addView(content);isVerticalScrollBarEnabled=false }
        dialog.setContentView(scroll)
        dialog.setOnShowListener { dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background=shape(a,card,28) }
        return dialog to content
    }
    fun toast(c: Context, message: String) = Toast.makeText(c,message,Toast.LENGTH_SHORT).show()
    fun bytes(value: Long): String = if(value>=1_073_741_824) String.format(Locale.US,"%.1f GB",value/1_073_741_824.0) else String.format(Locale.US,"%.1f MB",value/1_048_576.0)
}

class Glyph(c: Context, private val symbol: String, var tint: Int = Ui.muted) : View(c) {
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
        val p=Paint(Paint.ANTI_ALIAS_FLAG);val x=width*.48f;val y=height*.50f;val size=minOf(width,height)*.47f
        p.style=Paint.Style.STROKE;p.strokeWidth=Ui.dp(context,1).toFloat();p.color=Color.rgb(43,77,63)
        for(i in 1..3)canvas.drawCircle(x,y,size*i/3,p)
        canvas.save();canvas.rotate(-18f,x,y);p.style=Paint.Style.FILL
        p.shader=LinearGradient(x-size,y-size,x+size,y+size,Ui.mint,Color.rgb(79,155,119),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(x-size*.5f,y-size*.61f,x+size*.5f,y+size*.61f,size*.22f,size*.22f,p);p.shader=null
        p.color=Ui.bg;val path=Path();path.moveTo(x-size*.12f,y-size*.21f);path.lineTo(x+size*.22f,y);path.lineTo(x-size*.12f,y+size*.21f);path.close();canvas.drawPath(path,p);canvas.restore()
        p.color=Ui.mint;canvas.drawCircle(x+size*.82f,y-size*.48f,Ui.dp(context,4).toFloat(),p)
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
