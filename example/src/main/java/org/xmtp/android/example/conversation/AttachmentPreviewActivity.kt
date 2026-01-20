package org.xmtp.android.example.conversation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.viewpager2.widget.ViewPager2
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ActivityAttachmentPreviewBinding

/**
 * Telegram-style attachment preview activity.
 * Shows selected attachments with caption input before sending.
 */
class AttachmentPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttachmentPreviewBinding

    private val attachmentUris = mutableListOf<Uri>()
    private val captions = mutableMapOf<Int, String>()
    private var currentIndex = 0

    companion object {
        private const val EXTRA_ATTACHMENT_URIS = "attachment_uris"
        const val RESULT_ATTACHMENTS = "result_attachments"
        const val RESULT_CAPTIONS = "result_captions"

        fun intent(context: Context, uris: List<Uri>): Intent {
            return Intent(context, AttachmentPreviewActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_ATTACHMENT_URIS, ArrayList(uris))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttachmentPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get attachment URIs from intent
        val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_ATTACHMENT_URIS)
        if (uris.isNullOrEmpty()) {
            finish()
            return
        }
        attachmentUris.addAll(uris)

        setupUI()
        displayAttachment(0)
    }

    private fun setupUI() {
        // Close button
        binding.closeButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Send button
        binding.sendButton.setOnClickListener {
            // Save current caption before sending
            saveCaptionForCurrentIndex()

            val resultIntent = Intent().apply {
                putParcelableArrayListExtra(RESULT_ATTACHMENTS, ArrayList(attachmentUris))
                putStringArrayListExtra(RESULT_CAPTIONS, ArrayList(
                    attachmentUris.indices.map { captions[it] ?: "" }
                ))
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        // Caption text change listener
        binding.captionEditText.addTextChangedListener { text ->
            captions[currentIndex] = text?.toString() ?: ""
        }

        // Setup for multiple attachments
        if (attachmentUris.size > 1) {
            setupMultipleAttachments()
        } else {
            binding.thumbnailContainer.visibility = View.GONE
            binding.pageIndicator.visibility = View.GONE
            binding.attachmentViewPager.visibility = View.GONE
        }

        updateTitle()
    }

    private fun setupMultipleAttachments() {
        binding.thumbnailContainer.visibility = View.VISIBLE
        binding.pageIndicator.visibility = View.VISIBLE

        // Create thumbnail strip
        val thumbnailStrip = binding.thumbnailStrip
        thumbnailStrip.removeAllViews()

        attachmentUris.forEachIndexed { index, uri ->
            val thumbnailView = createThumbnailView(uri, index)
            thumbnailStrip.addView(thumbnailView)
        }

        updateThumbnailSelection(0)
    }

    private fun createThumbnailView(uri: Uri, index: Int): View {
        val thumbnailSize = resources.getDimensionPixelSize(R.dimen.thumbnail_size)
        val imageView = ImageView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(thumbnailSize, thumbnailSize).apply {
                marginEnd = 8
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.thumbnail_border)
            setPadding(4, 4, 4, 4)

            // Load thumbnail
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                setImageResource(R.drawable.ic_attach_file_24)
            }

            setOnClickListener {
                saveCaptionForCurrentIndex()
                displayAttachment(index)
                updateThumbnailSelection(index)
            }
        }
        imageView.tag = index
        return imageView
    }

    private fun updateThumbnailSelection(selectedIndex: Int) {
        for (i in 0 until binding.thumbnailStrip.childCount) {
            val child = binding.thumbnailStrip.getChildAt(i)
            child.alpha = if (i == selectedIndex) 1.0f else 0.5f
            child.scaleX = if (i == selectedIndex) 1.1f else 1.0f
            child.scaleY = if (i == selectedIndex) 1.1f else 1.0f
        }
    }

    private fun saveCaptionForCurrentIndex() {
        captions[currentIndex] = binding.captionEditText.text?.toString() ?: ""
    }

    private fun displayAttachment(index: Int) {
        currentIndex = index
        val uri = attachmentUris[index]

        // Load caption for this attachment
        binding.captionEditText.setText(captions[index] ?: "")

        // Get mime type
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        if (mimeType.startsWith("image/")) {
            // Show image
            binding.singleImageView.visibility = View.VISIBLE
            binding.filePreviewContainer.visibility = View.GONE

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.singleImageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Show file preview
            binding.singleImageView.visibility = View.GONE
            binding.filePreviewContainer.visibility = View.VISIBLE

            // Get file info
            val filename = getFilename(uri)
            val fileSize = getFileSize(uri)

            binding.fileName.text = filename
            binding.fileSize.text = Formatter.formatFileSize(this, fileSize)

            // Set appropriate icon based on mime type
            val iconRes = when {
                mimeType.startsWith("video/") -> R.drawable.ic_video_24
                mimeType.startsWith("audio/") -> R.drawable.ic_audio_24
                mimeType == "application/pdf" -> R.drawable.ic_pdf_24
                else -> R.drawable.ic_attach_file_24
            }
            binding.fileIcon.setImageResource(iconRes)
        }

        updateTitle()
    }

    private fun updateTitle() {
        if (attachmentUris.size > 1) {
            binding.titleText.text = getString(R.string.attachment_count, currentIndex + 1, attachmentUris.size)
            binding.pageIndicator.text = getString(R.string.attachment_count, currentIndex + 1, attachmentUris.size)
        } else {
            binding.titleText.text = getString(R.string.preview)
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
}
