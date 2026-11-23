package org.maqraatalnoor.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.MushafMark
import org.maqraatalnoor.data.MushafSource
import org.maqraatalnoor.data.MushafSurah
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.viewmodels.MushafViewModel

/** Highest zoom the reader allows. */
private const val MAX_ZOOM = 3f

/** A double tap goes straight to the maximum; another returns to 1×. */
private const val DOUBLE_TAP_ZOOM = MAX_ZOOM

/**
 * Screen 5 — the Mushaf. Two bundled mushafs read page by page as printed,
 * full screen, with an index that slides in from the right edge (surahs,
 * ahzab and ajza') and lands on the surah being read.
 */
@Composable
fun MushafScreen(
    viewModel: MushafViewModel = viewModel(),
) {
    val source by viewModel.source.collectAsState()
    val currentPage by viewModel.page.collectAsState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val currentSurahIndex = remember(source, currentPage) {
        source.surahs.indexOfLast { it.page <= currentPage }.coerceAtLeast(0)
    }
    val currentHizbIndex = remember(source, currentPage) {
        source.hizbs.indexOfLast { it.page <= currentPage }.coerceAtLeast(0)
    }
    val currentJuzIndex = remember(source, currentPage) {
        source.juzs.indexOfLast { it.page <= currentPage }.coerceAtLeast(0)
    }

    key(source.id) {
        val firstSurahPage = source.surahs.firstOrNull()?.page ?: 1
        val pagerState = rememberPagerState(initialPage = firstSurahPage - 1) { source.pageCount }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { index ->
                viewModel.goToPage(index + 1)
                zoom = 1f
                pan = Offset.Zero
            }
        }

        fun goTo(page: Int) {
            scope.launch { pagerState.scrollToPage(page - 1) }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            // Edge swipes stay with the page; the drawer opens from its tab.
            gesturesEnabled = drawerState.isOpen,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            drawerContent = {
                MushafIndexDrawer(
                    source = source,
                    sources = viewModel.sources,
                    currentPage = currentPage,
                    currentSurah = currentSurahIndex,
                    currentHizb = currentHizbIndex,
                    currentJuz = currentJuzIndex,
                    tab = tab,
                    onTab = { tab = it },
                    onClose = { scope.launch { drawerState.close() } },
                    onPick = { page ->
                        scope.launch { drawerState.close() }
                        goTo(page)
                    },
                    onSwitchSource = { id -> viewModel.selectSource(id) },
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Paper)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    MushafHeader(
                        source = source,
                        onOpenIndex = { scope.launch { drawerState.open() } },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoom <= 1.01f,
                        key = { it },
                    ) { index ->
                        MushafPageImage(
                            source = source,
                            page = index + 1,
                            path = viewModel.pagePath(index + 1),
                            zoom = zoom,
                            pan = pan,
                            onZoom = { zoom = it },
                            onPan = { pan = it },
                        )
                    }
                }
            }
        }
    }
}

/** Slim band: the index button on the right, the mushaf name on the left. */
@Composable
private fun MushafHeader(
    source: MushafSource,
    onOpenIndex: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepGreen)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Gold)
                .clickable { onOpenIndex() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = DeepGreen,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "الفهرس",
                color = DeepGreen,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            source.title,
            color = GoldLight,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** The index drawer: mushaf switch, three tabs, auto-focused on where you are. */
@Composable
private fun MushafIndexDrawer(
    source: MushafSource,
    sources: List<MushafSource>,
    currentPage: Int,
    currentSurah: Int,
    currentHizb: Int,
    currentJuz: Int,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onPick: (Int) -> Unit,
    onSwitchSource: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Land on the surah / hizb / juz being read whenever the index opens.
    LaunchedEffect(tab, source.id, currentPage) {
        val target = when (tab) {
            0 -> currentSurah
            1 -> currentHizb
            else -> currentJuz
        }
        listState.scrollToItem((target - 1).coerceAtLeast(0))
    }

    ModalDrawerSheet(
        drawerContainerColor = DeepGreen,
        modifier = Modifier.width(310.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepGreen)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "فهرس المصحف",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        source.title,
                        color = GoldLight,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = GoldLight)
                }
            }

            // Mushaf switch.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Paper)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { item ->
                    val selected = item.id == source.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (selected) Gold else Color(0xFFF4ECDD))
                            .clickable { onSwitchSource(item.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) DeepGreen else GoldDark,
                            maxLines = 1,
                        )
                    }
                }
            }

            IndexTabs(tab = tab, onTab = onTab)

            HorizontalDivider(thickness = 1.dp, color = CardLine)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Paper)
                    .navigationBarsPadding(),
            ) {
                when (tab) {
                    0 -> items(source.surahs) { surah ->
                        IndexRow(
                            badge = surah.index.toString(),
                            title = surah.name,
                            page = surah.page,
                            active = surah.index - 1 == currentSurah,
                            onClick = { onPick(surah.page) },
                        )
                    }

                    1 -> items(source.hizbs) { mark ->
                        IndexRow(
                            badge = mark.number.toString(),
                            title = "الحزب ${mark.number}",
                            page = mark.page,
                            active = mark.number - 1 == currentHizb,
                            onClick = { onPick(mark.page) },
                        )
                    }

                    else -> items(source.juzs) { mark ->
                        IndexRow(
                            badge = mark.number.toString(),
                            title = "الجزء ${mark.number}",
                            page = mark.page,
                            active = mark.number - 1 == currentJuz,
                            onClick = { onPick(mark.page) },
                        )
                    }
                }
            }
        }
    }
}

/** Three-segment switch: السور • الأحزاب • الأجزاء. */
@Composable
private fun IndexTabs(tab: Int, onTab: (Int) -> Unit) {
    val labels = listOf("السور", "الأحزاب", "الأجزاء")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Paper)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(if (selected) Color(0xFFF4ECDD) else Color.Transparent)
                    .clickable { onTab(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) DeepGreen else Muted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) Gold else Color.Transparent),
                )
            }
        }
    }
}

/** One line of the index: badge, name, page. */
@Composable
private fun IndexRow(
    badge: String,
    title: String,
    page: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) Color(0xFFF4ECDD) else Paper)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (active) Gold else Color(0xFFEDE6D4)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) DeepGreen else GoldDark,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            maxLines = 1,
        )
        Text(
            "صفحة $page",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
    HorizontalDivider(thickness = 0.6.dp, color = CardLine.copy(alpha = 0.7f))
}

/** One printed page: pinch to zoom, drag to pan, double-tap to zoom/reset. */
@Composable
private fun MushafPageImage(
    source: MushafSource,
    page: Int,
    path: String,
    zoom: Float,
    pan: Offset,
    onZoom: (Float) -> Unit,
    onPan: (Offset) -> Unit,
) {
    val context = LocalContext.current
    val zoomNow = rememberUpdatedState(zoom)
    val panNow = rememberUpdatedState(pan)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, source.id, page) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val pageWidth = with(density) { maxWidth.toPx() }
        val pageHeight = with(density) { maxHeight.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(source.id, page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var active = zoomNow.value > 1.001f
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.count { it.pressed }
                            val moved = event.changes.any {
                                (it.position - it.previousPosition).getDistance() >
                                    viewConfiguration.touchSlop
                            }
                            if (pointers > 1) active = true
                            if (active && (pointers > 1 || moved)) {
                                val zoomChange = if (pointers > 1) event.calculateZoom() else 1f
                                val panChange = event.calculatePan()
                                val next = (zoomNow.value * zoomChange).coerceIn(1f, MAX_ZOOM)
                                val limitX = pageWidth * (next - 1f) / 2f
                                val limitY = pageHeight * (next - 1f) / 2f
                                onZoom(next)
                                if (next <= 1.001f) {
                                    onPan(Offset.Zero)
                                } else {
                                    val base = panNow.value
                                    onPan(
                                        Offset(
                                            (base.x + panChange.x).coerceIn(-limitX, limitX),
                                            (base.y + panChange.y).coerceIn(-limitY, limitY),
                                        ),
                                    )
                                }
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(source.id, page) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomNow.value > 1f) {
                                onZoom(1f)
                                onPan(Offset.Zero)
                            } else {
                                onZoom(DOUBLE_TAP_ZOOM)
                                onPan(Offset.Zero)
                            }
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "صفحة $page",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                )
            } else {
                Text("…", color = Muted, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
