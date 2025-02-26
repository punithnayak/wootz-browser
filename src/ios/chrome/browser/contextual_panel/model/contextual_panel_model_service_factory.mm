// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/contextual_panel/model/contextual_panel_model_service_factory.h"

#import "base/no_destructor.h"
#import "components/keyed_service/ios/browser_state_dependency_manager.h"
#import "ios/chrome/browser/contextual_panel/model/contextual_panel_item_type.h"
#import "ios/chrome/browser/contextual_panel/model/contextual_panel_model_service.h"
#import "ios/chrome/browser/contextual_panel/sample/model/sample_panel_model.h"
#import "ios/chrome/browser/contextual_panel/sample/model/sample_panel_model_factory.h"
#import "ios/chrome/browser/price_insights/model/price_insights_feature.h"
#import "ios/chrome/browser/price_insights/model/price_insights_model.h"
#import "ios/chrome/browser/price_insights/model/price_insights_model_factory.h"
#import "ios/chrome/browser/shared/model/browser_state/chrome_browser_state.h"
#import "ios/chrome/browser/shared/public/features/features.h"

// static
ContextualPanelModelService*
ContextualPanelModelServiceFactory::GetForBrowserState(
    ChromeBrowserState* browser_state) {
  return static_cast<ContextualPanelModelService*>(
      GetInstance()->GetServiceForBrowserState(browser_state, /*create=*/true));
}

// static
ContextualPanelModelServiceFactory*
ContextualPanelModelServiceFactory::GetInstance() {
  static base::NoDestructor<ContextualPanelModelServiceFactory> instance;
  return instance.get();
}

ContextualPanelModelServiceFactory::ContextualPanelModelServiceFactory()
    : BrowserStateKeyedServiceFactory(
          "ContextualPanelModelService",
          BrowserStateDependencyManager::GetInstance()) {
  DependsOn(SamplePanelModelFactory::GetInstance());
  DependsOn(PriceInsightsModelFactory::GetInstance());
}

ContextualPanelModelServiceFactory::~ContextualPanelModelServiceFactory() {}

std::unique_ptr<KeyedService>
ContextualPanelModelServiceFactory::BuildServiceInstanceFor(
    web::BrowserState* context) const {
  ChromeBrowserState* browser_state =
      ChromeBrowserState::FromBrowserState(context);
  std::map<ContextualPanelItemType, raw_ptr<ContextualPanelModel>> models;
  if (IsContextualPanelForceShowEntrypointEnabled()) {
    models.emplace(ContextualPanelItemType::SamplePanelItem,
                   SamplePanelModelFactory::GetForBrowserState(browser_state));
  }

  if (IsPriceInsightsEnabled(browser_state)) {
    models.emplace(
        ContextualPanelItemType::PriceInsightsItem,
        PriceInsightsModelFactory::GetForBrowserState(browser_state));
  }
  return std::make_unique<ContextualPanelModelService>(models);
}
