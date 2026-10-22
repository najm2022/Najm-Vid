package com.najm.vid

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MainActivity : AppCompatActivity() {
    private lateinit var root: GlassHost
    private lateinit var body: FrameLayout
    private lateinit var nav: LiquidNavigation
    internal lateinit var flow: DownloadFlow
        private set
    private var draft=""
    private var page="home"
    private var filter="all"
    private var search=""
    private var list: LinearLayout?=null
    private var savedCount: TextView?=null
    private var activeCount: TextView?=null
    private var qualityLabel: TextView?=null
    private var urlInput: EditText?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadStore.init(this)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.statusBarColor=Ui.bg;window.navigationBarColor=Ui.bg
        WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true}
        flow=DownloadFlow(this,{draft="";urlInput?.setText("");selectPage("downloads");Ui.toast(this,"بدأ التحميل")},{})
        root=GlassHost(this).apply{background=SceneDrawable();tag="app_root"}
        body=FrameLayout(this).apply{tag="page_body"};root.addView(body,FrameLayout.LayoutParams(-1,-1))
        nav=LiquidNavigation(this){index->selectPage(listOf("home","downloads","settings")[index])}
        root.addView(nav,FrameLayout.LayoutParams(-1,Ui.dp(this,74),Gravity.BOTTOM).apply{setMargins(Ui.dp(this@MainActivity,18),0,Ui.dp(this@MainActivity,18),Ui.dp(this@MainActivity,10))})
        ViewCompat.setOnApplyWindowInsetsListener(root){v,insets->val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,insets.getInsets(WindowInsetsCompat.Type.ime()).bottom));insets}
        setContentView(root)
        draft=savedInstanceState?.getString("draft").orEmpty()
        onBackPressedDispatcher.addCallback(this,object: androidx.activity.OnBackPressedCallback(true){override fun handleOnBackPressed(){if(page!="home")selectPage("home")else finish()}})
        page=savedInstanceState?.getString("page")?:intent.getStringExtra("page")?:"home";filter=savedInstanceState?.getString("filter")?:"all";selectPage(page)
        savedInstanceState?.getString("draft")?.let{urlInput?.setText(it)}
        lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){DownloadStore.items.collect{refreshRecords()}}}
        if(intent.action==Intent.ACTION_SEND&&savedInstanceState==null)handleShare(intent)
    }
    override fun onNewIntent(intent: Intent){super.onNewIntent(intent);setIntent(intent);if(intent.action==Intent.ACTION_SEND)handleShare(intent) else intent.getStringExtra("page")?.let{selectPage(it)}}
    private fun handleShare(intent: Intent){flow.inspect(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())}
    override fun onSaveInstanceState(outState: Bundle){outState.putString("page",page);outState.putString("filter",filter);outState.putString("draft",urlInput?.text?.toString()?:draft);super.onSaveInstanceState(outState)}

    private fun selectPage(next: String){
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
        root.isFocusableInTouchMode=true;root.requestFocus()
        urlInput?.let{draft=it.text.toString()}
        page=next;list=null;savedCount=null;activeCount=null;qualityLabel=null;urlInput=null;body.removeAllViews();renderNav()
        val scroll=ScrollView(this).apply{tag="page_scroll";isVerticalScrollBarEnabled=false;isFillViewport=true;clipToPadding=false;setPadding(0,0,0,Ui.dp(context,104))}
        val content=Ui.column(this,22);scroll.addView(content);body.addView(scroll)
        scroll.setOnScrollChangeListener { _,_,_,_,_->root.refreshBackdrop() }
        when(page){"downloads"->downloads(content);"settings"->settings(content);else->home(content)}
        refreshRecords()
    }
    private fun renderNav(){
        val entries=listOf(Triple("home","home","الرئيسية"),Triple("downloads","download","التحميلات"),Triple("settings","settings","الإعدادات"))
        if(nav.cells.childCount==0)entries.forEach{(id,icon,label)->
            val item=Ui.column(this).apply{gravity=Gravity.CENTER;tag="nav_$id";contentDescription=label;isClickable=true;isFocusable=true;setOnClickListener{selectPage(id)}}
            item.addView(Glyph(this,icon,Ui.muted),LinearLayout.LayoutParams(Ui.dp(this,21),Ui.dp(this,21)))
            item.addView(Ui.space(this,2));item.addView(Ui.text(this,label,10f,Ui.muted).apply{gravity=Gravity.CENTER})
            nav.cells.addView(item,LinearLayout.LayoutParams(0,-1,1f))
        }
        entries.forEachIndexed{index,(id,_,_)->
            val selected=page==id;val cell=nav.cells.getChildAt(index) as LinearLayout
            (cell.getChildAt(0) as Glyph).apply{tint=if(selected)Ui.ink else Ui.muted;invalidate()}
            (cell.getChildAt(2) as TextView).apply{setTextColor(if(selected)Ui.ink else Ui.muted);typeface=Ui.font(context,selected)}
        }
        nav.select(entries.indexOfFirst{it.first==page}.coerceAtLeast(0))
    }
    private fun header(box: LinearLayout, title: String, subtitle: String){
        val row=Ui.row(this);val words=Ui.column(this)
        words.addView(Ui.text(this,title,27f,bold=true));words.addView(Ui.space(this,7));words.addView(Ui.text(this,subtitle,12f,Ui.muted))
        row.addView(words,LinearLayout.LayoutParams(0,-2,1f));row.addView(Ui.iconButton(this,"paste","رابط جديد"){selectPage("home");urlInput?.requestFocus()});box.addView(row);box.addView(Ui.space(this,28))
    }
    private fun home(box: LinearLayout){
        val top=Ui.row(this).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR}
        top.addView(BrandMark(this).apply{contentDescription="شعار Najm Vid"},LinearLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40)))
        top.addView(Ui.text(this,"Najm Vid",21f,Ui.ink,true).apply{gravity=Gravity.LEFT;setPadding(Ui.dp(context,9),0,0,0);textDirection=View.TEXT_DIRECTION_LTR},LinearLayout.LayoutParams(0,-2,1f))
        top.addView(Ui.iconButton(this,"settings","الإعدادات"){selectPage("settings")});box.addView(top)
        box.addView(Ui.space(this,24))
        box.addView(Ui.text(this,"احفظ ما يستحق\nالمشاهدة.",if(resources.configuration.screenWidthDp<=360)29f else 33f,Ui.ink,true).apply{setLineSpacing(0f,.82f)})
        box.addView(Ui.space(this,20))
        val entry=Ui.column(this,18).apply{background=Ui.surface(this@MainActivity,28)}
        val title=Ui.row(this);title.addView(Ui.text(this,"رابط جديد",17f,bold=true),LinearLayout.LayoutParams(0,-2,1f));title.addView(Glyph(this,"download",Ui.mint),LinearLayout.LayoutParams(Ui.dp(this,21),Ui.dp(this,21)));entry.addView(title);entry.addView(Ui.space(this,15))
        val inputRow=Ui.row(this).apply{background=Ui.glass(this@MainActivity,28,GlassTone.FIELD);setPadding(Ui.dp(context,12),Ui.dp(context,4),Ui.dp(context,12),Ui.dp(context,4))}
        val paste=Ui.text(this,"لصق",12f,Ui.ink,true).apply{gravity=Gravity.CENTER;background=Ui.surface(this@MainActivity,24);minHeight=Ui.dp(context,48);minWidth=Ui.dp(context,48);tag="paste_button";isClickable=true;isFocusable=true;setOnClickListener{
            val clip=(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val text=if(clip!=null&&clip.itemCount>0)clip.getItemAt(0).coerceToText(this@MainActivity).toString() else ""
            if(text.isBlank())Ui.toast(this@MainActivity,"لا يوجد رابط في الحافظة")else urlInput?.setText(text)
        }}
        inputRow.addView(paste)
        val input=LiquidEditText(this).apply{
            tag="url_input";hint="ألصق رابط الفيديو هنا";textSize=12f;typeface=Ui.font(context);setTextColor(Ui.white);setHintTextColor(Ui.muted);background=null
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines=2;minHeight=Ui.dp(context,54);imeOptions=EditorInfo.IME_ACTION_GO;textDirection=View.TEXT_DIRECTION_FIRST_STRONG_RTL
            setOnEditorActionListener{_,action,_->if(action==EditorInfo.IME_ACTION_GO){inspectInput();true}else false}
        };input.setText(draft);urlInput=input;inputRow.addView(input,LinearLayout.LayoutParams(0,-2,1f));entry.addView(inputRow);entry.addView(Ui.space(this,13))
        entry.addView(Ui.action(this,"فحص الرابط",tag="inspect_button"){inspectInput()})
        entry.addView(Ui.space(this,12));qualityLabel=Ui.text(this,"",11f,Ui.muted).apply{gravity=Gravity.CENTER;minHeight=Ui.dp(context,32);isClickable=true;isFocusable=true;setOnClickListener{qualityPicker()}}
        entry.addView(qualityLabel);box.addView(entry)
        box.addView(Ui.space(this,16))
        val stats=Ui.row(this)
        fun stat(label: String, icon: String): TextView {
            val item=Ui.row(this);item.addView(Glyph(this,icon,Ui.muted),LinearLayout.LayoutParams(Ui.dp(this,17),Ui.dp(this,17)))
            val number=Ui.text(this,"0",17f,Ui.white,true).apply{setPadding(Ui.dp(context,8),0,Ui.dp(context,8),0)};item.addView(number);item.addView(Ui.text(this,label,11f,Ui.muted));stats.addView(item,LinearLayout.LayoutParams(0,-2,1f));return number
        }
        savedCount=stat("مكتمل","check");activeCount=stat("قيد التحميل","download");box.addView(stats);box.addView(Ui.space(this,12))
        val recent=Ui.row(this);recent.addView(Ui.text(this,"آخر التحميلات",17f,bold=true),LinearLayout.LayoutParams(0,-2,1f));recent.addView(Ui.text(this,"عرض الكل",11f,Ui.mint).apply{minHeight=Ui.dp(context,48);gravity=Gravity.CENTER;isClickable=true;isFocusable=true;setOnClickListener{selectPage("downloads")}});box.addView(recent)
        list=Ui.column(this);box.addView(list)
    }
    private fun inspectInput(){
        val text=urlInput?.text?.toString().orEmpty()
        if(VideoRules.extractUrl(text)==null){urlInput?.error="ألصق رابط فيديو صالحًا";return}
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(urlInput?.windowToken,0)
        flow.inspect(text,true)
    }
    private fun downloads(box: LinearLayout){
        header(box,"التحميلات","مكتبتك، بترتيبك.")
        val searchInput=LiquidEditText(this).apply{
            tag="library_search";hint="البحث في التحميلات";textSize=13f;typeface=Ui.font(context);setTextColor(Ui.ink);setHintTextColor(Ui.muted)
            background=Ui.glass(this@MainActivity,28,GlassTone.FIELD);setPadding(Ui.dp(context,17),0,Ui.dp(context,17),0)
            isSingleLine=true;minHeight=Ui.dp(context,52);setText(search)
            addTextChangedListener(object: android.text.TextWatcher{
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int){}
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int){search=s?.toString().orEmpty();refreshRecords()}
                override fun afterTextChanged(s: android.text.Editable?){}
            })
        };box.addView(searchInput);box.addView(Ui.space(this,14))
        val tabs=Ui.row(this)
        listOf("all" to "الكل","video" to "فيديو","audio" to "صوت","active" to "الجارية").forEach{(key,label)->tabs.addView(Ui.pill(this,label,filter==key){filter=key;selectPage("downloads")},LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=Ui.dp(this@MainActivity,6)})}
        box.addView(tabs);box.addView(Ui.space(this,22));list=Ui.column(this);box.addView(list)
    }
    private fun refreshRecords(){
        val items=DownloadStore.items.value;savedCount?.text=items.count{it.status=="completed"}.toString();activeCount?.text=items.count{it.active}.toString()
        qualityLabel?.text="الجودة: ${AppPreferences.label(AppPreferences.quality(this))}  ·  تغيير"
        val target=list?:return;target.removeAllViews()
        val visible=if(page=="home")items.take(2)else items.filter{when(filter){"active"->it.active;"video"->!it.audio;"audio"->it.audio;else->true} && (search.isBlank()||it.title.contains(search,true)||it.platform.contains(search,true))}
        if(visible.isEmpty()){
            val empty=Ui.column(this,20).apply{gravity=Gravity.CENTER;background=Ui.surface(this@MainActivity,24)}
            if(page=="downloads"){empty.addView(OrbitView(this),LinearLayout.LayoutParams(Ui.dp(this,120),Ui.dp(this,125)));empty.addView(Ui.space(this,12))}
            empty.addView(Ui.text(this,if(page=="downloads"&&search.isNotBlank())"لا توجد نتائج مطابقة" else if(filter=="active"&&page=="downloads")"لا توجد تحميلات جارية" else "مكتبتك تبدأ برابط",15f,bold=true).apply{gravity=Gravity.CENTER})
            empty.addView(Ui.space(this,9));empty.addView(Ui.text(this,"شارك مقطعًا إلى Najm Vid أو ألصق رابطه.",11f,Ui.muted).apply{gravity=Gravity.CENTER})
            target.addView(empty);return
        }
        visible.forEach{target.addView(recordCard(it));target.addView(Ui.space(this,12))}
    }
    private fun recordCard(item: DownloadItem): View {
        val card=Ui.column(this,14).apply{background=Ui.surface(this@MainActivity,24);isClickable=true;isFocusable=true;setOnClickListener{recordActions(item)}}
        val row=Ui.row(this)
        val frame=FrameLayout(this).apply{background=Ui.shape(this@MainActivity,Ui.raised,14);clipToOutline=true}
        frame.addView(Glyph(this,if(item.audio)"audio" else "play",Ui.mint),FrameLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24),Gravity.CENTER))
        val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP};frame.addView(image,FrameLayout.LayoutParams(-1,-1));if(!item.audio)Artwork.load(this,image,item)
        row.addView(frame,LinearLayout.LayoutParams(Ui.dp(this,65),Ui.dp(this,66)).apply{marginEnd=Ui.dp(this@MainActivity,12)})
        val words=Ui.column(this);words.addView(Ui.text(this,item.title,13f,bold=true).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END});words.addView(Ui.space(this,7));words.addView(Ui.text(this,"${item.platform}  ·  ${item.quality}",11f,Ui.muted));row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
        if(item.status=="completed")row.addView(Glyph(this,"check",Ui.mint),LinearLayout.LayoutParams(Ui.dp(this,18),Ui.dp(this,18)))
        card.addView(row);card.addView(Ui.space(this,13))
        val status=when(item.status){"queued"->"في قائمة الانتظار";"loading"->"جارٍ التحميل  ${item.progress}%";"saving"->"جارٍ الحفظ في مكتبتك";"completed"->"تم الحفظ"+(if(item.bytes>0)"  ·  ${Ui.bytes(item.bytes)}" else  "");"cancelled"->"تم الإلغاء";"interrupted"->"توقف التحميل  ·  اضغط لإعادته";else->"لم يكتمل التحميل  ·  اضغط للمحاولة"}
        card.addView(Ui.text(this,status,11f,if(item.status in setOf("failed","interrupted"))Ui.danger else if(item.status=="completed")Ui.mint else Ui.muted))
        if(item.active){card.addView(Ui.space(this,9));card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=item.progress;isIndeterminate=item.status!="loading";progressTintList=android.content.res.ColorStateList.valueOf(Ui.accent);indeterminateTintList=android.content.res.ColorStateList.valueOf(Ui.accent)},LinearLayout.LayoutParams(-1,Ui.dp(this,4)))}
        return card
    }
    private fun recordActions(item: DownloadItem){
        val (sheet,box)=Ui.sheet(this);box.addView(Ui.text(this,item.title,20f,bold=true).apply{maxLines=3});box.addView(Ui.space(this,8));box.addView(Ui.text(this,"${item.platform}  ·  ${item.quality}",12f,Ui.muted));box.addView(Ui.space(this,22))
        fun action(label: String, primary: Boolean=true, callback:()->Unit){box.addView(Ui.button(this,label,primary){sheet.dismiss();callback()});box.addView(Ui.space(this,10))}
        when{
            item.status=="completed"->{action("فتح المقطع"){runCatching{contentResolver.openFileDescriptor(android.net.Uri.parse(item.uri),"r")?.close();DownloadActions.open(this,item)}.onFailure{Ui.toast(this,"الملف غير متاح؛ ربما نُقل أو حُذف")}};action("مشاركة الملف",false){runCatching{DownloadActions.share(this,item)}.onFailure{Ui.toast(this,"تعذرت مشاركة الملف")}}}
            item.status=="saving"->box.addView(Ui.text(this,"نُكمل حفظ الملف…",14f,Ui.muted))
            item.active->action("إلغاء التحميل",false){DownloadActions.cancel(this,item.id)}
            else->{action("إعادة المحاولة"){runCatching{DownloadActions.retry(this,item)}.onFailure{Ui.toast(this,"تعذر بدء التحميل")}};if(item.error.isNotBlank())action("عرض سبب التعثر",false){flow.error(item.error){DownloadActions.retry(this,item)}}}
        }
        if(!item.active)action("إزالة من السجل",false){DownloadStore.remove(item.id)}
        sheet.show()
    }
    private fun settings(box: LinearLayout){
        header(box,"على طريقتك","خيارات قليلة. تجربة تناسبك.")
        val quality=Ui.column(this,20).apply{background=Ui.surface(this@MainActivity,28);tag="quality_setting";isClickable=true;isFocusable=true;setOnClickListener{qualityPicker()}}
        quality.addView(Ui.text(this,"جودة المشاركة",16f,bold=true));quality.addView(Ui.space(this,9));quality.addView(Ui.text(this,AppPreferences.label(AppPreferences.quality(this)),14f,Ui.mint));quality.addView(Ui.space(this,11));quality.addView(Ui.text(this,"عند المشاركة من تطبيق آخر، نستخدم اختيارك تلقائيًا عندما يكون متاحًا.",12f,Ui.muted));box.addView(quality);box.addView(Ui.space(this,16))
        fun setting(title: String, subtitle: String, callback:()->Unit){val card=Ui.column(this,20).apply{background=Ui.surface(this@MainActivity,24);isClickable=true;isFocusable=true;setOnClickListener{callback()}};card.addView(Ui.text(this,title,15f,bold=true));card.addView(Ui.space(this,8));card.addView(Ui.text(this,subtitle,12f,Ui.muted));box.addView(card);box.addView(Ui.space(this,14))}
        setting("الإشعارات","تابع تقدم التحميل واكتماله."){startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
        setting("مكان الحفظ","الفيديو: \u2066Movies/NV_TikTok\u2069\nالصوت: \u2066Music/NV_TikTok\u2069"){Ui.toast(this,"يتغير اسم المجلد حسب المنصة، وتظهر المقاطع في معرض جهازك")}
        setting("مسح سجل التحميلات","تنظيف قائمة التحميلات المكتملة والمتوقفة."){AlertDialog.Builder(this).setTitle("مسح السجل؟").setMessage("ستُمسح العناصر المكتملة والمتوقفة من القائمة. تبقى الملفات المحفوظة على جهازك.").setPositiveButton("مسح السجل"){_,_->DownloadStore.clearFinished();Ui.toast(this,"تم مسح السجل")}.setNegativeButton("تراجع",null).show()}
        box.addView(Ui.space(this,20));box.addView(Ui.text(this,"NAJM VID  /  ${BuildConfig.VERSION_NAME}",11f,Ui.mint,true).apply{gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_LTR});box.addView(Ui.space(this,9));box.addView(Ui.text(this,"مساحة لما تختار الاحتفاظ به.",11f,Ui.muted).apply{gravity=Gravity.CENTER})
    }
    private fun qualityPicker(){
        val (sheet,box)=Ui.sheet(this);box.addView(Ui.text(this,"جودتك المفضلة",22f,bold=true));box.addView(Ui.space(this,10));box.addView(Ui.text(this,"نحفظ اختيارك للمشاركات القادمة.",13f,Ui.muted));box.addView(Ui.space(this,20))
        listOf("ask","best","1080","720","audio").forEach{key->box.addView(Ui.button(this,AppPreferences.label(key),AppPreferences.quality(this)==key){AppPreferences.setQuality(this,key);sheet.dismiss();if(page=="settings")selectPage("settings")else refreshRecords()}.apply{tag="quality_$key"});box.addView(Ui.space(this,10))};sheet.show()
    }
}
