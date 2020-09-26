package com.peanut.jiakaobaodian_1

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import java.io.File
import java.lang.StringBuilder

//处理图片
class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        //将图片下载地址保存到txt中用aria2下载，okhttp有点问题
        val dataBase = DataBase(this.getDatabasePath("question.db").path)
        val imgCur = dataBase.rawQuery("select hd_image_url from t_media")
        val urlListBuilder = StringBuilder()
        while (imgCur.moveToNext()) {
            val url = imgCur.getString(0)
            urlListBuilder.append(url).append(System.lineSeparator())
        }
        File(this.cacheDir.path+"/lists.txt").writeText(urlListBuilder.toString())
    }
}