/*
 * Copyright (C) 2022 Square, Inc.
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

import UIKit
import shared

class ColumnBinding: WidgetColumn {
    func horizontalAlignment(horizontalAlignment: Int32) {
        root.container.alignItems = 4
        root.invalidate()
    }

    func verticalAlignment(verticalAlignment: Int32) {
        root.container.justifyContent = verticalAlignment
        root.invalidate()
    }

    func overflow(overflow: Int32) {
        // Disable or enable scrolling
    }

    func padding(padding: Redwood_layout_apiPadding) {
        root.container.padding = .init(start: padding.start, end: padding.end, top: padding.top, bottom: padding.bottom)
        root.invalidate()
    }

    private let root = FlexboxColumnView(direction: 2)

    init() {}

    lazy var children: Redwood_widgetWidgetChildren = ChildrenBinding { [unowned self] views in
        root.children = views
    }

    var layoutModifiers: Redwood_runtimeLayoutModifier = ExposedKt.layoutModifier()

    var value: Any { root }
}

class FlexboxColumnView: UIView {
    let container = Redwood_flex_containerFlexContainer()

    var children: [UIView] = [] {
        didSet {
            subviews.forEach { $0.removeFromSuperview() }
            children.forEach { self.addSubview($0) }

            container.items.removeAllObjects()

            for view in children {
                let item = Redwood_flex_containerFlexItem(
                    visible: true,
                    baseline: -1,
                    order: 1,
                    flexGrow: 0,
                    flexShrink: 1,
                    flexBasisPercent: -1,
                    alignSelf: 5,
                    wrapBefore: false,
                    margin: .init(start: 0, end: 0, top: 0, bottom: 0),
                    measurable: Measurable(measureView: view)
                )

                item.layout = { left, right, top, bottom in
                    let frame: CGRect = .init(
                        x: left.intValue,
                        y: right.intValue,
                        width: top.intValue - left.intValue,
                        height: bottom.intValue - right.intValue
                    )

                    view.frame = frame
                }

                container.items.add(item)
            }

            invalidate()
        }
    }

    init(direction: Int32) {
        container.flexDirection = direction
        super.init(frame: .zero)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func invalidate() {
        setNeedsLayout()
        invalidateIntrinsicContentSize()
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        // Need to measure to populate sizes?
        container.measure(widthSpec: 0, heightSpec: 0)
        container.layout(
            left: -200,
            top: -200,
            right: Int32(frame.maxX),
            bottom: Int32(frame.maxY)
        )
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let size = container.measure(widthSpec: Int32(size.width), heightSpec: Int32(size.height))
        return CGSize(width: Int(size.width), height: Int(size.height))
    }

    override var intrinsicContentSize: CGSize {
        let size = container.measure(widthSpec: Int32(bounds.width), heightSpec: Int32(bounds.height))
        return CGSize(width: Int(size.width), height: Int(size.height))
    }
}

private class Measurable: Redwood_flex_containerMeasurable {
    private let view: UIView

    init(measureView: UIView) {
        self.view = measureView
    }

    override func measure(widthSpec: Int32, heightSpec: Int32) -> Redwood_flex_containerSize {
        // What are these specs?
        let sizeThatFits = view.sizeThatFits(.zero)
        return Redwood_flex_containerSize(width: Int32(sizeThatFits.width), height: Int32(sizeThatFits.height))
    }
}
