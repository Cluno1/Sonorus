/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.util

import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import io.github.cluno1.sonorus.R

/**
 * Utility class for handling equalizer-related functionality
 */
object EqualizerUtils {
    private const val TAG = "EqualizerUtils"
    
    /**
     * Opens the system equalizer
     * 
     * @param context The context to use for starting the activity
     * @param audioSessionId Optional audio session ID (use 0 for system default)
     * @param activity Optional Activity for startActivityForResult, if null uses startActivity
     * @param requestCode Request code for startActivityForResult
     * @return true if the equalizer was opened successfully, false otherwise
     */
    fun openSystemEqualizer(context: Context, audioSessionId: Int = 0, activity: Activity? = null, requestCode: Int = 0): Boolean {
        if (audioSessionId == AudioEffect.ERROR_BAD_VALUE) {
            RhythmMessages.error(context, R.string.no_audio_ID)
            return false
        }
        
        return try {
            val equalizer = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            if (activity != null) {
                activity.startActivityForResult(equalizer, requestCode)
            } else {
                context.startActivity(equalizer)
            }
            
            Log.d(TAG, "Opened system equalizer with session ID: $audioSessionId")
            true
        } catch (_: ActivityNotFoundException) {
            RhythmMessages.error(context, R.string.no_equalizer)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening system equalizer", e)
            RhythmMessages.failure(context, R.string.msg_equalizer_open_failed, e)
            false
        }
    }
    
    /**
     * Try to start an activity, returning true if successful
     */
    private fun tryStartActivity(context: Context, intent: Intent, activity: Activity? = null, requestCode: Int = 0): Boolean {
        return try {
            val packageManager = context.packageManager
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent, 
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            
            if (resolveInfo.isNotEmpty()) {
                if (activity != null) {
                    activity.startActivityForResult(intent, requestCode)
                } else {
                    context.startActivity(intent)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity", e)
            false
        }
    }
    
    /**
     * Try to open specific known equalizer apps
     */
    private fun tryOpenSpecificEqualizerApps(context: Context): Boolean {
        // List of known equalizer packages - expanded list
        val equalizerPackages = listOf(
            "com.android.musicfx",                    // Android's built-in equalizer
            "com.google.android.musicfx",             // Google's MusicFX
            "com.sec.android.app.soundalive",         // Samsung's equalizer
            "com.samsung.android.soundassistant",     // Samsung Sound Assistant
            "com.motorola.dtv.soundenhancer",         // Motorola's equalizer
            "com.motorola.audioeffects",              // Motorola Audio Effects
            "com.xiaomi.equalizer",                   // Xiaomi's equalizer
            "com.miui.audioeffect",                   // MIUI Audio Effect
            "com.oneplus.sound.tuner",                // OnePlus equalizer
            "com.oplus.audioeffect",                  // OPPO/OnePlus Audio Effect
            "com.sony.soundenhancement.spapp",        // Sony's equalizer
            "com.sonyericsson.music.audioeffect",     // Sony/Ericsson Audio Effect
            "com.google.android.soundpicker",         // Google's sound picker
            "com.huawei.audioeffectcenter",           // Huawei's equalizer
            "com.asus.visualmaster",                  // ASUS Visual Master (includes audio)
            "com.lge.equalizer",                      // LG Equalizer
            "com.htc.music.fx",                       // HTC Music FX
            "com.vivo.audiofx"                        // Vivo Audio FX
        )
        
        val packageManager = context.packageManager
        
        // Try each known equalizer package
        for (packageName in equalizerPackages) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.d(TAG, "Opened equalizer app: $packageName")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Equalizer package not available: $packageName")
                // Continue trying other packages
            }
        }
        
        return false
    }
    
    /**
     * Check if system has an equalizer available
     */
    fun isSystemEqualizerAvailable(context: Context): Boolean {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        val packageManager = context.packageManager
        
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        
        return resolveInfo.isNotEmpty()
    }
} 
