// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.overlays.strip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

import org.chromium.base.Callback;
import org.chromium.base.test.BaseRobolectricTestRunner;
import org.chromium.base.test.util.Feature;
import org.chromium.base.test.util.Features.DisableFeatures;
import org.chromium.base.test.util.Features.EnableFeatures;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutRenderHost;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.compositor.layouts.components.CompositorButton;
import org.chromium.chrome.browser.compositor.layouts.components.CompositorButton.CompositorOnClickHandler;
import org.chromium.chrome.browser.compositor.layouts.components.TintedCompositorButton;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.layouts.animation.CompositorAnimationHandler;
import org.chromium.chrome.browser.layouts.components.VirtualView;
import org.chromium.chrome.browser.multiwindow.MultiWindowTestUtils;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.MockTab;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabCreationState;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tab.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabCreator;
import org.chromium.chrome.browser.tasks.tab_groups.TabGroupModelFilter;
import org.chromium.chrome.browser.tasks.tab_groups.TabGroupModelFilterObserver;
import org.chromium.chrome.browser.tasks.tab_management.ActionConfirmationManager;
import org.chromium.chrome.browser.tasks.tab_management.ActionConfirmationManager.ConfirmationResult;
import org.chromium.chrome.browser.tasks.tab_management.TabUiThemeUtil;
import org.chromium.chrome.test.util.browser.tabmodel.MockTabModel;
import org.chromium.components.browser_ui.styles.SemanticColorUtils;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.shadows.ShadowAppCompatResources;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Tests for {@link StripLayoutHelper}. */
@RunWith(BaseRobolectricTestRunner.class)
@Config(
        manifest = Config.NONE,
        qualifiers = "sw600dp",
        shadows = {ShadowAppCompatResources.class})
@LooperMode(Mode.LEGACY)
public class StripLayoutHelperTest {
    @Mock private View mInteractingTabView;
    @Mock private LayoutManagerHost mManagerHost;
    @Mock private LayoutUpdateHost mUpdateHost;
    @Mock private LayoutRenderHost mRenderHost;
    @Mock private CompositorButton mModelSelectorBtn;
    @Mock private TabGroupModelFilter mTabGroupModelFilter;
    @Mock private View mToolbarContainerView;
    @Mock private StripTabHoverCardView mTabHoverCardView;
    @Mock private Profile mProfile;
    @Mock private CompositorOnClickHandler mClickHandler;
    @Mock private TabDragSource mTabDragSource;
    @Mock private WindowAndroid mWindowAndroid;
    @Mock private LayerTitleCache mLayerTitleCache;
    @Mock private ActionConfirmationManager mActionConfirmationManager;
    @Captor private ArgumentCaptor<Callback<Integer>> mConfirmationResultCaptor;

    private Activity mActivity;
    private Context mContext;
    private TestTabModel mModel = new TestTabModel();
    private StripLayoutHelper mStripLayoutHelper;
    private boolean mIncognito;

    private static final String[] TEST_TAB_TITLES = {"Tab 1", "Tab 2", "Tab 3", "", null};
    private static final String TEST_GROUP_TITLE = "Group";
    private static final String EXPECTED_MARGIN = "The tab should have a trailing margin.";
    private static final String EXPECTED_NO_MARGIN = "The tab should not have a trailing margin.";
    private static final String EXPECTED_TAB = "The view should be a tab.";
    private static final String EXPECTED_TITLE = "The view should be a title.";
    private static final String EXPECTED_NON_TITLE = "The view should not be a title.";
    private static final String CLOSE_TAB = "Close %1$s tab";
    private static final String IDENTIFIER = "Tab";
    private static final String IDENTIFIER_SELECTED = "Selected Tab";
    private static final String INCOGNITO_IDENTIFIER = "Incognito Tab";
    private static final String INCOGNITO_IDENTIFIER_SELECTED = "Selected Incognito Tab";
    private static final float SCREEN_WIDTH = 800.f;
    private static final float SCREEN_WIDTH_LANDSCAPE = 1200.f;
    // TODO(wenyufu): This needs to be renamed to TAB_STRIP_HEIGHT.
    private static final float SCREEN_HEIGHT = 40.f;
    private static final float TAB_WIDTH_1 = 140.f;
    private static final float TAB_WIDTH_SMALL = 108.f;
    private static final float TAB_OVERLAP_WIDTH = 28.f;
    private static final float TAB_WIDTH_MEDIUM = 156.f;
    private static final long TIMESTAMP = 5000;
    private static final float NEW_TAB_BTN_X_RTL = 100.f;
    private static final float NEW_TAB_BTN_X = 700.f;
    private static final float NEW_TAB_BTN_Y = 1400.f;
    private static final float NEW_TAB_BTN_WIDTH = 100.f;
    private static final float NEW_TAB_BTN_HEIGHT = 100.f;
    private static final float PADDING_LEFT = 10.f;
    private static final float PADDING_RIGHT = 20.f;
    private static final float REORDER_OVERLAP_SWITCH_PERCENTAGE = 0.53f;
    private static final PointF DRAG_START_POINT = new PointF(70f, 20f);
    private static final float EPSILON = 0.001f;

    /** Reset the environment before each test. */
    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);
        when(mTabGroupModelFilter.isTabInTabGroup(any())).thenReturn(false);
        mContext =
                new ContextThemeWrapper(
                        ApplicationProvider.getApplicationContext(),
                        R.style.Theme_BrowserUI_DayNight);

        mActivity = Robolectric.setupActivity(Activity.class);
        mActivity.setTheme(org.chromium.chrome.R.style.Theme_BrowserUI);
        when(mWindowAndroid.getActivity()).thenReturn(new WeakReference<>(mActivity));
    }

    @After
    public void tearDown() {
        if (mStripLayoutHelper != null) {
            mStripLayoutHelper.stopReorderModeForTesting();
            mStripLayoutHelper.setTabAtPositionForTesting(null);
            mStripLayoutHelper.setRunningAnimatorForTesting(null);
        }
        mTabDragSource = null;
    }

    /**
     * Test method for {@link StripLayoutHelper#getVirtualViews(List<VirtualView>)}.
     *
     * Checks that it returns the correct order of tabs, including correct content.
     */
    @Test
    @Feature({"Accessibility"})
    public void testSimpleTabOrder() {
        initializeTest(false, false, 0);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        assertTabStripAndOrder(getExpectedAccessibilityDescriptions(0));
    }

    /**
     * Test method for {@link StripLayoutHelper#getVirtualViews(List<VirtualView>)}.
     *
     * Checks that it returns the correct order of tabs, even when a tab except the first one is
     * selected.
     */
    @Test
    @Feature({"Accessibility"})
    public void testTabOrderWithIndex() {
        initializeTest(false, false, 1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Tabs should be in left to right order regardless of index
        assertTabStripAndOrder(getExpectedAccessibilityDescriptions(1));
    }

    /**
     * Test method for {@link StripLayoutHelper#getVirtualViews(List<VirtualView>)}.
     *
     * Checks that it returns the correct order of tabs, even in RTL mode.
     */
    @Test
    @Feature({"Accessibility"})
    public void testTabOrderRtl() {
        initializeTest(true, false, 0);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Tabs should be in linear order even in RTL.
        // Android will take care of reversing it.
        assertTabStripAndOrder(getExpectedAccessibilityDescriptions(0));
    }

    /**
     * Test method for {@link StripLayoutHelper#getVirtualViews(List<VirtualView>)}.
     *
     * Checks that it returns the correct order of tabs, even in incognito mode.
     */
    @Test
    @Feature({"Accessibility"})
    public void testIncognitoAccessibilityDescriptions() {
        initializeTest(false, true, 0);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        assertTabStripAndOrder(getExpectedAccessibilityDescriptions(0));
    }

    @Test
    @Feature({"Accessibility"})
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testGroupIndicatorAccessibilityDescriptions_OneTab() {
        // Setup and group first tab.
        initializeTest(false, false, 0);
        groupTabs(0, 1);

        // Verify.
        String expectedDescription = "Unnamed group - Tab 1";
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue("First should be a group title.", views[0] instanceof StripLayoutGroupTitle);
        assertEquals(
                "A11y description for group title was wrong.",
                expectedDescription,
                views[0].getAccessibilityDescription());
    }

    @Test
    @Feature({"Accessibility"})
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testGroupIndicatorAccessibilityDescriptions_MultipleTabs() {
        // Setup and group first three tabs.
        initializeTest(false, false, 0);
        groupTabs(0, 3);

        // Verify.
        String expectedDescription = "Unnamed group - Tab 1 and 2 other tabs";
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue("First should be a group title.", views[0] instanceof StripLayoutGroupTitle);
        assertEquals(
                "A11y description for group title was wrong.",
                expectedDescription,
                views[0].getAccessibilityDescription());
    }

    @Test
    @Feature({"Accessibility"})
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testGroupIndicatorAccessibilityDescriptions_MultipleTabs_NamedGroup() {
        // Setup and group first three tabs. Name the group.
        when(mTabGroupModelFilter.getTabGroupTitle(0)).thenReturn("Group name");
        initializeTest(false, false, 0);
        groupTabs(0, 3);

        // Verify.
        String expectedDescription = "Group name - Tab 1 and 2 other tabs";
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue("First should be a group title.", views[0] instanceof StripLayoutGroupTitle);
        assertEquals(
                "A11y description for group title was wrong.",
                expectedDescription,
                views[0].getAccessibilityDescription());
    }

    @Test
    public void testAllTabsClosed() {
        initializeTest(false, false, 0);
        assertTrue(
                mStripLayoutHelper.getStripLayoutTabsForTesting().length == TEST_TAB_TITLES.length);

        // Close all tabs
        mModel.closeAllTabs();

        // Notify strip of tab closure
        mStripLayoutHelper.willCloseAllTabs();

        // Verify strip has no tabs.
        assertTrue(mStripLayoutHelper.getStripLayoutTabsForTesting().length == 0);
    }

    @Test
    public void testTabSelected_SelectedNonLastTab_ShowCloseBtn() {
        initializeTest(false, true, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Non-last tab not overlapping strip fade:
        // drawX(530) + tabWidth(140 - 28) < width(800) - offsetXRight(20) - longRightFadeWidth(136)
        when(tabs[3].getDrawX()).thenReturn(530.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 3, Tab.INVALID_TAB_ID, false);

        // Close btn is visible on the selected tab.
        verify(tabs[3]).setCanShowCloseButton(true, false);
        // Close btn is hidden for the rest of tabs.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[4]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedNonLastTab_HideCloseBtn() {
        initializeTest(false, true, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Non-last tab overlapping strip fade:
        // drawX(600) + tabWidth(140 - 28) > width(800) - offsetXRight(20) - longRightFadeWidth(136)
        when(tabs[3].getDrawX()).thenReturn(600.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 3, Tab.INVALID_TAB_ID, false);

        // Close btn is hidden on the selected tab.
        verify(tabs[3]).setCanShowCloseButton(false, false);
        // Close btn is hidden for the rest of tabs as well.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[4]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedLastTab_ShowCloseBtn() {
        initializeTest(false, true, 4);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.getNewTabButton().setDrawX(NEW_TAB_BTN_X);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Last tab not overlapping NTB:
        // drawX(550) > NTB_X(700) + tabOverlapWidth(28) - tabWidth(140)
        when(tabs[4].getDrawX()).thenReturn(550.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 4, Tab.INVALID_TAB_ID, false);

        // Close btn is visible on the selected last tab.
        verify(tabs[4]).setCanShowCloseButton(true, false);
        // Close button is hidden for the rest of tabs.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[3]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedLastTab_HideCloseBtn() {
        initializeTest(false, true, 4);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.getNewTabButton().setDrawX(NEW_TAB_BTN_X);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Last tab overlapping NTB:
        // drawX(600) > NTB_X(700) + tabOverlapWidth(28) - tabWidth(140)
        when(tabs[4].getDrawX()).thenReturn(600.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 4, Tab.INVALID_TAB_ID, false);

        // Close btn is hidden on the selected last tab.
        verify(tabs[4]).setCanShowCloseButton(false, false);
        // Close button is hidden for the rest of tabs.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[3]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedNonLastTab_NoModelSelBtn_HideCloseBtn() {
        initializeTest(false, false, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Non-last tab overlapping strip fade:
        // drawX(630) + tabWidth(140 - 28) > width(800) - offsetXRight(20) -
        // mediumRightFadeWidth(72)
        when(tabs[3].getDrawX()).thenReturn(630.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 3, Tab.INVALID_TAB_ID, false);

        // Close button is hidden for selected tab.
        verify(tabs[3]).setCanShowCloseButton(false, false);
        // Close button is hidden for the rest of tabs as well.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[4]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedNonLastTab_NoModelSelBtn_ShowCloseBtn() {
        initializeTest(false, false, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Non-last tab not overlapping strip fade:
        // drawX(580) + tabWidth(140 - 28) > width(800) - offsetXRight(20) -
        // mediumRightFadeWidth(72)
        when(tabs[3].getDrawX()).thenReturn(580.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 3, Tab.INVALID_TAB_ID, false);

        // Close button is visible for selected tab
        verify(tabs[3]).setCanShowCloseButton(true, false);
        // Close button is hidden for the rest of tabs.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[4]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedLastTab_RTL_HideCloseBtn() {
        initializeTest(true, false, 4);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.getNewTabButton().setDrawX(NEW_TAB_BTN_X_RTL);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Last tab overlapping NTB:
        // drawX(100) + tabOverlapWidth(28) < NTB_X(100) + NTB_WIDTH(100)
        when(tabs[4].getDrawX()).thenReturn(100.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 4, Tab.INVALID_TAB_ID, false);

        // Close button is hidden for the selected last tab.
        verify(tabs[4]).setCanShowCloseButton(false, false);
        // Close button is hidden for the rest of tabs as well.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[3]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedLastTab_RTL_ShowCloseBtn() {
        initializeTest(true, false, 4);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.getNewTabButton().setDrawX(NEW_TAB_BTN_X_RTL);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Last tab not overlapping NTB:
        // drawX(200) + tabOverlapWidth(28) > NTB_X(100) + NTB_WIDTH(100)
        when(tabs[4].getDrawX()).thenReturn(200.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 4, Tab.INVALID_TAB_ID, false);

        // Close button is visible for selected last tab.
        verify(tabs[4]).setCanShowCloseButton(true, false);
        // Close button is hidden for the rest of tabs.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[3]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedNonLastTab_RTL_HideCloseBtn() {
        initializeTest(true, false, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Non-last tab overlapping strip fade:
        // drawX(50) + tabOverlapWidth(28) < offsetXRight(20) + mediumRightFadeWidth(72)
        when(tabs[3].getDrawX()).thenReturn(50.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 3, Tab.INVALID_TAB_ID, false);

        // Close btn is hidden for selected tab.
        verify(tabs[3]).setCanShowCloseButton(false, false);
        // Close btn is hidden for all the rest of tabs as well.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[4]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testTabSelected_SelectedNonLastTab_RTL_ShowCloseBtn() {
        initializeTest(true, false, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.getNewTabButton().setDrawX(NEW_TAB_BTN_X);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Non-last tab not overlapping strip fade:
        // drawX(70) + tabOverlapWidth(28) > offsetXRight(20) + mediumRightFadeWidth(72)
        when(tabs[3].getDrawX()).thenReturn(70.f);
        mStripLayoutHelper.tabSelected(TIMESTAMP, 3, Tab.INVALID_TAB_ID, false);

        // Close button is visible for the selected tab.
        verify(tabs[3]).setCanShowCloseButton(true, false);
        // Close button is hidden for the rest of tabs.
        verify(tabs[0]).setCanShowCloseButton(false, false);
        verify(tabs[1]).setCanShowCloseButton(false, false);
        verify(tabs[2]).setCanShowCloseButton(false, false);
        verify(tabs[4]).setCanShowCloseButton(false, false);
    }

    @Test
    public void testUpdateDividers_WithTabSelected() {
        // Setup with 5 tabs. Select tab 2.
        initializeTest(false, false, 2);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        // group 2nd and 3rd tab.
        groupTabs(1, 3);

        // Trigger update to set divider values.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify tabs 2 and 3's start dividers are hidden due to selection.
        assertFalse(
                "First start divider should always be hidden.", tabs[0].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[1].isStartDividerVisible());
        assertFalse(
                "Start divider is for selected tab and should be hidden.",
                tabs[2].isStartDividerVisible());
        assertFalse(
                "Start divider is adjacent to selected tab and should be hidden.",
                tabs[3].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[4].isStartDividerVisible());

        // Verify only last tab's end divider is visible.
        assertFalse("End divider should be hidden.", tabs[0].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[1].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[2].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[3].isEndDividerVisible());
        assertTrue("End divider should be visible.", tabs[4].isEndDividerVisible());
    }

    @Test
    public void testUpdateDividers_InReorderMode() {
        // Setup with 5 tabs. Select 2nd tab.
        initializeTest(false, false, true, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder mode at 2nd tab
        mStripLayoutHelper.startReorderModeAtIndexForTesting(1);
        // Trigger update to set divider values.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        // Verify only 4th and 5th tab's start divider is visible.
        assertFalse(
                "First start divider should always be hidden.", tabs[0].isStartDividerVisible());
        assertFalse("Start divider should be hidden.", tabs[1].isStartDividerVisible());
        assertFalse("Start divider should be hidden.", tabs[2].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[3].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[4].isStartDividerVisible());

        // Verify end divider visible only for 5th tab.
        assertFalse("End divider should be hidden.", tabs[0].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[1].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[2].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[3].isEndDividerVisible());
        assertTrue("End divider should be visible.", tabs[4].isEndDividerVisible());
    }

    @Test
    public void testUpdateDividers_InReorderModeWithTabGroups() {
        // Setup with 5 tabs. Select 2nd tab.
        initializeTest(false, false, true, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        // group 2nd and 3rd tab.
        groupTabs(1, 3);

        // Start reorder mode at 2nd tab
        mStripLayoutHelper.startReorderModeAtIndexForTesting(1);
        // Trigger update to set divider values.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        // Verify only 4th and 5th tab's start divider is visible.
        assertFalse(
                "First start divider should always be hidden.", tabs[0].isStartDividerVisible());
        assertFalse("Start divider should be hidden.", tabs[1].isStartDividerVisible());
        assertFalse("Start divider should be hidden.", tabs[2].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[3].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[4].isStartDividerVisible());

        // Verify end divider visible for 1st and 5th tab.
        assertTrue("End divider should be visible.", tabs[0].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[1].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[2].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[3].isEndDividerVisible());
        assertTrue("End divider should be visible.", tabs[4].isEndDividerVisible());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testUpdateDividers_InReorderModeWithTabGroups_TabGroupIndicators() {
        // Setup with 5 tabs. Select 2nd tab.
        initializeTest(false, false, true, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        // group 2nd and 3rd tab.
        groupTabs(1, 3);

        // Start reorder mode at 2nd tab
        mStripLayoutHelper.startReorderModeAtIndexForTesting(1);
        // Trigger update to set divider values.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        // Verify only 4th and 5th tab's start divider is visible.
        assertFalse(
                "First start divider should always be hidden.", tabs[0].isStartDividerVisible());
        assertFalse("Start divider should be hidden.", tabs[1].isStartDividerVisible());
        assertFalse("Start divider should be hidden.", tabs[2].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[3].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[4].isStartDividerVisible());

        // Verify end divider visible for 1st and 5th tab.
        assertTrue("End divider should be visible.", tabs[0].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[1].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[2].isEndDividerVisible());
        assertFalse("End divider should be hidden.", tabs[3].isEndDividerVisible());
        assertTrue("End divider should be visible.", tabs[4].isEndDividerVisible());
    }

    @Test
    public void testUpdateForegroundTabContainers() {
        // Setup with 5 tabs. Select tab 2.
        initializeTest(false, false, 2);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();

        // Trigger update to set foreground container visibility.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify tabs 2 and 3's dividers are hidden due to selection.
        float hiddenOpacity = StripLayoutHelper.TAB_OPACITY_HIDDEN;
        float visibleOpacity = StripLayoutHelper.TAB_OPACITY_VISIBLE_FOREGROUND;
        assertEquals(
                "Tab is not selected and container should not be visible.",
                hiddenOpacity,
                tabs[0].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Tab is not selected and container should not be visible.",
                hiddenOpacity,
                tabs[1].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Tab is selected and container should be visible.",
                visibleOpacity,
                tabs[2].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Tab is not selected and container should not be visible.",
                hiddenOpacity,
                tabs[3].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Tab is not selected and container should not be visible.",
                hiddenOpacity,
                tabs[4].getContainerOpacity(),
                EPSILON);
    }

    @Test
    public void testNewTabButtonYPosition_Folio() {
        int tabCount = 4;
        initializeTest(false, false, false, 3, tabCount);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Set New tab button position.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify new tab button y-position.
        assertEquals(
                "New tab button y-position is not as expected",
                3.f,
                mStripLayoutHelper.getNewTabButton().getDrawY(),
                EPSILON);
    }

    @Test
    public void testNewTabButtonXPosition() {
        // Setup
        int tabCount = 1;
        initializeTest(false, false, false, 0, tabCount);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify new tab button position.
        // tabWidth(237) + tabOverLapWidth(28) - ntbOffsetTowardsTabs(4) + offsetXLeft(10) = 271
        assertEquals(
                "New tab button x-position is not as expected",
                271.f,
                mStripLayoutHelper.getNewTabButton().getDrawX(),
                EPSILON);
        // rightBound(311) = expectedNtbDrawX(271) + ntbWidth(32) + touchSlop(8)
        assertEquals(
                "TouchableRect does not match. Right size should match ntb.getDrawX() + width.",
                new RectF(PADDING_LEFT, 0, 311.f, SCREEN_HEIGHT),
                mStripLayoutHelper.getTouchableRect());
    }

    @Test
    public void testNewTabButtonXPosition_TabStripFull() {
        // Setup
        int tabCount = 5;
        initializeTest(false, false, false, 0, tabCount);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify new tab button position.
        // stripWidth(800) - offsetXRight(20) - stripEndPadding(8) - NtbWidth(32) = 740
        assertEquals(
                "New tab button x-position is not as expected",
                740.f,
                mStripLayoutHelper.getNewTabButton().getDrawX(),
                EPSILON);
        assertEquals(
                "TouchableRect does not match. Strip is full, touch size should match the strip.",
                new RectF(PADDING_LEFT, 0, SCREEN_WIDTH - PADDING_RIGHT, SCREEN_HEIGHT),
                mStripLayoutHelper.getTouchableRect());
    }

    @Test
    public void testNewTabButtonXPosition_RTL() {
        int tabCount = 1;
        initializeTest(true, false, false, 0, tabCount);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify new tab button position.
        // stripWidth(800) - offsetXRight(20) - tabWidth(237) - tabOverLapWidth(28) - NtbWidth(32) +
        // ntbOffsetTowardsTabs(4) = 487
        assertEquals(
                "New tab button x-position is not as expected",
                487,
                mStripLayoutHelper.getNewTabButton().getDrawX(),
                EPSILON);
        // leftBound(479) = drawX(487) - touchSlop(8)
        assertEquals(
                "TouchableRect does not match. Left side should equal to ntb.getDrawX()",
                new RectF(479.f, 0, SCREEN_WIDTH - PADDING_RIGHT, SCREEN_HEIGHT),
                mStripLayoutHelper.getTouchableRect());
    }

    @Test
    public void testNewTabButtonXPosition_TabStripFull_RTL() {
        // Setup
        int tabCount = 5;
        initializeTest(true, false, false, 0, tabCount);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify new tab button position.
        // offsetXLeft(10) + buttonEndPadding(8) = 28.
        assertEquals(
                "New tab button x-position is not as expected",
                18.f,
                mStripLayoutHelper.getNewTabButton().getDrawX(),
                EPSILON);
        assertEquals(
                "TouchableRect does not match. Strip is full, touch size should match the strip.",
                new RectF(PADDING_LEFT, 0, SCREEN_WIDTH - PADDING_RIGHT, SCREEN_HEIGHT),
                mStripLayoutHelper.getTouchableRect());
    }

    @Test
    public void testNewTabButtonStyle_ButtonStyleDisabled() {
        int tabCount = 1;
        initializeTest(false, false, false, 0, tabCount);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        assertEquals(
                "Unexpected incognito button color.",
                AppCompatResources.getColorStateList(mContext, R.color.default_icon_color_tint_list)
                        .getDefaultColor(),
                ((org.chromium.chrome.browser.compositor.layouts.components.TintedCompositorButton)
                                mStripLayoutHelper.getNewTabButton())
                        .getTint());
    }

    @Test
    @Feature("Advanced Peripherals Support")
    public void testNewTabButtonHoverHighlightProperties() {
        // Setup
        initializeTest(false, false, false, 0, 1);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify new tab button hover highlight resource id.
        assertEquals(
                "New tab button hover highlight is not as expected",
                R.drawable.bg_circle_tab_strip_button,
                mStripLayoutHelper.getNewTabButton().getBackgroundResourceId());

        // Verify new tab button hover highlight default tint.
        TintedCompositorButton ntb = spy(mStripLayoutHelper.getNewTabButton());
        when(ntb.isHovered()).thenReturn(true);

        int defaultNTBHoverBackgroundTint =
                ColorUtils.setAlphaComponent(
                        SemanticColorUtils.getDefaultTextColor(mContext), (int) (0.08 * 255));
        assertEquals(
                "New tab button hover highlight default tint is not as expected",
                defaultNTBHoverBackgroundTint,
                ntb.getBackgroundTint());

        // Verify new tab button hover highlight pressed tint.
        when(ntb.isHovered()).thenReturn(false);
        when(ntb.isPressed()).thenReturn(true);
        when(ntb.isPressedFromMouse()).thenReturn(true);
        int pressedNTBHoverBackgroundTint =
                ColorUtils.setAlphaComponent(
                        SemanticColorUtils.getDefaultTextColor(mContext), (int) (0.12 * 255));
        assertEquals(
                "New tab button hover highlight pressed tint is not as expected",
                pressedNTBHoverBackgroundTint,
                ntb.getBackgroundTint());
        when(ntb.isPressedFromMouse()).thenReturn(false);

        // Verify new tab button incognito hover highlight default tint.
        when(ntb.isHovered()).thenReturn(true);
        when(ntb.isIncognito()).thenReturn(true);
        int defaultNTBHoverBackgroundIncognitoTint =
                ColorUtils.setAlphaComponent(
                        mContext.getColor(R.color.tab_strip_button_hover_bg_color),
                        (int) (0.08 * 255));
        assertEquals(
                "New tab button hover highlight default tint is not as expected",
                defaultNTBHoverBackgroundIncognitoTint,
                ntb.getBackgroundTint());

        // Verify new tab button incognito hover highlight pressed tint.
        when(ntb.isHovered()).thenReturn(false);
        when(ntb.isPressed()).thenReturn(true);
        when(ntb.isPressedFromMouse()).thenReturn(true);
        int hoverBackgroundPressedIncognitoColor =
                ColorUtils.setAlphaComponent(
                        mContext.getColor(R.color.tab_strip_button_hover_bg_color),
                        (int) (0.12 * 255));
        assertEquals(
                "New tab button hover highlight pressed tint is not as expected",
                hoverBackgroundPressedIncognitoColor,
                ntb.getBackgroundTint());
    }

    @Test
    @Feature("Advanced Peripherals Support")
    public void testNewTabButtonHoverEnter() {
        // Setup
        initializeTest(false, false, false, 0, 1);

        // Verify new tab button is hovered.
        int x = (int) mStripLayoutHelper.getNewTabButton().getDrawX();
        mStripLayoutHelper.onHoverEnter(
                x + 1, 0); // mouse position within NTB range(32dp width + 12dp click slop).
        assertTrue(
                "New tab button should be hovered",
                mStripLayoutHelper.getNewTabButton().isHovered());

        // Verify new tab button is NOT hovered
        mStripLayoutHelper.onHoverEnter(
                x + 45, 0); // mouse position out of NTB range(32dp width + 12dp click slop).
        assertFalse(
                "New tab button should NOT be hovered",
                mStripLayoutHelper.getNewTabButton().isHovered());
    }

    @Test
    @Feature("Advanced Peripherals Support")
    public void testNewTabButtonHoverOnDown() {
        // Setup
        initializeTest(false, false, false, 0, 1);

        // Verify new tab button is in pressed state, not hover state, when clicked from mouse.
        mStripLayoutHelper.onDown(
                1L, mStripLayoutHelper.getNewTabButton().getDrawX() + 1, 0, true, 1);
        assertFalse(
                "New tab button should not be hovered",
                mStripLayoutHelper.getNewTabButton().isHovered());
        assertTrue(
                "New tab button should be pressed from mouse",
                mStripLayoutHelper.getNewTabButton().isPressedFromMouse());
    }

    @Test
    @Feature("Advanced Peripherals Support")
    public void testCloseButtonHoverHighlightProperties() {
        // Setup
        initializeTest(false, false, 2);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        TintedCompositorButton closeButton = spy(tabs[0].getCloseButton());

        // Verify close button hover highlight resource id.
        assertEquals(
                "Close button hover highlight is not as expected",
                R.drawable.tab_close_button_bg,
                tabs[0].getCloseButton().getBackgroundResourceId());

        // Verify the non-hover background tint for the close button. It should always be
        // transparent, as no background should be applied when it is not being hovered over.
        assertEquals(
                "Close button non-hover background tint is not as expected",
                Color.TRANSPARENT,
                closeButton.getBackgroundTint());

        // Verify close button hover highlight default tint.
        when(closeButton.isHovered()).thenReturn(true);

        int defaultCloseButtonHoverBackgroundTint =
                ColorUtils.setAlphaComponent(
                        SemanticColorUtils.getDefaultTextColor(mContext), (int) (0.08 * 255));
        assertEquals(
                "Close button hover highlight default tint is not as expected",
                defaultCloseButtonHoverBackgroundTint,
                closeButton.getBackgroundTint());

        // Verify close button hover highlight pressed tint.
        when(closeButton.isHovered()).thenReturn(false);
        when(closeButton.isPressed()).thenReturn(true);
        when(closeButton.isPressedFromMouse()).thenReturn(true);
        int pressedCloseButtonHoverBackgroundTint =
                ColorUtils.setAlphaComponent(
                        SemanticColorUtils.getDefaultTextColor(mContext), (int) (0.12 * 255));
        assertEquals(
                "Close button hover highlight pressed tint is not as expected",
                pressedCloseButtonHoverBackgroundTint,
                closeButton.getBackgroundTint());

        when(closeButton.isPressed()).thenReturn(false);
        when(closeButton.isPressedFromMouse()).thenReturn(false);

        // Verify close button incognito hover highlight default tint.
        when(closeButton.isIncognito()).thenReturn(true);
        when(closeButton.isHovered()).thenReturn(true);
        int defaultNTBHoverBackgroundIncognitoTint =
                ColorUtils.setAlphaComponent(
                        mContext.getColor(R.color.tab_strip_button_hover_bg_color),
                        (int) (0.08 * 255));
        assertEquals(
                "Close button hover highlight default tint is not as expected",
                defaultNTBHoverBackgroundIncognitoTint,
                closeButton.getBackgroundTint());

        // Verify close button incognito hover highlight pressed tint.
        when(closeButton.isHovered()).thenReturn(false);
        when(closeButton.isPressed()).thenReturn(true);
        when(closeButton.isPressedFromMouse()).thenReturn(true);
        int hoverBackgroundPressedIncognitoColor =
                ColorUtils.setAlphaComponent(
                        mContext.getColor(R.color.tab_strip_button_hover_bg_color),
                        (int) (0.12 * 255));
        assertEquals(
                "Close button hover highlight pressed tint is not as expected",
                hoverBackgroundPressedIncognitoColor,
                closeButton.getBackgroundTint());
    }

    @Test
    @Feature("Advanced Peripherals Support")
    public void testCloseButtonHoverEnter() {
        // Setup
        initializeTest(false, false, 2);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        TintedCompositorButton closeButton =
                new TintedCompositorButton(mContext, 24.f, 24.f, mClickHandler);
        closeButton.setOpacity(1.f);
        int x = (int) closeButton.getDrawX();
        int y = (int) closeButton.getDrawY();
        StripLayoutHelper stripLayoutHelper = spy(mStripLayoutHelper);
        StripLayoutTab tab = spy(tabs[0]);
        when(stripLayoutHelper.getTabAtPosition(x)).thenReturn(tab);
        stripLayoutHelper.setTabAtPositionForTesting(tab);
        tab.setCloseButtonForTesting(closeButton);
        tab.setShowingCloseButtonForTesting(true);

        // Verify close button is hovered on.
        stripLayoutHelper.onHoverEnter(
                x + 1,
                y + 1); // mouse position within close button range(24dp width + 12dp click slop)
        assertTrue("Close button should be hovered", tab.isCloseHovered());

        // Verify close button is NOT hovered on.
        stripLayoutHelper.onHoverEnter(
                x + 37,
                y); // mouse position out of close button range(24dp width + 12dp click slop).
        assertFalse("Close button should NOT be hovered on", tab.isCloseHovered());
    }

    @Test
    @Feature("Advanced Peripherals Support")
    public void testCloseButtonHoverOnDown() {
        // Setup
        initializeTest(false, false, 2);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        TintedCompositorButton closeButton =
                new TintedCompositorButton(mContext, 24.f, 24.f, mClickHandler);
        closeButton.setOpacity(1.f);
        int x = (int) closeButton.getDrawX();
        int y = (int) closeButton.getDrawY();
        tabs[0].setCloseButtonForTesting(closeButton);

        // Verify close button is in pressed state, not hover state, when clicked from mouse.
        mStripLayoutHelper.onDown(1L, x + 1, y + 1, true, 1);
        assertFalse("Close button should not be hovered", closeButton.isHovered());
        mStripLayoutHelper.onDown(1L, (int) x + 1, y + 1, true, 1);
        assertFalse("Close should NOT be hovered", closeButton.isPressedFromMouse());

        // Verify close button is not in hover state or press state when long-pressed.
        mStripLayoutHelper.onLongPress(1L, x + 1, y + 1);
        assertFalse("Close button should NOT be hovered", closeButton.isHovered());
        assertFalse("Close button should NOT be pressed", closeButton.isPressed());
    }

    @Test
    public void testScrollOffset_OnResume_StartOnLeft_SelectedRightmostTab() {
        // Arrange: Initialize tabs with last tab selected and MSB visible (long fade).
        initializeTest(false, true, true, 9, 10);
        mStripLayoutHelper.setIsFirstLayoutPassForTesting(false);

        // Set screen width to 800dp and scroll selected tab to view.
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        mStripLayoutHelper.scrollTabToView(TIMESTAMP, false);

        // optimalEnd =
        // stripWidth(800) - rightPadding(20) - rightFade(136) - (index(9) + 1) * tabWidth(108-28) -
        // overlapWidth(28)
        float expectedFinalX = -184.f;
        assertEquals(expectedFinalX, mStripLayoutHelper.getScrollOffset(), EPSILON);
    }

    @Test
    public void testScrollOffset_OnResume_StartOnLeft_NoModelSelBtn_SelectedRightmostTab() {
        // Arrange: Initialize tabs with last tab selected and MSB not visible (medium fade).
        initializeTest(false, false, true, 9, 10);
        mStripLayoutHelper.setIsFirstLayoutPassForTesting(false);

        // Set screen width to 800dp and scroll selected tab to view.
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        mStripLayoutHelper.scrollTabToView(TIMESTAMP, false);

        // optimalEnd =
        // stripWidth(800) - rightPadding(20) - rightFade(72) - (index(9) + 1) * tabWidth(108-28) -
        // overlapWidth(28)
        float expectedFinalX = -120.f;
        assertEquals(expectedFinalX, mStripLayoutHelper.getScrollOffset(), EPSILON);
    }

    @Test
    public void testScrollOffset_OnResume_StartOnRight_SelectedLeftmostTab() {
        // Arrange: Initialize tabs with first tab selected.
        initializeTest(false, true, false, 0, 10);

        // Set screen width to 800dp and scroll selected tab to view.
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.scrollTabToView(TIMESTAMP, false);

        // optimalStart = leftFade(60) - (index(0) * tabWidth(108-28)) - leftPadding(10)
        int expectedFinalX = 50;
        assertEquals(expectedFinalX, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    public void testScrollOffset_OnOrientationChange_SelectedTabVisible() {
        // Arrange: Initialize tabs with last tab selected.
        initializeTest(false, false, false, 9, 10);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_SMALL, 150.f, 10);
        when(tabs[9].isVisible()).thenReturn(true);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Set screen width to 1200 to start.
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);

        // Assert: finalX value before orientation change.
        int initialFinalX = 0;
        assertEquals(initialFinalX, mStripLayoutHelper.getScrollerForTesting().getFinalX());

        // Act: change orientation.
        // drawX: tabWidth(108-28) * 9
        when(tabs[9].getDrawX()).thenReturn(720.f);
        when(tabs[9].getIdealX()).thenReturn(720.f);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, true, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Assert: finalX value after orientation change.
        // stripWidth(800) - rightFade(72) - rightPadding(20) - tabWidth(108-28) - idealX(720) -
        // overlapWidth(28) + leftPadding(10)
        int expectedFinalX = -110;
        assertEquals(expectedFinalX, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    public void testScrollOffset_OnOrientationChange_SelectedTabNotVisible() {
        // Arrange: Initialize tabs with last tab selected.
        initializeTest(false, false, false, 9, 10);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_MEDIUM, 150.f, 10);
        when(tabs[9].isVisible()).thenReturn(false);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Set screen width to 1200 to start
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);

        // Assert: finalX value before orientation change.
        int initialFinalX = 0;
        assertEquals(initialFinalX, mStripLayoutHelper.getScrollerForTesting().getFinalX());

        // Act: change orientation.
        when(tabs[9].getDrawX()).thenReturn(-1.f);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, true, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Assert: finalX value remains the same on orientation change.
        assertEquals(initialFinalX, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    public void testTabSelected_AfterTabClose_SkipsAutoScroll() {
        initializeTest(false, true, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_MEDIUM);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        // Set initial scroller position to 1000.
        mStripLayoutHelper.getScrollerForTesting().setFinalX(1000);

        // Act: close a non selected tab.
        mStripLayoutHelper.handleCloseButtonClick(tabs[1], TIMESTAMP);

        // Assert: scroller position is not modified.
        assertEquals(1000, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    public void testTabSelected_AfterSelectedTabClose_SkipsAutoScroll() {
        initializeTest(false, true, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_MEDIUM);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        // Set initial scroller position to 1000.
        mStripLayoutHelper.getScrollerForTesting().setFinalX(1000);

        // Act: close the selected tab.
        mStripLayoutHelper.handleCloseButtonClick(tabs[3], TIMESTAMP);

        // Assert: scroller position is not modified.
        assertEquals(1000, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testInReorderMode_StripStartMargin_TabGroupIndicators() {
        // Initialize.
        initializeTest(false, false, 5);
        groupTabs(0, 2);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Update layout.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Start reorder mode on the third tab.
        mStripLayoutHelper.disableAnimationsForTesting();
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);

        // Verify that we enter reorder mode.
        assertTrue("Should in reorder mode.", mStripLayoutHelper.getInReorderModeForTesting());

        // Assert: StripStartMargin is about 1/4 tab width to create space for dragging first tab
        // out of group on strip.
        // tabWidth(159.2) - tabOverlap(28) * (0.53(ReorderOverlapSwitchPercentage) * 2) = 36.8f
        assertEquals(
                "StripStartMargin is incorrect",
                36.8f,
                mStripLayoutHelper.getStripStartMarginForReorderForTesting(),
                0.1f);

        // Assert: There should be a scroll offset equal to counter the stripStartMargin, so that
        // the interacting tab would remain visually stationary.
        assertEquals(
                "scrollOffset is incorrect", -36.8f, mStripLayoutHelper.getScrollOffset(), 0.1f);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testInReorderMode_StripEndMargin_TabGroupIndicators() {
        // Initialize.
        initializeTest(false, false, 4);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        groupTabs(3, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Update layout.
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Start reorder mode on the fourth tab.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(3);

        // Verify that we enter reorder mode.
        assertTrue("Should in reorder mode.", mStripLayoutHelper.getInReorderModeForTesting());

        // Assert: Last tab's trailingMargin should be about 1/4 tab width to create space for
        // dragging last tab out of group on strip.
        assertEquals("Strip end margin is incorrect", 36.8f, tabs[4].getTrailingMargin(), 0.1f);
    }

    @Test
    public void testTabCreated_Animation() {
        // Initialize with default amount of tabs. Clear any animations.
        initializeTest(false, false, 3);
        mStripLayoutHelper.finishAnimationsAndPushTabUpdates();
        assertNull(
                "Animation should not be running.",
                mStripLayoutHelper.getRunningAnimatorForTesting());

        // Act: Create new tab in model and trigger update in tab strip.
        mModel.addTab("new tab");
        mStripLayoutHelper.tabCreated(TIMESTAMP, 5, 3, true, false, false);

        // Assert: Animation is running.
        assertNotNull(
                "Animation should running.", mStripLayoutHelper.getRunningAnimatorForTesting());
    }

    @Test
    public void testTabCreated_RestoredTab_SkipsAutoscroll() {
        initializeTest(false, true, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_MEDIUM);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        // Set initial scroller position to 1200.
        mStripLayoutHelper.getScrollerForTesting().setFinalX((int) SCREEN_WIDTH_LANDSCAPE);

        // Act: Tab was restored after undoing a tab closure.
        boolean closureCancelled = true;
        mModel.addTab("new tab");
        mStripLayoutHelper.tabCreated(TIMESTAMP, 5, 3, false, closureCancelled, false);

        // Assert: scroller position is not modified.
        assertEquals(1200, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    public void testTabCreated_NonRestoredTab_Autoscrolls() {
        initializeTest(false, true, 3);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_MEDIUM);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        // Set initial scroller position to 1200.
        mStripLayoutHelper.getScrollerForTesting().setFinalX((int) SCREEN_WIDTH_LANDSCAPE);

        // Act: Tab was not restored after undoing a tab closure.
        boolean closureCancelled = false;
        mModel.addTab("new tab");
        mStripLayoutHelper.tabCreated(TIMESTAMP, 5, 3, false, closureCancelled, false);

        // Assert: scroller position is not modified.
        assertNotEquals(1200, mStripLayoutHelper.getScrollerForTesting().getFinalX());
    }

    @Test
    public void testTabCreated_BringSelectedTabToVisibleArea_StartupRestoredUnselectedTab() {
        initializeTest(false, false, true, 1, 11);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        // Set initial scroller position to -500.
        mStripLayoutHelper.setScrollOffsetForTesting(-500);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Act: Tab was restored during startup.
        boolean selected = false;
        boolean onStartup = true;
        mModel.addTab("new tab");
        mStripLayoutHelper.tabCreated(TIMESTAMP, 12, 12, selected, false, onStartup);

        // Assert: We don't scroll to the created tab. The selected tab is not already visible, so
        // we scroll to it. With right padding, the scroll offset needs to include the rightPadding
        // so the last tab is made visible.
        // Offset = -(1 tab width) + leftTabWidth - rightPadding(20)= -80 + 60 -20= -40.
        float expectedOffset = -40f;
        assertEquals(
                "We should scroll to the selected tab",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);
    }

    @Test
    public void testScrollDuration() {
        initializeTest(false, true, 3);

        // Act: Set scroll offset greater than -960.
        mStripLayoutHelper.setScrollOffsetForTesting(-800);

        // Assert: Scroll duration is 250.
        assertEquals(mStripLayoutHelper.getScrollDurationForTesting(), 250);
    }

    @Test
    public void testScrollDuration_Medium() {
        initializeTest(false, true, false, 3, 12);

        // Act: Set scroll offset between -960 and -1920.
        mStripLayoutHelper.setScrollOffsetForTesting(-1000);

        // Assert: Scroll duration is 350.
        assertEquals(mStripLayoutHelper.getScrollDurationForTesting(), 350);
    }

    @Test
    public void testScrollDuration_Large() {
        initializeTest(false, true, false, 3, 24);

        // Act: Set scroll offset less than -1920
        mStripLayoutHelper.setScrollOffsetForTesting(-2000);

        // Assert: Scroll duration is 450.
        assertEquals(mStripLayoutHelper.getScrollDurationForTesting(), 450);
    }

    @Test
    public void testOnDown_OnNewTabButton() {
        // Initialize.
        initializeTest(false, false, true, 0, 5);

        // Set new tab button location and dimensions.
        mStripLayoutHelper.getNewTabButton().setDrawX(NEW_TAB_BTN_X);
        mStripLayoutHelper.getNewTabButton().setDrawY(NEW_TAB_BTN_Y);
        mStripLayoutHelper.getNewTabButton().setWidth(NEW_TAB_BTN_WIDTH);
        mStripLayoutHelper.getNewTabButton().setHeight(NEW_TAB_BTN_HEIGHT);

        // Press down on new tab button.
        // CenterX = getX() + (getWidth() / 2) = 700 + (100 / 2) = 750
        // CenterY = getY() + (getHeight() / 2) = 1400 + (100 / 2) = 1450
        mStripLayoutHelper.onDown(TIMESTAMP, 750f, 1450f, false, 0);

        // Verify.
        assertTrue(
                "New tab button should be pressed.",
                mStripLayoutHelper.getNewTabButton().isPressed());
        assertNull(
                "Should not set an interacting tab when pressing down on new tab button.",
                mStripLayoutHelper.getInteractingTabForTesting());
        assertFalse(
                "Should not start reorder mode when pressing down on new tab button.",
                mStripLayoutHelper.getInReorderModeForTesting());
    }

    @Test
    public void testOnDown_OnTab() {
        // Initialize.
        initializeTest(false, false, true, 0, 5);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Press down on second tab.
        when(tabs[1].checkCloseHitTest(anyFloat(), anyFloat())).thenReturn(false);
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onDown(TIMESTAMP, 150f, 0f, false, 0);

        // Verify.
        assertFalse(
                "New tab button should not be pressed.",
                mStripLayoutHelper.getNewTabButton().isPressed());
        assertEquals(
                "Second tab should be interacting tab.",
                tabs[1],
                mStripLayoutHelper.getInteractingTabForTesting());
        assertFalse(
                "Should not start reorder mode when pressing down on tab without mouse.",
                mStripLayoutHelper.getInReorderModeForTesting());
        verify(tabs[1], never()).setClosePressed(anyBoolean(), anyBoolean());
    }

    @Test
    public void testOnDown_OnTab_WithMouse() {
        // Initialize.
        initializeTest(false, false, true, 0, 5);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Press down on second tab with mouse.
        when(tabs[1].checkCloseHitTest(anyFloat(), anyFloat())).thenReturn(false);
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onDown(TIMESTAMP, 150f, 0f, true, 0);

        // Verify.
        assertFalse(
                "New tab button should not be pressed.",
                mStripLayoutHelper.getNewTabButton().isPressed());
        assertEquals(
                "Second tab should be interacting tab.",
                tabs[1],
                mStripLayoutHelper.getInteractingTabForTesting());
        assertTrue(
                "Should start reorder mode when pressing down on tab with mouse.",
                mStripLayoutHelper.getInReorderModeForTesting());
        verify(tabs[1], never()).setClosePressed(anyBoolean(), anyBoolean());
    }

    @Test
    public void testOnDown_OnTabCloseButton() {
        // Initialize.
        initializeTest(false, false, true, 0, 5);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Press down on second tab's close button.
        when(tabs[1].checkCloseHitTest(anyFloat(), anyFloat())).thenReturn(true);
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onDown(TIMESTAMP, 150f, 0f, false, 0);

        // Verify.
        assertFalse(
                "New tab button should not be pressed.",
                mStripLayoutHelper.getNewTabButton().isPressed());
        assertEquals(
                "Second tab should be interacting tab.",
                tabs[1],
                mStripLayoutHelper.getInteractingTabForTesting());
        assertFalse(
                "Should not start reorder mode from close button.",
                mStripLayoutHelper.getInReorderModeForTesting());
        verify(tabs[1]).setClosePressed(eq(true), eq(false));
    }

    @Test
    public void testOnDown_OnTabCloseButton_WithMouse() {
        // Initialize.
        initializeTest(false, false, true, 0, 5);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Press down on second tab's close button with mouse.
        when(tabs[1].checkCloseHitTest(anyFloat(), anyFloat())).thenReturn(true);
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onDown(TIMESTAMP, 150f, 0f, true, 0);

        // Verify.
        assertFalse(
                "New tab button should not be pressed.",
                mStripLayoutHelper.getNewTabButton().isPressed());
        assertEquals(
                "Second tab should be interacting tab.",
                tabs[1],
                mStripLayoutHelper.getInteractingTabForTesting());
        assertFalse(
                "Should not start reorder mode from close button.",
                mStripLayoutHelper.getInReorderModeForTesting());
        verify(tabs[1]).setClosePressed(eq(true), eq(true));
    }

    @Test
    public void testOnDown_WhileScrolling() {
        // Initialize and assert scroller is finished.
        initializeTest(false, false, true, 0, 5);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        assertTrue(
                "Scroller should be finished right after initializing.",
                mStripLayoutHelper.getScrollerForTesting().isFinished());

        // Start scroll and assert scroller is not finished.
        mStripLayoutHelper.getScrollerForTesting().startScroll(0, 0, 0, 0, TIMESTAMP, 1000);
        assertFalse(
                "Scroller should not be finished after starting scroll.",
                mStripLayoutHelper.getScrollerForTesting().isFinished());

        // Press down on second tab and assert scroller is finished.
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onDown(TIMESTAMP, 150f, 0f, false, 0);
        assertFalse(
                "New tab button should not be pressed.",
                mStripLayoutHelper.getNewTabButton().isPressed());
        assertNull(
                "Should not set an interacting tab when pressing down to stop scrolling.",
                mStripLayoutHelper.getInteractingTabForTesting());
        assertTrue(
                "Scroller should be force finished after pressing down on strip.",
                mStripLayoutHelper.getScrollerForTesting().isFinished());
    }

    @Test
    @DisableFeatures(ChromeFeatureList.TAB_DRAG_DROP_ANDROID)
    public void testOnLongPress_OnTab() {
        onLongPress_OnTab();
        // Verify we directly enter reorder mode.SS
        assertTrue(
                "Should be in reorder mode after long press on tab.",
                mStripLayoutHelper.getInReorderModeForTesting());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testOnLongPress_WithDragDrop_OnTab() {
        // Extra setup for DragDrop
        setTabDragSourceMock();
        onLongPress_OnTab();
        // Verify drag invoked
        verify(mTabDragSource).startTabDragAction(any(), any(), any(), anyFloat(), anyFloat());
    }

    private void onLongPress_OnTab() {
        // Initialize.
        initializeTest(false, false, 0);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Long press on second tab.
        when(tabs[1].checkCloseHitTest(anyFloat(), anyFloat())).thenReturn(false);
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onLongPress(TIMESTAMP, 150f, 0f);

        // Verify that we enter reorder mode.
        assertFalse(
                "Should not show tab menu after long press on tab.",
                mStripLayoutHelper.isTabMenuShowingForTesting());
    }

    @Test
    public void testOnLongPress_OnCloseButton() {
        // Initialize.
        initializeTest(false, false, 0);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Mock tab's view.
        View tabView = new View(mActivity);
        tabView.setLayoutParams(new MarginLayoutParams(150, 50));
        when(mModel.getTabAt(1).getView()).thenReturn(tabView);

        // Long press on second tab's close button.
        when(tabs[1].checkCloseHitTest(anyFloat(), anyFloat())).thenReturn(true);
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.onLongPress(TIMESTAMP, 150f, 0f);

        // Verify that we show the "Close all tabs" popup menu.
        assertFalse(
                "Should not be in reorder mode after long press on tab close button.",
                mStripLayoutHelper.getInReorderModeForTesting());
        assertTrue(
                "Should show tab menu after long press on tab close button.",
                mStripLayoutHelper.isTabMenuShowingForTesting());
    }

    @Test
    @DisableFeatures(ChromeFeatureList.TAB_DRAG_DROP_ANDROID)
    public void testOnLongPress_OffTab() {
        onLongPress_OffTab();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testOnLongPress_WithDragDrop_OffTab() {
        // Extra setup for DragDrop
        setTabDragSourceMock();
        Activity activity = spy(mActivity);
        when(mToolbarContainerView.getContext()).thenReturn(activity);

        onLongPress_OffTab();
        // verify tab drag not invoked.
        verifyNoInteractions(mTabDragSource);
    }

    private void onLongPress_OffTab() {
        // Initialize.
        initializeTest(false, false, 0);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(150f);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);

        // Long press past the last tab.
        mStripLayoutHelper.setTabAtPositionForTesting(null);
        mStripLayoutHelper.onLongPress(TIMESTAMP, 150f, 0f);

        // Verify that we show the "Close all tabs" popup menu.
        assertFalse(
                "Should not be in reorder mode after long press on empty space on tab strip.",
                mStripLayoutHelper.getInReorderModeForTesting());
        assertFalse(
                "Should not show after long press on empty space on tab strip.",
                mStripLayoutHelper.isTabMenuShowingForTesting());
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_BetweenTabs() {
        // Initialize with 3 tabs.
        initializeTest(false, false, true, 0, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify no tabs have a trailing margin, since there are no tab groups.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_TabToLeft() {
        // Mock 1 tab to the left of a tab group with 3 tabs.
        initializeTest(false, false, true, 0, 4);
        groupTabs(1, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify the leftmost and final tabs have a trailing margin.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float tabWidth = tabs[0].getWidth();
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[3].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_TabToRight() {
        // Mock 1 tab to the right of a tab group with 3 tabs.
        initializeTest(false, false, true, 0, 4);
        groupTabs(0, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify the rightmost tab in the tab group has a trailing margin.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float tabWidth = tabs[0].getWidth();
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[3].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_BetweenGroups() {
        // Mock a tab group with 2 tabs to the left of a tab group with 3 tabs.
        initializeTest(false, false, true, 0, 5);
        groupTabs(0, 2);
        groupTabs(2, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify the rightmost tab in the first group has a trailing margin.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float tabWidth = tabs[0].getWidth();
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[3].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[4].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_BetweenGroups_RTL() {
        // Mock a tab group with 2 tabs to the right of a tab group with 3 tabs.
        initializeTest(true, false, true, 0, 5);
        groupTabs(0, 2);
        groupTabs(2, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify the leftmost tab in the first group has a trailing margin.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float tabWidth = tabs[0].getWidth();
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[3].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[4].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_StartReorder_Animated() {
        // Mock 1 tab to the left of a tab group with 3 tabs.
        initializeTest(false, false, false, 0, 4);
        groupTabs(1, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify that only the last tab has a margin, since that one is not animated.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float tabWidth = tabs[0].getWidth();
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[3].getTrailingMargin(), EPSILON);

        // Complete the currently running animations.
        assertNotNull(mStripLayoutHelper.getRunningAnimatorForTesting());
        mStripLayoutHelper.getRunningAnimatorForTesting().end();

        // Verify the leftmost and final tabs have a trailing margin.
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[3].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_StopReorder_Animated() {
        // Mock 1 tab to the left of a tab group with 3 tabs.
        initializeTest(false, false, false, 0, 4);
        groupTabs(1, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Finish starting reorder, then begin stopping reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        mStripLayoutHelper.getRunningAnimatorForTesting().end();
        mStripLayoutHelper.stopReorderModeForTesting();

        // Verify the leftmost and final tabs have a trailing margin.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float tabWidth = tabs[0].getWidth();
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_MARGIN, tabWidth / 2, tabs[3].getTrailingMargin(), EPSILON);

        // Complete the currently running animations.
        assertNotNull(mStripLayoutHelper.getRunningAnimatorForTesting());
        mStripLayoutHelper.getRunningAnimatorForTesting().end();

        // Verify that there are no margins as we have stopped reordering.
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[3].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_ResetMarginsOnStopReorder() {
        // Mock 1 tab to the left of a tab group with 3 tabs.
        initializeTest(false, false, true, 0, 4);
        groupTabs(1, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start then stop reorder.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        mStripLayoutHelper.stopReorderModeForTesting();

        // Verify no tabs have a trailing margin when reordering is stopped.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[2].getTrailingMargin(), EPSILON);
        assertEquals(EXPECTED_NO_MARGIN, 0f, tabs[3].getTrailingMargin(), EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_NoScrollOnReorder() {
        // Mock 1 tab to the right of 2 tab groups with 2 tabs each.
        initializeTest(false, false, true, 0, 5);
        groupTabs(2, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setScrollOffsetForTesting(0);

        // Start reorder on leftmost tab. No margins to left of tab, so shouldn't scroll.
        // Verify the scroll offset is still 0.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        assertEquals(
                "There are no margins left of the selected tab, so we shouldn't scroll.",
                0f,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);

        // Stop reorder. Verify the scroll offset is still 0.
        mStripLayoutHelper.stopReorderModeForTesting();
        assertEquals(
                "Scroll offset should return to 0 after stopping reorder mode.",
                0f,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_ScrollOnReorder() {
        // Mock 6 tabs to the right of 2 tab groups with 2 tabs each.
        initializeTest(false, false, true, 0, 10);
        groupTabs(0, 2);
        groupTabs(2, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setScrollOffsetForTesting(0);

        // Start reorder on tab to the right of groups. 2 margins to left of tab, so should scroll.
        // Verify the scroll offset is 2 * (-marginWidth) + startMargin = 2 * -54 + -54 = -162
        // marginWidth is half of 0.5 * minTabWidth = 108 / 2 = 54.
        float expectedOffset = -162f;
        mStripLayoutHelper.startReorderModeAtIndexForTesting(4);
        assertEquals(
                "There are margins left of the selected tab, so we should scroll.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);

        // Stop reorder. Verify the scroll offset is once again 0.
        mStripLayoutHelper.stopReorderModeForTesting();
        assertEquals(
                "Scroll offset should return to 0 after stopping reorder mode.",
                0,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testTabGroupMargins_ScrollOnReorder_Animated() {
        // Mock 6 tabs to the right of 2 tab groups with 2 tabs each.
        initializeTest(false, false, false, 0, 10);
        groupTabs(0, 2);
        groupTabs(2, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setScrollOffsetForTesting(0);

        // Start reorder on tab to the right of groups. 2 margins to left of tab, so should scroll.
        // Verify the scroll offset has not yet changed.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(4);
        assertEquals(
                "The scroller has not finished yet, so the offset shouldn't change.",
                0f,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);

        // Finish animations.
        // Verify the scroll offset is 2 * (-marginWidth) + startMargin = 2 * -54 + -54 = -162
        // marginWidth is half of 0.5 * minTabWidth = 108 / 2 = 54.
        float expectedOffset = -162f;
        mStripLayoutHelper.getRunningAnimatorForTesting().end();
        assertEquals(
                "The scroller has finished, so the offset should change.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);

        // Stop reorder. Verify the scroll offset is still -285.
        mStripLayoutHelper.stopReorderModeForTesting();
        assertEquals(
                "The scroller has not finished yet, so the offset shouldn't change.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);

        // Finish animations.
        // Verify the scroll offset is once again 0.
        mStripLayoutHelper.getRunningAnimatorForTesting().end();
        assertEquals(
                "The scroller has finished, so the offset should change.",
                0,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);
    }

    @Test
    @DisableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testTabOutline_SelectedTabInGroup_NotShow() {
        // Initialize 5 tabs and make 2 tab groups each containing 2 tabs.
        initializeTest(false, false, false, 0, 5);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        groupTabs(0, 2);
        groupTabs(2, 4);

        // Test tab outline should not show for selected tab in group.
        assertFalse(
                "Tab outline should show for selected tab in group",
                mStripLayoutHelper.shouldShowTabOutline(tabs[0]));

        // Test tab outline should not show for the rest of tabs.
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[1]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[2]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[3]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[4]));
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testTabOutline_SelectedTabInGroup_Show() {
        // Initiailze 5 tabs and make 2 tab groups each containing 2 tabs.
        initializeTest(false, false, false, 0, 5);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        groupTabs(0, 2);
        groupTabs(2, 4);

        // Test tab outline should show for selected tab in group.
        assertTrue(
                "Tab outline should show for selected tab in group",
                mStripLayoutHelper.shouldShowTabOutline(tabs[0]));

        // Test tab outline should not show for the rest of tabs.
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[1]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[2]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[3]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[4]));
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_DRAG_DROP_ANDROID
    })
    public void testTabOutline_ForegroundedTabInGroup_TabDroppedOntoDestinationStrip_Show() {
        // Setup with 3 tabs and select the first tab.
        initializeTest(false, false, true, 0, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        groupTabs(0, 3);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();

        // Start reorder for tab drop between the 2nd and 3rd tab.
        mStripLayoutHelper.startReorderModeForTabDrop(300.f);

        // Test tab outline should show for the foregrounded tab in destination window during tab
        // drop.
        assertTrue("Tab outline should show.", mStripLayoutHelper.shouldShowTabOutline(tabs[0]));

        // Test tab outline should not show for the rest of tabs.
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[1]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[2]));
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testTabOutline_ReorderMode_NotShow() {
        // Mock 5 tabs and make 2 tab groups each containing 2 tabs.
        initializeTest(false, false, false, 0, 5);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1, 150f, 5);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        groupTabs(0, 2);
        groupTabs(2, 4);

        // Enter reorder mode.
        mStripLayoutHelper.setInReorderModeForTesting(true);

        // Test tab outline should not show for selected tab in group when enter reorder mode.
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[0]));

        // Test tab outline should not show for the rest of tabs.
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[1]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[2]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[3]));
        assertFalse(
                "Tab outline should not show.", mStripLayoutHelper.shouldShowTabOutline(tabs[4]));
    }

    @Test
    public void testReorder_SetSelectedTabGroupContainersVisible() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 2, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 2);

        // Start reorder mode on third tab. Drag to hover over the tab group.
        // -100 < -marginWidth = -95
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = -100f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify hovered group tab containers are visible.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float expectedHidden = StripLayoutHelper.TAB_OPACITY_HIDDEN;
        float expectedVisibleBackground = StripLayoutHelper.TAB_OPACITY_VISIBLE_BACKGROUND;
        float expectedVisibleForeground = StripLayoutHelper.TAB_OPACITY_VISIBLE_FOREGROUND;
        assertEquals(
                "Container in hovered group should be visible.",
                expectedVisibleBackground,
                tabs[0].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Container in hovered group should be visible.",
                expectedVisibleBackground,
                tabs[1].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Selected container should be visible.",
                expectedVisibleForeground,
                tabs[2].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Background containers should not be visible.",
                expectedHidden,
                tabs[3].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Background containers should not be visible.",
                expectedHidden,
                tabs[4].getContainerOpacity(),
                EPSILON);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testReorder_SetSelectedTabGroupContainersVisible_TabGroupIndicators() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 2, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 2);

        // Start reorder mode on third tab. Drag to hover over the tab group.
        // -100 < -marginWidth = -95
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = -100f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify hovered group tab containers are not visible for Tab Group Indicator.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        float expectedHidden = StripLayoutHelper.TAB_OPACITY_HIDDEN;
        float expectedVisibleForeground = StripLayoutHelper.TAB_OPACITY_VISIBLE_FOREGROUND;
        assertEquals(
                "Container in hovered group should not be visible.",
                expectedHidden,
                tabs[0].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Container in hovered group should not be visible.",
                expectedHidden,
                tabs[1].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Selected container should be visible.",
                expectedVisibleForeground,
                tabs[2].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Background containers should not be visible.",
                expectedHidden,
                tabs[3].getContainerOpacity(),
                EPSILON);
        assertEquals(
                "Background containers should not be visible.",
                expectedHidden,
                tabs[4].getContainerOpacity(),
                EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_HapticFeedback() {
        // Mock 5 tabs.
        initializeTest(false, false, 0);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder mode on first tab.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);

        // Verify we performed haptic feedback for a long-press.
        verify(mInteractingTabView).performHapticFeedback(eq(HapticFeedbackConstants.LONG_PRESS));
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_NoGroups() {
        // Mock 5 tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab thirdTab = tabs[2];
        StripLayoutTab fourthTab = tabs[3];

        // Start reorder on third tab. Drag right to trigger swap with fourth tab.
        // 100 > tabWidth * flipThreshold = (190-24) * 0.53 = 88
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = 100f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Assert the tabs swapped.
        assertEquals("Third and fourth tabs should have swapped.", thirdTab, tabs[3]);
        assertEquals("Third and fourth tabs should have swapped.", fourthTab, tabs[2]);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_DragOutOfGroup() {
        // Mock a tab group with 3 tabs with 1 tab to the left and 1 tab to the right.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab fourthTab = tabs[3];
        groupTabs(1, 4);

        // Start reorder on fourth tab. Drag right out of the tab group.
        // 60 > marginWidth * flipThreshold = 95 * 0.53 = 51
        mStripLayoutHelper.startReorderModeAtIndexForTesting(3);
        float dragDistance = 60f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify fourth tab was dragged out of group, but not reordered.
        assertEquals("Fourth tab should not have moved.", fourthTab, tabs[3]);
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(fourthTab.getId(), true);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_DragOutOfGroup_StartOfStrip() {
        // Mock a tab group with 3 tabs with 2 tabs to the right.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab firstTab = tabs[0];
        groupTabs(0, 3);

        // Start reorder on first tab. Drag left out of the tab group.
        // -60 < -(marginWidth * flipThreshold) = -(95 * 0.53) = -51
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        float dragDistance = -60f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify first tab was dragged out of group, but not reordered.
        assertEquals("First tab should not have moved.", firstTab, tabs[0]);
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(firstTab.getId(), false);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_DragOutOfGroup_EndOfStrip() {
        // Mock a tab group with 3 tabs with 2 tabs to the left.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab fifthTab = tabs[4];
        groupTabs(2, 5);

        // Start reorder on fifth tab. Drag right out of the tab group.
        // 60 > marginWidth * flipThreshold = 95 * 0.53 = 51
        mStripLayoutHelper.startReorderModeAtIndexForTesting(4);
        float dragDistance = 60f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify fifth tab was dragged out of group, but not reordered.
        assertEquals("Fifth tab should not have moved.", fifthTab, tabs[4]);
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(fifthTab.getId(), true);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_DragPastGroup() {
        // Mock a tab group with 3 tabs with 1 tab to the left and 1 tab to the right.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab firstTab = tabs[0];
        groupTabs(1, 4);

        // Start reorder on first tab. Drag right over the tab group.
        // 650 > 3*tabWidth + margin + flipThreshold*margin = 3*(190-24) + 1.53*95 = 644 > 300
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        float dragDistance = 300f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);
        // Verify no reordering, since we have not hovered over the tab group long enough.
        assertEquals("First tab should not have moved.", firstTab, tabs[0]);

        // Drag past the tab group.
        dragDistance = 650f;
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);
        // Verify reordering, since we have dragged past the tab group.
        assertEquals("First tab should now be the fourth tab.", firstTab.getId(), tabs[3].getId());
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_MergeToGroup() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab thirdTab = tabs[2];
        int oldSecondTabId = tabs[1].getId();
        groupTabs(0, 2);

        // Start reorder mode on third tab. Drag between tabs in group.
        // -300 < -(tabWidth + marginWidth) = -(190 + 95) = -285
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = -200f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify state has not yet changed.
        tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals("Third tab should not have moved.", thirdTab, tabs[2]);
        verify(mTabGroupModelFilter, never()).mergeTabsToGroup(anyInt(), anyInt());
        verify(mTabGroupModelFilter, never()).mergeTabsToGroup(anyInt(), anyInt(), anyBoolean());

        // Wait minimum time to trigger merge.
        // -10 > -(dropMaxDragOffset) = -36
        dragDistance = -10;
        startX = mStripLayoutHelper.getLastReorderXForTesting();
        long timeDelta = StripLayoutHelper.DROP_INTO_GROUP_MS;
        mStripLayoutHelper.drag(TIMESTAMP + timeDelta, startX + dragDistance, 0f, dragDistance);

        // Verify interacting tab was merged into group at the second index.
        tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals("Third tab should now be second tab.", thirdTab, tabs[1]);
        verify(mTabGroupModelFilter)
                .mergeTabsToGroup(eq(thirdTab.getId()), eq(oldSecondTabId), eq(true));
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testReorder_MergeToGroup_TabGroupIndicators() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab thirdTab = tabs[2];
        int oldSecondTabId = tabs[1].getId();
        groupTabs(0, 2);

        // Start reorder mode on third tab. Drag between tabs in group.
        // -300 < -(tabWidth + marginWidth) = -(190 + 95) = -285
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = -200f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify interacting tab was merged into group.
        verify(mTabGroupModelFilter)
                .mergeTabsToGroup(eq(thirdTab.getId()), eq(oldSecondTabId), eq(true));
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testReorder_MovePastCollapsedGroup_TabGroupIndicators() {
        // Mock 5 tabs. Group the second and third tabs.
        initializeTest(false, false, true, 3, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(1, 3);

        // Collapse the group.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[1], true);

        // Start reorder mode on fourth tab. Drag past the collapsed group.
        // -50 < -groupTitleWidth(46)
        mStripLayoutHelper.startReorderModeAtIndexForTesting(3);
        StripLayoutView draggedTab = views[4];
        assertEquals(
                "Should be dragging the fourth tab.",
                draggedTab,
                mStripLayoutHelper.getInteractingTabForTesting());

        float dragDistance = -50f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify interacting tab was moved past the collapsed group and is now the second tab.
        assertEquals("Dragged tab should now be second tab.", draggedTab, views[1]);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testBottomIndicatorWidth_MergeToGroup_TabGroupIndicators() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab thirdTab = tabs[2];
        groupTabs(0, 2);
        int oldSecondTabId = tabs[1].getId();

        // Assert: first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[0]);

        // Calculate tab and bottom indicator width.
        float tabWidth = views[1].getWidth();
        float expectedStartWidth = calculateExpectedBottomIndicatorWidth(tabWidth, 2, groupTitle);
        float expectedEndWidth = calculateExpectedBottomIndicatorWidth(tabWidth, 3, groupTitle);
        float expectedThreshold = mStripLayoutHelper.calculateTabGroupThreshold(2, false, false);

        // Assert: bottom indicator start width.
        assertEquals(
                "Bottom indicator start width is incorrect",
                expectedStartWidth,
                groupTitle.getBottomIndicatorWidth(),
                EPSILON);

        // Start reorder mode on third tab. Drag between tabs in group.
        // -300 < -(tabWidth + marginWidth) = -(190 + 95) = -285
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = -200f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify interacting tab was merged into group.
        verify(mTabGroupModelFilter)
                .mergeTabsToGroup(eq(thirdTab.getId()), eq(oldSecondTabId), eq(true));
        mStripLayoutHelper.maybeMergeToGroupForTabGroupIndicators(
                -expectedThreshold - 1, 2, false, expectedThreshold, groupTitle);
        assertEquals(
                "Bottom indicator end width is incorrect",
                expectedEndWidth,
                (groupTitle).getBottomIndicatorWidth(),
                EPSILON);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testBottomIndicatorWidth_DragOutOfGroup_TabGroupIndicators() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutTab thirdTab = tabs[2];
        groupTabs(0, 3);

        // Assert: first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[0]);

        // Calculate tab and bottom indicator width.
        float tabWidth = views[1].getWidth();
        float expectedStartWidth = calculateExpectedBottomIndicatorWidth(tabWidth, 3, groupTitle);
        float expectedEndWidth = calculateExpectedBottomIndicatorWidth(tabWidth, 2, groupTitle);

        // Assert: bottom indicator start width.
        assertEquals(
                "Bottom indicator start width is incorrect",
                expectedStartWidth,
                groupTitle.getBottomIndicatorWidth(),
                EPSILON);

        // Start reorder on fifth tab. Drag right out of the tab group.
        // 60 > marginWidth * flipThreshold = 95 * 0.53 = 51
        mStripLayoutHelper.startReorderModeAtIndexForTesting(2);
        float dragDistance = 60f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify third tab was dragged out of group.
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(thirdTab.getId(), true);

        // Act: End the animations to apply final values.
        Animator runningAnimator = mStripLayoutHelper.getRunningAnimatorForTesting();
        runningAnimator.end();

        assertEquals(
                "Bottom indicator end width is incorrect",
                expectedEndWidth,
                (groupTitle).getBottomIndicatorWidth(),
                EPSILON);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testBottomIndicatorWidthAfterTabResize_UngroupedTabClosed_TabGroupIndicators() {
        // Arrange
        int tabCount = 6;
        initializeTest(false, false, false, 3, tabCount);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        groupTabs(0, 2);

        // Assert: first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[0]);

        // Update layout and set up animation.
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        setupForAnimations();
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Check initial bottom indicator width.
        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // tabWidth = (availableSize(684) + 5 * overlap(28)) / 6 = 137.3333
        float expectedStartWidth = calculateExpectedBottomIndicatorWidth(137.3333f, 2, groupTitle);
        assertEquals(
                "Unexpected bottom indicator width before resize.",
                expectedStartWidth,
                (groupTitle).getBottomIndicatorWidth(),
                0.1f);

        // Act: Call on close tab button handler.
        mStripLayoutHelper.handleCloseButtonClick(tabs[2], TIMESTAMP);

        // Assert: Animations started.
        assertTrue(
                "MultiStepAnimations should have started.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Assert: Animations are still running.
        assertTrue(
                "MultiStepAnimations should still be running.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Act: Set animation time forward by 250ms for next set of animations.
        mStripLayoutHelper.getRunningAnimatorForTesting().end();

        // Act: End the animations to apply final values.
        Animator runningAnimator = mStripLayoutHelper.getRunningAnimatorForTesting();
        runningAnimator.end();

        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // ExpectedWidth = (availableSize(684) + 4 * overlap(28)) / 5 = 159.2
        float expectedWidthAfterResize = 159.2f;
        StripLayoutTab[] updatedTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        for (int i = 0; i < updatedTabs.length; i++) {
            StripLayoutTab stripTab = updatedTabs[i];
            assertEquals(
                    "Unexpected tab width after resize.",
                    expectedWidthAfterResize,
                    stripTab.getWidth(),
                    0.1f);
        }
        assertFalse(
                "MultiStepAnimations should have ended.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Check bottom indicator end width.
        float expectedEndWidth =
                calculateExpectedBottomIndicatorWidth(expectedWidthAfterResize, 2, groupTitle);
        assertEquals(
                "Unexpected bottom indicator width after resize.",
                expectedEndWidth,
                groupTitle.getBottomIndicatorWidth(),
                0.1f);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testBottomIndicatorWidthAfterTabResize_GroupedTabClosed_TabGroupIndicators() {
        // Arrange
        int tabCount = 6;
        initializeTest(false, false, false, 0, tabCount);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        groupTabs(0, 2);

        // Assert: first view should be a GroupTitle.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[0]);

        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Check initial bottom indicator width.
        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // tabWidth = (availableSize(684) + 5 * overlap(28)) / 6 = 137.3333
        float expectedStartWidth = calculateExpectedBottomIndicatorWidth(137.3333f, 2, groupTitle);
        assertEquals(
                "Unexpected bottom indicator width before resize.",
                expectedStartWidth,
                groupTitle.getBottomIndicatorWidth(),
                0.1f);

        setupForAnimations();
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Act: Call on close tab button handler.
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        when(tabs[0].getId()).thenReturn(0);
        tabModel.addTab(0);
        tabModel.setIndex(0, TabSelectionType.FROM_NEW, true);
        tabModel.setActive(true);
        mStripLayoutHelper.setTabModel(tabModel, null, false);
        mStripLayoutHelper.handleCloseButtonClick(tabs[0], TIMESTAMP);

        // Assert: Animations started.
        assertTrue(
                "MultiStepAnimations should have started.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        assertTrue(
                "MultiStepAnimations should still be running.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Act: Set animation time forward by 250ms for next set of animations.
        mStripLayoutHelper.getRunningAnimatorForTesting().end();

        // Act: End the animations to apply final values.
        Animator runningAnimator = mStripLayoutHelper.getRunningAnimatorForTesting();
        runningAnimator.end();

        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // ExpectedWidth = (availableSize(684) + 4 * overlap(28)) / 5  = 159.2.
        float expectedWidthAfterResize = 159.2f;
        StripLayoutTab[] updatedTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        for (int i = 0; i < updatedTabs.length; i++) {
            StripLayoutTab stripTab = updatedTabs[i];
            assertEquals(
                    "Unexpected tab width after resize.",
                    expectedWidthAfterResize,
                    stripTab.getWidth(),
                    0.1f);
        }
        assertFalse(
                "MultiStepAnimations should have ended.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Check bottom indicator end width.
        float expectedEndWidth =
                calculateExpectedBottomIndicatorWidth(expectedWidthAfterResize, 1, groupTitle);
        assertEquals(
                "Unexpected bottom indicator width after resize.",
                expectedEndWidth,
                groupTitle.getBottomIndicatorWidth(),
                0.1f);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testBottomIndicatorWidth_CollapseAndExpand() {
        // Mock 5 tabs, group first 3 tabs as group1 and group the rest as group2.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        groupTabs(0, 3);
        groupTabs(3, 5);

        // Assert: the first and fourth view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
        assertTrue(EXPECTED_TITLE, views[4] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle1 = ((StripLayoutGroupTitle) views[0]);
        StripLayoutGroupTitle groupTitle2 = ((StripLayoutGroupTitle) views[4]);

        // Calculate tab and bottom indicator initial width.
        float initialTabWidth = tabs[0].getWidth();
        float expectedStartWidth1 =
                calculateExpectedBottomIndicatorWidth(initialTabWidth, 3, groupTitle1);
        float expectedStartWidth2 =
                calculateExpectedBottomIndicatorWidth(initialTabWidth, 2, groupTitle1);

        // Assert: bottom indicator start width as usual.
        assertEquals(
                "Group 1 bottom indicator start width is incorrect",
                expectedStartWidth1,
                groupTitle1.getBottomIndicatorWidth(),
                EPSILON);
        assertEquals(
                "Group 2 bottom indicator start width is incorrect",
                expectedStartWidth2,
                groupTitle2.getBottomIndicatorWidth(),
                EPSILON);

        // Click to collapse the first tab group.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], true);

        // Assert: check bottom indicator end width for the 1st tab group should be 0.
        assertEquals(
                "Bottom indicator end width is incorrect",
                0.f,
                groupTitle1.getBottomIndicatorWidth(),
                EPSILON);

        // Assert: check bottom indicator end width for the 2nd tab group should been adjusted to
        // match the new tab width after collapse, since there are only 2 active tabs on strip, tab
        // width should become the max width.
        assertEquals(
                "Bottom indicator end width is incorrect",
                calculateExpectedBottomIndicatorWidth(265.f, 2, groupTitle2),
                groupTitle2.getBottomIndicatorWidth(),
                EPSILON);

        // Click to expand the first tab group.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], false);

        // Assert: check bottom indicator end width for the 1st tab group has been expanded to the
        // initial length.
        assertEquals(
                "Bottom indicator end width is incorrect",
                expectedStartWidth1,
                groupTitle1.getBottomIndicatorWidth(),
                EPSILON);

        // Assert: check bottom indicator end width for the 2st tab group has been adjusted to the
        // initial length.
        assertEquals(
                "Bottom indicator end width is incorrect",
                expectedStartWidth2,
                groupTitle2.getBottomIndicatorWidth(),
                EPSILON);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_DRAG_DROP_ANDROID
    })
    public void testBottomIndicatorWidth_TabHoveredOntoTabGroup_TabGroupIndicators() {
        // Arrange
        int tabCount = 6;
        initializeTest(false, false, false, 0, tabCount);
        groupTabs(0, 2);
        mStripLayoutHelper.disableAnimationsForTesting();

        // Assert: first view should be a GroupTitle.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[0]);

        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Check initial bottom indicator width.
        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // tabWidth = (availableSize(684) + 5 * overlap(28)) / 6 = 137.3333
        float expectedStartWidth = calculateExpectedBottomIndicatorWidth(137.3333f, 2, groupTitle);
        assertEquals(
                "Unexpected bottom indicator width before tab hover.",
                expectedStartWidth,
                groupTitle.getBottomIndicatorWidth(),
                0.1f);

        setupForAnimations();
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Start reorder for tab drop between the 1st and 2nd tab.
        mStripLayoutHelper.startReorderModeForTabDrop(150.f);

        // initial bottom indicator width(237) + trailing margin(69).
        float expectedEndWidth = 306.f;
        assertEquals(
                "Unexpected bottom indicator width after tab hover.",
                expectedEndWidth,
                groupTitle.getBottomIndicatorWidth(),
                0.5f);
    }

    @Test
    public void testFolioAttached_ReattachAnimationSkipped() {
        // Arrange
        int tabCount = 6;
        initializeTest(false, false, false, 0, tabCount);
        groupTabs(0, 2);
        StripLayoutHelper stripLayoutHelperSpy = spy(mStripLayoutHelper);

        // Start and stop reorder mode for tab drop.
        stripLayoutHelperSpy.startReorderModeForTabDrop(10.f);
        stripLayoutHelperSpy.stopReorderModeForTesting();

        // Verify: folio reattachment animation does not run for tab drop.
        verify(stripLayoutHelperSpy, never()).updateTabAttachState(any(), eq(true), notNull());
    }

    private float calculateExpectedBottomIndicatorWidth(
            float tabWidth, float tabCount, StripLayoutGroupTitle groupTitle) {
        // (tabWidth - tabOverlap(28.f)) * tabCount + groupTitleWidth -
        //      bottomIndicatorWidthOffset(27.f).
        return (tabWidth - TAB_OVERLAP_WIDTH) * tabCount
                + groupTitle.getWidth()
                - StripLayoutHelper.TAB_GROUP_BOTTOM_INDICATOR_WIDTH_OFFSET;
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testGroupTitleSlidingAnimation_MergeToGroup_TabGroupIndicators() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        groupTabs(1, 3);
        int firstTabId = tabs[0].getId();
        int secondTabId = tabs[1].getId();

        // Assert: first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[1] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[1]);

        // Start reorder mode on first tab. Drag between tabs in group.
        // 70 = (80(halfTabWidth) - 28(tabOverlapWidth)) * 0.53(ReorderOverlapSwitchPercentage).
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        float dragDistance = 70f;
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify interacting tab was merged into group.
        verify(mTabGroupModelFilter).mergeTabsToGroup(eq(firstTabId), eq(secondTabId), eq(true));

        // assert: verify group title sliding animation is running immediately when tab merge into
        // group through group title.
        assertTrue(mStripLayoutHelper.getGroupTitleSlidingForTesting());

        // Assert: verify bottom indicator width correctly updated.
        float expectedEndWidth =
                calculateExpectedBottomIndicatorWidth(tabs[0].getWidth(), 2, groupTitle);
        assertEquals(
                "Bottom indicator end width is incorrect",
                expectedEndWidth,
                (groupTitle).getBottomIndicatorWidth(),
                EPSILON);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testGroupTitleSlidingAnimation_dragOutOfGroup_TabGroupIndicators() {
        // Mock 5 tabs. Group the first two tabs.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        groupTabs(1, 3);
        int secondTabId = tabs[1].getId();

        // Assert: first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[1] instanceof StripLayoutGroupTitle);
        StripLayoutGroupTitle groupTitle = ((StripLayoutGroupTitle) views[1]);

        // Start reorder mode on first tab. Drag between tabs in group.
        // 38 = ((80(halfTabWidth) - 28(tabOverlapWidth)) * 0.53(ReorderOverlapSwitchPercentage)) *
        // 0.53.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(1);
        float dragDistance = -38f - groupTitle.getWidth();
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);

        // Verify interacting tab was moved out of group.
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(secondTabId, false);

        // assert: verify group title sliding animation is running immediately when tab move out of
        // group through group title.
        assertTrue(mStripLayoutHelper.getGroupTitleSlidingForTesting());

        // Assert: verify bottom indicator width correctly updated.
        float expectedEndWidth =
                calculateExpectedBottomIndicatorWidth(tabs[0].getWidth(), 2, groupTitle);
        assertEquals(
                "Bottom indicator end width is incorrect",
                expectedEndWidth,
                (groupTitle).getBottomIndicatorWidth(),
                EPSILON);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testTabGroupDeleteDialog_ImmediateContinue() {
        // Set up resources for testing tab group delete dialog.
        setUpSingleGroupAndDragEdgeTabOut(0, 1, 0);
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();

        // Verify group title is temporarily disappeared from the tab strip
        assertFalse(EXPECTED_NON_TITLE, views[0] instanceof StripLayoutGroupTitle);

        // Verify action confirmation dialog shows.
        verify(mActionConfirmationManager)
                .processRemoveTabAttempt(mConfirmationResultCaptor.capture());
        mConfirmationResultCaptor.getValue().onResult(ConfirmationResult.IMMEDIATE_CONTINUE);

        // Verify tab is moved out of group as user chooses delete tab group without showing the
        // dialog.
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(tabs[0].getId(), true);

        // Verify group title is removed from the tab strip
        views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertFalse(EXPECTED_NON_TITLE, views[0] instanceof StripLayoutGroupTitle);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testTabGroupDeleteDialog_ConfirmationPositive() {
        // Set up resources for testing tab group delete dialog.
        setUpSingleGroupAndDragEdgeTabOut(0, 1, 0);
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();

        // Verify group title is temporarily disappeared from the tab strip
        assertFalse(EXPECTED_NON_TITLE, views[0] instanceof StripLayoutGroupTitle);

        // Verify action confirmation dialog shows.
        verify(mActionConfirmationManager)
                .processRemoveTabAttempt(mConfirmationResultCaptor.capture());
        mConfirmationResultCaptor.getValue().onResult(ConfirmationResult.CONFIRMATION_POSITIVE);

        // Verify tab is moved out of group as user confirms tab group delete.
        verify(mTabGroupModelFilter).moveTabOutOfGroupInDirection(tabs[0].getId(), true);

        // Verify group title is removed from the tab strip
        views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertFalse(EXPECTED_NON_TITLE, views[0] instanceof StripLayoutGroupTitle);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testTabGroupDeleteDialog_ConfirmationNegative() {
        // Set up resources for testing tab group delete dialog.
        setUpSingleGroupAndDragEdgeTabOut(0, 1, 0);
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();

        // Verify group title is temporarily disappeared from the tab strip
        assertFalse(EXPECTED_NON_TITLE, views[0] instanceof StripLayoutGroupTitle);

        // Verify action confirmation dialog shows.
        verify(mActionConfirmationManager)
                .processRemoveTabAttempt(mConfirmationResultCaptor.capture());
        mConfirmationResultCaptor.getValue().onResult(ConfirmationResult.CONFIRMATION_NEGATIVE);

        // Verify tab is not moved out of group as user cancels tab group delete.
        verify(mTabGroupModelFilter, never()).moveTabOutOfGroupInDirection(tabs[0].getId(), true);

        // Verify group title is restored back on the tab strip
        views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);
    }

    private void setUpSingleGroupAndDragEdgeTabOut(
            int groupStartIndex, int groupEndIndex, int tabIndexToDrag) {
        // Mock 5 tabs. Group tab from start to endIndex.
        initializeTest(false, false, true, 0, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(groupStartIndex, groupEndIndex);

        // Assert: View should be group title.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[groupStartIndex] instanceof StripLayoutGroupTitle);

        // Start drag tab out of group.
        float dragDistance =
                (tabs[0].getWidth() - TAB_OVERLAP_WIDTH)
                        * REORDER_OVERLAP_SWITCH_PERCENTAGE
                        * REORDER_OVERLAP_SWITCH_PERCENTAGE;
        startDragTabOutOfTabGroup(tabIndexToDrag, dragDistance + 1);
    }

    private void startDragTabOutOfTabGroup(int index, float dragDistance) {
        // Start reorder and drag tab out of the tab group through group end.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(index);
        float startX = mStripLayoutHelper.getLastReorderXForTesting();
        mStripLayoutHelper.drag(TIMESTAMP, startX + dragDistance, 0f, dragDistance);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_NoExtraMinScroll() {
        // Mock 2 tabs so each tab is at its maximum width.
        initializeTest(false, false, true, 0, 2);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Start reorder mode on third tab.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(1);

        // Verify extra scroll offset.
        assertEquals(
                "Extra min offset should not be set.",
                0f,
                mStripLayoutHelper.getReorderExtraMinScrollOffsetForTesting(),
                EPSILON);
    }

    @Test
    @Feature("Tab Groups on Tab Strip")
    public void testReorder_ExtraMinScroll() {
        // Mock 2 tabs so each tab is at its maximum width.
        initializeTest(false, false, true, 0, 2);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 1);

        // Start reorder mode on third tab.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(1);

        // Verify extra scroll offset.
        assertNotEquals(
                "Extra min offset should be set.",
                0f,
                mStripLayoutHelper.getReorderExtraMinScrollOffsetForTesting(),
                EPSILON);
    }

    @Test
    public void testTabClosed() {
        // Initialize with 10 tabs.
        int tabCount = 10;
        initializeTest(false, false, false, 0, tabCount);

        // Remove tab from model and verify that the tab strip has not yet updated.
        int closedTabId = 1;
        int expectedNumTabs = tabCount;
        mModel.closeTab(mModel.getTabAt(closedTabId), false, true);
        assertEquals(
                "Tab strip should not yet have changed.",
                expectedNumTabs,
                mStripLayoutHelper.getStripLayoutTabsForTesting().length);

        // Trigger update and verify the tab strip matches the tab model.
        expectedNumTabs = 9;
        mStripLayoutHelper.tabClosed(TIMESTAMP, closedTabId);
        assertEquals(
                "Tab strip should match tab model.",
                expectedNumTabs,
                mStripLayoutHelper.getStripLayoutTabsForTesting().length);
        verify(mUpdateHost, times(5)).requestUpdate();
    }

    @Test
    public void testTabClosing_NoTabResize() {
        // Arrange
        int tabCount = 15;
        initializeTest(false, false, false, 14, tabCount);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        setupForAnimations();

        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Act: Call on close tab button handler.
        mStripLayoutHelper.handleCloseButtonClick(tabs[14], TIMESTAMP);

        // Assert: Animations started.
        assertTrue(
                "MultiStepAnimations should have started.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Act: End the tab closing animations to apply final values.
        Animator runningAnimator = mStripLayoutHelper.getRunningAnimatorForTesting();
        runningAnimator.end();

        // Assert: Tab is closed and animations are still running.
        int expectedTabCount = 14;
        assertEquals(
                "Unexpected tabs count",
                expectedTabCount,
                mStripLayoutHelper.getStripLayoutTabsForTesting().length);
        assertTrue(
                "MultiStepAnimations should still be running.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Act: End next set of animations to apply final values.
        mStripLayoutHelper.getRunningAnimatorForTesting().end();

        // Assert: Animations completed. The tab width is not resized and drawX does not change.
        // stripRightBound = width(800) - offsetXRight(20) = 780;
        // visibleTabRightBound = rightBound(780)- NTBWidth(32) - endPadding(8) = 740
        // lastTabDrawX = visibleTabRightBound(740) - tabWidth(108) = 632
        float expectedDrawX = 632.f;
        StripLayoutTab[] updatedTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        for (int i = updatedTabs.length - 1; i >= 0; i--) {
            StripLayoutTab stripTab = updatedTabs[i];
            assertEquals("Unexpected tab width after resize.", 108.f, stripTab.getWidth(), 0);
            assertEquals("Unexpected tab position.", expectedDrawX, stripTab.getDrawX(), 0);
            expectedDrawX -= TAB_WIDTH_SMALL - TAB_OVERLAP_WIDTH;
        }
        assertFalse(
                "MultiStepAnimations should have stopped running.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());
    }

    @Test
    public void testTabClosing_NonLastTab_TabResize() {
        // Arrange
        int tabCount = 4;
        initializeTest(false, false, false, 3, tabCount);
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        setupForAnimations();

        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Act: Call on close tab button handler.
        mStripLayoutHelper.handleCloseButtonClick(tabs[2], TIMESTAMP);

        // Assert: Animations started.
        assertTrue(
                "MultiStepAnimations should have started.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Act: End the animations to apply final values.
        Animator runningAnimator = mStripLayoutHelper.getRunningAnimatorForTesting();
        runningAnimator.end();

        // Assert: Tab is closed and animations are still running.
        int expectedTabCount = 3;
        assertEquals(expectedTabCount, mStripLayoutHelper.getStripLayoutTabsForTesting().length);
        assertTrue(
                "MultiStepAnimations should still be running.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());

        // Act: Set animation time forward by 250ms for next set of animations.
        mStripLayoutHelper.getRunningAnimatorForTesting().end();

        // Assert: Animations completed. The tab width is resized, tab.drawX is changed and
        // newTabButton.drawX is also changed.
        float expectedDrawX = 10.f; // offsetXLeft(10)
        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // = 730
        // ExpectedWidth = (availableSize(730) + 2 * overlap(28)) / 3  = 262
        float expectedWidthAfterResize = 262.f;
        StripLayoutTab[] updatedTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        for (int i = 0; i < updatedTabs.length; i++) {
            StripLayoutTab stripTab = updatedTabs[i];
            assertEquals(
                    "Unexpected tab width after resize.",
                    expectedWidthAfterResize,
                    stripTab.getWidth(),
                    0.1f);
            assertEquals("Unexpected tab position.", expectedDrawX, stripTab.getDrawX(), 0.1f);
            expectedDrawX += (expectedWidthAfterResize - TAB_OVERLAP_WIDTH);
        }
        assertFalse(
                "MultiStepAnimations should have ended.",
                mStripLayoutHelper.isMultiStepCloseAnimationsRunningForTesting());
    }

    @Test
    public void testTabClosingClearsTabHoverState() {
        initializeTabHoverTest();
        var tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();

        // Hover on tabs[2], and close it.
        mStripLayoutHelper.updateLastHoveredTab(tabs[2]);
        verify(mTabHoverCardView).show(any(), anyBoolean(), anyFloat(), anyFloat(), anyFloat());
        mStripLayoutHelper.handleCloseButtonClick(tabs[2], TIMESTAMP);

        // End the tab closure animation.
        var runningAnimator = mStripLayoutHelper.getRunningAnimatorForTesting();
        runningAnimator.end();

        verify(mTabHoverCardView).hide();
    }

    @Test
    public void testFlingLeft() {
        // Arrange
        initializeTest(false, false, false, 11, 12);
        // Disable the padding as changing the visible width change the existing expected fling
        // distance.
        mStripLayoutHelper.onSizeChanged(SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, 0, 0);
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        mStripLayoutHelper.setScrollOffsetForTesting(-150);

        // Act: Perform a fling and update layout.
        float velocityX = -7000f;
        // The velocityX value is used to calculate the scroller.finalX value.
        mStripLayoutHelper.fling(TIMESTAMP, 0, 0, velocityX, 0);
        // This will use the scroller.finalX value to update the scrollOffset. The timestamp
        // value here will determine the fling duration and affects the final offset value.
        mStripLayoutHelper.updateLayout(TIMESTAMP + 10);

        // Assert: Final scrollOffset.
        // The calculation of this value is done using the velocity. The velocity along a friction
        // constant is used to calculate deceleration and distance. That together with the animation
        // duration determines the final scroll offset position.
        float expectedOffset = -220.f;
        assertEquals(
                "Unexpected scroll offset.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                0.0);
    }

    @Test
    public void testFlingRight() {
        // Arrange
        initializeTest(false, false, false, 10, 11);
        // Disable the padding as changing the visible width change the existing expected fling
        // distance.
        mStripLayoutHelper.onSizeChanged(SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, 0, 0);
        // When updateLayout is called for the first time, bringSelectedTabToVisibleArea() method is
        // invoked. That also affects the scrollOffset value. So we call updateLayout before
        // performing a fling so that bringSelectedTabToVisible area isn't called after the fling.
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        mStripLayoutHelper.setScrollOffsetForTesting(-150);

        // Act: Perform a fling and update layout.
        float velocity = 5000f;
        // The velocityX value is used to calculate the scroller.finalX value.
        mStripLayoutHelper.fling(TIMESTAMP, 0, 0, velocity, 0);
        // This will use the scroller.finalX value to update the scrollOffset. The timestamp
        // value here will determine the fling duration and affects the final offset value.
        mStripLayoutHelper.updateLayout(TIMESTAMP + 20);

        // Assert: Final scrollOffset.
        // The calculation of this value is done using the velocity. The velocity along a friction
        // constant is used to calculate deceleration and distance. That together with the animation
        // duration determines the final scroll offset position.
        float expectedOffset = -48.f;
        assertEquals(
                "Unexpected scroll offset.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                0.0);
    }

    @Test
    public void testDrag_UpdatesScrollOffset_ScrollingStrip() {
        // Arrange
        initializeTest(false, false, false, 13, 14);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        // When updateLayout is called for the first time, bringSelectedTabToVisibleArea() method is
        // invoked. That also affects the scrollOffset value. So we call updateLayout before
        // performing a fling so that bringSelectedTabToVisible area isn't called after the fling.
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        mStripLayoutHelper.setScrollOffsetForTesting(-150);

        // Act: Drag and update layout.
        float dragDeltaX = -200.f;
        mStripLayoutHelper.drag(TIMESTAMP, 374.74f, 24.276f, dragDeltaX);

        float expectedOffset = -350; // mScrollOffset + dragDeltaX = -200 - 150 = -350
        // Assert scroll offset position.
        assertEquals(
                "Unexpected scroll offset.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                0.0);
        // Reorder mode is disabled for scrolling strip.
        assertFalse(mStripLayoutHelper.getInReorderModeForTesting());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_NoTabModel() {
        // Create StripLayoutHelper and mark that after tabs finish restoring, there will be five
        // tabs, where the third tab will be the active tab.
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModelStartupInfo(5, 2, false);

        // Verify there are 5 placeholders.
        StripLayoutTab[] stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertTrue("Tab at position 0 should be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertTrue("Tab at position 1 should be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertTrue("Tab at position 2 should be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertTrue("Tab at position 3 should be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertTrue("Tab at position 4 should be a placeholder.", stripTabs[4].getIsPlaceholder());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_PrepareOnSetTabModel() {
        // Create StripLayoutHelper and mark that after tabs finish restoring, there will be five
        // tabs, where the third tab will be the active tab.
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModelStartupInfo(5, 2, false);

        // Mock a tab model and set it in the StripLayoutHelper.
        int expectedActiveTabId = 0;
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        tabModel.addTab(expectedActiveTabId);
        tabModel.setIndex(0, TabSelectionType.FROM_NEW, true);
        tabModel.setActive(true);
        mStripLayoutHelper.setTabModel(tabModel, null, false);

        // Verify that the real and placeholder strip tabs were generated in the correct indices.
        StripLayoutTab[] stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertTrue("Tab at position 0 should be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertTrue("Tab at position 1 should be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertFalse(
                "Tab at position 2 should not be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertEquals(
                "Tab at position 2 should be the same from the mock.",
                expectedActiveTabId,
                stripTabs[2].getId());
        assertTrue("Tab at position 3 should be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertTrue("Tab at position 4 should be a placeholder.", stripTabs[4].getIsPlaceholder());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_PrepareOnSetTabModelInfo() {
        // Create StripLayoutHelper and mock a tab model and set it in the StripLayoutHelper.
        int expectedActiveTabId = 0;
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        tabModel.addTab(expectedActiveTabId);
        tabModel.setIndex(0, TabSelectionType.FROM_NEW, true);
        tabModel.setActive(true);
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModel(tabModel, null, false);

        // Verify that there are no placeholders yet.
        StripLayoutTab[] stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals("There should be no placeholders yet.", 0, stripTabs.length);

        // Mark that after tabs finish restoring, there will be five tabs, where the third tab will
        // be the active tab.
        mStripLayoutHelper.setTabModelStartupInfo(5, 2, false);

        // Verify that the real and placeholder strip tabs were generated in the correct indices.
        stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertTrue("Tab at position 0 should be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertTrue("Tab at position 1 should be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertFalse(
                "Tab at position 2 should not be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertEquals(
                "Tab at position 2 should be the same from the mock.",
                expectedActiveTabId,
                stripTabs[2].getId());
        assertTrue("Tab at position 3 should be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertTrue("Tab at position 4 should be a placeholder.", stripTabs[4].getIsPlaceholder());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_TabCreated() {
        // Create StripLayoutHelper and mark that after tabs finish restoring, there will be five
        // tabs, where the third tab will be the active tab.
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModelStartupInfo(5, 2, false);

        // Mock a tab model and set it in the StripLayoutHelper.
        int expectedActiveTabId = 0;
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        tabModel.addTab(expectedActiveTabId);
        tabModel.setIndex(0, TabSelectionType.FROM_NEW, true);
        tabModel.setActive(true);
        mStripLayoutHelper.setTabModel(tabModel, null, false);

        // Mark that a tab was restored.
        int expectedRestoredTabId = 1;
        tabModel.addTab(
                new MockTab(expectedRestoredTabId, mProfile),
                0,
                TabLaunchType.FROM_RESTORE,
                TabCreationState.FROZEN_ON_RESTORE);
        mStripLayoutHelper.tabCreated(
                TIMESTAMP, expectedRestoredTabId, Tab.INVALID_TAB_ID, false, false, true);

        // Verify that the third (active) and first tab are real.
        StripLayoutTab[] stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertFalse(
                "Tab at position 0 should not be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertEquals(
                "Tab at position 0 should be the same from the mock.",
                expectedRestoredTabId,
                stripTabs[0].getId());
        assertTrue("Tab at position 1 should be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertFalse(
                "Tab at position 2 should not be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertEquals(
                "Tab at position 2 should be the same from the mock.",
                expectedActiveTabId,
                stripTabs[2].getId());
        assertTrue("Tab at position 3 should be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertTrue("Tab at position 4 should be a placeholder.", stripTabs[4].getIsPlaceholder());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_OnTabStateInitialized() {
        // Create StripLayoutHelper and mark that after tabs finish restoring, there will be five
        // tabs, where the third tab will be the active tab.
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModelStartupInfo(5, 2, false);

        // Mock a tab model and set it in the StripLayoutHelper.
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        tabModel.setActive(true);
        mStripLayoutHelper.setTabModel(tabModel, null, false);

        // Verify there are placeholders.
        StripLayoutTab[] stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertTrue("Tab at position 0 should be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertTrue("Tab at position 1 should be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertTrue("Tab at position 2 should be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertTrue("Tab at position 3 should be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertTrue("Tab at position 4 should be a placeholder.", stripTabs[4].getIsPlaceholder());

        // Add the remaining tabs and mark that the tab state is finished initializing.
        tabModel.addTab(0);
        tabModel.addTab(1);
        tabModel.addTab(2);
        tabModel.addTab(3);
        tabModel.addTab(4);
        tabModel.setIndex(2, TabSelectionType.FROM_NEW, true);
        mStripLayoutHelper.onTabStateInitialized();

        // Verify the placeholders have been replaced.
        stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertFalse(
                "Tab at position 0 should not be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertFalse(
                "Tab at position 1 should not be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertFalse(
                "Tab at position 2 should not be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertFalse(
                "Tab at position 3 should not be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertFalse(
                "Tab at position 4 should not be a placeholder.", stripTabs[4].getIsPlaceholder());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_ScrollOnStartup() {
        // Create StripLayoutHelper and mark that after tabs finish restoring, there will be 20
        // tabs, where the last tab will be the active tab.
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModelStartupInfo(20, 19, false);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Mock a tab model and set it in the StripLayoutHelper.
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        tabModel.setActive(true);
        mStripLayoutHelper.setTabModel(tabModel, null, false);
        assertEquals("Offset should be 0.", 0, mStripLayoutHelper.getScrollOffset(), EPSILON);

        // Set size.
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        assertNotEquals(
                "Offset should have changed.", 0, mStripLayoutHelper.getScrollOffset(), EPSILON);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_STARTUP_REFACTORING)
    public void testPlaceholderStripLayout_CreatedTabOnStartup() {
        // Create StripLayoutHelper and mark that after tabs finish restoring, there will be five
        // tabs, where the third tab will be the active tab.
        mStripLayoutHelper = createStripLayoutHelper(false, false);
        mStripLayoutHelper.setTabModelStartupInfo(5, 2, true);

        // Mock a tab model and set it in the StripLayoutHelper.
        int expectedCreatedTabId = 4;
        MockTabModel tabModel = new MockTabModel(mProfile, null);
        tabModel.addTab(expectedCreatedTabId);
        tabModel.setIndex(0, TabSelectionType.FROM_NEW, true);
        tabModel.setActive(true);
        mStripLayoutHelper.setTabModel(tabModel, null, false);

        // Verify that the fifth (tab created from "intent") is real.
        StripLayoutTab[] stripTabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertTrue("Tab at position 0 should be a placeholder.", stripTabs[0].getIsPlaceholder());
        assertTrue("Tab at position 1 should be a placeholder.", stripTabs[1].getIsPlaceholder());
        assertTrue("Tab at position 2 should be a placeholder.", stripTabs[2].getIsPlaceholder());
        assertTrue("Tab at position 3 should be a placeholder.", stripTabs[3].getIsPlaceholder());
        assertFalse(
                "Tab at position 4 should not be a placeholder.", stripTabs[4].getIsPlaceholder());
        assertEquals(
                "Tab at position 4 should be the same from the mock.",
                expectedCreatedTabId,
                stripTabs[4].getId());
    }

    private void setupForAnimations() {
        CompositorAnimationHandler mHandler = new CompositorAnimationHandler(() -> {});
        when(mUpdateHost.getAnimationHandler()).thenReturn(mHandler);

        // Update layout when updateHost.requestUpdate is called.
        doAnswer(
                        invocation -> {
                            mStripLayoutHelper.updateLayout(TIMESTAMP);
                            return null;
                        })
                .when(mUpdateHost)
                .requestUpdate();
    }

    private void initializeTest(
            boolean rtl, boolean incognito, boolean disableAnimations, int tabIndex, int numTabs) {
        mStripLayoutHelper = createStripLayoutHelper(rtl, incognito);
        mIncognito = incognito;

        if (disableAnimations) mStripLayoutHelper.disableAnimationsForTesting();

        if (rtl) {
            mStripLayoutHelper.setLeftFadeWidth(
                    incognito
                            ? StripLayoutHelperManager.FADE_LONG_WIDTH_DP
                            : StripLayoutHelperManager.FADE_MEDIUM_WIDTH_DP);
            mStripLayoutHelper.setRightFadeWidth(StripLayoutHelperManager.FADE_SHORT_WIDTH_DP);
        } else {
            mStripLayoutHelper.setLeftFadeWidth(StripLayoutHelperManager.FADE_SHORT_WIDTH_DP);
            mStripLayoutHelper.setRightFadeWidth(
                    incognito
                            ? StripLayoutHelperManager.FADE_LONG_WIDTH_DP
                            : StripLayoutHelperManager.FADE_MEDIUM_WIDTH_DP);
        }

        if (numTabs <= 5) {
            for (int i = 0; i < numTabs; i++) {
                mModel.addTab(TEST_TAB_TITLES[i]);
                when(mModel.getTabAt(i).isHidden()).thenReturn(tabIndex != i);
                when(mModel.getTabAt(i).getView()).thenReturn(mInteractingTabView);
                when(mModel.getTabAt(i).getRootId()).thenReturn(i);
            }
        } else {
            for (int i = 0; i < numTabs; i++) {
                mModel.addTab("Tab " + i);
                when(mModel.getTabAt(i).isHidden()).thenReturn(tabIndex != i);
                when(mModel.getTabAt(i).getView()).thenReturn(mInteractingTabView);
                when(mModel.getTabAt(i).getRootId()).thenReturn(i);
            }
        }
        mModel.setIndex(tabIndex);
        mStripLayoutHelper.setTabModel(mModel, null, true);
        mStripLayoutHelper.setTabGroupModelFilter(mTabGroupModelFilter);
        mStripLayoutHelper.setLayerTitleCache(mLayerTitleCache);
        mStripLayoutHelper.tabSelected(0, tabIndex, 0, false);
        // Flush UI updated
    }

    private void initializeTest(int tabIndex) {
        initializeTest(false, false, tabIndex);
    }

    private void initializeTest(boolean rtl, boolean incognito, int tabIndex) {
        initializeTest(rtl, incognito, false, tabIndex, 5);
    }

    private void assertTabStripAndOrder(String[] expectedAccessibilityDescriptions) {
        // Each tab has a "close button", and there is one additional "new tab" button
        final int expectedNumberOfViews = 2 * expectedAccessibilityDescriptions.length + 1;

        final List<VirtualView> views = new ArrayList<>();
        mStripLayoutHelper.getVirtualViews(views);
        assertEquals(expectedNumberOfViews, views.size());

        // Tab titles
        for (int i = 0; i < expectedNumberOfViews - 1; i++) {
            final String expectedDescription =
                    i % 2 == 0
                            ? expectedAccessibilityDescriptions[i / 2]
                            : String.format(CLOSE_TAB, TEST_TAB_TITLES[i / 2]);
            assertEquals(expectedDescription, views.get(i).getAccessibilityDescription());
        }

        assertEquals(
                mActivity
                        .getResources()
                        .getString(
                                mIncognito
                                        ? R.string.accessibility_toolbar_btn_new_incognito_tab
                                        : R.string.accessibility_toolbar_btn_new_tab),
                views.get(views.size() - 1).getAccessibilityDescription());
    }

    private StripLayoutHelper createStripLayoutHelper(boolean rtl, boolean incognito) {
        LocalizationUtils.setRtlForTesting(rtl);
        final StripLayoutHelper stripLayoutHelper =
                new StripLayoutHelper(
                        mActivity,
                        mManagerHost,
                        mUpdateHost,
                        mRenderHost,
                        incognito,
                        mModelSelectorBtn,
                        mTabDragSource,
                        mToolbarContainerView,
                        mWindowAndroid,
                        mActionConfirmationManager);
        // Initialize StackScroller
        stripLayoutHelper.onContextChanged(mActivity);
        return stripLayoutHelper;
    }

    private String[] getExpectedAccessibilityDescriptions(int tabIndex) {
        final String[] expectedAccessibilityDescriptions = new String[TEST_TAB_TITLES.length];
        for (int i = 0; i < TEST_TAB_TITLES.length; i++) {
            final boolean isHidden = (i != tabIndex);
            String suffix;
            if (mIncognito) {
                suffix = isHidden ? INCOGNITO_IDENTIFIER : INCOGNITO_IDENTIFIER_SELECTED;
            } else {
                suffix = isHidden ? IDENTIFIER : IDENTIFIER_SELECTED;
            }
            String expectedDescription = "";
            if (!TextUtils.isEmpty(TEST_TAB_TITLES[i])) {
                expectedDescription += TEST_TAB_TITLES[i] + ", ";
            }
            expectedAccessibilityDescriptions[i] = expectedDescription + suffix;
        }
        return expectedAccessibilityDescriptions;
    }

    private StripLayoutTab[] getMockedStripLayoutTabs(float tabWidth, float mDrawX, int numTabs) {
        StripLayoutTab[] tabs = new StripLayoutTab[mModel.getCount()];

        for (int i = 0; i < numTabs; i++) {
            tabs[i] = mockStripTab(i, tabWidth, mDrawX);
        }

        return tabs;
    }

    private StripLayoutTab[] getMockedStripLayoutTabs(float tabWidth) {
        return getMockedStripLayoutTabs(tabWidth, 0.f, 5);
    }

    private StripLayoutTab mockStripTab(int id, float tabWidth, float mDrawX) {
        StripLayoutTab tab = mock(StripLayoutTab.class);
        when(tab.getWidth()).thenReturn(tabWidth);
        when(tab.getId()).thenReturn(id);
        when(tab.getDrawX()).thenReturn(mDrawX);
        return tab;
    }

    /**
     * Mock that the sequence of tabs from startIndex to endIndex are part of that same tab group.
     *
     * @param startIndex The index where we start including tabs in the group (inclusive).
     * @param endIndex The index where we stop including tabs in the group (exclusive).
     */
    private void groupTabs(int startIndex, int endIndex) {
        int groupRootId = mModel.getTabAt(startIndex).getId();
        int numTabs = endIndex - startIndex;
        List<Tab> relatedTabs = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            Tab tab = mModel.getTabAt(i);
            when(mTabGroupModelFilter.isTabInTabGroup(eq(tab))).thenReturn(true);
            when(tab.getRootId()).thenReturn(groupRootId);
            relatedTabs.add(tab);
        }
        when(mTabGroupModelFilter.getRelatedTabCountForRootId(eq(groupRootId))).thenReturn(numTabs);
        when(mTabGroupModelFilter.getRelatedTabListForRootId(eq(groupRootId)))
                .thenReturn(relatedTabs);

        mStripLayoutHelper.updateGroupAccessibilityDescription(groupRootId);
        mStripLayoutHelper.rebuildStripViews();
    }

    private void setTabDragSourceMock() {
        when(mTabDragSource.startTabDragAction(any(), any(), any(), anyFloat(), anyFloat()))
                .thenReturn(true);
        MultiWindowTestUtils.enableMultiInstance();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testDrag_AllowMovingTabOutOfStripLayout_SetActiveTab() {
        // Setup with 10 tabs and select tab 5.
        setTabDragSourceMock();
        initializeTest(false, false, false, 5, 10);
        StripLayoutTab[] tabs = getMockedStripLayoutTabs(TAB_WIDTH_1, 150f, 10);
        mStripLayoutHelper.setStripLayoutTabsForTesting(tabs);
        mStripLayoutHelper.tabSelected(1, 5, 0, false);
        // Trigger update to set foreground container visibility.
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        StripLayoutTab theClickedTab = tabs[5];

        // Clean active tab environment and ensure.
        mStripLayoutHelper.clearTabDragState();
        assertTrue(
                "Dragged Tab should be empty before drag action.",
                mStripLayoutHelper.getActiveClickedTabForTesting() == null);

        // Act and verify.
        mStripLayoutHelper.allowMovingTabOutOfStripLayout(theClickedTab, DRAG_START_POINT);

        verify(mTabDragSource, times(1))
                .startTabDragAction(any(), any(), any(), anyFloat(), anyFloat());
        assertTrue(
                "Tab being dragged should exist during drag action.",
                mStripLayoutHelper.getActiveClickedTabForTesting() != null);
        assertTrue(
                "Dragged Tab should match selected tab during drag action.",
                mStripLayoutHelper.getActiveClickedTabForTesting() == theClickedTab);
        mStripLayoutHelper.clearTabDragState();
        assertTrue(
                "Dragged Tab should be cleared at the end of drag action.",
                mStripLayoutHelper.getActiveClickedTabForTesting() == null);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testDrag_clearState() {
        // Initialize with 10 tabs.
        int selectedIndex = 5;
        initializeTest(false, false, false, selectedIndex, 10);
        StripLayoutTab draggedTab =
                mStripLayoutHelper.getStripLayoutTabsForTesting()[selectedIndex];
        draggedTab.setIsDraggedOffStrip(true);

        // Clear any animators.
        mStripLayoutHelper.finishAnimationsAndPushTabUpdates();
        assertNull("Should not be animating.", mStripLayoutHelper.getRunningAnimatorForTesting());

        // Act and verify.
        mStripLayoutHelper.clearTabDragState();
        assertNotNull("Should be animating.", mStripLayoutHelper.getRunningAnimatorForTesting());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testDrag_sendMoveWindowBroadcast_success() {
        // Setup with tabs and select first tab.
        setTabDragSourceMock();
        when(mToolbarContainerView.getContext()).thenReturn(mActivity);
        initializeTest(false, false, false, 0, 5);

        // Act and verify the broadcast is sent.
        onLongPress_OffTab();
        verify(mWindowAndroid, times(1)).sendBroadcast(any());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testDrag_DragActiveClickedTabOntoStrip() {
        // Setup and mark the active clicked tab.
        initializeTest(false, false, false, 0, 5);

        // Drag tab back onto strip.
        float expectedOffsetX = 123.45f;
        mStripLayoutHelper.setLastOffsetXForTesting(expectedOffsetX);
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0f, 0f, true, false);

        // Verify we continue reorder mode with the correct x-offset.
        assertFalse(
                "Should mark the tab is not off the strip.",
                mStripLayoutHelper.getInteractingTabForTesting().isDraggedOffStrip());
        assertEquals(
                "Should restore x-offset.",
                expectedOffsetX,
                mStripLayoutHelper.getInteractingTabForTesting().getOffsetX(),
                EPSILON);
        assertTrue(
                "Should re-enter reorder mode.", mStripLayoutHelper.getInReorderModeForTesting());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testDrag_DragActiveClickedTabOutOfStrip() {
        // Setup and mark the active clicked tab.
        initializeTest(false, false, false, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Drag tab out of strip.
        float expectedOffsetX = 123.45f;
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0f, 0f, true, false);
        StripLayoutTab draggedTab = mStripLayoutHelper.getInteractingTabForTesting();
        draggedTab.setOffsetX(expectedOffsetX);
        mStripLayoutHelper.clearForTabDrop(TIMESTAMP, true, false);

        // Finish animations.
        assertNotNull(
                "Animator should be running.", mStripLayoutHelper.getRunningAnimatorForTesting());
        mStripLayoutHelper.finishAnimationsAndPushTabUpdates();

        // Verify we stop reorder mode and animate the tab exiting.
        assertEquals(
                "Should have stored dragged tab's x-offset.",
                expectedOffsetX,
                mStripLayoutHelper.getLastOffsetXForTesting(),
                EPSILON);
        assertTrue("Should mark the tab is off the strip.", draggedTab.isDraggedOffStrip());
        assertFalse(
                "Should not be in reorder mode.", mStripLayoutHelper.getInReorderModeForTesting());
        assertEquals(
                "Tab should be translated off the strip.",
                SCREEN_HEIGHT,
                draggedTab.getOffsetY(),
                EPSILON);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testDrag2_DragActiveClickedTabOutOfStrip() {
        // Setup and mark the active clicked tab.
        initializeTest(false, false, false, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Drag tab out of strip.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        mStripLayoutHelper.setTabAtPositionForTesting(tabs[1]);
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0f, 0f, true, false);
        mStripLayoutHelper.clearForTabDrop(TIMESTAMP, true, false);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Verify 3rd, 4th and 5th tab's start divider is visible.
        assertFalse("Start divider should be hidden.", tabs[0].isStartDividerVisible());
        assertFalse("DraggedTab divider should be hidden.", tabs[1].isStartDividerVisible());
        assertTrue(
                "Tab after draggedTab start divider should be visible.",
                tabs[2].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[3].isStartDividerVisible());
        assertTrue("Start divider should be visible.", tabs[4].isStartDividerVisible());
    }

    @Test
    public void testGetTabIndexForTabDrop_FirstHalfOfTab() {
        // Setup with 3 tabs.
        initializeTest(false, false, true, 1, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // First half of second tab:
        // tabWidth(265) - overlapWidth(28) + inset(16) to +halfTabWidth(132.5) = 253 to 385.5
        int expectedIndex = 1;
        float dropX = 300.f;
        assertEquals(
                "Should prepare to drop at index 1.",
                expectedIndex,
                mStripLayoutHelper.getTabIndexForTabDrop(dropX));
    }

    @Test
    public void testGetTabIndexForTabDrop_SecondHalfOfTab() {
        // Setup with 3 tabs.
        initializeTest(false, false, true, 1, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // First half of second tab:
        // tabWidth(265) - overlapWidth(28) + inset(16) to +halfTabWidth(132.5) = 253 to 385.5
        int expectedIndex = 2;
        float dropX = 400.f;
        assertEquals(
                "Should prepare to drop at index 2.",
                expectedIndex,
                mStripLayoutHelper.getTabIndexForTabDrop(dropX));
    }

    @Test
    public void testGetTabIndexForTabDrop_OnStartGap() {
        // Setup with 3 tabs.
        initializeTest(false, false, true, 1, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Prepare for tab drop.
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0.f, 0.f, false, false);
        // Start gap will be tabWidth(265) / 2 = 132.5
        mStripLayoutHelper.setScrollOffsetForTesting(-132);

        // Last tab ends at:
        // 3 * (tabWidth(265) - overlapWidth(28)) = 711
        int expectedIndex = 0;
        float dropX = 50;
        assertEquals(
                "Should prepare to drop at index 0.",
                expectedIndex,
                mStripLayoutHelper.getTabIndexForTabDrop(dropX));
    }

    @Test
    public void testGetTabIndexForTabDrop_OnEndGap() {
        // Setup with 3 tabs.
        initializeTest(false, false, true, 1, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Last tab ends at:
        // 3 * (tabWidth(265) - overlapWidth(28)) = 711
        int expectedIndex = 3;
        float dropX = 750;
        assertEquals(
                "Should prepare to drop at index 3.",
                expectedIndex,
                mStripLayoutHelper.getTabIndexForTabDrop(dropX));
    }

    @Test
    public void testPrepareForTabDrop() {
        // Setup with 5 tabs.
        initializeTest(false, false, true, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);
        // Group 2nd and 3rd tab.
        groupTabs(1, 3);

        // Prepare for tab drop.
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0.f, 0.f, false, false);

        // Verify.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertTrue("Should be in reorder mode.", mStripLayoutHelper.getInReorderModeForTesting());
        assertNotEquals("Should be tab margin after tab 0.", 0, tabs[0].getTrailingMargin());
        assertEquals(
                "Should not be tab margin after tab 1.", 0, tabs[1].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 2.", 0, tabs[2].getTrailingMargin());
        assertEquals(
                "Should not be tab margin after tab 3.", 0, tabs[3].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 4.", 0, tabs[4].getTrailingMargin());

        assertEquals(
                "TouchableRect does not match. Touch size should match the strip during drag.",
                new RectF(PADDING_LEFT, 0, SCREEN_WIDTH_LANDSCAPE - PADDING_RIGHT, SCREEN_HEIGHT),
                mStripLayoutHelper.getTouchableRect());
    }

    @Test
    public void testUpdateReorderPositionForTabDrop() {
        // Setup with 4 tabs.
        initializeTest(false, false, true, 1, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Prepare for tab drop.
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0.f, 0.f, false, false);

        // Hover between 2nd and 3rd tab:
        // 2 * (tabWidth(265) - overlapWidth(28)) = 474
        mStripLayoutHelper.updateReorderPositionForTabDrop(474.f);

        // Verify.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals(
                "Should not be tab margin after tab 0.", 0, tabs[0].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 1.", 0, tabs[1].getTrailingMargin());
        assertEquals(
                "Should not be tab margin after tab 2.", 0, tabs[2].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 3.", 0, tabs[3].getTrailingMargin());

        // Now hover between 1st and 2nd tab:
        // tabWidth(265) - overlapWidth(28) = 237
        mStripLayoutHelper.updateReorderPositionForTabDrop(237.f);

        // Verify.
        assertNotEquals("Should be tab margin after tab 0.", 0, tabs[0].getTrailingMargin());
        assertEquals(
                "Should not be tab margin after tab 1.", 0, tabs[1].getTrailingMargin(), EPSILON);
        assertEquals(
                "Should not be tab margin after tab 2.", 0, tabs[2].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 3.", 0, tabs[3].getTrailingMargin());
    }

    @Test
    public void testUpdateReorderPositionForTabDrop_StartAndEndGap() {
        // Setup with 3 tabs.
        initializeTest(false, false, true, 1, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH_LANDSCAPE,
                SCREEN_HEIGHT,
                false,
                TIMESTAMP,
                PADDING_LEFT,
                PADDING_RIGHT);
        mStripLayoutHelper.updateLayout(TIMESTAMP);

        // Prepare for tab drop.
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0.f, 0.f, false, false);
        // Start gap will be tabWidth(265) / 2 = 132.5
        mStripLayoutHelper.setScrollOffsetForTesting(-132);

        // Hover in start gap:
        mStripLayoutHelper.updateReorderPositionForTabDrop(50);

        // Verify.
        StripLayoutTab[] tabs = mStripLayoutHelper.getStripLayoutTabsForTesting();
        assertEquals(
                "Should not be tab margin after tab 0.", 0, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(
                "Should not be tab margin after tab 1.", 0, tabs[1].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 2.", 0, tabs[2].getTrailingMargin());

        // Hover in end gap:
        mStripLayoutHelper.updateReorderPositionForTabDrop(1100);

        // Verify.
        assertEquals(
                "Should not be tab margin after tab 0.", 0, tabs[0].getTrailingMargin(), EPSILON);
        assertEquals(
                "Should not be tab margin after tab 1.", 0, tabs[1].getTrailingMargin(), EPSILON);
        assertNotEquals("Should be tab margin after tab 2.", 0, tabs[2].getTrailingMargin());
    }

    @Test
    public void testDestinationStripForTabDrop_DifferentIncognitoState() {
        // Setup with 3 tabs.
        boolean isIncognito = false;
        initializeTest(false, isIncognito, true, 1, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);

        // Prepare and verify no interaction.
        mStripLayoutHelper.prepareForTabDrop(TIMESTAMP, 0.f, 0.f, false, !isIncognito);
        assertFalse(
                "Shouldn't start reorder when dragged tab Incognito state is different.",
                mStripLayoutHelper.getInReorderModeForTesting());

        // Drag and verify no interaction.
        float expectedOffset = mStripLayoutHelper.getScrollOffset();
        mStripLayoutHelper.dragForTabDrop(TIMESTAMP, PADDING_LEFT, 0.f, 50.f, !isIncognito);
        assertEquals(
                "Shouldn't have scrolled when dragged tab Incognito is different.",
                expectedOffset,
                mStripLayoutHelper.getScrollOffset(),
                EPSILON);

        // Set reorder mode for testing, then clear for tab drop and verify no interaction.
        mStripLayoutHelper.startReorderModeAtIndexForTesting(0);
        mStripLayoutHelper.clearForTabDrop(TIMESTAMP, false, !isIncognito);
        assertTrue(
                "Shouldn't stop reorder when dragged tab Incognito state is different.",
                mStripLayoutHelper.getInReorderModeForTesting());
    }

    @Test
    @DisableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testRebuildNonTabViews_IndicatorsDisabled() {
        // Initialize with 10 tabs. Group tabs 2 through 3. Group tabs 5 through 8.
        initializeTest(false, false, true, 0, 10);
        groupTabs(1, 3);
        groupTabs(4, 8);

        // Verify.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertEquals("Should be 10 views (10 tabs).", 10, views.length);
        for (StripLayoutView view : views) {
            assertTrue(EXPECTED_TAB, view instanceof StripLayoutTab);
        }
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testRebuildNonTabViews_IndicatorsEnabled() {
        // Initialize with 10 tabs. Group tabs 2 through 3. Group tabs 5 through 8.
        initializeTest(false, false, true, 0, 10);
        groupTabs(1, 3);
        groupTabs(4, 8);

        // Verify.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertEquals("Should be 12 views (10 tabs and 2 titles).", 12, views.length);
        assertTrue(EXPECTED_TAB, views[0] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TITLE, views[1] instanceof StripLayoutGroupTitle);
        assertTrue(EXPECTED_TAB, views[2] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[3] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[4] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TITLE, views[5] instanceof StripLayoutGroupTitle);
        assertTrue(EXPECTED_TAB, views[6] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[7] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[8] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[9] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[10] instanceof StripLayoutTab);
        assertTrue(EXPECTED_TAB, views[11] instanceof StripLayoutTab);

        // verify bottom indicator width.
        float tabWidth = views[0].getWidth() - TAB_OVERLAP_WIDTH;
        float expectedWidth1 =
                views[1].getWidth()
                        + tabWidth * 2
                        - StripLayoutHelper.TAB_GROUP_BOTTOM_INDICATOR_WIDTH_OFFSET;
        float expectedWidth2 =
                views[5].getWidth()
                        + tabWidth * 4
                        - StripLayoutHelper.TAB_GROUP_BOTTOM_INDICATOR_WIDTH_OFFSET;
        assertEquals(
                expectedWidth1, ((StripLayoutGroupTitle) views[1]).getBottomIndicatorWidth(), 0.f);
        assertEquals(
                expectedWidth2, ((StripLayoutGroupTitle) views[5]).getBottomIndicatorWidth(), 0.f);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testHandleGroupTitleClick_Collapse() {
        // Initialize with 4 tabs. Group first three tabs.
        initializeTest(false, false, true, 3, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 3);

        // Fake a click on the group indicator.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        mStripLayoutHelper.handleGroupTitleClick((StripLayoutGroupTitle) views[0]);

        // Verify the proper event was sent to the TabGroupModelFilter.
        verify(mTabGroupModelFilter).setTabGroupCollapsed(0, true);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testHandleGroupTitleClick_Expand() {
        // Initialize with 4 tabs. Group first three tabs.
        initializeTest(false, false, true, 3, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 3);

        // Mark the group as collapsed. Fake a click on the group indicator.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], true);
        when(mTabGroupModelFilter.getTabGroupCollapsed(0)).thenReturn(true);
        mStripLayoutHelper.handleGroupTitleClick((StripLayoutGroupTitle) views[0]);

        // Verify the proper event was sent to the TabGroupModelFilter.
        verify(mTabGroupModelFilter).setTabGroupCollapsed(0, false);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testUpdateTabGroupCollapsed_Collapse() {
        // Initialize with 4 tabs. Group first three tabs.
        initializeTest(false, false, true, 3, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 3);

        // Verify initial dimensions.
        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // tabWidth = (availableSize(684) + 3 * overlap(28)) / 4 = 192.f
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        float initialTabWidth = 192.f;
        assertEquals("Tab width is incorrect.", initialTabWidth, views[1].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", initialTabWidth, views[2].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", initialTabWidth, views[3].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", initialTabWidth, views[4].getWidth(), EPSILON);

        // Collapse the group.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], true);

        // Verify final dimensions.
        float collapsedWidth = TAB_OVERLAP_WIDTH;
        float endTabWidth = TabUiThemeUtil.getMaxTabStripTabWidthDp();
        assertEquals("Tab width is incorrect.", collapsedWidth, views[1].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", collapsedWidth, views[2].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", collapsedWidth, views[3].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", endTabWidth, views[4].getWidth(), EPSILON);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testUpdateTabGroupCollapsed_Expand() {
        // Initialize with 4 tabs. Group first three tabs.
        initializeTest(false, false, true, 3, 4);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 3);

        // Collapse the group.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], true);

        // Verify initial dimensions.
        float collapsedWidth = TAB_OVERLAP_WIDTH;
        float initialTabWidth = TabUiThemeUtil.getMaxTabStripTabWidthDp();
        assertEquals("Tab width is incorrect.", collapsedWidth, views[1].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", collapsedWidth, views[2].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", collapsedWidth, views[3].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", initialTabWidth, views[4].getWidth(), EPSILON);

        // Fake a click on the tab group to expand.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], false);

        // Verify final dimensions.
        // availableSize = width(800) - NTB(32) - endPadding(8) - offsetXLeft(10) - offsetXRight(20)
        // - groupTitleWidth(46) = 684.
        // tabWidth = (availableSize(684) + 3 * overlap(28)) / 4 = 192.f
        float endTabWidth = 192.f;
        assertEquals("Tab width is incorrect.", endTabWidth, views[1].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", endTabWidth, views[2].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", endTabWidth, views[3].getWidth(), EPSILON);
        assertEquals("Tab width is incorrect.", endTabWidth, views[4].getWidth(), EPSILON);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testSelectedTabCollapse_MiddleGroup_PrevTabSelected() {
        // Initialize with 5 tabs. Group last two tabs.
        initializeTest(false, false, true, 3, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(3, 4);

        // Assert: the 4th tab is selected.
        assertEquals(
                "The tab selected is incorrect.", 3, mStripLayoutHelper.getSelectedStripTabIndex());

        // Assert: the fourth view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[3] instanceof StripLayoutGroupTitle);

        // Click to collapse the first tab group.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[3], true);

        // Assert: the previous tab is selected as there is no expanded tab towards the end.
        assertEquals(
                "The tab selected is incorrect.", 2, mStripLayoutHelper.getSelectedStripTabIndex());
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testSelectedTabCollapse_StartGroup_NextTabSelected() {
        // Initialize with 5 tabs. Group first three tabs.
        initializeTest(false, false, true, 1, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 3);

        // Assert: the 2nd tab is selected.
        assertEquals(
                "The tab selected is incorrect.", 1, mStripLayoutHelper.getSelectedStripTabIndex());

        // Assert: the first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);

        // Click to collapse the first tab group.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], true);

        // Assert: the fourth tab is selected.
        assertEquals(
                "The tab selected is incorrect.", 3, mStripLayoutHelper.getSelectedStripTabIndex());
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testCollapseSelectedTab_EndGroup_PrevTabSelected() {
        // Initialize with 5 tabs. Group last two tabs.
        initializeTest(false, false, true, 3, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(3, 5);

        // Assert: the 4th tab is selected.
        assertEquals(
                "The tab selected is incorrect.", 3, mStripLayoutHelper.getSelectedStripTabIndex());

        // Assert: the fourth view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[3] instanceof StripLayoutGroupTitle);

        // Click to collapse the first tab group.
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[3], true);

        // Assert: the previous tab is selected as there is no expanded tab towards the end.
        assertEquals(
                "The tab selected is incorrect.", 2, mStripLayoutHelper.getSelectedStripTabIndex());
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testCollapseSelectedTab_OpenNtp() {
        // Initialize with 5 tabs. Group all five tabs.
        initializeTest(false, false, true, 3, 5);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        groupTabs(0, 5);

        // Assert: the 4th tab is selected.
        assertEquals(
                "The tab selected is incorrect.", 3, mStripLayoutHelper.getSelectedStripTabIndex());

        // Assert: the first view should be group title.
        StripLayoutView[] views = mStripLayoutHelper.getStripLayoutViewsForTesting();
        assertTrue(EXPECTED_TITLE, views[0] instanceof StripLayoutGroupTitle);

        // Click to collapse the first tab group.
        TabCreator tabCreator = mock(TabCreator.class);
        mStripLayoutHelper.setTabModel(spy(mModel), tabCreator, true);
        mStripLayoutHelper.collapseTabGroupForTesting((StripLayoutGroupTitle) views[0], true);

        // Verify: Ntp opened since there is no expanded tab on strip.
        verify(tabCreator).launchNtp();
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testTabSelected_ExpandsGroup() {
        // Group first two tabs and collapse.
        int startIndex = 3;
        int groupId = 0;
        initializeTest(startIndex);
        groupTabs(groupId, 2);
        when(mTabGroupModelFilter.getTabGroupCollapsed(groupId)).thenReturn(true);

        // Select the first tab.
        mStripLayoutHelper.tabSelected(TIMESTAMP, groupId, startIndex, /* skipAutoScroll= */ false);

        // Verify we auto-expand.
        verify(mTabGroupModelFilter).deleteTabGroupCollapsed(groupId);
    }

    private void testTabCreated_InCollapsedGroup(boolean selected) {
        // Group first two tabs and collapse.
        int groupId = 0;
        initializeTest(/* tabIndex= */ 3);
        groupTabs(groupId, 2);
        when(mTabGroupModelFilter.getTabGroupCollapsed(groupId)).thenReturn(true);

        // Create a tab in the collapsed group.
        int tabId = 5;
        mModel.addTab("new tab");
        mModel.getTabById(tabId).setRootId(groupId);
        mStripLayoutHelper.tabCreated(
                TIMESTAMP,
                tabId,
                tabId,
                selected,
                /* closureCancelled */ false,
                /* onStartup= */ false);

        // Verify we only auto-expand if selected.
        verify(mTabGroupModelFilter, times(selected ? 1 : 0)).deleteTabGroupCollapsed(groupId);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testTabCreated_InCollapsedGroup_Selected() {
        testTabCreated_InCollapsedGroup(/* selected= */ true);
    }

    @Test
    @EnableFeatures({
        ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS,
        ChromeFeatureList.TAB_STRIP_GROUP_COLLAPSE
    })
    public void testTabCreated_InCollapsedGroup_NotSelected() {
        testTabCreated_InCollapsedGroup(/* selected= */ false);
    }

    @Test
    public void testUpdateLastHoveredTab() {
        // Assume tab0 is selected, tab1 is hovered on.
        initializeTabHoverTest();
        var hoveredTab = mStripLayoutHelper.getStripLayoutTabsForTesting()[1];
        mStripLayoutHelper.updateLastHoveredTab(hoveredTab);
        assertEquals(
                "Last hovered tab is not set.", hoveredTab, mStripLayoutHelper.getLastHoveredTab());
        verify(mTabHoverCardView)
                .show(
                        mModel.getTabAt(1),
                        false,
                        hoveredTab.getDrawX(),
                        hoveredTab.getWidth(),
                        SCREEN_HEIGHT);
        assertEquals(
                "Tab container opacity is incorrect.",
                StripLayoutHelper.TAB_OPACITY_VISIBLE_FOREGROUND,
                hoveredTab.getContainerOpacity(),
                0.0);
    }

    @Test
    public void testUpdateLastHoveredTab_animationRunning() {
        initializeTabHoverTest();
        var hoveredTab = mStripLayoutHelper.getStripLayoutTabsForTesting()[1];

        // Assume that animations are running.
        var animator = mock(Animator.class);
        when(animator.isRunning()).thenReturn(true);
        mStripLayoutHelper.setRunningAnimatorForTesting(animator);
        mStripLayoutHelper.updateLastHoveredTab(hoveredTab);
        verify(mTabHoverCardView, never())
                .show(any(), anyBoolean(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    public void testIsTabCompletelyHidden() {
        initializeTabHoverTest();
        var hoveredTab = mStripLayoutHelper.getStripLayoutTabsForTesting()[1];

        // Set simulated hovered StripLayoutTab drawX and width to assume a position beyond the left
        // fade.
        hoveredTab.setDrawX(-50f);
        hoveredTab.setWidth(
                StripLayoutHelperManager.FADE_SHORT_WIDTH_DP - 1 - hoveredTab.getDrawX());
        assertTrue(
                "Tab should be considered hidden for hover state.",
                mStripLayoutHelper.isTabCompletelyHidden(hoveredTab));

        // Set simulated hovered StripLayoutTab drawX to assume a position beyond the right fade.
        hoveredTab.setDrawX(SCREEN_WIDTH - StripLayoutHelperManager.FADE_MEDIUM_WIDTH_DP + 1);
        assertTrue(
                "Tab should be considered hidden for hover state.",
                mStripLayoutHelper.isTabCompletelyHidden(hoveredTab));
    }

    private void initializeTabHoverTest() {
        initializeTest(false, false, true, 0, 3);
        mStripLayoutHelper.onSizeChanged(
                SCREEN_WIDTH, SCREEN_HEIGHT, false, TIMESTAMP, PADDING_LEFT, PADDING_RIGHT);
        mStripLayoutHelper.setTabHoverCardView(mTabHoverCardView);
        // For ease of dp/px calculation.
        mContext.getResources().getDisplayMetrics().density = 1f;
    }

    @Test
    public void testSetTabModelFilter() {
        // Setup and verify initial state.
        initializeTest(false, false, 0);
        TabGroupModelFilterObserver observer =
                mStripLayoutHelper.getTabGroupModelFilterObserverForTesting();
        verify(mTabGroupModelFilter).addTabGroupObserver(observer);

        // Set a new TabGroupModelFilter.
        TabGroupModelFilter newModelFilter = mock(TabGroupModelFilter.class);
        mStripLayoutHelper.setTabGroupModelFilter(newModelFilter);

        // Verify the observers have been updated as expected.
        verify(mTabGroupModelFilter).removeTabGroupObserver(observer);
        verify(newModelFilter).addTabGroupObserver(observer);
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testSetLayerTitleCache() {
        // Setup. Group 2nd and 3rd tab.
        initializeTest(false, false, 0);
        groupTabs(1, 3);

        // Set a new LayerTitleCache.
        LayerTitleCache newTitleCache = mock(LayerTitleCache.class);
        mStripLayoutHelper.setLayerTitleCache(newTitleCache);

        // Verify the observers have been updated as expected.
        verify(newTitleCache).getGroupTitleWidth(eq(false), eq(null));
    }

    @Test
    @EnableFeatures(ChromeFeatureList.TAB_STRIP_GROUP_INDICATORS)
    public void testDestroy() {
        // Setup.
        initializeTest(false, false, 0);
        TabGroupModelFilterObserver observer =
                mStripLayoutHelper.getTabGroupModelFilterObserverForTesting();

        // Destroy the instance.
        mStripLayoutHelper.destroy();

        // Verify the observer has been removed as expected.
        verify(mTabGroupModelFilter).removeTabGroupObserver(observer);
    }
}
