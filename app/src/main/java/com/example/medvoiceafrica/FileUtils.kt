package com.example.medvoiceafrica

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Simple utility to copy a specific file from assets to internal storage.
 */
object FileUtils {
    private const val TAG = "FileUtils"

    fun copySingleFile(context: Context, assetPath: String, targetFile: File) {
        try {
            // If the file already exists, we don't do anything to save time
            if (targetFile.exists()) {
                Log.d(TAG, "File already exists at: ${targetFile.absolutePath}")
                return
            }

            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "SUCCESS: Copied $assetPath to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to copy $assetPath. Reason: ${e.message}")
        }
    }
}