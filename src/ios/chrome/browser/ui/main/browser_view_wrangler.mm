// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/main/browser_view_wrangler.h"

#import "base/feature_list.h"
#import "base/files/file_path.h"
#import "base/ios/ios_util.h"
#import "base/memory/raw_ptr.h"
#import "base/strings/sys_string_conversions.h"
#import "ios/chrome/app/application_delegate/app_state.h"
#import "ios/chrome/browser/crash_report/model/crash_report_helper.h"
#import "ios/chrome/browser/device_sharing/model/device_sharing_browser_agent.h"
#import "ios/chrome/browser/metrics/model/tab_usage_recorder_browser_agent.h"
#import "ios/chrome/browser/sessions/ios_chrome_tab_restore_service_factory.h"
#import "ios/chrome/browser/sessions/session_restoration_service.h"
#import "ios/chrome/browser/sessions/session_restoration_service_factory.h"
#import "ios/chrome/browser/sessions/session_util.h"
#import "ios/chrome/browser/settings/model/sync/utils/sync_presenter.h"
#import "ios/chrome/browser/shared/coordinator/scene/scene_state.h"
#import "ios/chrome/browser/shared/model/browser/browser.h"
#import "ios/chrome/browser/shared/model/browser/browser_list.h"
#import "ios/chrome/browser/shared/model/browser/browser_list_factory.h"
#import "ios/chrome/browser/shared/model/browser/browser_provider.h"
#import "ios/chrome/browser/shared/model/browser_state/chrome_browser_state.h"
#import "ios/chrome/browser/shared/model/web_state_list/web_state_list.h"
#import "ios/chrome/browser/shared/public/commands/application_commands.h"
#import "ios/chrome/browser/shared/public/commands/browsing_data_commands.h"
#import "ios/chrome/browser/shared/public/commands/command_dispatcher.h"
#import "ios/chrome/browser/shared/public/commands/settings_commands.h"
#import "ios/chrome/browser/snapshots/model/snapshot_browser_agent.h"
#import "ios/chrome/browser/tabs/model/inactive_tabs/features.h"
#import "ios/chrome/browser/tabs/model/inactive_tabs/utils.h"
#import "ios/chrome/browser/ui/browser_view/browser_coordinator.h"
#import "ios/chrome/browser/ui/browser_view/browser_view_controller.h"
#import "ios/chrome/browser/ui/incognito_reauth/incognito_reauth_scene_agent.h"
#import "ios/chrome/browser/ui/main/wrangled_browser.h"

@implementation BrowserViewWrangler {
  raw_ptr<ChromeBrowserState> _browserState;

  __weak SceneState* _sceneState;
  __weak id<ApplicationCommands> _applicationEndpoint;
  __weak id<SettingsCommands> _settingsEndpoint;
  __weak id<BrowsingDataCommands> _browsingDataEndpoint;

  std::unique_ptr<Browser> _mainBrowser;
  std::unique_ptr<Browser> _otrBrowser;

  BrowserCoordinator* _mainBrowserCoordinator;
  BrowserCoordinator* _incognitoBrowserCoordinator;

  BOOL _isShutdown;
}

- (instancetype)
    initWithBrowserState:(ChromeBrowserState*)browserState
              sceneState:(SceneState*)sceneState
     applicationEndpoint:(id<ApplicationCommands>)applicationEndpoint
        settingsEndpoint:(id<SettingsCommands>)settingsEndpoint
    browsingDataEndpoint:(id<BrowsingDataCommands>)browsingDataEndpoint {
  if ((self = [super init])) {
    _browserState = browserState;
    _sceneState = sceneState;
    _applicationEndpoint = applicationEndpoint;
    _settingsEndpoint = settingsEndpoint;
    _browsingDataEndpoint = browsingDataEndpoint;

    // Create all browsers.
    _mainBrowser = Browser::Create(_browserState, _sceneState);
    [self setupBrowser:_mainBrowser.get()];
    [self setupBrowser:_mainBrowser->CreateInactiveBrowser()];

    ChromeBrowserState* otrBrowserState =
        _browserState->GetOffTheRecordChromeBrowserState();
    _otrBrowser = Browser::Create(otrBrowserState, _sceneState);
    [self setupBrowser:_otrBrowser.get()];
  }
  return self;
}

- (void)dealloc {
  DCHECK(_isShutdown) << "-shutdown must be called before -dealloc";
}

- (void)createMainCoordinatorAndInterface {
  DCHECK(!_mainInterface)
      << "-createMainCoordinatorAndInterface must not be called once";

  // Create the main coordinator, and thus the main interface.
  _mainBrowserCoordinator = [[BrowserCoordinator alloc]
      initWithBaseViewController:nil
                         browser:_mainBrowser.get()];
  [_mainBrowserCoordinator start];

  DCHECK(_mainBrowserCoordinator.viewController);
  _mainInterface =
      [[WrangledBrowser alloc] initWithCoordinator:_mainBrowserCoordinator];
  _mainInterface.inactiveBrowser = _mainBrowser->GetInactiveBrowser();

  _incognitoInterface = [self createOTRInterface];
}

- (void)loadSession {
  DCHECK(_mainBrowser);
  DCHECK(_mainInterface)
      << "-loadSession must be called after -createMainCoordinatorAndInterface";

  Browser* inactiveBrowser = _mainBrowser->GetInactiveBrowser();

  // Restore the session after creating the coordinator.
  [self loadSessionForBrowser:_mainBrowser.get()];
  [self loadSessionForBrowser:inactiveBrowser];
  [self loadSessionForBrowser:_otrBrowser.get()];

  if (IsInactiveTabsEnabled()) {
    // Ensure there is no active element in the restored inactive browser. It
    // can be caused by a flag change, for example.
    // TODO(crbug.com/40890696): Remove the following line as soon as inactive
    // tabs is fully launched. After fully launched the only place where tabs
    // can move from inactive to active is after a settings change, this line
    // will be called at this specific moment.
    MoveTabsFromInactiveToActive(inactiveBrowser, _mainBrowser.get());

    // Moves all tabs that might have become inactive since the last launch.
    MoveTabsFromActiveToInactive(_mainBrowser.get(), inactiveBrowser);
  } else {
    RestoreAllInactiveTabs(inactiveBrowser, _mainBrowser.get());
  }
}

#pragma mark - BrowserProviderInterface

- (id<BrowserProvider>)mainBrowserProvider {
  return _mainInterface;
}

- (id<BrowserProvider>)incognitoBrowserProvider {
  if (!self.hasIncognitoBrowserProvider) {
    // Ensure that the method return nil if self.hasIncognitoBrowserProvider
    // returns NO.
    return nil;
  }

  return _incognitoInterface;
}

- (id<BrowserProvider>)currentBrowserProvider {
  return _currentInterface;
}

// This method should almost never return NO since the incognitoInterface
// is not lazily created, but it is possible for it to return YES after
// -shutdown or as a transient state while the OTR ChromeBrowserState is
// being detroyed and recreated (see SceneController).
- (BOOL)hasIncognitoBrowserProvider {
  return _mainInterface && _incognitoInterface;
}

#pragma mark - BrowserViewInformation property implementations

- (void)setCurrentInterface:(WrangledBrowser*)interface {
  DCHECK(interface);
  // `interface` must be one of the interfaces this class already owns.
  DCHECK(_mainInterface == interface || _incognitoInterface == interface);
  if (_currentInterface == interface) {
    return;
  }

  if (_currentInterface) {
    // Record that the primary browser was changed.
    TabUsageRecorderBrowserAgent* tabUsageRecorder =
        TabUsageRecorderBrowserAgent::FromBrowser(_currentInterface.browser);
    if (tabUsageRecorder) {
      tabUsageRecorder->RecordPrimaryBrowserChange(true);
    }
  }

  _currentInterface = interface;

  // Update the shared active URL for the new interface.
  DeviceSharingBrowserAgent::FromBrowser(_currentInterface.browser)
      ->UpdateForActiveBrowser();
}

#pragma mark - Other public methods

- (void)willDestroyIncognitoBrowserState {
  // It is theoretically possible that a Tab has been added to the webStateList
  // since the deletion has been scheduled. It is unlikely to happen for real
  // because it would require superhuman speed.
  DCHECK(_incognitoInterface);
  DCHECK(_otrBrowser->GetWebStateList()->empty());

  // At this stage, a new incognitoBrowserCoordinator shouldn't be lazily
  // constructed by calling the property getter.
  BOOL otrBVCIsCurrent = self.currentInterface == _incognitoInterface;
  @autoreleasepool {
    // At this stage, a new incognitoBrowserCoordinator shouldn't be lazily
    // constructed by calling the property getter.
    [_incognitoBrowserCoordinator stop];
    _incognitoBrowserCoordinator = nil;
    _incognitoInterface = nil;

    // Cleanup and destroy the OTR browser. It will be recreated with the
    // off-the-record ChromeBrowserState.
    [self cleanupBrowser:_otrBrowser.get()];
    _otrBrowser.reset();

    // There's no guarantee the tab model was ever added to the BVC (or even
    // that the BVC was created), so ensure the tab model gets notified.
    if (otrBVCIsCurrent) {
      _currentInterface = nil;
    }
  }
}

- (void)incognitoBrowserStateCreated {
  DCHECK(_browserState);
  DCHECK(_browserState->HasOffTheRecordChromeBrowserState());
  DCHECK(!_otrBrowser);

  // An empty _otrBrowser must be created at this point, because it is then
  // possible to prevent the tabChanged notification being sent. Otherwise,
  // when it is created, a notification with no tabs will be sent, and it will
  // be immediately deleted.
  ChromeBrowserState* incognitoBrowserState =
      _browserState->GetOffTheRecordChromeBrowserState();

  _otrBrowser = Browser::Create(incognitoBrowserState, _sceneState);
  [self setupBrowser:_otrBrowser.get()];

  // Recreate the off-the-record interface, but do not load the session as
  // we had just closed all the tabs.
  _incognitoInterface = [self createOTRInterface];

  if (_currentInterface == nil) {
    self.currentInterface = _incognitoInterface;
  }
}

- (void)shutdown {
  DCHECK(!_isShutdown);
  _isShutdown = YES;

  // Inform the command dispatchers of the shutdown. Should be in reverse
  // order of -init.
  Browser* inactiveBrowser = _mainBrowser->GetInactiveBrowser();
  [_otrBrowser->GetCommandDispatcher() prepareForShutdown];
  [inactiveBrowser->GetCommandDispatcher() prepareForShutdown];
  [_mainBrowser->GetCommandDispatcher() prepareForShutdown];

  // At this stage, new BrowserCoordinators shouldn't be lazily constructed by
  // calling their property getters.
  [_mainBrowserCoordinator stop];
  _mainBrowserCoordinator = nil;
  [_incognitoBrowserCoordinator stop];
  _incognitoBrowserCoordinator = nil;

  // Destroy all Browsers. This handles removing observers, stopping crash key
  // monitoring, closing all tabs, ... Should be in reverse order of -init.
  [self cleanupBrowser:_otrBrowser.get()];
  _otrBrowser.reset();

  [self cleanupBrowser:inactiveBrowser];
  [self cleanupBrowser:_mainBrowser.get()];
  _mainBrowser->DestroyInactiveBrowser();
  _mainBrowser.reset();

  _browserState = nullptr;
}

#pragma mark - Internal methods

- (void)dispatchToEndpointsForBrowser:(Browser*)browser {
  IncognitoReauthSceneAgent* reauthAgent =
      [IncognitoReauthSceneAgent agentFromScene:_sceneState];

  CommandDispatcher* dispatcher = browser->GetCommandDispatcher();
  [dispatcher startDispatchingToTarget:reauthAgent
                           forProtocol:@protocol(IncognitoReauthCommands)];

  [dispatcher startDispatchingToTarget:_applicationEndpoint
                           forProtocol:@protocol(ApplicationCommands)];
  [dispatcher startDispatchingToTarget:_settingsEndpoint
                           forProtocol:@protocol(SettingsCommands)];
  [dispatcher startDispatchingToTarget:_browsingDataEndpoint
                           forProtocol:@protocol(BrowsingDataCommands)];
}

// Sets up an existing browser.
- (void)setupBrowser:(Browser*)browser {
  ChromeBrowserState* browserState = browser->GetBrowserState();
  BrowserList* browserList =
      BrowserListFactory::GetForBrowserState(browserState);
  if (browserState->IsOffTheRecord()) {
    browserList->AddIncognitoBrowser(browser);
  } else {
    browserList->AddBrowser(browser);
  }

  [self dispatchToEndpointsForBrowser:browser];

  [self setSessionIDForBrowser:browser];

  crash_report_helper::MonitorTabStateForWebStateList(
      browser->GetWebStateList());

  // Follow loaded URLs in the non-incognito browser to send those in case of
  // crashes.
  if (!browserState->IsOffTheRecord()) {
    crash_report_helper::MonitorURLsForWebStateList(browser->GetWebStateList());
  }
}

// Create the OTR interface object.
- (WrangledBrowser*)createOTRInterface {
  DCHECK(!_incognitoInterface);

  // The backing coordinator should not have been created yet.
  DCHECK(!_incognitoBrowserCoordinator);
  _incognitoBrowserCoordinator =
      [[BrowserCoordinator alloc] initWithBaseViewController:nil
                                                     browser:_otrBrowser.get()];
  [_incognitoBrowserCoordinator start];

  DCHECK(_incognitoBrowserCoordinator.viewController);
  return [[WrangledBrowser alloc]
      initWithCoordinator:_incognitoBrowserCoordinator];
}

// Cleanup `browser` and associated state before destroying it.
- (void)cleanupBrowser:(Browser*)browser {
  DCHECK(browser);

  // Remove the Browser from the browser list. The browser itself is still
  // alive during this call, so any observer can act on it.
  ChromeBrowserState* browserState = browser->GetBrowserState();
  BrowserList* browserList =
      BrowserListFactory::GetForBrowserState(browserState);
  if (browserState->IsOffTheRecord()) {
    browserList->RemoveIncognitoBrowser(browser);
  } else {
    browserList->RemoveBrowser(browser);
  }

  // Stop serializing the state of `browser`.
  SessionRestorationServiceFactory::GetForBrowserState(browserState)
      ->Disconnect(browser);

  WebStateList* webStateList = browser->GetWebStateList();
  crash_report_helper::StopMonitoringTabStateForWebStateList(webStateList);
  if (!browser->GetBrowserState()->IsOffTheRecord()) {
    crash_report_helper::StopMonitoringURLsForWebStateList(webStateList);
  }

  // Close all webstates in `webStateList`. Do this in an @autoreleasepool as
  // WebStateList observers will be notified (they are unregistered later). As
  // some of them may be implemented in Objective-C and unregister themselves
  // in their -dealloc method, ensure the -autorelease introduced by ARC are
  // processed before the WebStateList destructor is called.
  @autoreleasepool {
    CloseAllWebStates(*webStateList, WebStateList::CLOSE_NO_FLAGS);
  }
}

// Configures the BrowserAgent with the session identifier for `browser`.
- (void)setSessionIDForBrowser:(Browser*)browser {
  const std::string identifier = session_util::GetSessionIdentifier(browser);

  SnapshotBrowserAgent::FromBrowser(browser)->SetSessionID(identifier);

  ChromeBrowserState* browserState = browser->GetBrowserState();
  SessionRestorationServiceFactory::GetForBrowserState(browserState)
      ->SetSessionID(browser, identifier);
}

// Load session for `browser`.
- (void)loadSessionForBrowser:(Browser*)browser {
  ChromeBrowserState* browserState = browser->GetBrowserState();
  SessionRestorationServiceFactory::GetForBrowserState(browserState)
      ->LoadSession(browser);
}

@end
