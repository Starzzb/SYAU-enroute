package cn.dkarbon.enroute.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.dkarbon.enroute.data.faculty.FacultyImages
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PHOTO_H = 124.dp

/** 图还没解码出来、或某张落盘失败时留的固定空位（有边界，不是一块白） */
private val PHOTO_EMPTY_W = 92.dp

/**
 * 官网正文图片：贴在详情页最上方的一条横向图带。
 *
 * 取自 `FacultyImages` 的本地副本（`filesDir/faculty/img/`）——官网不在 CDN 上，
 * 每次点开都重拉既慢又给站里添负担。落盘没有的那几张在这里补拉一次，
 * 拉不到就只是少一张图，**不显示占位说明**。
 *
 * 高度统一 124dp、宽度按原图比例算出来（夹在 72–220dp 之间），
 * 所以证件照是竖的、活动现场照是横的，视觉上仍是一排齐顶的带子。
 */
@Composable
fun TeacherPhotoRow(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    val ctx = LocalContext.current
    var files by remember(urls) {
        mutableStateOf(urls.mapNotNull { FacultyImages.cached(ctx, it) })
    }

    LaunchedEffect(urls) {
        if (files.size >= urls.size) return@LaunchedEffect
        val done = withContext(Dispatchers.IO) {
            runCatching { FacultyImages.ensure(ctx, urls) }.getOrElse { emptyList() }
        }
        if (done.isNotEmpty()) files = done
    }

    if (files.isEmpty()) return

    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        files.forEach { Photo(it) }
    }
}

@Composable
private fun Photo(file: File) {
    val t = InkTokens.current
    var bitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file.path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }

    val img = bitmap
    val w = if (img != null && img.height > 0) {
        PHOTO_H * (img.width.toFloat() / img.height.toFloat())
    } else {
        PHOTO_EMPTY_W
    }

    Box(
        Modifier
            .height(PHOTO_H)
            .width(w.coerceIn(72.dp, 220.dp))
            .background(t.card)
            .border(Stroke.hairline, t.outline),
    ) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
