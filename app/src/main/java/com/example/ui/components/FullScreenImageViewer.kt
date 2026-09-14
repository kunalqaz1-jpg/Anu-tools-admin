package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.IndustrialOrange
import com.example.util.ImageModelResolver
import kotlinx.coroutines.launch

/**
 * Fullscreen In-App Image Viewer with:
 * - Left/Right swipe gesture between all product photos (HorizontalPager)
 * - Two-finger pinch-to-zoom (1x - 5x)
 * - Double-tap to zoom (toggle 1x <-> 2.5x)
 * - Pan / drag while zoomed
 * - Automatic zoom reset when swiping to another photo
 * - Photo position counter ("2 / 5")
 * - Close / Back button
 * - Optional "Set as Thumbnail" action
 */
@Composable
fun FullScreenImageViewer(
    images: List<Any>,
    initialIndex: Int = 0,
    thumbnailUrl: String? = null,
    onSetThumbnail: ((index: Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    val safeInitialIndex = initialIndex.coerceIn(0, images.size - 1)
    val pagerState = rememberPagerState(initialPage = safeInitialIndex) { images.size }
    val coroutineScope = rememberCoroutineScope()

    // Keep track of active image scale to disable pager swipe while zoomed in
    var activeScale by remember { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("fullscreen_image_viewer")
        ) {
            // Horizontal Pager for swiping between photos
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = activeScale <= 1.05f,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val imageModel = images.getOrNull(page)
                ZoomableImageItem(
                    model = imageModel,
                    contentDescription = "Photo ${page + 1} of ${images.size}",
                    isActivePage = (page == pagerState.currentPage),
                    onScaleChanged = { scale ->
                        if (page == pagerState.currentPage) {
                            activeScale = scale
                        }
                    }
                )
            }

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .testTag("fullscreen_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Photo ${pagerState.currentPage + 1} / ${images.size}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Pinch or double-tap to zoom • Swipe to change",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Thumbnail action button if callback provided
                if (onSetThumbnail != null) {
                    val currentModel = images.getOrNull(pagerState.currentPage)
                    val isCurrentThumbnail = when (currentModel) {
                        is String -> currentModel == thumbnailUrl
                        else -> currentModel.toString() == thumbnailUrl
                    }

                    if (isCurrentThumbnail) {
                        Surface(
                            color = IndustrialOrange,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Main Thumbnail", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSetThumbnail(pagerState.currentPage) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.7f))),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Set as Main", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Bottom Navigation Dots / Counter Bar
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(images.size.coerceAtMost(10)) { dotIndex ->
                        val isSelected = pagerState.currentPage == dotIndex
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 8.dp else 6.dp)
                                .background(
                                    color = if (isSelected) IndustrialOrange else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                        )
                    }
                    if (images.size > 10) {
                        Text(
                            text = "+${images.size - 10}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageItem(
    model: Any?,
    contentDescription: String?,
    isActivePage: Boolean,
    onScaleChanged: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom when page is no longer active
    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            scaleAnim.snapTo(1f)
            offsetXAnim.snapTo(0f)
            offsetYAnim.snapTo(0f)
            onScaleChanged(1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(isActivePage) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.2f) {
                                // Reset to 1x
                                launch { scaleAnim.animateTo(1f, tween(250)) }
                                launch { offsetXAnim.animateTo(0f, tween(250)) }
                                launch { offsetYAnim.animateTo(0f, tween(250)) }
                                onScaleChanged(1f)
                            } else {
                                // Zoom into 2.5x around tap position
                                val targetScale = 2.5f
                                val maxOffsetX = (containerSize.width * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (containerSize.height * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val targetOffsetX = ((containerSize.width / 2f - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX)
                                val targetOffsetY = ((containerSize.height / 2f - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)
                                launch { scaleAnim.animateTo(targetScale, tween(250)) }
                                launch { offsetXAnim.animateTo(targetOffsetX, tween(250)) }
                                launch { offsetYAnim.animateTo(targetOffsetY, tween(250)) }
                                onScaleChanged(targetScale)
                            }
                        }
                    }
                )
            }
            .pointerInput(isActivePage) {
                detectTransformGestures { _, pan, zoom, _ ->
                    coroutineScope.launch {
                        val newScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                        scaleAnim.snapTo(newScale)
                        onScaleChanged(newScale)

                        if (newScale > 1f) {
                            val maxOffsetX = (containerSize.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                            val maxOffsetY = (containerSize.height * (newScale - 1f) / 2f).coerceAtLeast(0f)
                            val newOffsetX = (offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            val newOffsetY = (offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            offsetXAnim.snapTo(newOffsetX)
                            offsetYAnim.snapTo(newOffsetY)
                        } else {
                            offsetXAnim.snapTo(0f)
                            offsetYAnim.snapTo(0f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val resolved = ImageModelResolver.resolveImageModel(model)

        SubcomposeAsyncImage(
            model = resolved,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IndustrialOrange, strokeWidth = 2.5.dp)
                }
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Image Error",
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Image could not be loaded",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}
