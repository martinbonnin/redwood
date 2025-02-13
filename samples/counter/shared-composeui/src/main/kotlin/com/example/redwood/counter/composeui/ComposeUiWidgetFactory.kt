/*
 * Copyright (C) 2021 Square, Inc.
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
package com.example.redwood.counter.composeui

import androidx.compose.runtime.Composable
import com.example.redwood.counter.widget.Box
import com.example.redwood.counter.widget.Button
import com.example.redwood.counter.widget.SchemaWidgetFactory
import com.example.redwood.counter.widget.Text

object ComposeUiWidgetFactory : SchemaWidgetFactory<@Composable () -> Unit> {
  override fun Box(): Box<@Composable () -> Unit> = ComposeUiBox()
  override fun Text(): Text<@Composable () -> Unit> = ComposeUiText()
  override fun Button(): Button<@Composable () -> Unit> = ComposeUiButton()
}
