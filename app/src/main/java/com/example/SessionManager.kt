package com.example

import android.content.Context
import java.io.File

class SessionManager(private val context: Context) {

    fun getSessionDir(): File {
        val dir = File(context.filesDir, "auth_info_baileys")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun clearAllSessions() {
        val dir = getSessionDir()
        if (dir.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }

    fun cleanGhostSessions(): Int {
        val dir = getSessionDir()
        if (!dir.exists()) return 0
        
        var deleted = 0
        val now = System.currentTimeMillis()
        val twoDays = 48 * 60 * 60 * 1000L
        
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                var isGhost = false
                if (file.length() == 0L) {
                    isGhost = true
                } else if (now - file.lastModified() > twoDays) {
                    // It's older than 48h
                    isGhost = true
                } else if (file.name.endsWith(".json")) {
                    // Try to read and parse quickly
                    try {
                        val content = file.readText()
                        if (!content.trim().startsWith("{")) {
                            isGhost = true
                        }
                    } catch (e: Exception) {
                        isGhost = true
                    }
                }
                
                if (isGhost) {
                    if (file.delete()) deleted++
                }
            }
        }
        return deleted
    }

    fun getSessionInfo(): Pair<Int, Long> {
        val dir = getSessionDir()
        if (!dir.exists()) return Pair(0, 0L)
        
        val files = dir.listFiles() ?: return Pair(0, 0L)
        var totalSize = 0L
        for (f in files) {
            if (f.isFile) totalSize += f.length()
        }
        return Pair(files.size, totalSize)
    }
}
