package com.peanut.poi.android

import android.app.Activity
import android.content.pm.PackageManager

import androidx.core.app.ActivityCompat

object PermissionRequester {

    fun check(activity: Activity, permission: String) =
        PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            activity,
            permission
        )


    fun request(activity: Activity, permission: String) =
        ActivityCompat.requestPermissions(activity, arrayOf(permission), 1)
}