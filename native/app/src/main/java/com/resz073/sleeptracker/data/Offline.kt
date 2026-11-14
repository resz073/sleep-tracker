package com.resz073.sleeptracker.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepDoc
import com.resz073.sleeptracker.engine.SleepModel

/** The signed-in account's data as this phone has it, for the parts that work without the app open (reminders, widget, tile) */
object Offline {
    fun model(context: Context): SleepModel? {
        if (!Backend.ready) return null
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val now = Now.system()
        return SleepModel(SleepDoc.fromText(FileCopy(context.applicationContext, uid).read(), now), now)
    }
}
