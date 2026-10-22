package com.najm.vid

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.*
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveGlassTest {
    @androidx.test.filters.SdkSuppress(minSdkVersion=33)
    @Test fun touchAnimationSettlesAndNavigationLensMoves(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        fun setting(value: String){i.uiAutomation.executeShellCommand("settings put global animator_duration_scale $value").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{input->input.readBytes()}}}
        setting("1")
        try{
            ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
                await(scenario){GlassTouch.motion(it)}
                lateinit var button: View
                lateinit var material: GlassDrawable
                lateinit var nav: LiquidNavigation
                var right=0f
                scenario.onActivity{a->
                    button=a.window.decorView.findViewWithTag("inspect_button");material=button.background as GlassDrawable
                    nav=a.window.decorView.findViewWithTag("liquid_nav");right=nav.selectedCenter
                    material.contact(button,button.width*.25f,button.height*.5f,true)
                }
                await(scenario){material.pressure>.8f && button.scaleX>1.01f}
                scenario.onActivity{material.contact(button,button.width*.25f,button.height*.5f,false);nav.select(2)}
                await(scenario){material.pressure<.01f && kotlin.math.abs(button.scaleX-1f)<.001f && nav.selectedCenter<right*.5f}
            }
        }finally{setting("0")}
    }
    @Test fun liveContentUpdatesWithoutCapturingControlsAndTouchReturnsToRest(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use{scenario->
            lateinit var stage: GlassHost
            lateinit var nav: LiquidNavigation
            lateinit var fixture: View
            var redSerial=0L
            val red=Color.rgb(218,65,43);val blue=Color.rgb(35,91,224)
            scenario.onActivity{a->
                stage=a.window.decorView.findViewWithTag("app_root")
                nav=a.window.decorView.findViewWithTag("liquid_nav")
                val body=a.window.decorView.findViewWithTag<FrameLayout>("page_body")
                fixture=View(a).apply{setBackgroundColor(red)}
                body.addView(fixture,FrameLayout.LayoutParams(-1,Ui.dp(a,180),Gravity.BOTTOM))
                stage.refreshBackdrop()
            }
            await(scenario){sample(stage,nav)==red}
            scenario.onActivity{
                redSerial=stage.frame!!.serial
                assertTrue((nav.background as GlassDrawable).usesLiveSource)
                if(Build.VERSION.SDK_INT>=33)assertTrue((nav.background as GlassDrawable).usesGpuLens)
            }
            capture(context,"07-live-glass-red.png")
            scenario.onActivity{fixture.setBackgroundColor(blue)}
            await(scenario){stage.frame!!.serial>redSerial && sample(stage,nav)==blue}
            capture(context,"08-live-glass-blue.png")
            instrumentation.waitForIdleSync();SystemClock.sleep(100)
            var idleSerial=0L
            scenario.onActivity{idleSerial=stage.captureCount}
            SystemClock.sleep(220)
            scenario.onActivity{assertEquals("Idle content must not be recaptured continuously",idleSerial,stage.captureCount)}
            scenario.onActivity{a->
                val button=a.window.decorView.findViewWithTag<View>("inspect_button")
                val material=button.background as GlassDrawable
                val pos=IntArray(2);button.getLocationOnScreen(pos)
                val time=SystemClock.uptimeMillis()
                fun send(action: Int){
                    val e=MotionEvent.obtain(time,time,action,pos[0]+button.width/2f,pos[1]+button.height/2f,0)
                    e.offsetLocation(-pos[0].toFloat(),-pos[1].toFloat());button.dispatchTouchEvent(e);e.recycle()
                }
                send(MotionEvent.ACTION_DOWN)
                if(!GlassTouch.motion(a))assertEquals(1f,material.pressure,0f)
                send(MotionEvent.ACTION_CANCEL)
                if(!GlassTouch.motion(a))assertEquals(0f,material.pressure,0f)
                nav.select(2,false);val left=nav.selectedCenter
                nav.select(0,false);assertTrue("Selection lens must move across tabs",nav.selectedCenter>left)
                assertEquals("Changing a control must not contaminate its source",blue,sample(stage,nav))
            }
            await(scenario){(it.window.decorView.findViewWithTag<View>("inspect_button").background as GlassDrawable).pressure<.01f}
        }
    }

    private fun sample(stage: GlassHost,nav: View): Int {
        val frame=stage.frame?:return 0
        val a=IntArray(2);val b=IntArray(2);stage.getLocationInWindow(a);nav.getLocationInWindow(b)
        val x=((b[0]-a[0]+nav.width/2f)*frame.bitmap.width/frame.width).toInt().coerceIn(0,frame.bitmap.width-1)
        val y=((b[1]-a[1]+nav.height/2f)*frame.bitmap.height/frame.height).toInt().coerceIn(0,frame.bitmap.height-1)
        return frame.bitmap.getPixel(x,y)
    }
    private fun await(scenario: ActivityScenario<MainActivity>,check: (MainActivity)->Boolean){
        val end=SystemClock.elapsedRealtime()+5000
        while(SystemClock.elapsedRealtime()<end){var ok=false;scenario.onActivity{ok=check(it)};if(ok)return;SystemClock.sleep(40)}
        fail("Timed out waiting for live glass to update")
    }
    private fun capture(context: Context,name: String){
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync();SystemClock.sleep(120)
        val bitmap=i.uiAutomation.takeScreenshot()!!
        val values=android.content.ContentValues().apply{
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/NajmVidPreview")
            put(android.provider.MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        context.contentResolver.update(uri,android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.IS_PENDING,0)},null,null)
    }
}
