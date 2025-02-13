/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.redwood.flexbox

import app.cash.redwood.flexbox.FlexItem.Companion.DefaultFlexBasisPercent
import app.cash.redwood.flexbox.FlexItem.Companion.DefaultFlexGrow
import app.cash.redwood.flexbox.FlexItem.Companion.UndefinedFlexShrink
import app.cash.redwood.flexbox.Measurable.Companion.MatchParent
import kotlin.math.roundToInt

/**
 * A class that measures and positions its children according to its flex properties.
 */
public class FlexContainer {
  /**
   * If true, the container will fill the available width passed to it in [measure].
   */
  public var fillWidth: Boolean = false

  /**
   * If true, the container will fill the available height passed to it in [measure].
   */
  public var fillHeight: Boolean = false

  /**
   * The flex direction attribute of the container.
   */
  public var flexDirection: FlexDirection = FlexDirection.Row

  /**
   * The flex wrap attribute of the container.
   */
  public var flexWrap: FlexWrap = FlexWrap.NoWrap

  /**
   * The justify content attribute of the container.
   */
  public var justifyContent: JustifyContent = JustifyContent.FlexStart

  /**
   * The align content attribute of the container.
   */
  public var alignItems: AlignItems = AlignItems.FlexStart

  /**
   * The align items attribute of the container.
   */
  public var alignContent: AlignContent = AlignContent.FlexStart

  /**
   * The padding of the container.
   */
  public var padding: Spacing = Spacing.Zero

  /**
   * The current value of the maximum number of flex lines.
   */
  public var maxLines: Int = Int.MAX_VALUE

  /**
   * If true, the flex container rounds item positions to the nearest integer.
   */
  public var roundToInt: Boolean = false

  /**
   * Returns the items held in the container.
   */
  public val items: MutableList<FlexItem> = mutableListOf()

  //region measure

  /**
   * Measures the flexbox's items according to the parent's [widthSpec] and [heightSpec] and returns
   * the expected size of the container.
   *
   * @param widthSpec horizontal space requirements as imposed by the parent.
   * @param heightSpec vertical space requirements as imposed by the parent.
   */
  public fun measure(widthSpec: MeasureSpec, heightSpec: MeasureSpec): Size {
    val width = if (fillWidth && widthSpec.mode != MeasureSpecMode.Exactly && widthSpec.size > 0) {
      MeasureSpec.from(widthSpec.size, MeasureSpecMode.Exactly)
    } else {
      widthSpec
    }
    val height = if (fillHeight && heightSpec.mode != MeasureSpecMode.Exactly && heightSpec.size > 0) {
      MeasureSpec.from(heightSpec.size, MeasureSpecMode.Exactly)
    } else {
      heightSpec
    }

    if (flexDirection.isHorizontal) {
      return measureHorizontal(width, height)
    } else {
      return measureVertical(width, height)
    }
  }

  private fun measureHorizontal(
    widthSpec: MeasureSpec,
    heightSpec: MeasureSpec,
  ): Size {
    val flexLines = calculateFlexLines(widthSpec, heightSpec)
    determineMainSize(flexLines, widthSpec, heightSpec)

    if (alignItems == AlignItems.Baseline) {
      flexLines.forEachIndices { flexLine ->
        // The largest height value that also take the baseline shift into account
        var largestHeightInLine = Double.MIN_VALUE
        for (i in flexLine.indices) {
          val child = items[i]
          val heightInLine = if (flexWrap != FlexWrap.WrapReverse) {
            val marginTop = maxOf(flexLine.maxBaseline - child.baseline, child.margin.top)
            child.height + marginTop + child.margin.bottom
          } else {
            val marginBottom = maxOf(flexLine.maxBaseline - child.height + child.baseline, child.margin.bottom)
            child.height + child.margin.top + marginBottom
          }
          largestHeightInLine = maxOf(largestHeightInLine, heightInLine)
        }
        flexLine.crossSize = largestHeightInLine
      }
    }

    determineCrossSize(flexLines, widthSpec, heightSpec)
    stretchChildren(flexLines)
    val containerSize = calculateContainerSize(flexLines, widthSpec, heightSpec)
    layout(flexLines, containerSize)
    return containerSize
  }

  private fun measureVertical(
    widthSpec: MeasureSpec,
    heightSpec: MeasureSpec,
  ): Size {
    val flexLines = calculateFlexLines(widthSpec, heightSpec)
    determineMainSize(flexLines, widthSpec, heightSpec)
    determineCrossSize(flexLines, widthSpec, heightSpec)
    stretchChildren(flexLines)
    val containerSize = calculateContainerSize(flexLines, widthSpec, heightSpec)
    layout(flexLines, containerSize)
    return containerSize
  }

  /**
   * Calculates how many flex lines are needed in the flex container layout by measuring each child.
   * Expanding or shrinking the flex items depending on the flex grow and flex shrink
   * attributes are done in a later procedure, so the items' measured width and measured
   * height may be changed in a later process.
   */
  internal fun calculateFlexLines(
    widthMeasureSpec: MeasureSpec,
    heightMeasureSpec: MeasureSpec,
  ): MutableList<FlexLine> {
    val mainMeasureSpec: MeasureSpec
    val crossMeasureSpec: MeasureSpec
    if (flexDirection.isHorizontal) {
      mainMeasureSpec = widthMeasureSpec
      crossMeasureSpec = heightMeasureSpec
    } else {
      mainMeasureSpec = heightMeasureSpec
      crossMeasureSpec = widthMeasureSpec
    }
    val orientation = flexDirection.toOrientation()
    val mainMode = mainMeasureSpec.mode
    val mainSize = mainMeasureSpec.size
    val flexLines = ArrayList<FlexLine>(1)

    // The largest size in the cross axis.
    var maxCrossSize = Double.MIN_VALUE
    // The amount of cross size calculated in this method call.
    var sumCrossSize = 0.0
    // The index of the item in the flex line.
    var indexInFlexLine = 0

    var flexLine = FlexLine()
    flexLine.mainSize = orientation.mainPadding(padding)
    for (i in 0 until items.size) {
      val item = items[i]
      val childMainSize = if (item.flexBasisPercent != DefaultFlexBasisPercent && mainMode == MeasureSpecMode.Exactly) {
        roundIfEnabled(item.flexBasisPercent * mainSize)
      } else {
        orientation.mainSize(item)
      }

      val childMainMeasureSpec = MeasureSpec.getChildMeasureSpec(
        spec = mainMeasureSpec,
        padding = orientation.mainPadding(padding) + orientation.mainMargin(item),
        childDimension = childMainSize,
      )
      var childCrossMeasureSpec = MeasureSpec.getChildMeasureSpec(
        spec = crossMeasureSpec,
        padding = orientation.crossPadding(padding) + orientation.crossMargin(item) + sumCrossSize,
        childDimension = orientation.crossSize(item),
      )
      if (flexDirection.isHorizontal) {
        item.measure(childMainMeasureSpec, childCrossMeasureSpec)
      } else {
        item.measure(childCrossMeasureSpec, childMainMeasureSpec)
      }

      if (
        isWrapRequired(
          mode = mainMode,
          maxSize = mainSize,
          currentLength = flexLine.mainSize,
          childLength = orientation.mainMeasuredSizeWithMargin(item),
          flexItem = item,
          flexLinesSize = flexLines.size,
        )
      ) {
        if (flexLine.itemCount > 0) {
          flexLine.sumCrossSizeBefore = sumCrossSize
          flexLines += flexLine
          sumCrossSize += flexLine.crossSize
        }
        val crossSize = orientation.crossSize(item)
        if (crossSize == MatchParent) {
          // This case takes care of the corner case where the cross size of the
          // child is affected by the just added flex line.
          // E.g. in a row when the child's layout_height is set to match_parent, the
          // height of that child needs to be determined taking the total cross size
          // used so far into account. In that case, the height of the child needs to
          // be measured again note that we don't need to judge if the wrapping occurs
          // because it doesn't change the size along the main axis.
          childCrossMeasureSpec = MeasureSpec.getChildMeasureSpec(
            spec = crossMeasureSpec,
            padding = orientation.crossPadding(padding) + orientation.crossMargin(item) + sumCrossSize,
            childDimension = crossSize,
          )
          item.measure(childMainMeasureSpec, childCrossMeasureSpec)
        }
        flexLine = FlexLine()
        flexLine.itemCount = 1
        flexLine.mainSize = orientation.mainPadding(padding)
        flexLine.firstIndex = i
        indexInFlexLine = 0
        maxCrossSize = Double.MIN_VALUE
      } else {
        flexLine.itemCount++
        indexInFlexLine++
      }
      flexLine.anyItemsHaveFlexGrow = flexLine.anyItemsHaveFlexGrow || item.flexGrow != DefaultFlexGrow
      flexLine.anyItemsHaveFlexShrink = flexLine.anyItemsHaveFlexShrink || item.flexShrink != UndefinedFlexShrink
      flexLine.mainSize += orientation.mainMeasuredSizeWithMargin(item)
      flexLine.totalFlexGrow += item.flexGrow
      flexLine.totalFlexShrink += item.flexShrink
      maxCrossSize = maxOf(maxCrossSize, orientation.crossMeasuredSizeWithMargin(item))

      // Temporarily set the cross axis length as the largest child in the flexLine
      // Expand along the cross axis depending on the alignContent property if needed later
      flexLine.crossSize = maxOf(flexLine.crossSize, maxCrossSize)

      if (flexDirection.isHorizontal) {
        flexLine.maxBaseline = if (flexWrap != FlexWrap.WrapReverse) {
          maxOf(flexLine.maxBaseline, item.baseline + item.margin.top)
        } else {
          // If the flex wrap property is WrapReverse, calculate the
          // baseline as the distance from the cross end and the baseline
          // since the cross size calculation is based on the distance from the cross end
          maxOf(flexLine.maxBaseline, item.height - item.baseline + item.margin.bottom)
        }
      }

      if (isLastFlexItem(i, items.size, flexLine)) {
        flexLine.sumCrossSizeBefore = sumCrossSize
        flexLines += flexLine
        sumCrossSize += flexLine.crossSize
      }
    }
    return flexLines
  }

  /**
   * Determine if a wrap is required (add a new flex line).
   *
   * @param mode the width or height mode along the main axis direction
   * @param maxSize the max size along the main axis direction
   * @param currentLength the accumulated current length
   * @param childLength the length of a child which is to be collected to the flex line
   * @param flexItem the LayoutParams for the view being determined whether a new flex line is needed
   * @param flexLinesSize the number of the existing flexlines
   * @return `true` if a wrap is required, `false` otherwise
   */
  private fun isWrapRequired(
    mode: MeasureSpecMode,
    maxSize: Double,
    currentLength: Double,
    childLength: Double,
    flexItem: FlexItem,
    flexLinesSize: Int,
  ): Boolean {
    if (flexWrap == FlexWrap.NoWrap) {
      return false
    }
    if (flexItem.wrapBefore) {
      return true
    }
    if (mode == MeasureSpecMode.Unspecified) {
      return false
    }
    // Judge the condition by adding 1 to the current flexLinesSize because the flex line
    // being computed isn't added to the flexLinesSize.
    if (maxLines <= flexLinesSize + 1) {
      return false
    }
    return maxSize < currentLength + childLength
  }

  private fun isLastFlexItem(
    childIndex: Int,
    childCount: Int,
    flexLine: FlexLine,
  ): Boolean {
    return childIndex == childCount - 1 && flexLine.itemCount > 0
  }

  /**
   * Determine the main size by expanding (shrinking if negative remaining free space is given)
   * an individual child in each flex line if any children's flexGrow (or flexShrink if remaining
   * space is negative) properties are set to non-zero.
   */
  private fun determineMainSize(
    flexLines: List<FlexLine>,
    widthMeasureSpec: MeasureSpec,
    heightMeasureSpec: MeasureSpec,
  ) {
    // Holds the 'frozen' state of children during measure. If an item is frozen it will no longer
    // expand or shrink regardless of flex grow/flex shrink attributes.
    val childrenFrozen = BooleanArray(items.size)

    val mainSize: Double
    val paddingAlongMainAxis: Double
    if (flexDirection.isHorizontal) {
      mainSize = if (widthMeasureSpec.mode == MeasureSpecMode.Exactly) {
        widthMeasureSpec.size
      } else {
        minOf(flexLines.getLargestMainSize(), widthMeasureSpec.size)
      }
      paddingAlongMainAxis = padding.start + padding.end
    } else {
      mainSize = if (heightMeasureSpec.mode == MeasureSpecMode.Exactly) {
        heightMeasureSpec.size
      } else {
        flexLines.getLargestMainSize()
      }
      paddingAlongMainAxis = padding.top + padding.bottom
    }
    flexLines.forEachIndices { flexLine ->
      if (flexLine.mainSize < mainSize && flexLine.anyItemsHaveFlexGrow) {
        expandFlexItems(
          childrenFrozen = childrenFrozen,
          widthMeasureSpec = widthMeasureSpec,
          heightMeasureSpec = heightMeasureSpec,
          flexLine = flexLine,
          maxMainSize = mainSize,
          paddingAlongMainAxis = paddingAlongMainAxis,
          calledRecursively = false,
        )
      } else if (flexLine.mainSize > mainSize && flexLine.anyItemsHaveFlexShrink) {
        shrinkFlexItems(
          childrenFrozen = childrenFrozen,
          widthMeasureSpec = widthMeasureSpec,
          heightMeasureSpec = heightMeasureSpec,
          flexLine = flexLine,
          maxMainSize = mainSize,
          paddingAlongMainAxis = paddingAlongMainAxis,
          calledRecursively = false,
        )
      }
    }
  }

  /**
   * Expand the flex items along the main axis based on the individual [FlexItem.flexGrow] attribute.
   *
   * @param widthMeasureSpec the horizontal space requirements as imposed by the parent
   * @param heightMeasureSpec the vertical space requirements as imposed by the parent
   * @param flexLine the flex line to which flex items belong
   * @param maxMainSize the maximum main size. Expanded main size will be this size
   * @param paddingAlongMainAxis the padding value along the main axis
   * @param calledRecursively true if this method is called recursively, false otherwise
   */
  private fun expandFlexItems(
    childrenFrozen: BooleanArray,
    widthMeasureSpec: MeasureSpec,
    heightMeasureSpec: MeasureSpec,
    flexLine: FlexLine,
    maxMainSize: Double,
    paddingAlongMainAxis: Double,
    calledRecursively: Boolean,
  ) {
    if (flexLine.totalFlexGrow <= 0 || maxMainSize < flexLine.mainSize) {
      return
    }
    val sizeBeforeExpand = flexLine.mainSize
    var needsReexpand = false
    val unitSpace = (maxMainSize - flexLine.mainSize) / flexLine.totalFlexGrow
    flexLine.mainSize = paddingAlongMainAxis

    // Setting the cross size of the flex line as the temporal value since the cross size of
    // each flex item may be changed from the initial calculation
    // (in the measureHorizontal/measureVertical method) even this method is part of the main
    // size determination.
    // E.g. If a TextView's layout_width is set to 0dp, layout_height is set to wrap_content,
    // and layout_flexGrow is set to 1, the TextView is trying to expand to the vertical
    // direction to enclose its content (in the measureHorizontal method), but
    // the width will be expanded in this method. In that case, the height needs to be measured
    // again with the expanded width.
    var largestCrossSize = 0.0
    if (!calledRecursively) {
      flexLine.crossSize = Double.MIN_VALUE
    }
    var accumulatedRoundError = 0.0
    for (i in flexLine.indices) {
      val child = items[i]
      val measurable = child.measurable
      if (flexDirection.isHorizontal) {
        // The direction of the main axis is horizontal
        if (!childrenFrozen[i] && child.flexGrow > 0) {
          var rawCalculatedWidth = (child.width + unitSpace * child.flexGrow)
          if (i == flexLine.lastIndex) {
            rawCalculatedWidth += accumulatedRoundError
            accumulatedRoundError = 0.0
          }
          var newWidth = roundIfEnabled(rawCalculatedWidth)
          val maxWidth = measurable.maxWidth
          if (newWidth > maxWidth) {
            // This means the child can't expand beyond the value of the maxWidth attribute.
            // To adjust the flex line length to the size of maxMainSize, remaining
            // positive free space needs to be re-distributed to other flex items
            // (children items). In that case, invoke this method again with the same fromIndex.
            needsReexpand = true
            newWidth = maxWidth
            childrenFrozen[i] = true
            flexLine.totalFlexGrow -= child.flexGrow
          } else {
            accumulatedRoundError += rawCalculatedWidth - newWidth
            if (accumulatedRoundError > 1.0) {
              newWidth += 1
              accumulatedRoundError -= 1.0
            } else if (accumulatedRoundError < -1.0) {
              newWidth -= 1
              accumulatedRoundError += 1.0
            }
          }
          val childHeightMeasureSpec = getChildHeightMeasureSpecInternal(
            heightMeasureSpec = heightMeasureSpec,
            flexItem = child,
            padding = flexLine.sumCrossSizeBefore,
          )
          child.height = when (childHeightMeasureSpec.mode) {
            MeasureSpecMode.Exactly -> childHeightMeasureSpec.size
            MeasureSpecMode.AtMost -> child.measurable.height(newWidth).coerceAtMost(childHeightMeasureSpec.size)
            MeasureSpecMode.Unspecified -> child.measurable.height(newWidth)
            else -> throw AssertionError()
          }
          child.width = newWidth
        }
        largestCrossSize = maxOf(largestCrossSize, child.height + child.margin.top + child.margin.bottom)
        flexLine.mainSize += (child.width + child.margin.start + child.margin.end)
      } else {
        // The direction of the main axis is vertical
        if (!childrenFrozen[i] && child.flexGrow > 0) {
          var rawCalculatedHeight = (child.height + unitSpace * child.flexGrow)
          if (i == flexLine.lastIndex) {
            rawCalculatedHeight += accumulatedRoundError
            accumulatedRoundError = 0.0
          }
          var newHeight = roundIfEnabled(rawCalculatedHeight)
          val maxHeight = measurable.maxHeight
          if (newHeight > maxHeight) {
            // This means the child can't expand beyond the value of the maxHeight attribute.
            // To adjust the flex line length to the size of maxMainSize, remaining
            // positive free space needs to be re-distributed to other flex items
            // (children items). In that case, invoke this method again with the same fromIndex.
            needsReexpand = true
            newHeight = maxHeight
            childrenFrozen[i] = true
            flexLine.totalFlexGrow -= child.flexGrow
          } else {
            accumulatedRoundError += rawCalculatedHeight - newHeight
            if (accumulatedRoundError > 1.0) {
              newHeight += 1
              accumulatedRoundError -= 1.0
            } else if (accumulatedRoundError < -1.0) {
              newHeight -= 1
              accumulatedRoundError += 1.0
            }
          }
          val childWidthMeasureSpec = getChildWidthMeasureSpecInternal(
            widthMeasureSpec = widthMeasureSpec,
            flexItem = child,
            padding = flexLine.sumCrossSizeBefore,
          )
          child.width = when (childWidthMeasureSpec.mode) {
            MeasureSpecMode.Exactly -> childWidthMeasureSpec.size
            MeasureSpecMode.AtMost -> child.measurable.width(newHeight).coerceAtMost(childWidthMeasureSpec.size)
            MeasureSpecMode.Unspecified -> child.measurable.width(newHeight)
            else -> throw AssertionError()
          }
          child.height = newHeight
        }
        largestCrossSize = maxOf(largestCrossSize, child.width + child.margin.start + child.margin.end)
        flexLine.mainSize += (child.height + child.margin.top + child.margin.bottom)
      }
      flexLine.crossSize = maxOf(flexLine.crossSize, largestCrossSize)
    }
    if (needsReexpand && sizeBeforeExpand != flexLine.mainSize) {
      // Re-invoke the method with the same flex line to distribute the positive free space
      // that wasn't fully distributed (because of maximum length constraint)
      expandFlexItems(
        childrenFrozen = childrenFrozen,
        widthMeasureSpec = widthMeasureSpec,
        heightMeasureSpec = heightMeasureSpec,
        flexLine = flexLine,
        maxMainSize = maxMainSize,
        paddingAlongMainAxis = paddingAlongMainAxis,
        calledRecursively = true,
      )
    }
  }

  /**
   * Shrink the flex items along the main axis based on the individual [FlexItem.flexShrink] attribute.
   *
   * @param widthMeasureSpec the horizontal space requirements as imposed by the parent
   * @param heightMeasureSpec the vertical space requirements as imposed by the parent
   * @param flexLine the flex line to which flex items belong
   * @param maxMainSize the maximum main size. Shrank main size will be this size
   * @param paddingAlongMainAxis the padding value along the main axis
   * @param calledRecursively true if this method is called recursively, false otherwise
   */
  private fun shrinkFlexItems(
    childrenFrozen: BooleanArray,
    widthMeasureSpec: MeasureSpec,
    heightMeasureSpec: MeasureSpec,
    flexLine: FlexLine,
    maxMainSize: Double,
    paddingAlongMainAxis: Double,
    calledRecursively: Boolean,
  ) {
    val sizeBeforeShrink = flexLine.mainSize
    if (flexLine.totalFlexShrink <= 0 || maxMainSize > flexLine.mainSize) {
      return
    }
    var needsReshrink = false
    val unitShrink = (flexLine.mainSize - maxMainSize) / flexLine.totalFlexShrink
    var accumulatedRoundError = 0.0
    flexLine.mainSize = paddingAlongMainAxis

    // Setting the cross size of the flex line as the temporal value since the cross size of
    // each flex item may be changed from the initial calculation
    // (in the measureHorizontal/measureVertical method) even this method is part of the main
    // size determination.
    // E.g. If a TextView's layout_width is set to 0dp, layout_height is set to wrap_content,
    // and layout_flexGrow is set to 1, the TextView is trying to expand to the vertical
    // direction to enclose its content (in the measureHorizontal method), but
    // the width will be expanded in this method. In that case, the height needs to be measured
    // again with the expanded width.
    var largestCrossSize = 0.0
    if (!calledRecursively) {
      flexLine.crossSize = Double.MIN_VALUE
    }
    for (i in flexLine.indices) {
      val child = items[i]
      val measurable = child.measurable
      if (flexDirection.isHorizontal) {
        // The direction of main axis is horizontal
        if (!childrenFrozen[i] && child.flexShrink > 0) {
          var rawCalculatedWidth = child.width - unitShrink * child.flexShrink
          if (i == flexLine.lastIndex) {
            rawCalculatedWidth += accumulatedRoundError
            accumulatedRoundError = 0.0
          }
          var newWidth = roundIfEnabled(rawCalculatedWidth)
          val minWidth = measurable.minWidth
          if (newWidth < minWidth) {
            // This means the child doesn't have enough space to distribute the negative
            // free space. To adjust the flex line length down to the maxMainSize, remaining
            // negative free space needs to be re-distributed to other flex items (children
            // items). In that case, invoke this method again with the same fromIndex.
            needsReshrink = true
            newWidth = minWidth
            childrenFrozen[i] = true
            flexLine.totalFlexShrink -= child.flexShrink
          } else {
            accumulatedRoundError += rawCalculatedWidth - newWidth
            if (accumulatedRoundError > 1.0) {
              newWidth += 1
              accumulatedRoundError -= 1
            } else if (accumulatedRoundError < -1.0) {
              newWidth -= 1
              accumulatedRoundError += 1
            }
          }
          val childHeightMeasureSpec = getChildHeightMeasureSpecInternal(
            heightMeasureSpec = heightMeasureSpec,
            flexItem = child,
            padding = flexLine.sumCrossSizeBefore,
          )
          child.height = when (childHeightMeasureSpec.mode) {
            MeasureSpecMode.Exactly -> childHeightMeasureSpec.size
            MeasureSpecMode.AtMost -> child.measurable.height(newWidth).coerceAtMost(childHeightMeasureSpec.size)
            MeasureSpecMode.Unspecified -> child.measurable.height(newWidth)
            else -> throw AssertionError()
          }
          child.width = newWidth
        }
        largestCrossSize = maxOf(largestCrossSize, child.height + child.margin.top + child.margin.bottom)
        flexLine.mainSize += child.width + child.margin.start + child.margin.end
      } else {
        // The direction of main axis is vertical
        if (!childrenFrozen[i] && child.flexShrink > 0) {
          var rawCalculatedHeight = child.height - unitShrink * child.flexShrink
          if (i == flexLine.lastIndex) {
            rawCalculatedHeight += accumulatedRoundError
            accumulatedRoundError = 0.0
          }
          var newHeight = roundIfEnabled(rawCalculatedHeight)
          val minHeight = measurable.minHeight
          if (newHeight < minHeight) {
            // Need to invoke this method again like the case flex direction is vertical
            needsReshrink = true
            newHeight = minHeight
            childrenFrozen[i] = true
            flexLine.totalFlexShrink -= child.flexShrink
          } else {
            accumulatedRoundError += rawCalculatedHeight - newHeight
            if (accumulatedRoundError > 1.0) {
              newHeight += 1
              accumulatedRoundError -= 1
            } else if (accumulatedRoundError < -1.0) {
              newHeight -= 1
              accumulatedRoundError += 1
            }
          }
          val childWidthMeasureSpec = getChildWidthMeasureSpecInternal(
            widthMeasureSpec = widthMeasureSpec,
            flexItem = child,
            padding = flexLine.sumCrossSizeBefore,
          )
          child.width = when (childWidthMeasureSpec.mode) {
            MeasureSpecMode.Exactly -> childWidthMeasureSpec.size
            MeasureSpecMode.AtMost -> child.measurable.width(newHeight).coerceAtMost(childWidthMeasureSpec.size)
            MeasureSpecMode.Unspecified -> child.measurable.width(newHeight)
            else -> throw AssertionError()
          }
          child.height = newHeight
        }
        largestCrossSize = maxOf(largestCrossSize, child.width + child.margin.start + child.margin.end)
        flexLine.mainSize += (child.height + child.margin.top + child.margin.bottom)
      }
      flexLine.crossSize = maxOf(flexLine.crossSize, largestCrossSize)
    }
    if (needsReshrink && sizeBeforeShrink != flexLine.mainSize) {
      // Re-invoke the method with the same fromIndex to distribute the negative free space
      // that wasn't fully distributed (because some items length were not enough)
      shrinkFlexItems(
        childrenFrozen = childrenFrozen,
        widthMeasureSpec = widthMeasureSpec,
        heightMeasureSpec = heightMeasureSpec,
        flexLine = flexLine,
        maxMainSize = maxMainSize,
        paddingAlongMainAxis = paddingAlongMainAxis,
        calledRecursively = true,
      )
    }
  }

  private fun getChildWidthMeasureSpecInternal(
    widthMeasureSpec: MeasureSpec,
    flexItem: FlexItem,
    padding: Double,
  ): MeasureSpec {
    val measurable = flexItem.measurable
    val childWidthMeasureSpec = MeasureSpec.getChildMeasureSpec(
      spec = widthMeasureSpec,
      padding = this.padding.start + this.padding.end + flexItem.margin.start + flexItem.margin.end + padding,
      childDimension = measurable.requestedWidth,
    )
    val childWidth = childWidthMeasureSpec.size
    val maxWidth = measurable.maxWidth
    if (childWidth > maxWidth) {
      return MeasureSpec.from(maxWidth, childWidthMeasureSpec.mode)
    }
    val minWidth = measurable.minWidth
    if (childWidth < minWidth) {
      return MeasureSpec.from(minWidth, childWidthMeasureSpec.mode)
    }
    return childWidthMeasureSpec
  }

  private fun getChildHeightMeasureSpecInternal(
    heightMeasureSpec: MeasureSpec,
    flexItem: FlexItem,
    padding: Double,
  ): MeasureSpec {
    val measurable = flexItem.measurable
    val childHeightMeasureSpec = MeasureSpec.getChildMeasureSpec(
      spec = heightMeasureSpec,
      padding = this.padding.top + this.padding.bottom + flexItem.margin.top + flexItem.margin.bottom + padding,
      childDimension = measurable.requestedHeight,
    )
    val childHeight = childHeightMeasureSpec.size
    val maxHeight = measurable.maxHeight
    if (childHeight > maxHeight) {
      return MeasureSpec.from(maxHeight, childHeightMeasureSpec.mode)
    }
    val minHeight = measurable.minHeight
    if (childHeight < minHeight) {
      return MeasureSpec.from(minHeight, childHeightMeasureSpec.mode)
    }
    return childHeightMeasureSpec
  }

  /**
   * Determines the cross size (Calculate the length along the cross axis).
   * Expand the cross size only if the height mode is [MeasureSpecMode.Exactly], otherwise
   * use the sum of cross sizes of all flex lines.
   *
   * @param widthMeasureSpec horizontal space requirements as imposed by the parent
   * @param heightMeasureSpec vertical space requirements as imposed by the parent
   */
  private fun determineCrossSize(
    flexLines: MutableList<FlexLine>,
    widthMeasureSpec: MeasureSpec,
    heightMeasureSpec: MeasureSpec,
  ) {
    val paddingAlongCrossAxis = flexDirection.toOrientation().crossPadding(padding)
    val mode: MeasureSpecMode // The MeasureSpec mode along the cross axis
    val size: Double // The MeasureSpec size along the cross axis
    if (flexDirection.isHorizontal) {
      mode = heightMeasureSpec.mode
      size = heightMeasureSpec.size
    } else {
      mode = widthMeasureSpec.mode
      size = widthMeasureSpec.size
    }
    if (mode == MeasureSpecMode.Exactly) {
      val totalCrossSize = flexLines.getSumOfCrossSize() + paddingAlongCrossAxis
      if (flexLines.size == 1) {
        flexLines[0].crossSize = size - paddingAlongCrossAxis
        // alignContent property is valid only if the Flexbox has at least two lines
      } else if (flexLines.size >= 2) {
        when (alignContent) {
          AlignContent.Stretch -> run switch@{
            if (totalCrossSize >= size) {
              return@switch
            }
            val freeSpaceUnit = (size - totalCrossSize) / flexLines.size
            var accumulatedError = 0.0
            var i = 0
            while (i < flexLines.size) {
              val flexLine = flexLines[i]
              var newCrossSizeAsFloat = flexLine.crossSize + freeSpaceUnit
              if (i == flexLines.lastIndex) {
                newCrossSizeAsFloat += accumulatedError
                accumulatedError = 0.0
              }
              var newCrossSize = roundIfEnabled(newCrossSizeAsFloat)
              accumulatedError += newCrossSizeAsFloat - newCrossSize
              if (accumulatedError > 1) {
                newCrossSize += 1
                accumulatedError -= 1
              } else if (accumulatedError < -1) {
                newCrossSize -= 1
                accumulatedError += 1
              }
              flexLine.crossSize = newCrossSize
              i++
            }
          }
          AlignContent.SpaceAround -> run switch@{
            if (totalCrossSize >= size) {
              // If the size of the content is larger than the flex container, the
              // Flex lines should be aligned center like ALIGN_CONTENT_CENTER
              flexLines.clear()
              flexLines += constructFlexLinesForAlignContentCenter(
                flexLines = flexLines,
                size = size,
                totalCrossSize = totalCrossSize,
              )
              return@switch
            }
            // The value of free space along the cross axis which needs to be put on top
            // and below the bottom of each flex line.
            var spaceTopAndBottom = size - totalCrossSize
            // The number of spaces along the cross axis
            val numberOfSpaces = flexLines.size * 2
            spaceTopAndBottom /= numberOfSpaces
            val newFlexLines = ArrayList<FlexLine>()
            val dummySpaceFlexLine = FlexLine()
            dummySpaceFlexLine.crossSize = spaceTopAndBottom
            flexLines.forEachIndices { flexLine ->
              newFlexLines += dummySpaceFlexLine
              newFlexLines += flexLine
              newFlexLines += dummySpaceFlexLine
            }
            flexLines.clear()
            flexLines += newFlexLines
          }
          AlignContent.SpaceBetween -> run switch@{
            if (totalCrossSize >= size) {
              return@switch
            }
            // The value of free space along the cross axis between each flex line.
            var spaceBetweenFlexLine = size - totalCrossSize
            val numberOfSpaces = flexLines.lastIndex
            spaceBetweenFlexLine /= numberOfSpaces
            var accumulatedError = 0.0
            val newFlexLines = ArrayList<FlexLine>()
            var i = 0
            while (i < flexLines.size) {
              val flexLine = flexLines[i]
              newFlexLines += flexLine
              if (i != flexLines.lastIndex) {
                val dummySpaceFlexLine = FlexLine()
                if (i == flexLines.size - 2) {
                  // The last dummy space block in the flex container.
                  // Adjust the cross size by the accumulated error.
                  dummySpaceFlexLine.crossSize = roundIfEnabled(spaceBetweenFlexLine + accumulatedError)
                  accumulatedError = 0.0
                } else {
                  dummySpaceFlexLine.crossSize = roundIfEnabled(spaceBetweenFlexLine)
                }
                accumulatedError += (spaceBetweenFlexLine - dummySpaceFlexLine.crossSize)
                if (accumulatedError > 1) {
                  dummySpaceFlexLine.crossSize += 1
                  accumulatedError -= 1
                } else if (accumulatedError < -1) {
                  dummySpaceFlexLine.crossSize -= 1
                  accumulatedError += 1
                }
                newFlexLines += dummySpaceFlexLine
              }
              i++
            }
            flexLines.clear()
            flexLines += newFlexLines
          }
          AlignContent.Center -> {
            flexLines.clear()
            flexLines += constructFlexLinesForAlignContentCenter(
              flexLines = flexLines,
              size = size,
              totalCrossSize = totalCrossSize,
            )
          }
          AlignContent.FlexEnd -> {
            val spaceTop = size - totalCrossSize
            val dummySpaceFlexLine = FlexLine()
            dummySpaceFlexLine.crossSize = spaceTop
            flexLines.add(0, dummySpaceFlexLine)
          }
        }
      }
    }
  }

  private fun constructFlexLinesForAlignContentCenter(
    flexLines: List<FlexLine>,
    size: Double,
    totalCrossSize: Double,
  ): List<FlexLine> {
    val spaceAboveAndBottom = (size - totalCrossSize) / 2
    val newFlexLines = ArrayList<FlexLine>()
    val dummySpaceFlexLine = FlexLine()
    dummySpaceFlexLine.crossSize = spaceAboveAndBottom
    for (i in flexLines.indices) {
      if (i == 0) {
        newFlexLines += dummySpaceFlexLine
      }
      val flexLine = flexLines[i]
      newFlexLines += flexLine
      if (i == flexLines.lastIndex) {
        newFlexLines += dummySpaceFlexLine
      }
    }
    return newFlexLines
  }

  /**
   * Expand the item if the [FlexContainer.alignItems] attribute is set to
   * [AlignItems.Stretch] or [FlexItem.alignSelf] is set as [AlignItems.Stretch].
   */
  private fun stretchChildren(flexLines: List<FlexLine>) {
    if (alignItems == AlignItems.Stretch) {
      for (flexLine in flexLines) {
        for (i in flexLine.indices) {
          val item = items[i]
          if (item.alignSelf != AlignSelf.Auto && item.alignSelf != AlignSelf.Stretch) {
            continue
          }
          if (flexDirection.isHorizontal) {
            stretchItemVertically(item, flexLine.crossSize)
          } else {
            stretchItemHorizontally(item, flexLine.crossSize)
          }
        }
      }
    } else {
      flexLines.forEachIndices { flexLine ->
        for (index in flexLine.indices) {
          val item = items[index]
          if (item.alignSelf == AlignSelf.Stretch) {
            if (flexDirection.isHorizontal) {
              stretchItemVertically(item, flexLine.crossSize)
            } else {
              stretchItemHorizontally(item, flexLine.crossSize)
            }
          }
        }
      }
    }
  }

  /**
   * Expand the item vertically to the size of the [crossSize] (considering [item]'s margins).
   */
  private fun stretchItemVertically(item: FlexItem, crossSize: Double) {
    item.height = (crossSize - item.margin.top - item.margin.bottom)
      .coerceIn(item.measurable.minHeight, item.measurable.maxHeight)
  }

  /**
   * Expand the item horizontally to the size of the crossSize (considering [item]'s margins).
   */
  private fun stretchItemHorizontally(item: FlexItem, crossSize: Double) {
    item.width = (crossSize - item.margin.start - item.margin.end)
      .coerceIn(item.measurable.minWidth, item.measurable.maxWidth)
  }

  /**
   * Set this flexbox's width and height depending on the calculated size of main axis and
   * cross axis.
   *
   * @param widthMeasureSpec horizontal space requirements as imposed by the parent
   * @param heightMeasureSpec vertical space requirements as imposed by the parent
   */
  private fun calculateContainerSize(
    flexLines: List<FlexLine>,
    widthMeasureSpec: MeasureSpec,
    heightMeasureSpec: MeasureSpec,
  ): Size {
    val calculatedMaxWidth: Double
    val calculatedMaxHeight: Double
    if (flexDirection.isHorizontal) {
      calculatedMaxWidth = flexLines.getLargestMainSize()
      calculatedMaxHeight = flexLines.getSumOfCrossSize() + padding.top + padding.bottom
    } else {
      calculatedMaxWidth = flexLines.getSumOfCrossSize() + padding.start + padding.end
      calculatedMaxHeight = flexLines.getLargestMainSize()
    }
    val width = when (widthMeasureSpec.mode) {
      MeasureSpecMode.Exactly -> {
        MeasureSpec.resolveSize(widthMeasureSpec.size, widthMeasureSpec)
      }
      MeasureSpecMode.AtMost -> {
        MeasureSpec.resolveSize(minOf(widthMeasureSpec.size, calculatedMaxWidth), widthMeasureSpec)
      }
      MeasureSpecMode.Unspecified -> {
        MeasureSpec.resolveSize(calculatedMaxWidth, widthMeasureSpec)
      }
      else -> throw AssertionError()
    }
    val height = when (heightMeasureSpec.mode) {
      MeasureSpecMode.Exactly -> {
        MeasureSpec.resolveSize(heightMeasureSpec.size, heightMeasureSpec)
      }
      MeasureSpecMode.AtMost -> {
        MeasureSpec.resolveSize(minOf(heightMeasureSpec.size, calculatedMaxHeight), heightMeasureSpec)
      }
      MeasureSpecMode.Unspecified -> {
        MeasureSpec.resolveSize(calculatedMaxHeight, heightMeasureSpec)
      }
      else -> throw AssertionError()
    }
    return Size(width, height)
  }

  private fun roundIfEnabled(value: Double): Double {
    return if (roundToInt) value.roundToInt().toDouble() else value
  }

  //endregion
  //region layout

  /**
   * Compute the left/top/right/bottom coordinates for each [FlexItem] in [items].
   */
  private fun layout(flexLines: List<FlexLine>, containerSize: Size) {
    when (flexDirection) {
      FlexDirection.Row -> {
        layoutHorizontal(
          flexLines = flexLines,
          width = containerSize.width,
          height = containerSize.height,
          rightToLeft = false
        )
      }
      FlexDirection.RowReverse -> {
        layoutHorizontal(
          flexLines = flexLines,
          width = containerSize.width,
          height = containerSize.height,
          rightToLeft = true
        )
      }
      FlexDirection.Column -> {
        layoutVertical(
          flexLines = flexLines,
          width = containerSize.width,
          height = containerSize.height,
          rightToLeft = flexWrap == FlexWrap.WrapReverse,
          bottomToTop = false
        )
      }
      FlexDirection.ColumnReverse -> {
        layoutVertical(
          flexLines = flexLines,
          width = containerSize.width,
          height = containerSize.height,
          rightToLeft = flexWrap != FlexWrap.WrapReverse,
          bottomToTop = true
        )
      }
      else -> throw AssertionError()
    }
  }

  /** Layout all [items] along the horizontal axis. */
  private fun layoutHorizontal(
    flexLines: List<FlexLine>,
    width: Double,
    height: Double,
    rightToLeft: Boolean,
  ) {
    val paddingLeft = padding.start
    val paddingRight = padding.end
    var childBottom = height - padding.bottom
    var childTop = padding.top
    var childLeft: Double
    var childRight: Double
    flexLines.forEachIndices { flexLine ->
      var spaceBetweenItem = 0.0
      when (justifyContent) {
        JustifyContent.FlexStart -> {
          childLeft = paddingLeft
          childRight = width - paddingRight
        }
        JustifyContent.FlexEnd -> {
          childLeft = width - flexLine.mainSize + paddingRight
          childRight = flexLine.mainSize - paddingLeft
        }
        JustifyContent.Center -> {
          childLeft = paddingLeft + (width - flexLine.mainSize) / 2
          childRight = width - paddingRight - (width - flexLine.mainSize) / 2
        }
        JustifyContent.SpaceAround -> {
          if (flexLine.itemCount != 0) {
            spaceBetweenItem = ((width - flexLine.mainSize) / flexLine.itemCount)
          }
          childLeft = paddingLeft + spaceBetweenItem / 2
          childRight = width - paddingRight - spaceBetweenItem / 2
        }
        JustifyContent.SpaceBetween -> {
          childLeft = paddingLeft
          val denominator = if (flexLine.itemCount != 1) flexLine.itemCount - 1 else 1
          spaceBetweenItem = (width - flexLine.mainSize) / denominator
          childRight = width - paddingRight
        }
        JustifyContent.SpaceEvenly -> {
          if (flexLine.itemCount != 0) {
            spaceBetweenItem = (width - flexLine.mainSize) / (flexLine.itemCount + 1)
          }
          childLeft = paddingLeft + spaceBetweenItem
          childRight = width - paddingRight - spaceBetweenItem
        }
        else -> throw AssertionError()
      }
      spaceBetweenItem = maxOf(spaceBetweenItem, 0.0)
      for (i in flexLine.indices) {
        val child = items[i]
        childLeft += child.margin.start
        childRight -= child.margin.end
        if (flexWrap == FlexWrap.WrapReverse) {
          if (rightToLeft) {
            layoutSingleChildHorizontal(
              item = child,
              line = flexLine,
              left = childRight - child.width,
              top = childBottom - child.height,
              right = childRight,
              bottom = childBottom,
            )
          } else {
            layoutSingleChildHorizontal(
              item = child,
              line = flexLine,
              left = childLeft,
              top = childBottom - child.height,
              right = childLeft + child.width,
              bottom = childBottom,
            )
          }
        } else {
          if (rightToLeft) {
            layoutSingleChildHorizontal(
              item = child,
              line = flexLine,
              left = childRight - child.width,
              top = childTop,
              right = childRight,
              bottom = childTop + child.height,
            )
          } else {
            layoutSingleChildHorizontal(
              item = child,
              line = flexLine,
              left = childLeft,
              top = childTop,
              right = childLeft + child.width,
              bottom = childTop + child.height,
            )
          }
        }
        childLeft += child.width + spaceBetweenItem + child.margin.end
        childRight -= child.width + spaceBetweenItem + child.margin.start
      }
      childTop += flexLine.crossSize
      childBottom -= flexLine.crossSize
    }
  }

  /** Layout all [items] along the vertical axis. */
  private fun layoutVertical(
    flexLines: List<FlexLine>,
    width: Double,
    height: Double,
    rightToLeft: Boolean,
    bottomToTop: Boolean,
  ) {
    val paddingTop = padding.top
    val paddingBottom = padding.bottom
    val paddingRight = padding.end
    var childLeft = padding.start
    var childRight = width - paddingRight
    var childTop: Double
    var childBottom: Double
    flexLines.forEachIndices { flexLine ->
      var spaceBetweenItem = 0.0
      when (justifyContent) {
        JustifyContent.FlexStart -> {
          childTop = paddingTop
          childBottom = height - paddingBottom
        }
        JustifyContent.FlexEnd -> {
          childTop = height - flexLine.mainSize + paddingBottom
          childBottom = flexLine.mainSize - paddingTop
        }
        JustifyContent.Center -> {
          childTop = paddingTop + (height - flexLine.mainSize) / 2
          childBottom = height - paddingBottom - (height - flexLine.mainSize) / 2
        }
        JustifyContent.SpaceAround -> {
          if (flexLine.itemCount != 0) {
            spaceBetweenItem = ((height - flexLine.mainSize) / flexLine.itemCount)
          }
          childTop = paddingTop + spaceBetweenItem / 2
          childBottom = height - paddingBottom - spaceBetweenItem / 2
        }
        JustifyContent.SpaceBetween -> {
          childTop = paddingTop
          val denominator = if (flexLine.itemCount != 1) flexLine.itemCount - 1 else 1
          spaceBetweenItem = (height - flexLine.mainSize) / denominator
          childBottom = height - paddingBottom
        }
        JustifyContent.SpaceEvenly -> {
          if (flexLine.itemCount != 0) {
            spaceBetweenItem = ((height - flexLine.mainSize) / (flexLine.itemCount + 1))
          }
          childTop = paddingTop + spaceBetweenItem
          childBottom = height - paddingBottom - spaceBetweenItem
        }
        else -> throw AssertionError()
      }
      spaceBetweenItem = maxOf(spaceBetweenItem, 0.0)
      for (i in flexLine.indices) {
        val child = items[i]
        childTop += child.margin.top
        childBottom -= child.margin.bottom
        if (rightToLeft) {
          if (bottomToTop) {
            layoutSingleChildVertical(
              item = child,
              line = flexLine,
              left = childRight - child.width,
              top = childBottom - child.height,
              right = childRight,
              bottom = childBottom,
              rightToLeft = true,
            )
          } else {
            layoutSingleChildVertical(
              item = child,
              line = flexLine,
              left = childRight - child.width,
              top = childTop,
              right = childRight,
              bottom = childTop + child.height,
              rightToLeft = true,
            )
          }
        } else {
          if (bottomToTop) {
            layoutSingleChildVertical(
              item = child,
              line = flexLine,
              left = childLeft,
              top = childBottom - child.height,
              right = childLeft + child.width,
              bottom = childBottom,
              rightToLeft = false,
            )
          } else {
            layoutSingleChildVertical(
              item = child,
              line = flexLine,
              left = childLeft,
              top = childTop,
              right = childLeft + child.width,
              bottom = childTop + child.height,
              rightToLeft = false,
            )
          }
        }
        childTop += child.height + spaceBetweenItem + child.margin.bottom
        childBottom -= child.height + spaceBetweenItem + child.margin.top
      }
      childLeft += flexLine.crossSize
      childRight -= flexLine.crossSize
    }
  }

  /** Layout a single [item] along the horizontal axis. */
  private fun layoutSingleChildHorizontal(
    item: FlexItem,
    line: FlexLine,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
  ) {
    var alignItems = alignItems
    if (item.alignSelf != AlignSelf.Auto) {
      alignItems = item.alignSelf.toAlignItems()
    }
    val crossSize = line.crossSize
    when (alignItems) {
      AlignItems.FlexStart, AlignItems.Stretch -> if (flexWrap != FlexWrap.WrapReverse) {
        item.layout(left, top + item.margin.top, right, bottom + item.margin.top)
      } else {
        item.layout(left, top - item.margin.bottom, right, bottom - item.margin.bottom)
      }
      AlignItems.Baseline -> if (flexWrap != FlexWrap.WrapReverse) {
        val marginTop = maxOf(line.maxBaseline - item.baseline, item.margin.top)
        item.layout(left, top + marginTop, right, bottom + marginTop)
      } else {
        val marginBottom = maxOf(line.maxBaseline - item.height + item.baseline, item.margin.bottom)
        item.layout(left, top - marginBottom, right, bottom - marginBottom)
      }
      AlignItems.FlexEnd -> if (flexWrap != FlexWrap.WrapReverse) {
        item.layout(
          left = left,
          top = top + crossSize - item.height - item.margin.bottom,
          right = right,
          bottom = top + crossSize - item.margin.bottom,
        )
      } else {
        item.layout(
          left = left,
          top = top - crossSize + item.height + item.margin.top,
          right = right,
          bottom = bottom - crossSize + item.height + item.margin.top,
        )
      }
      AlignItems.Center -> {
        val topFromCrossAxis = (crossSize - item.height + item.margin.top - item.margin.bottom) / 2
        if (flexWrap != FlexWrap.WrapReverse) {
          item.layout(
            left = left,
            top = top + topFromCrossAxis,
            right = right,
            bottom = top + topFromCrossAxis + item.height,
          )
        } else {
          item.layout(
            left = left,
            top = top - topFromCrossAxis,
            right = right,
            bottom = top - topFromCrossAxis + item.height,
          )
        }
      }
    }
  }

  /** Layout a single [item] along the vertical axis. */
  private fun layoutSingleChildVertical(
    item: FlexItem,
    line: FlexLine,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
    rightToLeft: Boolean,
  ) {
    var alignItems = alignItems
    if (item.alignSelf != AlignSelf.Auto) {
      alignItems = item.alignSelf.toAlignItems()
    }
    val crossSize = line.crossSize
    when (alignItems) {
      AlignItems.FlexStart, AlignItems.Stretch, AlignItems.Baseline -> if (!rightToLeft) {
        item.layout(
          left = left + item.margin.start,
          top = top,
          right = right + item.margin.start,
          bottom = bottom,
        )
      } else {
        item.layout(
          left = left - item.margin.end,
          top = top,
          right = right - item.margin.end,
          bottom = bottom,
        )
      }
      AlignItems.FlexEnd -> if (!rightToLeft) {
        item.layout(
          left = left + crossSize - item.width - item.margin.end,
          top = top,
          right = right + crossSize - item.width - item.margin.end,
          bottom = bottom,
        )
      } else {
        item.layout(
          left = left - crossSize + item.width + item.margin.start,
          top = top,
          right = right - crossSize + item.width + item.margin.start,
          bottom = bottom,
        )
      }
      AlignItems.Center -> {
        val leftFromCrossAxis = (crossSize - item.width + item.margin.start - item.margin.end) / 2
        if (!rightToLeft) {
          item.layout(left + leftFromCrossAxis, top, right + leftFromCrossAxis, bottom)
        } else {
          item.layout(left - leftFromCrossAxis, top, right - leftFromCrossAxis, bottom)
        }
      }
    }
  }

  //endregion
}
