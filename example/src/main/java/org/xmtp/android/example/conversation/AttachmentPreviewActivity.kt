package org.xmtp.android.example.conversation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.xmtp.android.example.ui.screens.AttachmentPreviewItem
import org.xmtp.android.example.ui.screens.AttachmentPreviewScreen
import org.xmtp.android.example.ui.theme.XMTPTheme

class AttachmentPreviewActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ATTACHMENT_URIS = "attachment_uris"
        const val RESULT_ATTACHMENTS = "result_attachments"
        const val RESULT_CAPTIONS = "result_captions"

        fun intent(
            context: Context,
            uris: List<Uri>,
        ): Intent =
            Intent(context, AttachmentPreviewActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_ATTACHMENT_URIS, ArrayList(uris))
                // Grant read permission for URIs so they can be accessed in the preview activity
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }

    private val attachmentUris = mutableListOf<Uri>()
    private val captions = mutableMapOf<Int, String>()
    private val previewBitmaps = mutableMapOf<Int, Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get attachment URIs from intent (API 33+ compatible)
        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_ATTACHMENT_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_ATTACHMENT_URIS)
        }

        if (uris.isNullOrEmpty()) {
            finish()
            return
        }

        attachmentUris.addAll(uris)

        setContent {
            XMTPTheme {
                var currentCaption by remember { mutableStateOf("") }
                var currentIndex by remember { mutableIntStateOf(0) }

                // Build attachment preview items
                val attachments = remember {
                    mutableStateListOf<AttachmentPreviewItem>().apply {
                        attachmentUris.forEachIndexed { index, uri ->
                            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                            val isImage = mimeType.startsWith("image/")
                            val filename = getFilename(uri)
                            val fileSize = Formatter.formatFileSize(
                                this@AttachmentPreviewActivity,
                                getFileSize(uri),
                            )

                            // Load bitmap for images
                            val bitmap = if (isImage) {
                                loadBitmap(uri)?.also { previewBitmaps[index] = it }
                            } else {
                                null
                            }

                            add(
                                AttachmentPreviewItem(
                                    uri = uri,
                                    filename = filename,
                                    fileSize = fileSize,
                                    mimeType = mimeType,
                                    thumbnail = bitmap,
                                    isImage = isImage,
                                ),
                            )
                        }
                    }
                }

                AttachmentPreviewScreen(
                    attachments = attachments,
                    caption = currentCaption,
                    selectedIndex = currentIndex,
                    onCaptionChange = { caption ->
                        currentCaption = caption
                        captions[currentIndex] = caption
                    },
                    onSelectedIndexChange = { newIndex ->
                        // Save current caption before switching
                        captions[currentIndex] = currentCaption
                        currentIndex = newIndex
                        // Load caption for new index
                        currentCaption = captions[newIndex] ?: ""
                    },
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onSend = {
                        // Save current caption before sending
                        captions[currentIndex] = currentCaption

                        val resultIntent = Intent().apply {
                            putParcelableArrayListExtra(RESULT_ATTACHMENTS, ArrayList(attachmentUris))
                            putStringArrayListExtra(
                                RESULT_CAPTIONS,
                                ArrayList(attachmentUris.indices.map { idx -> captions[idx] ?: "" }),
                            )
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                )
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun getFilename(uri: Uri): String {
        var filename = "attachment"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex) ?: "attachment"
                }
            }
        }
        return filename
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    override fun onDestroy() {
        super.onDestroy()
        // Recycle bitmaps to prevent memory leaks
        previewBitmaps.values.forEach { it.recycle() }
        previewBitmaps.clear()
    }
}
