// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/webauthn/authenticator_gpm_arbitrary_pin_sheet_view.h"

#include "chrome/browser/ui/views/webauthn/authenticator_gpm_arbitrary_pin_view.h"

AuthenticatorGPMArbitraryPinSheetView::AuthenticatorGPMArbitraryPinSheetView(
    std::unique_ptr<AuthenticatorGPMArbitraryPinSheetModel> sheet_model)
    : AuthenticatorRequestSheetView(std::move(sheet_model)) {}

AuthenticatorGPMArbitraryPinSheetView::
    ~AuthenticatorGPMArbitraryPinSheetView() = default;

AuthenticatorGPMArbitraryPinSheetModel*
AuthenticatorGPMArbitraryPinSheetView::gpm_arbitrary_pin_sheet_model() {
  return static_cast<AuthenticatorGPMArbitraryPinSheetModel*>(model());
}

std::pair<std::unique_ptr<views::View>,
          AuthenticatorGPMArbitraryPinSheetView::AutoFocus>
AuthenticatorGPMArbitraryPinSheetView::BuildStepSpecificContent() {
  bool ui_disabled = gpm_arbitrary_pin_sheet_model()->ui_disabled();
  return std::make_pair(
      std::make_unique<AuthenticatorGPMArbitraryPinView>(
          ui_disabled, gpm_arbitrary_pin_sheet_model()->pin(), this),
      ui_disabled ? AutoFocus::kNo : AutoFocus::kYes);
}

void AuthenticatorGPMArbitraryPinSheetView::OnPinChanged(std::u16string pin) {
  gpm_arbitrary_pin_sheet_model()->SetPin(std::move(pin));
}
