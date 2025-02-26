// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import {html} from '//resources/lit/v3_0/lit.rollup.js';

import type {ThemesElement} from './themes.js';

export function getHtml(this: ThemesElement) {
  return html`<!--_html_template_start_-->
<div class="sp-card">
  <sp-heading id="heading" @back-button-click="${this.onBackClick_}"
      back-button-aria-label="$i18n{backButton}"
      back-button-title="$i18n{backButton}">
    <h2 slot="heading">${this.header_}</h2>
  </sp-heading>
  <div id="refreshDailyToggleContainer">
    <div id="refreshDailyToggleTitle">$i18n{refreshDaily}</div>
    <cr-toggle id="refreshDailyToggle" title="$i18n{refreshDaily}"
        ?checked="${this.isRefreshToggleChecked_}"
        @change="${this.onRefreshDailyToggleChange_}">
    </cr-toggle>
  </div>
  <cr-grid columns="3" disable-arrow-navigation>
    ${this.themes_.map((item, index) => html`
      <div class="tile theme" tabindex="0" role="button"
          data-index="${index}" @click="${this.onSelectTheme_}"
          title="${item.attribution1}"
          aria-current="${this.isThemeSelected_(item.imageUrl.url)}">
        <customize-chrome-check-mark-wrapper
            ?checked="${this.isThemeSelected_(item.imageUrl.url)}">
          <div class="image-container">
            <img is="cr-auto-img"
                .autoSrc="${item.previewImageUrl.url}"
                draggable="false"
                @load="${this.onPreviewImageLoad_}">
            </img>
          </div>
        </customize-chrome-check-mark-wrapper>
      </div>
    `)}
  <cr-grid>
</div>
<!--_html_template_end_-->`;
}
