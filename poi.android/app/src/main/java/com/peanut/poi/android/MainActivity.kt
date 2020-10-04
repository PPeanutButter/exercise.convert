package com.peanut.poi.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*

class MainActivity : PeanutActivity() {
    private var logger: Logger? = null
    private val sb = java.lang.StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        floatingActionButton.setOnClickListener {
            startActivity(Intent(this, MainActivity2::class.java))
        }
        logger = Logger(this, logcat)
        val stringBuilder = StringBuilder()
        stringBuilder.append("========== 设备信息 ==========").append("\n")
        stringBuilder.append("[VERSION]  :" + BuildConfig.VERSION_NAME).append("\n")
        stringBuilder.append("[DEVICE]   :" + Build.DEVICE).append("\n")
        logger?.v(stringBuilder.toString())
        logger?.v("========== 流程日志 ==========")
        logger?.v("检查必备（存储）权限")
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { _, grantResults ->
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                logger?.v("权限申请通过，请选择excel文件！")
                fileChooser { resultCode: Int, data: Intent? ->
                    data?.data?.let { returnUri ->
                        val (fileName, fileSize) = FileCompat.getFileNameAndSize(this, returnUri)
                        logger?.v("========== 实时日志 ==========")
                        logger?.v("开始处理...")
                        object : Thread() {
                            override fun run() {
                                val destination = this@MainActivity.getExternalFilesDir("excel_temp").toString() + "/$fileName"
                                FileCompat.copyFile(returnUri, destination, this@MainActivity)
                                ExcelParser().run(this@MainActivity, destination, logger) { result ->
                                    Handler(this@MainActivity.mainLooper).post {
                                        if (result) {
                                            logger?.v("处理完成！")
                                            setResult(200, Intent().setData(FileProvider.getUriForFile(this@MainActivity, "com.peanut.poi.android", File(Objects.requireNonNull(this@MainActivity.getExternalFilesDir("Database")).toString() + "/parse.db"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                                            logger?.e("按返回键退出！")
                                        } else {
                                            logger?.e("处理终止！")
                                            setResult(-1)
                                            logger?.e("按返回键退出！")
                                            string2File(sb.toString(), File(this@MainActivity.getExternalFilesDir("log").toString() + "/log.txt"))
                                        }
                                    }
                                }
                            }
                        }.start()
                    }
                }
            }
        }
    }


    inner class Logger internal constructor(private var context: Context, private val logcat: LinearLayout) {
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

        fun update(string: String) {
            textView?.text = string
        }

        init {
            logcat.removeAllViews()
        }
    }

    private fun string2File(res: String, filePath: File) = filePath.writeText(res)
}


