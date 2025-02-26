// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 * @fileoverview
 * Suite of browser tests for the Search and Assistant settings card element.
 * This suite of tests runs when the OsSettingsRevampWayfinding feature flag is
 * both enabled and disabled.
 */

import {OsSettingsRoutes, Router, routes, SearchAndAssistantSettingsCardElement, settingMojom, SettingsToggleButtonElement} from 'chrome://os-settings/os_settings.js';
import {loadTimeData} from 'chrome://resources/js/load_time_data.js';
import {getDeepActiveElement} from 'chrome://resources/js/util.js';
import {flush} from 'chrome://resources/polymer/v3_0/polymer/polymer_bundled.min.js';
import {assertEquals, assertFalse, assertNull, assertTrue} from 'chrome://webui-test/chai_assert.js';
import {waitAfterNextRender} from 'chrome://webui-test/polymer_test_util.js';
import {eventToPromise, isVisible} from 'chrome://webui-test/test_util.js';

interface SubpageTriggerData {
  triggerSelector: string;
  routeName: keyof OsSettingsRoutes;
}

suite('<search-and-assistant-settings-card>', () => {
  const isRevampWayfindingEnabled =
      loadTimeData.getBoolean('isRevampWayfindingEnabled');
  const defaultRoute =
      isRevampWayfindingEnabled ? routes.SYSTEM_PREFERENCES : routes.OS_SEARCH;

  let searchAndAssistantSettingsCard: SearchAndAssistantSettingsCardElement;

  function createSearchAndAssistantCard() {
    searchAndAssistantSettingsCard =
        document.createElement('search-and-assistant-settings-card');
    document.body.appendChild(searchAndAssistantSettingsCard);
    flush();
  }

  setup(() => {
    loadTimeData.overrideValues({
      isAssistantAllowed: false,
      isQuickAnswersSupported: false,
    });
  });

  teardown(() => {
    searchAndAssistantSettingsCard.remove();
    Router.getInstance().resetRouteForTesting();
  });

  suite('when Quick Answers is supported', () => {
    setup(() => {
      loadTimeData.overrideValues({isQuickAnswersSupported: true});
    });

    test('Search subpage row should be visible', () => {
      createSearchAndAssistantCard();
      const searchRow =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#searchRow');
      assertTrue(isVisible(searchRow));
    });

    test('Search engine row should not be stamped', () => {
      createSearchAndAssistantCard();
      const searchEngineRow =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              'settings-search-engine');
      assertNull(searchEngineRow);
    });
  });

  suite('Mahi setting toggle', () => {
    test('should appear if isMahiEnabled flag is true.', () => {
      loadTimeData.overrideValues({
        isMahiEnabled: true,
      });
      createSearchAndAssistantCard();
      assertTrue(
          isVisible(searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#mahiToggle')));
    });

    test('should be hidden if isMahiEnabled flag is false.', () => {
      loadTimeData.overrideValues({
        isMahiEnabled: false,
      });
      createSearchAndAssistantCard();
      assertNull(searchAndAssistantSettingsCard.shadowRoot!.querySelector(
          '#mahiToggle'));
    });

    test('Mahi toggle reflects pref value', () => {
      loadTimeData.overrideValues({
        isMahiEnabled: true,
      });
      createSearchAndAssistantCard();
      const fakePrefs = {
        settings: {
          mahi_enabled: {
            value: true,
          },
        },
      };
      searchAndAssistantSettingsCard.prefs = fakePrefs;
      flush();

      const mahiToggle =
          searchAndAssistantSettingsCard.shadowRoot!
              .querySelector<SettingsToggleButtonElement>('#mahiToggle');
      assertTrue(!!mahiToggle);

      assertTrue(mahiToggle.checked);
      assertTrue(searchAndAssistantSettingsCard.get(
          'prefs.settings.mahi_enabled.value'));

      // Click the toggle change the value of the pref
      mahiToggle.click();
      assertFalse(mahiToggle.checked);
      assertFalse(searchAndAssistantSettingsCard.get(
          'prefs.settings.mahi_enabled.value'));
    });

    test('Mahi Toggle is deep linkable', async () => {
      createSearchAndAssistantCard();

      const setting = settingMojom.Setting.kMahiOnOff;
      const params = new URLSearchParams();
      params.append('settingId', setting.toString());
      Router.getInstance().navigateTo(defaultRoute, params);

      const deepLinkElement =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector<HTMLElement>(
              '#mahiToggle');
      assertTrue(!!deepLinkElement);

      await waitAfterNextRender(deepLinkElement);
      assertEquals(
          deepLinkElement,
          searchAndAssistantSettingsCard.shadowRoot!.activeElement,
          `Element should be focused for settingId=${setting}.'`);
    });
  });

  suite('when Quick Answers is not supported', () => {
    test('Search engine row should be visible', () => {
      createSearchAndAssistantCard();
      const searchEngineRow =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              'settings-search-engine');
      assertTrue(isVisible(searchEngineRow));
    });

    test('Search subpage row should not be stamped', () => {
      createSearchAndAssistantCard();
      const searchRow =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#searchRow');
      assertNull(searchRow);
    });

    test('Search engine select is deep linkable', async () => {
      createSearchAndAssistantCard();

      const params = new URLSearchParams();
      params.append('settingId', '600');
      Router.getInstance().navigateTo(defaultRoute, params);

      const settingsSearchEngine =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              'settings-search-engine');
      assertTrue(!!settingsSearchEngine);

      const browserSearchSettingsLink =
          settingsSearchEngine.shadowRoot!.querySelector(
              '#browserSearchSettingsLink');
      assertTrue(!!browserSearchSettingsLink);

      const deepLinkElement =
          browserSearchSettingsLink.shadowRoot!.querySelector('cr-icon-button');
      assertTrue(!!deepLinkElement);

      await waitAfterNextRender(deepLinkElement);
      assertEquals(
          deepLinkElement, getDeepActiveElement(),
          'Preferred search dropdown should be focused for settingId=600.');
    });
  });

  suite('when Assistant settings are available', () => {
    setup(() => {
      loadTimeData.overrideValues({isAssistantAllowed: true});
    });

    test('Assistant row should be visible', () => {
      createSearchAndAssistantCard();
      const assistantRow =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#assistantRow');
      assertTrue(isVisible(assistantRow));
    });
  });

  suite('when Assistant settings are not available', () => {
    test('Assistant row should not be stamped', () => {
      createSearchAndAssistantCard();
      const assistantRow =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#assistantRow');
      assertNull(assistantRow);
    });
  });

  const subpageTriggerData: SubpageTriggerData[] = [
    {
      triggerSelector: '#searchRow',
      routeName: 'SEARCH_SUBPAGE',
    },
    {
      triggerSelector: '#assistantRow',
      routeName: 'GOOGLE_ASSISTANT',
    },
  ];
  subpageTriggerData.forEach(({triggerSelector, routeName}) => {
    test(
        `Row for ${routeName} is focused when returning from subpage`,
        async () => {
          loadTimeData.overrideValues({
            isAssistantAllowed: true,       // Show google assistant row
            isQuickAnswersSupported: true,  // Show quick answers row
          });
          createSearchAndAssistantCard();

          Router.getInstance().navigateTo(defaultRoute);

          const subpageTrigger =
              searchAndAssistantSettingsCard.shadowRoot!
                  .querySelector<HTMLElement>(triggerSelector);
          assertTrue(!!subpageTrigger);

          // Sub-page trigger navigates to subpage for route
          subpageTrigger.click();
          assertEquals(routes[routeName], Router.getInstance().currentRoute);

          // Navigate back
          const popStateEventPromise = eventToPromise('popstate', window);
          Router.getInstance().navigateToPreviousRoute();
          await popStateEventPromise;
          await waitAfterNextRender(searchAndAssistantSettingsCard);

          assertEquals(
              subpageTrigger,
              searchAndAssistantSettingsCard.shadowRoot!.activeElement,
              `${triggerSelector} should be focused.`);
        });
  });

  if (isRevampWayfindingEnabled) {
    test('Content recommendations toggle is visible', () => {
      createSearchAndAssistantCard();
      const contentRecommendationsToggle =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#contentRecommendationsToggle');
      assertTrue(isVisible(contentRecommendationsToggle));
    });

    test('Content recommendations toggle reflects pref value', () => {
      createSearchAndAssistantCard();
      const fakePrefs = {
        settings: {
          suggested_content_enabled: {
            value: true,
          },
        },
      };
      searchAndAssistantSettingsCard.prefs = fakePrefs;
      flush();

      const contentRecommendationsToggle =
          searchAndAssistantSettingsCard.shadowRoot!
              .querySelector<SettingsToggleButtonElement>(
                  '#contentRecommendationsToggle');
      assertTrue(!!contentRecommendationsToggle);

      assertTrue(contentRecommendationsToggle.checked);
      assertTrue(searchAndAssistantSettingsCard.get(
          'prefs.settings.suggested_content_enabled.value'));

      contentRecommendationsToggle.click();
      assertFalse(contentRecommendationsToggle.checked);
      assertFalse(searchAndAssistantSettingsCard.get(
          'prefs.settings.suggested_content_enabled.value'));
    });
  } else {
    test('Content recommendations toggle is not stamped', () => {
      createSearchAndAssistantCard();
      const contentRecommendationsToggle =
          searchAndAssistantSettingsCard.shadowRoot!.querySelector(
              '#contentRecommendationsToggle');
      assertNull(contentRecommendationsToggle);
    });
  }
});
