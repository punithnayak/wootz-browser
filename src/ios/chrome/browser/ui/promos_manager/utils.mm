// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/promos_manager/utils.h"

#import "components/policy/core/common/cloud/user_cloud_policy_manager.h"
#import "components/prefs/pref_service.h"
#import "ios/chrome/app/application_delegate/app_state.h"
#import "ios/chrome/app/application_delegate/app_state_observer.h"
#import "ios/chrome/browser/shared/coordinator/scene/scene_state.h"
#import "ios/chrome/browser/shared/model/application_context/application_context.h"
#import "ios/chrome/browser/shared/model/browser/browser.h"
#import "ios/chrome/browser/shared/model/browser/browser_provider.h"
#import "ios/chrome/browser/shared/model/browser/browser_provider_interface.h"
#import "ios/chrome/browser/shared/model/browser_state/chrome_browser_state.h"
#import "ios/chrome/browser/signin/model/authentication_service.h"
#import "ios/chrome/browser/signin/model/authentication_service_factory.h"
#import "ios/chrome/browser/ui/policy/user_policy_util.h"

bool ShouldPromoManagerDisplayPromos() {
  return GetApplicationContext()->WasLastShutdownClean();
}

bool IsUIAvailableForPromo(SceneState* scene_state) {
  // The following app & scene conditions need to be met to enable a promo's
  // display (please note the Promos Manager may still decide *not* to display a
  // promo, based on its own internal criteria):

  // (1) The app initialization is over (the stage InitStageFinal is reached).
  if (scene_state.appState.initStage < InitStageFinal) {
    return NO;
  }

  // (2) The scene is in the foreground.
  if (scene_state.activationLevel < SceneActivationLevelForegroundActive) {
    return NO;
  }

  // (3) There is no UI blocker.
  if (scene_state.appState.currentUIBlocker) {
    return NO;
  }

  // (4) The app isn't shutting down.
  if (scene_state.appState.appIsTerminating) {
    return NO;
  }

  // (5) There are no launch intents (external intents).
  if (scene_state.startupHadExternalIntent) {
    return NO;
  }

  // Additional, sensible checks to add to minimize user annoyance:

  // (6) The user isn't currently signing in.
  if (scene_state.signinInProgress) {
    return NO;
  }

  // (7) The user isn't currently looking at a modal overlay.
  if (scene_state.presentingModalOverlay) {
    return NO;
  }

  // (8) User Policy notification has priority over showing promos.
  // This will only prevent showing a promo before policy notification but might
  // show a promo within same user session.
  ChromeBrowserState* browser_state =
      scene_state.browserProviderInterface.currentBrowserProvider.browser
          ->GetBrowserState();
  PrefService* pref_service = browser_state->GetPrefs();
  AuthenticationService* auth_service =
      AuthenticationServiceFactory::GetForBrowserState(browser_state);
  policy::UserCloudPolicyManager* user_policy_manager =
      browser_state->GetUserCloudPolicyManager();
  return !IsUserPolicyNotificationNeeded(auth_service, pref_service,
                                         user_policy_manager);
}
