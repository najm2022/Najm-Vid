package com.najm.vid

import android.Manifest
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.*

class DownloadFlow(private val a: AppCompatActivity, private val onQueued: () -> Unit, private val onClosed: () -> Unit) : DefaultLifecycleObserver {
    private var dialog: Dialog?=null
    private var work: Job?=null
    private var pending: (() -> Unit)?=null
    private val permission=a.registerForActivityResult(ActivityResultContracts.RequestPermission()) { val next=pending;pending=null;next?.invoke() }
    init { a.lifecycle.addObserver(this) }

    fun inspect(raw: String, manual: Boolean=false, allowDuplicate: Boolean=false) {
        val url=VideoRules.extractUrl(raw)
        if(url==null){error("ألصق رابط فيديو صالحًا يبدأ بـ https://") { onClosed() };return}
        DownloadStore.init(a)
        val saved=DownloadStore.items.value.firstOrNull{it.url==url&&it.status=="completed"}
        if(saved!=null&&!allowDuplicate){
            dialog?.dismiss();val (sheet,box)=Ui.sheet(a);dialog=sheet
            box.addView(Ui.text(a,"المقطع في مكتبتك",22f,bold=true));box.addView(Ui.space(a,12));box.addView(Ui.text(a,saved.title,14f,Ui.muted))
            box.addView(Ui.space(a,22));box.addView(Ui.button(a,"فتح المقطع") { runCatching{DownloadActions.open(a,saved)}.onFailure{Ui.toast(a,"تعذر فتح الملف المحفوظ")};sheet.dismiss();onClosed() })
            box.addView(Ui.space(a,10));box.addView(Ui.button(a,"تحميل نسخة أخرى",false){sheet.dismiss();inspect(url,manual,true)})
            sheet.setOnCancelListener{onClosed()};sheet.show();return
        }
        work?.cancel();dialog?.dismiss()
        val (loading,content)=Ui.sheet(a);dialog=loading
        content.addView(Ui.text(a,"لحظة، نقرأ الرابط",23f,bold=true));content.addView(Ui.space(a,10))
        content.addView(Ui.text(a,"نبحث عن المقطع والجودات المتاحة.",14f,Ui.muted));content.addView(Ui.space(a,24))
        content.addView(ProgressBar(a).apply{indeterminateTintList=android.content.res.ColorStateList.valueOf(Ui.mint)},LinearLayout.LayoutParams(Ui.dp(a,32),Ui.dp(a,32)).apply{gravity=Gravity.CENTER_HORIZONTAL})
        content.addView(Ui.space(a,24));content.addView(Ui.button(a,"إلغاء",false){work?.cancel();loading.dismiss();onClosed()})
        loading.setOnCancelListener{work?.cancel();onClosed()};loading.show()
        work=a.lifecycleScope.launch {
            try {
                val info=withContext(Dispatchers.IO){DownloadEngine.prepare(a.applicationContext);YoutubeDL.getInstance().getInfo(DownloadEngine.request(url))}
                val heights=info.formats.orEmpty().filter{it.height>0&&it.vcodec!="none"}.map{it.height}.distinct().sortedDescending()
                loading.dismiss()
                val pref=AppPreferences.quality(a)
                if(manual||(pref=="ask"&&heights.size>1))preview(url,info,heights)
                else {
                    val limit=pref.toIntOrNull()?:0
                    val height=if(limit>0&&heights.isNotEmpty())heights.filter{it<=limit}.maxOrNull() else 0
                    if(limit>0&&heights.isNotEmpty()&&height==null)preview(url,info,heights)
                    else queue(url,info,height?:0,pref=="audio")
                }
            }catch(e: CancellationException){throw e}
            catch(e: Exception){loading.dismiss();error(e.message?:"تعذر قراءة الرابط"){inspect(url,manual,allowDuplicate)}}
        }
    }

    internal fun preview(url: String, info: VideoInfo, heights: List<Int>) {
        val (sheet,box)=Ui.sheet(a);dialog=sheet
        val header=Ui.row(a);header.addView(Ui.text(a,"معاينة الفيديو",23f,bold=true),LinearLayout.LayoutParams(0,-2,1f));header.addView(Ui.iconButton(a,"close","إغلاق"){sheet.dismiss();onClosed()});box.addView(header)
        box.addView(Ui.space(a,14))
        val item=DownloadItem(url=url,title=info.title?:"مقطع فيديو",thumbnail=info.thumbnail.orEmpty())
        if(item.thumbnail.isNotBlank()){
            val image=ImageView(a).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=Ui.shape(a,Ui.raised,18);clipToOutline=true}
            box.addView(image,LinearLayout.LayoutParams(-1,Ui.dp(a,146)));Artwork.load(a,image,item);box.addView(Ui.space(a,16))
        }
        box.addView(Ui.pill(a,VideoRules.platform(url),true));box.addView(Ui.space(a,10));box.addView(Ui.text(a,item.title,17f,bold=true).apply{maxLines=3})
        box.addView(Ui.space(a,10));box.addView(Ui.text(a,"اختر الجودة",17f,Ui.ink,true));box.addView(Ui.space(a,12))
        val group=RadioGroup(a).apply{orientation=RadioGroup.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        var selectedHeight=0;var audio=false
        val choices=if(heights.isEmpty())listOf(0) else heights
        val pref=AppPreferences.quality(a);val limit=pref.toIntOrNull()
        val preferred=if(limit!=null)choices.firstOrNull{it<=limit}?:choices.first() else choices.first()
        choices.forEach{height ->
            val size=info.formats.orEmpty().filter{it.height==height}.maxOfOrNull{maxOf(it.fileSize,it.fileSizeApproximate)}?:0
            val label=(if(height==0)"الجودة الأصلية" else "${height}p")+(if(size>0)"  ·  نحو ${Ui.bytes(size)}" else "")
            val radio=RadioButton(a).apply{
                id=View.generateViewId();text=label;setTextColor(Ui.white);textSize=14f;buttonTintList=android.content.res.ColorStateList.valueOf(Ui.mint)
                minHeight=Ui.dp(a,56);background=Ui.glass(a,20,GlassTone.FIELD)
                setPadding(Ui.dp(a,12),Ui.dp(a,6),Ui.dp(a,12),Ui.dp(a,6))
                setOnCheckedChangeListener{_,checked->if(checked){selectedHeight=height;audio=false}}
            };group.addView(radio,RadioGroup.LayoutParams(-1,-2).apply{bottomMargin=Ui.dp(a,8)});if(height==preferred&&pref!="audio")radio.isChecked=true
        }
        val mp3=RadioButton(a).apply{id=View.generateViewId();text="صوت فقط  ·  MP3";setTextColor(Ui.white);textSize=14f;minHeight=Ui.dp(a,56);background=Ui.glass(a,20,GlassTone.FIELD);setPadding(Ui.dp(a,12),Ui.dp(a,6),Ui.dp(a,12),Ui.dp(a,6));buttonTintList=android.content.res.ColorStateList.valueOf(Ui.mint);setOnCheckedChangeListener{_,checked->if(checked){audio=true;selectedHeight=0}}}
        group.addView(mp3);if(pref=="audio")mp3.isChecked=true;box.addView(group)
        box.addView(Ui.space(a,18));box.addView(Ui.action(a,"بدء التحميل","download"){sheet.dismiss();queue(url,info,selectedHeight,audio)})
        box.addView(Ui.space(a,14));box.addView(Ui.text(a,"الحفظ في: \u2066Movies/NV_${VideoRules.platform(url)}\u2069 أو \u2066Music/NV_${VideoRules.platform(url)}\u2069",11f,Ui.muted))
        sheet.setOnCancelListener{onClosed()};sheet.show()
    }

    private fun queue(url: String, info: VideoInfo, height: Int, audio: Boolean) {
        val action={
            try{DownloadActions.enqueue(a,url,height,audio,info.title.orEmpty(),info.thumbnail.orEmpty());onQueued()}
            catch(e: Exception){error(e.message?:"تعذر بدء التحميل"){queue(url,info,height,audio)}}
            Unit
        }
        val p=a.getSharedPreferences("ui_permissions",Context.MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(a,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!p.getBoolean("asked",false)){
            p.edit().putBoolean("asked",true).apply();pending=action;permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else action()
    }

    fun error(details: String, retry: () -> Unit) {
        dialog?.dismiss();val (sheet,box)=Ui.sheet(a);dialog=sheet
        val friendly=when{
            details.contains("timed out",true)||details.contains("network",true)||details.contains("resolve",true)->"تعذر الاتصال بالمصدر. تحقق من الإنترنت وأعد المحاولة."
            details.contains("private",true)||details.contains("login",true)||details.contains("sign in",true)->"هذا المقطع يحتاج إذن وصول أو تسجيل دخول."
            details.contains("unsupported",true)->"الرابط غير مدعوم حاليًا. جرّب رابط المقطع الأصلي."
            details.contains("403")->"المصدر رفض الوصول إلى هذا المقطع."
            details.contains("DRM",true)->"هذا المقطع محمي ولا يمكن تحميله."
            details.startsWith("ألصق")->details
            else->"تعذر إكمال العملية. يمكنك إعادة المحاولة أو عرض التفاصيل."
        }
        box.addView(Ui.text(a,"لم يكتمل التحميل",22f,bold=true));box.addView(Ui.space(a,12));box.addView(Ui.text(a,friendly,14f,Ui.muted));box.addView(Ui.space(a,24))
        box.addView(Ui.button(a,"إعادة المحاولة"){sheet.dismiss();retry()});box.addView(Ui.space(a,10))
        box.addView(Ui.button(a,"تفاصيل الخطأ",false){AlertDialog.Builder(a).setTitle("تفاصيل الخطأ").setMessage(details.take(6000)).setPositiveButton("نسخ"){_,_->(a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("خطأ التحميل",details));Ui.toast(a,"تم النسخ")}.setNegativeButton("إغلاق",null).show()})
        box.addView(Ui.space(a,10));box.addView(Ui.button(a,"إغلاق",false){sheet.dismiss();onClosed()});sheet.setOnCancelListener{onClosed()};sheet.show()
    }
    override fun onDestroy(owner: LifecycleOwner) {work?.cancel();dialog?.dismiss()}
}

class ShareActivity : AppCompatActivity() {
    private lateinit var flow: DownloadFlow
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        flow=DownloadFlow(this,{Ui.toast(this,"بدأ التحميل في الخلفية");finish()},{finish()})
        if(savedInstanceState==null)handle(intent) else handle(intent)
    }
    override fun onNewIntent(intent: Intent){super.onNewIntent(intent);setIntent(intent);handle(intent)}
    private fun handle(source: Intent){flow.inspect(source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())}
}
