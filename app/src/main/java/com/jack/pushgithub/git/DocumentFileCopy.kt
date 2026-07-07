package com.jack.pushgithub.git

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object DocumentFileCopy {
    fun copyFromUri(context: Context, sourceTreeUri: Uri, destDir: File) {
        val srcDoc = DocumentFile.fromTreeUri(context, sourceTreeUri)
            ?: throw IllegalArgumentException("无法访问选择的文件夹")

        copyDocumentDirectory(context, srcDoc, destDir)
    }

    private fun copyDocumentDirectory(context: Context, sourceDoc: DocumentFile, destDir: File) {
        destDir.mkdirs()
        sourceDoc.listFiles().forEach { file ->
            if (file.isDirectory) {
                copyDocumentDirectory(context, file, File(destDir, file.name ?: "unknown"))
            } else if (file.isFile) {
                val destFile = File(destDir, file.name ?: "unknown")
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}