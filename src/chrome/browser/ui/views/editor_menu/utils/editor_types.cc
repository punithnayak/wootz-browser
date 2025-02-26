// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/editor_menu/utils/editor_types.h"

namespace chromeos::editor_menu {

EditorContext::EditorContext(EditorMode mode, PresetTextQueries queries)
    : mode(mode), preset_queries(queries) {}

EditorContext::EditorContext(const EditorContext&) = default;
EditorContext::~EditorContext() = default;

}  // namespace chromeos::editor_menu
