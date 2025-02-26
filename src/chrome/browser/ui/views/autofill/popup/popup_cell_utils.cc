// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/autofill/popup/popup_cell_utils.h"

#include <memory>
#include <string>
#include <utility>

#include "base/feature_list.h"
#include "base/metrics/histogram_functions.h"
#include "build/branding_buildflags.h"
#include "chrome/app/vector_icons/vector_icons.h"
#include "chrome/browser/ui/passwords/ui_utils.h"
#include "chrome/browser/ui/views/autofill/popup/popup_base_view.h"
#include "chrome/browser/ui/views/autofill/popup/popup_row_content_view.h"
#include "chrome/browser/ui/views/autofill/popup/popup_row_view.h"
#include "chrome/browser/ui/views/autofill/popup/popup_view_utils.h"
#include "chrome/browser/ui/views/chrome_layout_provider.h"
#include "chrome/browser/ui/views/chrome_typography.h"
#include "components/autofill/core/browser/data_model/credit_card.h"
#include "components/autofill/core/browser/filling_product.h"
#include "components/autofill/core/browser/ui/autofill_resource_utils.h"
#include "components/autofill/core/browser/ui/suggestion.h"
#include "components/autofill/core/browser/ui/suggestion_type.h"
#include "components/autofill/core/common/autofill_features.h"
#include "components/autofill/core/common/autofill_payments_features.h"
#include "components/omnibox/browser/vector_icons.h"
#include "ui/color/color_id.h"
#include "ui/compositor/layer.h"
#include "ui/gfx/geometry/size.h"
#include "ui/views/controls/throbber.h"
#include "ui/views/style/typography.h"
#if BUILDFLAG(GOOGLE_CHROME_BRANDING)
#include "components/plus_addresses/resources/vector_icons.h"
#endif
#include "components/strings/grit/components_strings.h"
#include "components/vector_icons/vector_icons.h"
#include "ui/base/l10n/l10n_util.h"
#include "ui/gfx/paint_vector_icon.h"
#include "ui/views/controls/image_view.h"
#include "ui/views/controls/label.h"
#include "ui/views/controls/menu/menu_config.h"
#include "ui/views/layout/box_layout.h"
#include "ui/views/layout/box_layout_view.h"
#include "ui/views/layout/flex_layout.h"
#include "ui/views/layout/table_layout_view.h"
#include "ui/views/view.h"

namespace autofill::popup_cell_utils {

namespace {

// The default icon size used in the suggestion drop down.
constexpr int kIconSize = 16;
constexpr int kChromeRefreshIconSize = 20;

// The additional height of the row in case it has two lines of text.
constexpr int kAutofillPopupAdditionalDoubleRowHeight = 16;

// The additional padding of the row in case it has three lines of text.
constexpr int kAutofillPopupAdditionalVerticalPadding = 16;

// Vertical spacing between labels in one row.
constexpr int kAdjacentLabelsVerticalSpacing = 2;

// The icon size used in the suggestion dropdown for displaying the Google
// Password Manager icon in the Manager Passwords entry.
constexpr int kGooglePasswordManagerIconSize = 20;

// Metric to measure the duration of getting the image for the Autofill pop-up.
constexpr char kHistogramGetImageViewByName[] =
    "Autofill.PopupGetImageViewTime";

// The opacity for grayed-out disabled views.
constexpr double kGrayedOutOpacity = 0.38;

// Returns the name of the network for payment method icons, empty string
// otherwise.
std::u16string GetIconAccessibleName(Suggestion::Icon icon) {
  // Networks for which icons are currently shown.
  switch (icon) {
    case Suggestion::Icon::kCardAmericanExpress:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_AMEX);
    case Suggestion::Icon::kCardDiners:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_DINERS);
    case Suggestion::Icon::kCardDiscover:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_DISCOVER);
    case Suggestion::Icon::kCardElo:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_ELO);
    case Suggestion::Icon::kCardJCB:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_JCB);
    case Suggestion::Icon::kCardMasterCard:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_MASTERCARD);
    case Suggestion::Icon::kCardMir:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_MIR);
    case Suggestion::Icon::kCardTroy:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_TROY);
    case Suggestion::Icon::kCardUnionPay:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_UNION_PAY);
    case Suggestion::Icon::kCardVerve:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_VERVE);
    case Suggestion::Icon::kCardVisa:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_VISA);
    // Other networks.
    case Suggestion::Icon::kCardGeneric:
      return l10n_util::GetStringUTF16(IDS_AUTOFILL_CC_GENERIC);
    case Suggestion::Icon::kNoIcon:
    case Suggestion::Icon::kAccount:
    case Suggestion::Icon::kClear:
    case Suggestion::Icon::kCreate:
    case Suggestion::Icon::kCode:
    case Suggestion::Icon::kDelete:
    case Suggestion::Icon::kDevice:
    case Suggestion::Icon::kEdit:
    case Suggestion::Icon::kEmpty:
    case Suggestion::Icon::kGlobe:
    case Suggestion::Icon::kGoogle:
    case Suggestion::Icon::kGooglePasswordManager:
    case Suggestion::Icon::kGooglePay:
    case Suggestion::Icon::kGooglePayDark:
    case Suggestion::Icon::kHttpWarning:
    case Suggestion::Icon::kHttpsInvalid:
    case Suggestion::Icon::kIban:
    case Suggestion::Icon::kKey:
    case Suggestion::Icon::kLocation:
    case Suggestion::Icon::kMagic:
    case Suggestion::Icon::kOfferTag:
    case Suggestion::Icon::kPenSpark:
    case Suggestion::Icon::kPlusAddress:
    case Suggestion::Icon::kScanCreditCard:
    case Suggestion::Icon::kSettings:
    case Suggestion::Icon::kSettingsAndroid:
    case Suggestion::Icon::kUndo:
      return std::u16string();
  }
  NOTREACHED_NORETURN();
}

std::unique_ptr<views::ImageView> ImageViewFromImageSkia(
    const gfx::ImageSkia& image_skia) {
  if (image_skia.isNull()) {
    return nullptr;
  }
  auto image_view = std::make_unique<views::ImageView>();
  image_view->SetImage(ui::ImageModel::FromImageSkia(image_skia));
  return image_view;
}

std::unique_ptr<views::ImageView> GetIconImageViewFromIcon(
    Suggestion::Icon icon) {
  switch (icon) {
    case Suggestion::Icon::kNoIcon:
      return nullptr;
    case Suggestion::Icon::kHttpWarning:
      // For the http warning message, get the icon images from VectorIcon,
      // which is the same as the security indicator icons in the location bar.
      return ImageViewFromVectorIcon(omnibox::kHttpIcon, kIconSize);
    case Suggestion::Icon::kHttpsInvalid:
      return std::make_unique<views::ImageView>(ui::ImageModel::FromVectorIcon(
          vector_icons::kNotSecureWarningIcon, ui::kColorAlertHighSeverity,
          kIconSize));
    case Suggestion::Icon::kKey:
      return ImageViewFromVectorIcon(kKeyIcon, kIconSize);
    case Suggestion::Icon::kEdit:
      return ImageViewFromVectorIcon(vector_icons::kEditChromeRefreshIcon,
                                     kChromeRefreshIconSize);
    case Suggestion::Icon::kCode:
      return ImageViewFromVectorIcon(vector_icons::kCodeIcon, kIconSize);
    case Suggestion::Icon::kLocation:
      return ImageViewFromVectorIcon(vector_icons::kLocationOnChromeRefreshIcon,
                                     kChromeRefreshIconSize);
    case Suggestion::Icon::kDelete:
      return ImageViewFromVectorIcon(kTrashCanRefreshIcon,
                                     kChromeRefreshIconSize);
    case Suggestion::Icon::kClear:
      return ImageViewFromVectorIcon(kBackspaceIcon, kIconSize);
    case Suggestion::Icon::kUndo:
      return ImageViewFromVectorIcon(vector_icons::kUndoIcon, kIconSize);
    case Suggestion::Icon::kGlobe:
      return ImageViewFromVectorIcon(kGlobeIcon, kIconSize);
    case Suggestion::Icon::kMagic:
      return ImageViewFromVectorIcon(vector_icons::kMagicButtonIcon, kIconSize);
    case Suggestion::Icon::kAccount:
      return ImageViewFromVectorIcon(kAccountCircleIcon, kIconSize);
    case Suggestion::Icon::kSettings:
      return ImageViewFromVectorIcon(omnibox::kProductIcon, kIconSize);
    case Suggestion::Icon::kEmpty:
      return ImageViewFromVectorIcon(omnibox::kHttpIcon, kIconSize);
    case Suggestion::Icon::kDevice:
      return ImageViewFromVectorIcon(kDevicesIcon, kIconSize);
    case Suggestion::Icon::kGoogle:
#if BUILDFLAG(GOOGLE_CHROME_BRANDING)
      return ImageViewFromImageSkia(gfx::CreateVectorIcon(
          vector_icons::kGoogleGLogoIcon, kIconSize, gfx::kPlaceholderColor));
#else
      return nullptr;
#endif
    case Suggestion::Icon::kPenSpark:
#if BUILDFLAG(GOOGLE_CHROME_BRANDING)
      return ImageViewFromVectorIcon(vector_icons::kPenSparkIcon, kIconSize);
#else
      return ImageViewFromVectorIcon(vector_icons::kEditIcon, kIconSize);
#endif
    case Suggestion::Icon::kGooglePasswordManager:
      return ImageViewFromVectorIcon(GooglePasswordManagerVectorIcon(),
                                     kGooglePasswordManagerIconSize);
    case Suggestion::Icon::kPlusAddress:
#if BUILDFLAG(GOOGLE_CHROME_BRANDING)
      return ImageViewFromVectorIcon(plus_addresses::kPlusAddressesLogoIcon,
                                     kIconSize);
#else
      return ImageViewFromVectorIcon(vector_icons::kEmailIcon, kIconSize);
#endif
#if !BUILDFLAG(GOOGLE_CHROME_BRANDING)
    case Suggestion::Icon::kGooglePay:
    case Suggestion::Icon::kGooglePayDark:
      return nullptr;
#else
    case Suggestion::Icon::kGooglePay:
    case Suggestion::Icon::kGooglePayDark:
#endif
    case Suggestion::Icon::kIban:
    case Suggestion::Icon::kCreate:
    case Suggestion::Icon::kOfferTag:
    case Suggestion::Icon::kScanCreditCard:
    case Suggestion::Icon::kSettingsAndroid:
    case Suggestion::Icon::kCardGeneric:
    case Suggestion::Icon::kCardAmericanExpress:
    case Suggestion::Icon::kCardDiners:
    case Suggestion::Icon::kCardDiscover:
    case Suggestion::Icon::kCardElo:
    case Suggestion::Icon::kCardJCB:
    case Suggestion::Icon::kCardMasterCard:
    case Suggestion::Icon::kCardMir:
    case Suggestion::Icon::kCardTroy:
    case Suggestion::Icon::kCardUnionPay:
    case Suggestion::Icon::kCardVerve:
    case Suggestion::Icon::kCardVisa:
      // For other suggestion entries, get the icon from PNG files.
      int icon_id = GetIconResourceID(icon);
      DCHECK_NE(icon_id, 0);
      return ImageViewFromImageSkia(
          *ui::ResourceBundle::GetSharedInstance().GetImageSkiaNamed(icon_id));
  }
  NOTREACHED_NORETURN();
}

}  // namespace

std::u16string GetVoiceOverStringFromSuggestion(const Suggestion& suggestion) {
  if (suggestion.voice_over) {
    return *suggestion.voice_over;
  }

  std::vector<std::u16string> text;
  auto add_if_not_empty = [&text](std::u16string value) {
    if (!value.empty()) {
      text.push_back(std::move(value));
    }
  };

  add_if_not_empty(GetIconAccessibleName(suggestion.icon));
  text.push_back(suggestion.main_text.value);
  add_if_not_empty(suggestion.minor_text.value);

  for (const std::vector<Suggestion::Text>& row : suggestion.labels) {
    for (const Suggestion::Text& label : row) {
      // `label_text` is not populated for footers or autocomplete entries.
      add_if_not_empty(label.value);
    }
  }

  // `additional_label` is only populated in a passwords context.
  add_if_not_empty(suggestion.additional_label);

  return base::JoinString(text, u" ");
}

gfx::Insets GetMarginsForContentCell() {
  // The `PopupRowView` already adds some extra horizontal margin on the left -
  // deduct that.
  return gfx::Insets::VH(0,
                         std::max(0, PopupBaseView::ArrowHorizontalMargin() -
                                         PopupRowView::GetHorizontalMargin()));
}

std::unique_ptr<views::ImageView> GetIconImageView(
    const Suggestion& suggestion) {
  base::TimeTicks start_time = base::TimeTicks::Now();

  if (!suggestion.custom_icon.IsEmpty()) {
    return ImageViewFromImageSkia(suggestion.custom_icon.AsImageSkia());
  }
  std::unique_ptr<views::ImageView> icon_image_view =
      GetIconImageViewFromIcon(suggestion.icon);
  base::UmaHistogramTimes(kHistogramGetImageViewByName,
                          base::TimeTicks::Now() - start_time);

  if (icon_image_view) {
    // It is possible to have icons of different sizes (kChromeRefreshIconSize
    // and kIconSize) on the same popup. Setting the icon view width to
    // the largest value ensures that the icon occupies consistent horizontal
    // space and makes icons (and the text after them) aligned. It expands
    // the area of kIconSize icons only and doesn't change those that are bigger
    // by design (e.g. payment card icons) and have no alignment issues.
    gfx::Size size = icon_image_view->GetPreferredSize();
    size.set_width(std::max(kChromeRefreshIconSize, size.width()));
    icon_image_view->SetPreferredSize(size);
  }

  return icon_image_view;
}

std::unique_ptr<views::ImageView> GetTrailingIconImageView(
    const Suggestion& suggestion) {
  base::TimeTicks start_time = base::TimeTicks::Now();
  std::unique_ptr<views::ImageView> icon_image_view =
      GetIconImageViewFromIcon(suggestion.trailing_icon);
  base::UmaHistogramTimes(kHistogramGetImageViewByName,
                          base::TimeTicks::Now() - start_time);

  return icon_image_view;
}

// Adds a spacer with `spacer_width` to `view`. `layout` must be the
// LayoutManager of `view`.
void AddSpacerWithSize(views::View& view,
                       views::BoxLayout& layout,
                       int spacer_width,
                       bool resize) {
  auto spacer = views::Builder<views::View>()
                    .SetPreferredSize(gfx::Size(spacer_width, 1))
                    .Build();
  layout.SetFlexForView(view.AddChildView(std::move(spacer)),
                        /*flex=*/resize ? 1 : 0,
                        /*use_min_size=*/true);
}

// Creates the table in which all  the Autofill suggestion content apart from
// leading and trailing icons is contained and adds it to `content_view`.
// It registers `main_text_label`, `minor_text_label`, and `description_label`
// with `content_view` for tracking, but assumes that the labels inside of of
// `subtext_views` have already been registered for tracking with
// `content_view`.
void AddSuggestionContentTableToView(
    std::unique_ptr<views::Label> main_text_label,
    std::unique_ptr<views::Label> minor_text_label,
    std::unique_ptr<views::Label> description_label,
    std::vector<std::unique_ptr<views::View>> subtext_views,
    PopupRowContentView& content_view) {
  const int kDividerSpacing = ChromeLayoutProvider::Get()->GetDistanceMetric(
      DISTANCE_RELATED_LABEL_HORIZONTAL_LIST);
  auto content_table =
      views::Builder<views::TableLayoutView>()
          .AddColumn(views::LayoutAlignment::kStart,
                     views::LayoutAlignment::kStretch,
                     views::TableLayout::kFixedSize,
                     views::TableLayout::ColumnSize::kUsePreferred, 0, 0)
          .AddPaddingColumn(views::TableLayout::kFixedSize, kDividerSpacing)
          .AddColumn(views::LayoutAlignment::kStart,
                     views::LayoutAlignment::kStretch,
                     views::TableLayout::kFixedSize,
                     views::TableLayout::ColumnSize::kUsePreferred, 0, 0)
          .Build();

  // Major and minor text go into the first row, first column.
  content_table->AddRows(1, 0);
  if (minor_text_label) {
    auto first_line_container = std::make_unique<views::View>();
    first_line_container
        ->SetLayoutManager(std::make_unique<views::FlexLayout>())
        ->SetOrientation(views::LayoutOrientation::kHorizontal)
        .SetMainAxisAlignment(views::LayoutAlignment::kStart)
        .SetCrossAxisAlignment(views::LayoutAlignment::kCenter)
        .SetIgnoreDefaultMainAxisMargins(true)
        .SetCollapseMargins(true)
        .SetDefault(
            views::kMarginsKey,
            gfx::Insets::VH(0, ChromeLayoutProvider::Get()->GetDistanceMetric(
                                   DISTANCE_RELATED_LABEL_HORIZONTAL_LIST)));

    first_line_container->AddChildView(std::move(main_text_label));

    first_line_container->AddChildView(std::move(minor_text_label));
    content_table->AddChildView(std::move(first_line_container));
  } else {
    content_table->AddChildView(std::move(main_text_label));
  }

  // The description goes into the first row, second column.
  if (description_label) {
    content_table->AddChildView(std::move(description_label));
  } else {
    content_table->AddChildView(std::make_unique<views::View>());
  }

  // Every subtext label goes into an additional row.
  for (std::unique_ptr<views::View>& subtext_view : subtext_views) {
    content_table->AddPaddingRow(0, kAdjacentLabelsVerticalSpacing)
        .AddRows(1, 0);
    content_table->AddChildView(std::move(subtext_view));
    content_table->AddChildView(std::make_unique<views::View>());
  }
  content_view.AddChildView(std::move(content_table));
}

// Creates the content structure shared by autocomplete, address, credit card,
// and password suggestions.
// - `minor_text_label`, `description_label`, and `subtext_labels` may all be
// null or empty.
// - `content_view` is the (assumed to be empty) view to which the content
// structure for the `suggestion` is added.
void AddSuggestionContentToView(
    const Suggestion& suggestion,
    std::unique_ptr<views::Label> main_text_label,
    std::unique_ptr<views::Label> minor_text_label,
    std::unique_ptr<views::Label> description_label,
    std::vector<std::unique_ptr<views::View>> subtext_views,
    PopupRowContentView& content_view) {
  views::BoxLayout& layout =
      *content_view.SetLayoutManager(std::make_unique<views::BoxLayout>(
          views::BoxLayout::Orientation::kHorizontal,
          GetMarginsForContentCell()));

  layout.set_cross_axis_alignment(
      views::BoxLayout::CrossAxisAlignment::kCenter);

  // Adjust the row height based on the number of subtexts (lines of text).
  int row_height = views::MenuConfig::instance().touchable_menu_height;
  if (!subtext_views.empty()) {
    row_height += kAutofillPopupAdditionalDoubleRowHeight;
  }
  layout.set_minimum_cross_axis_size(row_height);

  // If there are three rows in total, add extra padding to avoid cramming.
  DCHECK_LE(subtext_views.size(), 2u);
  if (subtext_views.size() == 2u) {
    layout.set_inside_border_insets(
        gfx::Insets::TLBR(kAutofillPopupAdditionalVerticalPadding,
                          layout.inside_border_insets().left(),
                          kAutofillPopupAdditionalVerticalPadding,
                          layout.inside_border_insets().right()));
  }

  // The leading icon.
  if (suggestion.is_loading) {
    content_view.AddChildView(std::make_unique<views::Throbber>())->Start();
    AddSpacerWithSize(content_view, layout,
                      PopupBaseView::ArrowHorizontalMargin(),
                      /*resize=*/false);
    content_view.SetEnabled(false);
  } else if (std::unique_ptr<views::ImageView> icon =
                 GetIconImageView(suggestion)) {
    if (suggestion.apply_deactivated_style) {
      ApplyDeactivatedStyle(*icon);
    }
    content_view.AddChildView(std::move(icon));
    AddSpacerWithSize(content_view, layout,
                      PopupBaseView::ArrowHorizontalMargin(),
                      /*resize=*/false);
  }

  // The actual content table.
  AddSuggestionContentTableToView(
      std::move(main_text_label), std::move(minor_text_label),
      std::move(description_label), std::move(subtext_views), content_view);

  // The trailing icon.
  if (std::unique_ptr<views::ImageView> trailing_icon =
          GetTrailingIconImageView(suggestion)) {
    AddSpacerWithSize(content_view, layout,
                      PopupBaseView::ArrowHorizontalMargin(),
                      /*resize=*/true);
    content_view.AddChildView(std::move(trailing_icon));
  }

  // Force a refresh to ensure all the labels'styles are correct.
  content_view.UpdateStyle(/*selected=*/false);
}

std::unique_ptr<views::ImageView> ImageViewFromVectorIcon(
    const gfx::VectorIcon& vector_icon,
    int icon_size = kIconSize) {
  return std::make_unique<views::ImageView>(
      ui::ImageModel::FromVectorIcon(vector_icon, ui::kColorIcon, icon_size));
}

void ApplyDeactivatedStyle(views::View& view) {
  view.SetPaintToLayer();
  view.layer()->SetOpacity(kGrayedOutOpacity);
}

const gfx::VectorIcon& GetExpandableMenuIcon(SuggestionType type) {
  CHECK(IsExpandableSuggestionType(type));
  // Only compose suggestions have a different expandable icon.
  return GetFillingProductFromSuggestionType(type) == FillingProduct::kCompose
             ? kBrowserToolsChromeRefreshIcon
             : vector_icons::kSubmenuArrowChromeRefreshIcon;
}

}  // namespace autofill::popup_cell_utils
