// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/autofill/autofill_suggestion_controller_utils.h"

#include <string>
#include <vector>

#include "base/functional/overloaded.h"
#include "chrome/browser/feature_engagement/tracker_factory.h"
#include "components/autofill/content/browser/content_autofill_driver.h"
#include "components/autofill/core/browser/ui/suggestion.h"
#include "components/autofill/core/browser/ui/suggestion_type.h"
#include "components/autofill/core/common/dense_set.h"
#include "components/autofill/core/common/form_field_data.h"
#include "components/feature_engagement/public/feature_constants.h"
#include "components/feature_engagement/public/tracker.h"
#include "components/password_manager/content/browser/content_password_manager_driver.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/browser/render_widget_host_view.h"
#include "content/public/browser/web_contents.h"

namespace autofill {

bool IsAcceptableSuggestionType(SuggestionType id) {
  using enum SuggestionType;
  static constexpr auto kUnacceptableItemIds =
      DenseSet({kSeparator, kInsecureContextPaymentDisabledMessage,
                kMixedFormMessage, kTitle});
  return !kUnacceptableItemIds.contains(id);
}

bool IsFooterSuggestionType(SuggestionType type) {
  switch (type) {
    case SuggestionType::kAllSavedPasswordsEntry:
    case SuggestionType::kAutofillOptions:
    case SuggestionType::kClearForm:
    case SuggestionType::kDeleteAddressProfile:
    case SuggestionType::kEditAddressProfile:
    case SuggestionType::kFillEverythingFromAddressProfile:
    case SuggestionType::kPasswordAccountStorageEmpty:
    case SuggestionType::kPasswordAccountStorageOptIn:
    case SuggestionType::kPasswordAccountStorageOptInAndGenerate:
    case SuggestionType::kPasswordAccountStorageReSignin:
    case SuggestionType::kScanCreditCard:
    case SuggestionType::kSeePromoCodeDetails:
    case SuggestionType::kShowAccountCards:
    case SuggestionType::kViewPasswordDetails:
      return true;
    case SuggestionType::kAccountStoragePasswordEntry:
    case SuggestionType::kAddressEntry:
    case SuggestionType::kAddressFieldByFieldFilling:
    case SuggestionType::kAutocompleteEntry:
    case SuggestionType::kComposeResumeNudge:
    case SuggestionType::kComposeProactiveNudge:
    case SuggestionType::kComposeDisable:
    case SuggestionType::kComposeGoToSettings:
    case SuggestionType::kComposeNeverShowOnThisSiteAgain:
    case SuggestionType::kComposeSavedStateNotification:
    case SuggestionType::kCreateNewPlusAddress:
    case SuggestionType::kCreditCardEntry:
    case SuggestionType::kCreditCardFieldByFieldFilling:
    case SuggestionType::kDatalistEntry:
    case SuggestionType::kDevtoolsTestAddressEntry:
    case SuggestionType::kDevtoolsTestAddresses:
    case SuggestionType::kFillExistingPlusAddress:
    case SuggestionType::kFillFullAddress:
    case SuggestionType::kFillFullEmail:
    case SuggestionType::kFillFullName:
    case SuggestionType::kFillFullPhoneNumber:
    case SuggestionType::kFillPassword:
    case SuggestionType::kGeneratePasswordEntry:
    case SuggestionType::kIbanEntry:
    case SuggestionType::kInsecureContextPaymentDisabledMessage:
    case SuggestionType::kMerchantPromoCodeEntry:
    case SuggestionType::kMixedFormMessage:
    case SuggestionType::kPasswordEntry:
    case SuggestionType::kPasswordFieldByFieldFilling:
    case SuggestionType::kSeparator:
    case SuggestionType::kTitle:
    case SuggestionType::kVirtualCreditCardEntry:
    case SuggestionType::kWebauthnCredential:
    case SuggestionType::kWebauthnSignInWithAnotherDevice:
      return false;
  }
}

bool IsFooterItem(const std::vector<Suggestion>& suggestions,
                  size_t line_number) {
  if (line_number >= suggestions.size()) {
    return false;
  }

  // Separators are a special case: They belong into the footer iff the next
  // item exists and is a footer item.
  SuggestionType type = suggestions[line_number].type;
  return type == SuggestionType::kSeparator
             ? IsFooterItem(suggestions, line_number + 1)
             : IsFooterSuggestionType(type);
}

bool IsStandaloneSuggestionType(SuggestionType type) {
  return !IsFooterSuggestionType(type) ||
         (type == SuggestionType::kScanCreditCard);
}

content::RenderFrameHost* GetRenderFrameHost(
    AutofillSuggestionDelegate& delegate) {
  return absl::visit(
      base::Overloaded{
          [](AutofillDriver* driver) {
            return static_cast<ContentAutofillDriver*>(driver)
                ->render_frame_host();
          },
          [](password_manager::PasswordManagerDriver* driver) {
            return static_cast<password_manager::ContentPasswordManagerDriver*>(
                       driver)
                ->render_frame_host();
          }},
      delegate.GetDriver());
}

bool IsAncestorOf(content::RenderFrameHost* ancestor,
                  content::RenderFrameHost* descendant) {
  for (auto* rfh = descendant; rfh; rfh = rfh->GetParent()) {
    if (rfh == ancestor) {
      return true;
    }
  }
  return false;
}

bool IsPointerLocked(content::WebContents* web_contents) {
  content::RenderFrameHost* rfh;
  content::RenderWidgetHostView* rwhv;
  return web_contents && (rfh = web_contents->GetFocusedFrame()) &&
         (rwhv = rfh->GetView()) && rwhv->IsPointerLocked();
}

void NotifyIphAboutAcceptedSuggestion(content::BrowserContext* browser_context,
                                      const Suggestion& suggestion) {
  if (suggestion.type == SuggestionType::kVirtualCreditCardEntry) {
    feature_engagement::TrackerFactory::GetForBrowserContext(browser_context)
        ->NotifyEvent(suggestion.feature_for_iph ==
                              &feature_engagement::
                                  kIPHAutofillVirtualCardCVCSuggestionFeature
                          ? "autofill_virtual_card_cvc_suggestion_accepted"
                          : "autofill_virtual_card_suggestion_accepted");
  }

  if (suggestion.feature_for_iph ==
      &feature_engagement::
          kIPHAutofillExternalAccountProfileSuggestionFeature) {
    feature_engagement::TrackerFactory::GetForBrowserContext(browser_context)
        ->NotifyEvent("autofill_external_account_profile_suggestion_accepted");
  }
}

std::vector<Suggestion> UpdateSuggestionsFromDataList(
    base::span<const SelectOption> options,
    std::vector<Suggestion> suggestions) {
  // Remove all the old data list values, which should always be at the top of
  // the list if they are present.
  std::erase_if(suggestions, [](const Suggestion& suggestion) {
    return suggestion.type == SuggestionType::kDatalistEntry;
  });

  // If there are no new data list values, exit (clearing the separator if there
  // is one).
  if (options.empty()) {
    if (!suggestions.empty() &&
        suggestions[0].type == SuggestionType::kSeparator) {
      suggestions.erase(suggestions.begin());
    }
    return suggestions;
  }

  // Add a separator if there are any other values.
  if (!suggestions.empty() &&
      suggestions[0].type != SuggestionType::kSeparator) {
    suggestions.insert(suggestions.begin(),
                       Suggestion(SuggestionType::kSeparator));
  }

  // Prepend the parameters to the suggestions we already have.
  suggestions.insert(suggestions.begin(), options.size(), Suggestion());
  for (size_t i = 0; i < options.size(); i++) {
    suggestions[i].main_text =
        Suggestion::Text(options[i].value, Suggestion::Text::IsPrimary(true));
    suggestions[i].labels = {{Suggestion::Text(options[i].content)}};
    suggestions[i].type = SuggestionType::kDatalistEntry;
  }
  return suggestions;
}

}  // namespace autofill
