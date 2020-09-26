package com.peanut.poi.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private var logger:Logger? = null
    private val sb = java.lang.StringBuilder()
    lateinit var scv:ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        floatingActionButton.setOnClickListener {
            startActivity(Intent(this,MainActivity2::class.java))
        }
        logger = Logger(this,logcat)
        scv = sv
        val stringBuilder = StringBuilder()
        stringBuilder.append("========== 设备信息 ==========").append("\n")
        stringBuilder.append("[VERSION]  :" + BuildConfig.VERSION_NAME).append("\n")
        stringBuilder.append("[ID]       :" + Build.ID).append("\n")
        stringBuilder.append("[DISPLAY]  :" + Build.DISPLAY).append("\n")
        stringBuilder.append("[PRODUCT]  :" + Build.PRODUCT).append("\n")
        stringBuilder.append("[DEVICE]   :" + Build.DEVICE).append("\n")
        stringBuilder.append("[BOARD]    :" + Build.BOARD).append("\n")
        stringBuilder.append("[MANUFA...]:" + Build.MANUFACTURER).append("\n")
        stringBuilder.append("[HARDWARE] :" + Build.HARDWARE).append("\n")
        stringBuilder.append("[BOOT.]    :" + Build.BOOTLOADER).append("\n")
        stringBuilder.append("[MODEL]    :" + Build.MODEL).append("\n")
        stringBuilder.append("[BRAND]    :" + Build.BRAND).append("\n")
        stringBuilder.append("[API]      :" + Build.VERSION.SDK_INT).append("\n")
        stringBuilder.append("[ABI]      :").append(Build.SUPPORTED_ABIS[0] ?: "").append("\n")
        for ((i, abi) in Build.SUPPORTED_ABIS.withIndex()) {
            if (i == 0) continue
            stringBuilder.append("            $abi").append("\n")
        }
        logger?.v(stringBuilder.toString())
        logger?.v("========== 流程日志 ==========")
        logger?.w("更新日志:\n更改选择器\n请不要将你的excel文件删除")
        logger?.v("未找到题库文件! 请选择一个以继续：")
        logger?.v("检查必备（存储）权限")
        if (PermissionRequester.check(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)){
            try{
                start()
            }catch (e:Exception){
                val a = StringWriter()
                e.printStackTrace(PrintWriter(a))
                logger?.e(a.toString())
            }
        }else{
            PermissionRequester.request(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun openFileSelector() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, 2)
    }

    private fun start(){
        logger?.v("权限申请通过，请选择excel文件！")
        openFileSelector()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (PermissionRequester.check(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    start()
                } else {
                    PermissionRequester.request(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            2 -> {
                if (data == null || data.data == null) {
                    logger?.e("未选择文件")
                    return
                }
                var fileName = data.data.toString().substring(data.data.toString().lastIndexOf("/")+1)
                if (fileName.contains(".").not()){
                    data.data?.let { returnUri ->
                        contentResolver.query(returnUri, null, null, null, null)
                    }?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst())
                        fileName = cursor.getString(nameIndex)
                    }
                }
                logger?.v("========== 实时日志 ==========")
                logger?.v("开始处理...")
                object : Thread() {
                    override fun run() {
                        val sharedDB = contentResolver.openFileDescriptor(data.data!!, "r")
                        val fd = sharedDB!!.fileDescriptor
                        val fis = FileInputStream(fd)
                        val a = this@MainActivity.getExternalFilesDir("excel_temp").toString() + "/$fileName"
                        val fos = FileOutputStream(a)
                        copyFileUseStream(fis, fos)
                        val result = ExcelParser().run(this@MainActivity, this@MainActivity.getExternalFilesDir("excel_temp").toString() + "/$fileName", logger)
                        if (result) {
                            Handler(this@MainActivity.mainLooper).post {
                                logger?.v("处理完成！")
                                setResult(200, Intent().setData(FileProvider.getUriForFile(this@MainActivity, "com.peanut.poi.android", File(Objects.requireNonNull(this@MainActivity.getExternalFilesDir("Database")).toString() + "/parse.db"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                            }
                            var i = 4
                            while (i > 0) {
                                Handler(this@MainActivity.mainLooper).post {
                                    logger?.w("program will exit in ${--i} second...")
                                }
                                sleep(1000)
                            }
                            Handler(this@MainActivity.mainLooper).post {
                                string2File(sb.toString(), File(this@MainActivity.getExternalFilesDir("log").toString() + "/log.txt"))
                                this@MainActivity.finish()
                            }
                        } else {
                            Handler(this@MainActivity.mainLooper).post {
                                logger?.e("处理终止！")
                                setResult(-1)
                                logger?.e("按返回键退出！")
                                string2File(sb.toString(), File(this@MainActivity.getExternalFilesDir("log").toString() + "/log.txt"))
                            }
                        }
                    }
                }.start()
            }
        }
    }


    fun copyFileUseStream(fileInputStream: FileInputStream, fileOutputStream: FileOutputStream) {
        val buffer = ByteArray(1024)
        var byteRead: Int
        while (-1 != fileInputStream.read(buffer).also { byteRead = it }) {
            fileOutputStream.write(buffer, 0, byteRead)
        }
        fileInputStream.close()
        fileOutputStream.flush()
        fileOutputStream.close()
    }

    inner class Logger internal constructor(private var context: Context, private val logcat:LinearLayout) {
        private var textView: TextView? = null

        fun v(text: String) {
            textView = TextView(context)
            textView!!.typeface = Typeface.createFromAsset(assets, "ubuntu_mono.ttf")
            textView!!.text = text
            textView!!.textSize = 8f
            setColor(Color.WHITE)
            logcat.addView(textView)
            sb.append(text).append("\n")
        }

        fun e(string: String) {
            v(string)
            setColor(Color.rgb(255, 0, 0))
        }

        fun w(string: String) {
            v(string)
            setColor(Color.rgb(0, 255, 0))
        }

        private fun setColor(color: Int) = textView?.setTextColor(color)

        fun update(string:String){
            textView?.text = string
        }

        init {
            logcat.removeAllViews()
        }
    }

    private fun string2File(res: String, filePath: File): Boolean {
        var flag = true
        var bufferedReader: BufferedReader? = null
        val bufferedWriter: BufferedWriter
        try {
            bufferedReader = BufferedReader(StringReader(res))
            bufferedWriter = BufferedWriter(FileWriter(filePath))
            val buf = CharArray(1024) //字符缓冲区
            var len: Int
            while (bufferedReader.read(buf).also { len = it } != -1) {
                bufferedWriter.write(buf, 0, len)
            }
            bufferedWriter.flush()
            bufferedReader.close()
            bufferedWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
            flag = false
            return flag
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return flag
    }
}


