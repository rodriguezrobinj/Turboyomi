package tachiyomi.presentation.core.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastLastOrNull
import androidx.compose.ui.util.fastMaxBy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.util.PAGE_FRACTION
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Draws vertical fast scroller to a lazy list
 *
 * Set key with [STICKY_HEADER_KEY_PREFIX] prefix to any sticky header item in the list.
 */
@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val gutter = ScrollerGutter.roundToPx()
        val contentConstraints = constraints.withoutGutter(gutter)
        val contentPlaceable = subcompose("content", content).map { it.measure(contentConstraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount == 0) return@subcompose

            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()

            // listState.isScrollInProgress occasionally flickers
            val scrollStateTracker = remember { MutableData(listState.isScrollInProgress) }
            val stableScrollInProgress = scrollStateTracker.value || listState.isScrollInProgress
            scrollStateTracker.value = listState.isScrollInProgress
            val anyScrollInProgress = stableScrollInProgress || isThumbDragged

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() -
                thumbTopPadding -
                thumbBottomPadding -
                listState.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx
            val scrollHeightPx = contentHeight.toFloat() -
                listState.layoutInfo.beforeContentPadding -
                listState.layoutInfo.afterContentPadding -
                thumbBottomPadding

            val visibleItems = layoutInfo.visibleItemsInfo
            val topItem = visibleItems.fastFirstOrNull {
                it.bottom >= 0 &&
                    (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true
            } ?: visibleItems.first()
            val bottomItem = visibleItems.fastLastOrNull {
                it.top <= scrollHeightPx &&
                    (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true
            } ?: visibleItems.last()

            val topHiddenProportion = -1f * topItem.top / topItem.size.coerceAtLeast(1)
            val bottomHiddenProportion = (bottomItem.bottom - scrollHeightPx) / bottomItem.size.coerceAtLeast(1)
            val previousSections = topHiddenProportion + topItem.index
            val remainingSections = bottomHiddenProportion + (layoutInfo.totalItemsCount - (bottomItem.index + 1))
            val scrollableSections = previousSections + remainingSections

            val layoutChangeTracker = remember { MutableData(scrollableSections) }
            val layoutChanged = !anyScrollInProgress && abs(layoutChangeTracker.value - scrollableSections) > 0.1
            layoutChangeTracker.value = scrollableSections

            val estimateConfidence = remember { MutableData(remainingSections) }
            if (layoutChanged) estimateConfidence.value = remainingSections
            val maxRemainingSections = remember(estimateConfidence.value) { scrollableSections }
            estimateConfidence.value = max(estimateConfidence.value, remainingSections)

            if (maxRemainingSections < 0.5) return@subcompose

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                val thumbProportion = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                if (thumbProportion <= 0.001f) {
                    estimateConfidence.value = -1f
                    listState.scrollToItem(index = 0, scrollOffset = 0)
                    return@LaunchedEffect
                }
                val scrollRemainingSections = (1f - thumbProportion) * maxRemainingSections
                val currentSection = layoutInfo.totalItemsCount - scrollRemainingSections
                val scrollSectionIndex = currentSection.toInt().coerceAtMost(layoutInfo.totalItemsCount)
                val expectedScrollItem = visibleItems.find { it.index == scrollSectionIndex } ?: visibleItems.first()
                val scrollRelativeOffset = expectedScrollItem.size * (currentSection - scrollSectionIndex)
                val scrollSectionOffset = (scrollRelativeOffset - scrollHeightPx).roundToInt()
                val scrollItemIndex = scrollSectionIndex.coerceIn(0, layoutInfo.totalItemsCount - 1)
                val scrollItemOffset = scrollSectionOffset + (scrollSectionIndex - scrollItemIndex) * bottomItem.size
                listState.scrollToItem(index = scrollItemIndex, scrollOffset = scrollItemOffset)
            }

            // When list scrolled
            if (layoutInfo.totalItemsCount != 0 && !isThumbDragged) {
                val proportion = 1f - remainingSections / maxRemainingSections
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
            }

            val pageScope = rememberCoroutineScope()
            ScrollerBar(
                thumbOffsetY = thumbOffsetY,
                scrollInProgress = stableScrollInProgress,
                thumbAllowed = thumbAllowed,
                thumbColor = thumbColor,
                dragInteractionSource = dragInteractionSource,
                onDrag = { delta ->
                    thumbOffsetY = (thumbOffsetY + delta).coerceIn(thumbTopPadding, thumbTopPadding + trackHeightPx)
                },
                onTrackPress = { towardsEnd ->
                    val page = PAGE_FRACTION * contentHeight
                    pageScope.launch { listState.animateScrollBy(if (towardsEnd) page else -page) }
                },
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth + gutter, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth + gutter - scrollerWidth, 0)
            }
        }
    }
}

@Composable
private fun rememberColumnWidthSums(
    columns: GridCells,
    horizontalArrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
) = remember<Density.(Constraints) -> List<Int>>(
    columns,
    horizontalArrangement,
    contentPadding,
) {
    { constraints ->
        require(constraints.maxWidth != Constraints.Infinity) {
            "LazyVerticalGrid's width should be bound by parent"
        }
        val horizontalPadding = contentPadding.calculateStartPadding(LayoutDirection.Ltr) +
            contentPadding.calculateEndPadding(LayoutDirection.Ltr)
        val gridWidth = constraints.maxWidth - horizontalPadding.roundToPx()
        with(columns) {
            calculateCrossAxisCellSizes(
                gridWidth,
                horizontalArrangement.spacing.roundToPx(),
            ).toMutableList().apply {
                for (i in 1..<size) {
                    this[i] += this[i - 1]
                }
            }
        }
    }
}

/*
    VerticalGridFastScroller was written with a regularity assumption, so it is slightly inaccurate for layouts with
    varying row sizes.
 */
// TODO: Ideally rewrite VerticalGridFastScroller to use similar logic as VerticalFastScroller
@Composable
fun VerticalGridFastScroller(
    state: LazyGridState,
    columns: GridCells,
    arrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    val slotSizesSums = rememberColumnWidthSums(
        columns = columns,
        horizontalArrangement = arrangement,
        contentPadding = contentPadding,
    )

    SubcomposeLayout(modifier = modifier) { constraints ->
        // The grid's end padding already keeps the cards off the thumb: reserve only what it lacks,
        // so the gap from the last card to the thumb matches the one at the start edge
        val gutter = (ScrollerGutter - contentPadding.calculateEndPadding(layoutDirection))
            .coerceAtLeast(ThumbClearance)
            .roundToPx()
        val contentConstraints = constraints.withoutGutter(gutter)
        val contentPlaceable = subcompose("content", content).map { it.measure(contentConstraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = state.layoutInfo
            val showScroller = remember(columns, layoutInfo.totalItemsCount) {
                layoutInfo.visibleItemsInfo.size < layoutInfo.totalItemsCount
            }
            if (!showScroller) return@subcompose
            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() -
                thumbTopPadding -
                thumbBottomPadding -
                state.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx

            val columnCount = remember(columns) { slotSizesSums(contentConstraints).size.coerceAtLeast(1) }
            val scrollRange = remember(columns) { computeGridScrollRange(state = state, columnCount = columnCount) }

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                val visibleItems = state.layoutInfo.visibleItemsInfo
                val startChild = visibleItems.first()
                val endChild = visibleItems.last()
                val laidOutArea = (endChild.offset.y + endChild.size.height) - startChild.offset.y
                val laidOutRows = 1 + abs(endChild.index - startChild.index) / columnCount
                val avgSizePerRow = laidOutArea.toFloat() / laidOutRows

                val scrollRatio = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                val scrollAmt = scrollRatio * (scrollRange.toFloat() - heightPx).coerceAtLeast(1f)
                val rowNumber = (scrollAmt / avgSizePerRow).toInt()
                val rowOffset = scrollAmt - rowNumber * avgSizePerRow

                state.scrollToItem(index = columnCount * rowNumber, scrollOffset = rowOffset.roundToInt())
            }

            // When list scrolled
            LaunchedEffect(state.firstVisibleItemScrollOffset) {
                if (state.layoutInfo.totalItemsCount == 0 || isThumbDragged) return@LaunchedEffect
                val scrollOffset = computeGridScrollOffset(state = state, columnCount = columnCount)
                /*
                    LazyGridItemInfo doesn't always give the accurate height of the object, so we clamp the proportion
                    at 1 to ensure that there are no issues due to this -- ideally we would correctly compute the value
                 */
                val extraScrollRange = (scrollRange.toFloat() - heightPx).coerceAtLeast(1f)
                val proportion = (scrollOffset.toFloat() / extraScrollRange).coerceAtMost(1f)
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
            }

            val pageScope = rememberCoroutineScope()
            ScrollerBar(
                thumbOffsetY = thumbOffsetY,
                scrollInProgress = state.isScrollInProgress,
                thumbAllowed = thumbAllowed,
                thumbColor = thumbColor,
                dragInteractionSource = dragInteractionSource,
                onDrag = { delta ->
                    thumbOffsetY = (thumbOffsetY + delta).coerceIn(thumbTopPadding, thumbTopPadding + trackHeightPx)
                },
                onTrackPress = { towardsEnd ->
                    val page = PAGE_FRACTION * contentHeight
                    pageScope.launch { state.animateScrollBy(if (towardsEnd) page else -page) }
                },
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth + gutter, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth + gutter - scrollerWidth, 0)
            }
        }
    }
}

// TODO: not sure why abs corrections are in the following functions; these can probably be removed

private fun computeGridScrollOffset(state: LazyGridState, columnCount: Int): Int {
    if (state.layoutInfo.totalItemsCount == 0) return 0
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val startChild = visibleItems.first()
    val endChild = visibleItems.last()
    val laidOutArea = (endChild.offset.y + endChild.size.height) - startChild.offset.y
    val laidOutRows = 1 + abs(endChild.index - startChild.index) / columnCount
    val avgSizePerRow = laidOutArea.toFloat() / laidOutRows

    val rowsBefore = min(startChild.index, endChild.index).coerceAtLeast(0) / columnCount
    return (rowsBefore * avgSizePerRow - startChild.offset.y).roundToInt()
}

private fun computeGridScrollRange(state: LazyGridState, columnCount: Int): Int {
    if (state.layoutInfo.totalItemsCount == 0) return 0
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val startChild = visibleItems.first()
    val endChild = visibleItems.last()
    val laidOutArea = (endChild.offset.y + endChild.size.height) - startChild.offset.y
    val laidOutRows = 1 + abs(endChild.index - startChild.index) / columnCount
    val avgSizePerRow = laidOutArea.toFloat() / laidOutRows

    val totalRows = 1 + (state.layoutInfo.totalItemsCount - 1) / columnCount
    val endSpacing = avgSizePerRow - endChild.size.height
    return (endSpacing + (laidOutArea.toFloat() / laidOutRows) * totalRows).roundToInt()
}

private class MutableData<T>(var value: T)

/** The strip the scroller owns at the end: the content is measured without it, so rows never reach the thumb. */
private fun Constraints.withoutGutter(gutter: Int) = copy(
    minWidth = (minWidth - gutter).coerceAtLeast(0),
    maxWidth = if (hasBoundedWidth) (maxWidth - gutter).coerceAtLeast(0) else maxWidth,
)

object Scroller {
    const val STICKY_HEADER_KEY_PREFIX = "sticky:"
}

private val ThumbLength = 48.dp
private val ScrollerGutter = 18.dp
private val ThumbThickness = 6.dp

/** From the thumb's start edge to the end of the gutter: the least the content must leave free. */
private val ThumbClearance = (ScrollerGutter + ThumbThickness) / 2
private val ThumbThicknessHover = 10.dp
private val ThumbShape = RoundedCornerShape(50)
private const val RESTING_THUMB_ALPHA = 0.5f
private val TrackRepeatDelay = 400.milliseconds
private val TrackRepeatInterval = 120.milliseconds

internal const val THUMB_TAG = "scroller-thumb"
internal const val TRACK_TAG = "scroller-track"

internal fun thumbAlpha(allowed: Boolean, active: Boolean): Float = when {
    !allowed -> 0f
    active -> 1f
    else -> RESTING_THUMB_ALPHA
}

/**
 * The track and thumb both scrollers draw (spec 7 §3 D): always there when the content does not fit, wider under the
 * pointer, a thumb to drag, and a press on the track that pages towards the pointer, repeating while held.
 * [thumbOffsetY] is the thumb's top in the scroller's own pixels.
 */
@Composable
private fun ScrollerBar(
    thumbOffsetY: Float,
    scrollInProgress: Boolean,
    thumbAllowed: () -> Boolean,
    thumbColor: Color,
    dragInteractionSource: MutableInteractionSource,
    onDrag: (Float) -> Unit,
    onTrackPress: (towardsEnd: Boolean) -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val dragged by dragInteractionSource.collectIsDraggedAsState()
    val alpha by animateFloatAsState(thumbAlpha(thumbAllowed(), hovered || dragged || scrollInProgress))
    val thickness by animateDpAsState(if (hovered || dragged) ThumbThicknessHover else ThumbThickness)
    val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
    val currentOffset by rememberUpdatedState(thumbOffsetY)
    val currentTrackPress by rememberUpdatedState(onTrackPress)

    Box(
        Modifier
            .fillMaxHeight()
            .width(ScrollerGutter)
            .testTag(TRACK_TAG)
            .hoverable(hoverSource)
            .pointerInput(thumbHeightPx) {
                detectTapGestures(
                    onPress = { press ->
                        // null while the pointer is on the thumb, which has its own drag
                        fun towardsEnd(): Boolean? = when {
                            press.y < currentOffset -> false
                            press.y > currentOffset + thumbHeightPx -> true
                            else -> null
                        }
                        val first = towardsEnd() ?: return@detectTapGestures
                        currentTrackPress(first)
                        coroutineScope {
                            val repeat = launch {
                                delay(TrackRepeatDelay)
                                // Stops when the thumb reaches the pointer
                                while (towardsEnd() == first) {
                                    currentTrackPress(first)
                                    delay(TrackRepeatInterval)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                .testTag(THUMB_TAG)
                .draggable(
                    interactionSource = dragInteractionSource,
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState(onDrag),
                )
                .height(ThumbLength)
                .width(thickness)
                .alpha(alpha)
                .background(color = thumbColor, shape = ThumbShape),
        )
    }
}

private val LazyListItemInfo.top: Int
    get() = offset

private val LazyListItemInfo.bottom: Int
    get() = offset + size
