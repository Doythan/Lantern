// src/main/java/com/ssafy/lanterns/utils/WakeWordUtils.kt
package com.ssafy.lanterns.utils

import android.content.Context
import com.ssafy.lanterns.BuildConfig
import java.io.IOException

object WakeWordUtils {
    private const val PV_FILE    = "porcupine_params.pv"
    // BuildConfig.FLAVOR 이 "dev1" 혹은 "dev2" 로 세팅되어 있어야 함
    private val PPN_FILE    = BuildConfig.PV_KEYWORD_FILE

    /**
     * assets 에 PV + flavor별 PPN 이 모두 있어야 true
     */
    fun hasModelFiles(context: Context): Boolean {
        val assets = context.assets
        return try {
            assets.open(PV_FILE).close()
            // hey_lantern_dev1.ppn 또는 hey_lantern_dev2.ppn
            assets.open(PPN_FILE).close()
            true
        } catch (e: IOException) {
            false
        }
    }
}
