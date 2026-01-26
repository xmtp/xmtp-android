package org.xmtp.android.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme

data class AttachmentPreviewItem(
    val uri: Uri,
    val filename: String,
    val fileSize: String,
    val mimeType: String,
    val thumbnail: Bitmap? = null,
    val isImage: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AttachmentPreviewScreen(
    attachments: List<AttachmentPreviewItem>,
    caption: String = "",
    selectedIndex: Int = 0,
    onCaptionChange: (String) -> Unit = {},
    onSelectedIndexChange: (Int) -> Unit = {},
    onClose: () -> Unit = {},
    onSend: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { attachments.size },
    )

    // Sync pager with selected index from parent
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedIndex) {
            onSelectedIndexChange(pagerState.currentPage)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    if (attachments.size > 1) {
                        Text(
                            text = "${selectedIndex + 1} of ${attachments.size}",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "Preview",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSend) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (attachments.size == 1) {
                    // Single attachment
                    AttachmentContent(
                        attachment = attachments.first(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Multiple attachments - pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        AttachmentContent(
                            attachment = attachments[page],
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Caption input
            CaptionInput(
                caption = caption,
                onCaptionChange = onCaptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(12.dp),
            )

            // Thumbnail strip for multiple attachments
            if (attachments.size > 1) {
                ThumbnailStrip(
                    attachments = attachments,
                    selectedIndex = selectedIndex,
                    onThumbnailClick = { index -> onSelectedIndexChange(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun AttachmentContent(
    attachment: AttachmentPreviewItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.isImage && attachment.thumbnail != null) {
            Image(
                bitmap = attachment.thumbnail.asImageBitmap(),
                contentDescription = attachment.filename,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            // File preview
            FilePreviewContent(
                filename = attachment.filename,
                fileSize = attachment.fileSize,
            )
        }
    }
}

@Composable
private fun FilePreviewContent(
    filename: String,
    fileSize: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = "File",
            tint = Color.White,
            modifier = Modifier.size(80.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = filename,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = fileSize,
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CaptionInput(
    caption: String,
    onCaptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = caption,
        onValueChange = onCaptionChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = "Add a caption...",
                color = Color.White.copy(alpha = 0.5f),
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
        ),
        maxLines = 3,
    )
}

@Composable
private fun ThumbnailStrip(
    attachments: List<AttachmentPreviewItem>,
    selectedIndex: Int,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(attachments) { index, attachment ->
            ThumbnailItem(
                attachment = attachment,
                isSelected = index == selectedIndex,
                onClick = { onThumbnailClick(index) },
            )
        }
    }
}

@Composable
private fun ThumbnailItem(
    attachment: AttachmentPreviewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.isImage && attachment.thumbnail != null) {
            Image(
                bitmap = attachment.thumbnail.asImageBitmap(),
                contentDescription = attachment.filename,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "File",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AttachmentPreviewScreenSingleImagePreview() {
    XMTPTheme {
        AttachmentPreviewScreen(
            attachments = listOf(
                AttachmentPreviewItem(
                    uri = Uri.EMPTY,
                    filename = "photo.jpg",
                    fileSize = "2.5 MB",
                    mimeType = "image/jpeg",
                    isImage = true,
                    thumbnail = null,
                ),
            ),
            caption = "",
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AttachmentPreviewScreenFilePreview() {
    XMTPTheme {
        AttachmentPreviewScreen(
            attachments = listOf(
                AttachmentPreviewItem(
                    uri = Uri.EMPTY,
                    filename = "document.pdf",
                    fileSize = "1.2 MB",
                    mimeType = "application/pdf",
                    isImage = false,
                ),
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AttachmentPreviewScreenMultiplePreview() {
    XMTPTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Simulating multiple attachment preview
            Text(
                text = "1 of 3",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center,
            ) {
                FilePreviewContent(
                    filename = "document.pdf",
                    fileSize = "1.2 MB",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .border(
                                width = if (index == 0) 2.dp else 0.dp,
                                color = if (index == 0) Color.Blue else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "File",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
