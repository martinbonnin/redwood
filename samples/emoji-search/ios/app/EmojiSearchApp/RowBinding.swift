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

class RowBinding: WidgetRow {
    let container = Redwood_flex_containerFlexContainer()

    func horizontalAlignment(horizontalAlignment_ horizontalAlignment: Int32) {
        container.alignContent = horizontalAlignment
    }

    func overflow(overflow: Int32) {
        // ???
    }

    func padding(padding: Redwood_layout_apiPadding) {
        // container.padding = padding
    }

    func verticalAlignment(verticalAlignment_ verticalAlignment: Int32) {
        container.alignItems = verticalAlignment
    }

    private let root = UIView()

    init() {

    }

    lazy var children: Redwood_widgetWidgetChildren = ChildrenBinding { [unowned self] views in
        self.root.subviews.forEach { $0.removeFromSuperview() }
        views.forEach { self.root.addSubview($0) }
    }
    var layoutModifiers: Redwood_runtimeLayoutModifier = ExposedKt.layoutModifier()
    var value: Any { root }
}

class FlexboxView: UIView {
    let 
}
