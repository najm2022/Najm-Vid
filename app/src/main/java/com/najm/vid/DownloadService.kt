package com.najm.vid

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val queue=Mutex()
    private var pending=0
    private val cancelled=ConcurrentHashMap.newKeySet<String>()
    @Volatile private var processId: String?=null
    private lateinit var notifications: NotificationManager
    override fun onCreate(){
        super.onCreate();DownloadStore.init(this)
        notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"تحميلات Najm Vid",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="cancel"){
            val id=intent.getStringExtra("id")
            if(id!=null&&DownloadStore.get(id)?.status in listOf("queued","loading")){
                cancelled.add(id);DownloadStore.update(id){it.copy(status="cancelled")}
                if(processId==id)scope.launch{YoutubeDL.getInstance().destroyProcessById(id)}
            }
            if(pending==0)stopSelf(startId)
            return START_NOT_STICKY
        }
        val url=intent?.getStringExtra("url")?.let(VideoRules::extractUrl)
        if(url==null){if(pending==0)stopSelf(startId);return START_NOT_STICKY}
        val item=DownloadItem(url=url,title=intent.getStringExtra("title")?.takeIf{it.isNotBlank()}?:"مقطع من ${VideoRules.platform(url)}",
            height=intent.getIntExtra("height",0),audio=intent.getBooleanExtra("audio",false),thumbnail=intent.getStringExtra("thumbnail").orEmpty())
        DownloadStore.add(item);pending++
        ServiceCompat.startForeground(this,1,notification("جارٍ تجهيز التحميل",-1,processId?:item.id),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch{
            try{queue.withLock{if(!cancelled.contains(item.id))download(item)}}
            finally{withContext(NonCancellable+Dispatchers.Main){pending--;if(pending==0){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}}
        }
        return START_NOT_STICKY
    }
    private fun download(item: DownloadItem){
        val id=item.id;val platform=item.platform
        val workDir=File(cacheDir,"downloads/$id")
        processId=id
        val wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NajmVid:download")
        try{
            wakeLock.acquire(3*60*60*1000L);check(workDir.mkdirs()){ "تعذر تجهيز مساحة التحميل" }
            DownloadStore.update(id){it.copy(status="loading")};progress("جارٍ قراءة المقطع",-1,id)
            DownloadEngine.prepare(applicationContext)
            check(!cancelled.contains(id)){"cancelled"}
            val request=DownloadEngine.request(item.url).apply{
                addOption("--no-mtime");addOption("--windows-filenames")
                addOption("-o","${workDir.absolutePath}/NV_%(title).60B_%(id).40B.%(ext)s")
                if(item.audio){addOption("-x");addOption("--audio-format","mp3")}
                else{addOption("-f",VideoRules.format(item.height));addOption("--merge-output-format","mp4");addOption("--remux-video","mp4")}
            }
            var last=0L
            YoutubeDL.getInstance().execute(request,id,false){value,_,_->
                val now=System.currentTimeMillis()
                if(now-last>=800){last=now;val percent=value.toInt().coerceIn(0,100)
                    if(!cancelled.contains(id)){DownloadStore.update(id){it.copy(progress=percent)};progress("${item.title.take(45)}  ·  $percent%",percent,id)}
                }
            }
            check(!cancelled.contains(id)){"cancelled"}
            val extensions=if(item.audio)setOf("mp3")else setOf("mp4","mkv","webm","mov","m4v")
            val file=workDir.listFiles()?.filter{it.isFile&&it.extension.lowercase() in extensions}?.maxByOrNull{it.length()}?:error("لم ينتج المصدر ملف فيديو أو صوت صالحًا")
            DownloadStore.update(id){it.copy(status="saving",progress=100)};progress("جارٍ حفظ ${item.title.take(45)}",-1,null)
            val uri=MediaPublisher.publish(applicationContext,file,platform,item.audio)
            val mime=if(item.audio)"audio/mpeg" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())?:"video/mp4"
            DownloadStore.update(id){it.copy(status="completed",progress=100,uri=uri.toString(),mime=mime,bytes=file.length())}
            val directory=if(item.audio)"Music" else "Movies"
            saveStatus("تم الحفظ: $directory/NV_$platform/${file.name}")
            val open=PendingIntent.getActivity(this,id.hashCode(),Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            complete(id,"تم الحفظ في NV_$platform",open,DownloadStore.get(id))
        }catch(e: Exception){
            if(cancelled.contains(id)){DownloadStore.update(id){it.copy(status="cancelled")};saveStatus("تم إلغاء التحميل")}
            else{
                Log.e("NajmVid","Download failed",e);val message=e.message?:e.javaClass.simpleName
                DownloadStore.update(id){it.copy(status="failed",error=message)};saveStatus("فشل التحميل: $message")
                complete(id,"لم يكتمل التحميل؛ اضغط لإعادة المحاولة",appIntent(),null)
            }
        }finally{processId=null;cancelled.remove(id);if(wakeLock.isHeld)wakeLock.release();workDir.deleteRecursively()}
    }
    private fun saveStatus(message: String){getSharedPreferences("status",MODE_PRIVATE).edit().putString("last",message).apply()}
    private fun appIntent()=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("page","downloads"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(message: String,percent: Int,id: String?): Notification {
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_download).setContentTitle("Najm Vid")
            .setContentText(message).setContentIntent(appIntent()).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,percent.coerceAtLeast(0),percent<0)
        if(id!=null){val cancel=PendingIntent.getService(this,id.hashCode(),Intent(this,DownloadService::class.java).setAction("cancel").putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);builder.addAction(R.drawable.ic_download,"إلغاء",cancel)}
        return builder.build()
    }
    @SuppressLint("MissingPermission") private fun progress(message: String,percent: Int,id: String?){notifications.notify(1,notification(message,percent,id))}
    @SuppressLint("MissingPermission") private fun complete(id: String,message: String,contentIntent: PendingIntent,item: DownloadItem?){
        if(!notifications.areNotificationsEnabled())return
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_download).setContentTitle("Najm Vid").setContentText(message).setContentIntent(contentIntent).setAutoCancel(true)
        if(item!=null){val share=Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM,android.net.Uri.parse(item.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"مشاركة المقطع");builder.addAction(R.drawable.ic_download,"مشاركة",PendingIntent.getActivity(this,id.hashCode()+1,share,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))}
        notifications.notify(id.hashCode(),builder.build())
    }
    override fun onDestroy(){processId?.let{YoutubeDL.getInstance().destroyProcessById(it)};scope.cancel();super.onDestroy()}
    companion object{private const val CHANNEL="downloads"}
}
