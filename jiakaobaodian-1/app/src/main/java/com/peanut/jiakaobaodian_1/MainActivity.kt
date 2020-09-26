package com.peanut.jiakaobaodian_1

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.*
import java.util.concurrent.TimeUnit
import kotlin.experimental.xor

class MainActivity : AppCompatActivity() {
    private val key = "_jiakaobaodian.com_".toByteArray()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        this.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE),0)
//        return
        object : Thread() {
            override fun run() {
                //=============链接数据库===========//
                val dataBase = DataBase(this@MainActivity.getDatabasePath("question.db").path)
                //=============建表===========//
                val tables = arrayOf("PD", "DX", "DD")
                val output = DataBase(
                    this@MainActivity,
                    this@MainActivity.getDatabasePath("地方题库.db").path,
                    null,
                    1,
                    null
                )
                for (table in tables)
                    output.execSQL("CREATE TABLE $table(QID int primary key,Topic text,OptionList text,Result text,Chapter text,Explain text,chapId tinyint);")
                output.execSQL("CREATE TABLE img(name text primary key,binary LONGBLOB);")
                output.execSQL("CREATE TABLE Chapter(chapId tinyint primary key,name text);")
                //=============复制题目===========//
                //科目一：(121,122,123,124,2014)
                //科目四：(127,128,129,130,131,132,133)
                val cursor = dataBase.rawQuery("select * from t_question where chapter_id not in (121,122,123,124,2014,127,128,129,130,131,132,133)")
                Log.v("总进度",cursor.count.toString())
                val indexes = arrayOf(0, 0, 0)
                while (cursor.moveToNext()){
                    Log.v("进度",cursor.position.toString())
                    //题目
                    var topic = String(decode(cursor.getBlob(cursor.getColumnIndex("question"))))
                    //题目类型=0判-1单-2多
                    val optionType = cursor.getInt(cursor.getColumnIndex("option_type"))
                    //题目类型=0无-1webP-2mp4
                    val mediaType = cursor.getInt(cursor.getColumnIndex("media_type"))
                    if(mediaType == 1) {
                        val values = ContentValues()
                        val media_key = cursor.getString(cursor.getColumnIndex("media_key"))
                        topic += "@##$media_key##@"
                        //=============复制图片===========//
                        val baos = ByteArrayOutputStream()
                        Log.v("file",File(this@MainActivity.getExternalFilesDir("img")?.path + "/${media_key}.webp").exists().toString())
                        val bitmap = BitmapFactory.decodeFile(
                            File(this@MainActivity.getExternalFilesDir("img")?.path + "/${media_key}.webp").path
                        )
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 75, baos)
                        val bin = baos.toByteArray()
                        bitmap.recycle()
                        values.put("binary", bin)
                        values.put(
                            "name",
                            media_key
                        )
                        output.sqLiteDatabase!!.insert("img", null, values)
                    }
                    //答案
                    val answer = getAns(cursor.getInt(cursor.getColumnIndex("answer")))
                    //解析
                    val sb = StringBuilder()
                    val ea = cursor.getBlob(cursor.getColumnIndex("explain"))
                    if (ea!=null)
                        sb.append(String(decode(ea))+"\n")
                    val eb = cursor.getBlob(cursor.getColumnIndex("concise_explain"))
                    if (eb!=null)
                        sb.append(String(decode(eb))+"\n")
                    val ec = cursor.getBlob(cursor.getColumnIndex("illiteracy_explain"))
                    if (ec!=null)
                        sb.append(String(decode(ec)))
                    val chapter = cursor.getString(cursor.getColumnIndex("chapter_id"))
                    when(optionType){
                        0->{
                            output.execSQL("insert into PD values ('${indexes[0]++}','${Base64.encodeToString(
                                topic.toByteArray(),
                                0
                            )}','','${if (answer == "A") "Y" else "N"}','','${Base64.encodeToString(
                                sb.toString().toByteArray(),
                                0
                            )}','$chapter')")
                        }
                        1->{
                            //选项
                            val a = 'a'
                            val array = JSONArray()
                            for (i in 0..3) {
                                array.put(cursor.getString(cursor.getColumnIndex("option_${a + i}")))
                            }
                            val optionList: String? = array.toString()
                            output.execSQL("insert into DX values ('${indexes[1]++}','${Base64.encodeToString(
                                topic.toByteArray(),
                                0
                            )}','${Base64.encodeToString(
                                (optionList ?: "空").toByteArray(),
                                0
                            )}','$answer','','${Base64.encodeToString(
                                sb.toString().toByteArray(),
                                0
                            )}','$chapter')")
                        }
                        2->{
                            //选项
                            val a = 'a'
                            val array = JSONArray()
                            for (i in 0..3) {
                                array.put(cursor.getString(cursor.getColumnIndex("option_${a + i}")))
                            }
                            val optionList: String? = array.toString()
                            output.execSQL("insert into DD values ('${indexes[2]++}','${Base64.encodeToString(
                                topic.toByteArray(),
                                0
                            )}','${Base64.encodeToString(
                                (optionList ?: "空").toByteArray(),
                                0
                            )}','$answer','','${Base64.encodeToString(
                                sb.toString().toByteArray(),
                                0
                            )}','$chapter')")
                        }
                    }
                }
                cursor.close()
                //添加章节
                val chapCursor = dataBase.rawQuery("select _id,title from t_chapter where _id not in (121,122,123,124,2014,127,128,129,130,131,132,133)")
                while (chapCursor.moveToNext()){
                    output.execSQL("insert into Chapter values ('${chapCursor.getInt(0)}','${chapCursor.getString(1)}')")
                }
                chapCursor.close()
                output.close()
                dataBase.close()
                Handler(this@MainActivity.mainLooper).post {
                    this@MainActivity.state.text = "Done."
                }
            }
        }.start()
    }

    private fun decode(byteArray: ByteArray): ByteArray {
        //解密-异或
        for (i in byteArray.indices) {
            byteArray[i] = byteArray[i].xor(key[i % key.size])
        }
        return byteArray
    }

    private fun getAns(int: Int): String {
        //解密-位处理
//        Log.v("question", getAns(240))//abcd
//        Log.v("question", getAns(16))//a
//        Log.v("question", getAns(64))//c
//        Log.v("question", getAns(128))//d
//        Log.v("question", getAns(32))//b
//        Log.v("question", getAns(160))//bd
//        Log.v("question", getAns(224))//bcd
        val sb = StringBuilder()
        if (int.and(0b00010000) != 0)
            sb.append('A')
        if (int.and(0b00100000) != 0)
            sb.append('B')
        if (int.and(0b01000000) != 0)
            sb.append('C')
        if (int.and(0b10000000) != 0)
            sb.append('D')
        return sb.toString()
    }


    fun downloadFile(
        path: String,
        url: String,
        fileName: String,
        onOkHttpDownloaderUpdate: Listener.OnOkHttpDownloaderUpdate
    ) {
        val okHttpClient = OkHttpClient().newBuilder().retryOnConnectionFailure(true).connectTimeout(30, TimeUnit.SECONDS).build()
        val request: Request = Request.Builder()
            .url(url)
            .addHeader("Connection", "close")
            .build()
        val response = okHttpClient.newCall(request).execute()
        var inputStream: InputStream? = null
        val buf = ByteArray(2048)
        var len: Int
        var fos: FileOutputStream? = null
        // 储存下载文件的目录
        File(path).mkdirs()
        try {
            inputStream = response.body!!.byteStream()
            val total = response.body!!.contentLength()
            val file =
                File("$path/$fileName")
            file.delete()
            fos = FileOutputStream(file)
            var sum: Long = 0
            while (inputStream.read(buf).also { len = it } != -1) {
                fos.write(buf, 0, len)
                sum += len.toLong()
                val progress = (sum * 1.0f / total * 100).toInt()
                onOkHttpDownloaderUpdate.update(progress)
            }
            fos.flush()
            onOkHttpDownloaderUpdate.onDownloadSuccessful()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            onOkHttpDownloaderUpdate.onDownloadFailed(e.localizedMessage ?: "Unknown")
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
            }
            try {
                fos?.close()
            } catch (e: IOException) {
            }
        }
    }
}
