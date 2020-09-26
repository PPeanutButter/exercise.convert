package com.peanut.jiakaobaodian_1

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQL代码参见:
 * http://www.w3school.com.cn/sql/sql_update.asp
 */
class DataBase {
    var sqLiteDatabase: SQLiteDatabase? = null

    constructor(context: Context?, name: String?, cursorFactory: SQLiteDatabase.CursorFactory?, version: Int, onOperateListener: Listener.OnOperateListener?) {
        val dataBase = `DataBase$`(context, name, cursorFactory, version, onOperateListener)
        sqLiteDatabase = dataBase.writableDatabase
    }

    constructor(path: String) {
        try {
            sqLiteDatabase = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public fun version() = sqLiteDatabase!!.version

    public fun execSQL(sql: String?): String {
        return try {
            Log.v("SQL", sql)
            sqLiteDatabase!!.execSQL(sql)
            "Successful"
        } catch (e: SQLiteException) {
            e.printStackTrace()
            e.localizedMessage
        }
    }

    public fun execSQL(sql: String?, onError: String?): String {
        return try {
            Log.v("SQL", sql)
            sqLiteDatabase!!.execSQL(sql)
            "Successful"
        } catch (e: SQLiteException) {
            e.printStackTrace()
            execSQL(onError)
            execSQL(sql)
        }
    }

    public fun rawQuery(sql: String?): Cursor {
        Log.v("SQL", sql)
        return sqLiteDatabase!!.rawQuery(sql, null)
    }

    public fun close() {
        sqLiteDatabase!!.close()
    }
}

internal class `DataBase$`(context: Context?, name: String?, cursorFactory: SQLiteDatabase.CursorFactory?, version: Int, private val onOperateListener: Listener.OnOperateListener?) : SQLiteOpenHelper(context, name, cursorFactory, version) {
    override fun onCreate(db: SQLiteDatabase) {
        onOperateListener?.onCreate(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onOperateListener?.onUpgrade(db, oldVersion, newVersion)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        onOperateListener?.onOpen(db)
    }

}