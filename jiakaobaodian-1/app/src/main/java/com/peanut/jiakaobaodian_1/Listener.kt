package com.peanut.jiakaobaodian_1

import android.database.sqlite.SQLiteDatabase

class Listener {
    interface OnOperateListener {
        fun onCreate(db: SQLiteDatabase?)
        fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int)
        fun onOpen(db: SQLiteDatabase?)
    }

    interface OnOkHttpDownloaderUpdate {
        fun update(percentage: Int)
        fun onDownloadFailed(message: String)
        fun onDownloadSuccessful()
    }
}