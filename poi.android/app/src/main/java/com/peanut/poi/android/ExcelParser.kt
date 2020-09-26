package com.peanut.poi.android

import android.content.Context
import android.os.Handler
import android.util.Base64
import com.peanut.sdk.database.DataBase
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.*
import org.apache.poi.util.IOUtils
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.*
import java.util.*

class ExcelParser {

    fun run(context: Context, cmd: String, logger: MainActivity.Logger?): Boolean {
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLInputFactory",
                "com.fasterxml.aalto.stax.InputFactoryImpl"
        )
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLOutputFactory",
                "com.fasterxml.aalto.stax.OutputFactoryImpl"
        )
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLEventFactory",
                "com.fasterxml.aalto.stax.EventFactoryImpl"
        )
        return readExcel(context, cmd, logger)
    }

    private fun getReadWorkBookType(context: Context, filePath: String, logger: MainActivity.Logger?): Workbook? {
        var fileInputStream: FileInputStream? = null
        try {
            fileInputStream = FileInputStream(filePath)
            if (filePath.endsWith(".xlsx", ignoreCase = true)) {
                return XSSFWorkbook(fileInputStream)
            } else if (filePath.endsWith(".xls", ignoreCase = true)) {
                return HSSFWorkbook(fileInputStream)
            }
        } catch (e: IOException) {
            val a = StringWriter()
            e.printStackTrace(PrintWriter(a))
            Handler(context.mainLooper).post { logger?.e(a.toString()) }
        } finally {
            IOUtils.closeQuietly(fileInputStream)
        }
        return null
    }

    private fun String.encode64(): String = Base64.encodeToString(this.toByteArray(), 0)

    fun String.l(s: String) = this.toLowerCase(Locale.ROOT) == s

    private fun Cell.cellValue(): String {
        return when (this.cellTypeEnum) {
            CellType.NUMERIC -> this.numericCellValue.toString()
            CellType.BOOLEAN -> this.booleanCellValue.toString()
            CellType.ERROR -> this.errorCellValue.toString()
            CellType.STRING, CellType.FORMULA, CellType.BLANK ->
                try {
                    this.stringCellValue
                } catch (e: Exception) {
                    e.localizedMessage
                }
            else -> "单元格格式:" + this.cellTypeEnum.name + "不支持!请使用文本型单元格格式"
        }
    }

    private operator fun Sheet.get(row: Int, column: Int) = this[row][column]

    private operator fun Sheet.get(row: Int) = this.getRow(row)

    private operator fun Row.get(column: Int) = this.getCell(column)

    private fun readExcel(context: Context, sourceFilePath: String, logger: MainActivity.Logger?): Boolean {
        Handler(context.mainLooper).post { logger?.v("源题库 : $sourceFilePath with ${File(sourceFilePath).length()}") }
        val db = File(Objects.requireNonNull(context.getExternalFilesDir("Database")).toString() + "/parse.db")
        Handler(context.mainLooper).post { logger?.v("目标生成题库 : ${db.path}") }
        Handler(context.mainLooper).post { logger?.v("正在预热插件(需要几秒钟):") }
        db.delete()
        var workbook: Workbook? = null
        var position = 0
        return try {
            workbook = getReadWorkBookType(context, sourceFilePath, logger)
            val sheet = workbook!!.getSheetAt(0)
            val count = sheet.lastRowNum
            Handler(context.mainLooper).post {
                logger?.v("总共检测到的题目数量 : $count")
            }
            Handler(context.mainLooper).post {
                logger?.v("初始化指针ing")
            }
            val temp = DataBase(context, db.toString(), null, 1, null)
            temp.execSQL("create table PD(QID int PRIMARY KEY,Topic text,OptionList text,Result text,Explain text,chapId tinyint);")
            temp.execSQL("create table DX(QID int PRIMARY KEY,Topic text,OptionList text,Result text,Explain text,chapId tinyint);")
            temp.execSQL("create table DD(QID int PRIMARY KEY,Topic text,OptionList text,Result text,Explain text,chapId tinyint);")
            temp.execSQL("create table TK(QID int PRIMARY KEY,Topic text,OptionList text,Explain text,chapId tinyint);")
            temp.execSQL("create table JD(QID int PRIMARY KEY,Topic text,Explain text,chapId tinyint);")
            if (sheet[0, 0].cellValue().l("topic") &&
                    sheet[0, 1].cellValue().l("optionlist") &&
                    sheet[0, 2].cellValue().l("result") &&
                    sheet[0, 3].cellValue().l("explain") &&
                    sheet[0, 4].cellValue().l("type") &&
                    sheet[0, 5].cellValue().l("chapter")
            ) {
                val indexes = intArrayOf(0, 0, 0, 0, 0)
                var escape = 0
                var chapList = emptyList<String>()
                for (i in 1..count) {
                    position = i
                    Handler(context.mainLooper).post {
                        logger?.update("开始处理第${position}题(行)")
                    }
                    val row = sheet[i]
                    if (row?.getCell(0) == null || row.getCell(4) == null) {
                        escape++
                        continue
                    }
                    //解决单元格格式导致的问题
                    val topic = row[0].cellValue().encode64()
                    val optionList = getJsonArray(row[1]?.cellValue()).encode64()
                    val explain = row[3]?.cellValue()?.encode64()
                    val result = row[2]?.cellValue()
                    val chapter = row[5]?.cellValue()
                    if (chapList.indexOf(chapter) == -1 && chapter!=null && chapter.trim().isNotEmpty())
                        chapList = chapList.plus(chapter)
                    val chapId = chapList.indexOf(chapter)
                    when (getVisibleTerm(row[4].cellValue().toLowerCase(Locale.ROOT))) {
                        "pd" ->
                            temp.execSQL("insert into PD values ('${++indexes[0]}','$topic','$optionList','$result','$explain','$chapId')")
                        "dx" ->
                            temp.execSQL("insert into DX values ('${++indexes[1]}','$topic','$optionList','$result','$explain','$chapId')")
                        "dd" ->
                            temp.execSQL("insert into DD values ('${++indexes[2]}','$topic','$optionList','$result','$explain','$chapId')")
                        "tk" ->
                            temp.execSQL("insert into TK values ('${++indexes[3]}','$topic','$optionList','$explain','$chapId')")
                        "jd" ->
                            temp.execSQL("insert into JD values ('${++indexes[4]}','$topic','$explain','$chapId')")
                        else ->
                            Handler(context.mainLooper).post {
                                logger?.e("未知的Type类型，请检查type列是否有\\n之类的不可见元素存在")
                                escape++
                            }
                    }
                }
                if (chapList.isNotEmpty()){
                    temp.execSQL("create table Chapter(chapId tinyint PRIMARY KEY,name text);")
                    for ((index,value) in chapList.withIndex()){
                        temp.execSQL("insert into Chapter values ('$index','$value')")
                    }
                }
                if(escape > 0) {
                    Handler(context.mainLooper).post {
                        logger?.e("总共跳过了${escape}个不符合规则的题目")
                    }
                }
                temp.close()
                return true
            } else {
                Handler(context.mainLooper).post {
                    logger?.e("excel首行格式校验 : 不通过，请检查是否拼写错误等")
                }
            }
            temp.close()
            false
        } catch (e: Exception) {
            val a = StringWriter()
            e.printStackTrace(PrintWriter(a))
            Handler(context.mainLooper).post {
                logger?.e("处理第${position}行(题)时出现了致命错误，原因如下：")
                logger?.e(a.toString())
            }
            false
        } finally {
            IOUtils.closeQuietly(workbook)
        }
    }

    private fun getJsonArray(optionList: String?): String {
        if (optionList == null) return "[]"
        val list = optionList.split(";;")
        val jsonArray = JSONArray()
        for (a in list) {
            jsonArray.put(a.replace("\"", "'"))
        }
        return jsonArray.toString()
    }

    private fun getVisibleTerm(str: String?): String? {
        if (str == null) return null
        val sb = StringBuilder()
        for (char in str) {
            if (char.toUpperCase() in 'A'..'Z')
                sb.append(char)
        }
        return sb.toString()
    }

}