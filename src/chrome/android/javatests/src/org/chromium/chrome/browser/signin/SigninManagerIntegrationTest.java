// Copyright 2013 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import androidx.test.filters.MediumTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import org.chromium.base.test.BaseJUnit4ClassRunner;
import org.chromium.base.test.util.CriteriaHelper;
import org.chromium.base.test.util.DoNotBatch;
import org.chromium.base.test.util.Features;
import org.chromium.base.test.util.Features.DisableFeatures;
import org.chromium.base.test.util.Features.EnableFeatures;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.profiles.ProfileManager;
import org.chromium.chrome.browser.signin.services.IdentityServicesProvider;
import org.chromium.chrome.browser.signin.services.SigninManager;
import org.chromium.chrome.browser.signin.services.SigninPreferencesManager;
import org.chromium.chrome.test.util.browser.signin.SigninTestRule;
import org.chromium.chrome.test.util.browser.signin.SigninTestUtil;
import org.chromium.components.signin.SigninFeatures;
import org.chromium.components.signin.base.AccountInfo;
import org.chromium.components.signin.base.CoreAccountInfo;
import org.chromium.components.signin.identitymanager.ConsentLevel;
import org.chromium.components.signin.identitymanager.IdentityManager;
import org.chromium.components.signin.metrics.SignoutReason;
import org.chromium.content_public.browser.test.NativeLibraryTestUtils;
import org.chromium.content_public.browser.test.util.TestThreadUtils;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Integration test for the IdentityManager.
 *
 * <p>These tests initialize the native part of the service.
 */
@RunWith(BaseJUnit4ClassRunner.class)
@DoNotBatch(reason = "Integration test suite that changes the list of accounts")
public class SigninManagerIntegrationTest {
    @Rule public final SigninTestRule mSigninTestRule = new SigninTestRule();

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Rule public TestRule mProcessor = new Features.InstrumentationProcessor();

    private static final String TEST_ACCOUNT1 = "foo@gmail.com";
    private static final String TEST_ACCOUNT2 = "bar@gmail.com";

    private CoreAccountInfo mTestAccount1;
    private CoreAccountInfo mTestAccount2;

    private IdentityManager mIdentityManager;
    private SigninManager mSigninManager;

    @Mock private SigninManager.SignInStateObserver mSignInStateObserverMock;

    @Before
    public void setUp() {
        mTestAccount1 = mSigninTestRule.toCoreAccountInfo(TEST_ACCOUNT1);
        mTestAccount2 = mSigninTestRule.toCoreAccountInfo(TEST_ACCOUNT2);

        NativeLibraryTestUtils.loadNativeLibraryAndInitBrowserProcess();

        mSigninTestRule.waitForSeeding();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Profile profile = ProfileManager.getLastUsedRegularProfile();
                    mIdentityManager = IdentityServicesProvider.get().getIdentityManager(profile);
                    mSigninManager = IdentityServicesProvider.get().getSigninManager(profile);
                    mSigninManager.addSignInStateObserver(mSignInStateObserverMock);
                });
    }

    @Test
    @MediumTest
    public void testUpdateAccountListNoAccountsRegisteredAndNoSignedInUser() {
        Assert.assertArrayEquals(
                "Initial state: getAccounts must be empty",
                new CoreAccountInfo[] {},
                mIdentityManager.getAccountsWithRefreshTokens());

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    // Run test.
                    if (!ChromeFeatureList.isEnabled(SigninFeatures.SEED_ACCOUNTS_REVAMP)) {
                        mSigninManager.reloadAllAccountsFromSystem(null);
                    }

                    Assert.assertArrayEquals(
                            "No account: getAccounts must be empty",
                            new CoreAccountInfo[] {},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    public void testUpdateAccountListOneAccountsRegisteredAndNoSignedInUser() {
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    // Run test.
                    if (!ChromeFeatureList.isEnabled(SigninFeatures.SEED_ACCOUNTS_REVAMP)) {
                        mSigninManager.reloadAllAccountsFromSystem(null);
                    }

                    Assert.assertArrayEquals(
                            "No signed in account: getAccounts must be empty",
                            new CoreAccountInfo[] {},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    public void testUpdateAccountListOneAccountsRegisteredSignedIn() {
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        SigninTestUtil.signin(mTestAccount1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertArrayEquals(
                            "Signed in: one account should be available",
                            new CoreAccountInfo[] {mTestAccount1},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    @DisableFeatures(SigninFeatures.SEED_ACCOUNTS_REVAMP)
    // TODO(crbug.com/40284908): Delete this test
    public void testUpdateAccountListOneAccountsRegisteredSignedInOther() {
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    // Run test.
                    mSigninManager.reloadAllAccountsFromSystem(mTestAccount2.getId());

                    Assert.assertArrayEquals(
                            "Signed in but different account, getAccounts must remain empty",
                            new CoreAccountInfo[] {},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    public void testUpdateAccountListSingleAccountThenAddOne() {
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        SigninTestUtil.signin(mTestAccount1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertArrayEquals(
                            "Signed in and one account available",
                            new CoreAccountInfo[] {mTestAccount1},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });

        // Add another account.
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT2);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    // Re-run validation.
                    if (!ChromeFeatureList.isEnabled(SigninFeatures.SEED_ACCOUNTS_REVAMP)) {
                        mSigninManager.reloadAllAccountsFromSystem(mTestAccount1.getId());
                    }

                    Assert.assertEquals(
                            "Signed in and two accounts available",
                            new HashSet<>(Arrays.asList(mTestAccount1, mTestAccount2)),
                            new HashSet<>(
                                    Arrays.asList(
                                            mIdentityManager.getAccountsWithRefreshTokens())));
                });
    }

    @Test
    @MediumTest
    @EnableFeatures(SigninFeatures.SEED_ACCOUNTS_REVAMP)
    public void testUpdateAccountListTwoAccountsThenRemoveOne() {
        // Add accounts.
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        SigninTestUtil.signin(mTestAccount1);
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT2);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertEquals(
                            "Signed in and two accounts available",
                            new HashSet<>(Arrays.asList(mTestAccount1, mTestAccount2)),
                            new HashSet<>(
                                    Arrays.asList(
                                            mIdentityManager.getAccountsWithRefreshTokens())));
                });

        mSigninTestRule.removeAccountAndWaitForSeeding(mTestAccount2.getId());

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertArrayEquals(
                            "Only one account available, account2 should not be returned anymore",
                            new CoreAccountInfo[] {mTestAccount1},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    public void testUpdateAccountListTwoAccountsThenRemoveAll() {
        // Add accounts.
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT2);
        SigninTestUtil.signin(mTestAccount1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertEquals(
                            "Signed in and two accounts available",
                            new HashSet<>(Arrays.asList(mTestAccount1, mTestAccount2)),
                            new HashSet<>(
                                    Arrays.asList(
                                            mIdentityManager.getAccountsWithRefreshTokens())));
                });

        // Remove all.
        mSigninTestRule.removeAccountAndWaitForSeeding(mTestAccount1.getId());
        mSigninTestRule.removeAccountAndWaitForSeeding(mTestAccount2.getId());

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    // Re-validate and run checks.
                    if (!ChromeFeatureList.isEnabled(SigninFeatures.SEED_ACCOUNTS_REVAMP)) {
                        mSigninManager.reloadAllAccountsFromSystem(mTestAccount1.getId());
                    }

                    Assert.assertArrayEquals(
                            "No account available",
                            new CoreAccountInfo[] {},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    @EnableFeatures(SigninFeatures.SEED_ACCOUNTS_REVAMP)
    public void testUpdateAccountListTwoAccountsThenRemoveAllSignOut() {
        // Add accounts.
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT2);
        SigninTestUtil.signin(mTestAccount1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertEquals(
                            "Signed in and two accounts available",
                            new HashSet<>(Arrays.asList(mTestAccount1, mTestAccount2)),
                            new HashSet<>(
                                    Arrays.asList(
                                            mIdentityManager.getAccountsWithRefreshTokens())));
                });

        mSigninTestRule.signOut();
        mSigninTestRule.removeAccountAndWaitForSeeding(mTestAccount1.getId());
        mSigninTestRule.removeAccountAndWaitForSeeding(mTestAccount2.getId());

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertArrayEquals(
                            "Not signed in and no accounts available",
                            new CoreAccountInfo[] {},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    public void testUpdateAccountListTwoAccountsRegisteredAndOneSignedIn() {
        // Add accounts.
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT2);
        SigninTestUtil.signin(mTestAccount1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertEquals(
                            "Signed in and two accounts available",
                            new HashSet<>(Arrays.asList(mTestAccount1, mTestAccount2)),
                            new HashSet<>(
                                    Arrays.asList(
                                            mIdentityManager.getAccountsWithRefreshTokens())));
                });
    }

    @Test
    @MediumTest
    @DisableFeatures(SigninFeatures.SEED_ACCOUNTS_REVAMP)
    // TODO(crbug.com/40284908): Delete this test
    public void testUpdateAccountListNoAccountsRegisteredButSignedIn() {
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    // Run test.
                    mSigninManager.reloadAllAccountsFromSystem(mTestAccount1.getId());

                    Assert.assertArrayEquals(
                            "No accounts available",
                            new CoreAccountInfo[] {},
                            mIdentityManager.getAccountsWithRefreshTokens());
                });
    }

    @Test
    @MediumTest
    @EnableFeatures(SigninFeatures.SEED_ACCOUNTS_REVAMP)
    @DisableFeatures(SigninFeatures.USE_CONSENT_LEVEL_SIGNIN_FOR_LEGACY_ACCOUNT_EMAIL_PREF)
    public void testPrimaryAccountRemoval_signsOut() {
        mSigninTestRule.addAccount(TEST_ACCOUNT1);
        SigninTestUtil.signinAndEnableSync(mTestAccount1, null);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertEquals(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail(),
                            mTestAccount1.getEmail());
                });

        mSigninTestRule.removeAccount(mTestAccount1.getId());

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertNull(mIdentityManager.getPrimaryAccountInfo(ConsentLevel.SIGNIN));
                    assertNull(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail());
                });
    }

    @Test
    @MediumTest
    @EnableFeatures(SigninFeatures.SEED_ACCOUNTS_REVAMP)
    @DisableFeatures(SigninFeatures.USE_CONSENT_LEVEL_SIGNIN_FOR_LEGACY_ACCOUNT_EMAIL_PREF)
    public void testPrimaryAccountRenaming_updatesLegacySyncAccountEmail() {
        mSigninTestRule.addAccount(TEST_ACCOUNT1);
        SigninTestUtil.signinAndEnableSync(mTestAccount1, null);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertEquals(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail(),
                            mTestAccount1.getEmail());
                });

        AccountInfo renamedAccount =
                new AccountInfo.Builder("renamed@gmail.com", mTestAccount1.getGaiaId()).build();
        try (var ignored = mSigninTestRule.blockGetCoreAccountInfosUpdate(true)) {
            mSigninTestRule.removeAccount(mTestAccount1.getId());
            mSigninTestRule.addAccount(renamedAccount);
        }

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertEquals(
                            mIdentityManager.getPrimaryAccountInfo(ConsentLevel.SYNC).getEmail(),
                            renamedAccount.getEmail());
                    assertEquals(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail(),
                            renamedAccount.getEmail());
                });
    }

    @Test
    @MediumTest
    @EnableFeatures({
        SigninFeatures.SEED_ACCOUNTS_REVAMP,
        SigninFeatures.USE_CONSENT_LEVEL_SIGNIN_FOR_LEGACY_ACCOUNT_EMAIL_PREF
    })
    public void testSignInAndSignOut_updateLegacySyncAccountEmail() {
        mSigninTestRule.addAccountThenSignin(SigninTestRule.TEST_ACCOUNT_1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertEquals(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail(),
                            SigninTestRule.TEST_ACCOUNT_1.getEmail());
                });

        mSigninTestRule.signOut();

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertNull(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail());
                });
    }

    @Test
    @MediumTest
    @EnableFeatures({
        SigninFeatures.SEED_ACCOUNTS_REVAMP,
        SigninFeatures.USE_CONSENT_LEVEL_SIGNIN_FOR_LEGACY_ACCOUNT_EMAIL_PREF
    })
    public void testPrimaryAccountRenaming_updatesLegacySyncAccountEmail_whenSignedIn() {
        mSigninTestRule.addAccountThenSignin(SigninTestRule.TEST_ACCOUNT_1);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertEquals(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail(),
                            SigninTestRule.TEST_ACCOUNT_1.getEmail());
                });

        AccountInfo renamedAccount =
                new AccountInfo.Builder(
                                "renamed@gmail.com", SigninTestRule.TEST_ACCOUNT_1.getGaiaId())
                        .build();

        try (var ignored = mSigninTestRule.blockGetCoreAccountInfosUpdate(true)) {
            mSigninTestRule.removeAccount(SigninTestRule.TEST_ACCOUNT_1.getId());
            mSigninTestRule.addAccount(renamedAccount);
        }

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertEquals(
                            mIdentityManager.getPrimaryAccountInfo(ConsentLevel.SIGNIN).getEmail(),
                            renamedAccount.getEmail());
                    assertEquals(
                            SigninPreferencesManager.getInstance().getLegacyPrimaryAccountEmail(),
                            renamedAccount.getEmail());
                });
    }

    @Test
    @MediumTest
    public void testClearPrimaryAccountWithSyncNotEnabled_signsOut() {
        // Add accounts.
        mSigninTestRule.addTestAccountThenSignin();

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertTrue(mIdentityManager.hasPrimaryAccount(ConsentLevel.SIGNIN));

                    // Run test.
                    mSigninManager.signOut(SignoutReason.TEST);

                    // Check the account is signed out
                    Assert.assertFalse(mIdentityManager.hasPrimaryAccount(ConsentLevel.SIGNIN));
                });

        // Wait for the operation to have completed - the revokeSyncConsent processing calls back
        // SigninManager, and if we don't wait for this to complete before test teardown then we
        // can hit a race condition where this async processing overlaps with the signout causing
        // teardown to fail.
        verify(mSignInStateObserverMock, timeout(CriteriaHelper.DEFAULT_MAX_TIME_TO_POLL).times(1))
                .onSignedOut();
    }

    @Test
    @MediumTest
    public void testClearPrimaryAccountWithSyncEnabled_signsOut() {
        // Add accounts.
        mSigninTestRule.addTestAccountThenSigninAndEnableSync();

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertTrue(mIdentityManager.hasPrimaryAccount(ConsentLevel.SYNC));

                    // Run test.
                    mSigninManager.signOut(SignoutReason.TEST);

                    Assert.assertFalse(mIdentityManager.hasPrimaryAccount(ConsentLevel.SIGNIN));
                });

        // Wait for the operation to have completed - the revokeSyncConsent processing calls back
        // SigninManager, and if we don't wait for this to complete before test teardown then we
        // can hit a race condition where this async processing overlaps with the signout causing
        // teardown to fail.
        verify(mSignInStateObserverMock, timeout(CriteriaHelper.DEFAULT_MAX_TIME_TO_POLL).times(1))
                .onSignedOut();
    }

    @Test
    @MediumTest
    public void testRevokeSyncConsent_disablesSync() {
        // Add account.
        mSigninTestRule.addTestAccountThenSigninAndEnableSync();

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertTrue(mIdentityManager.hasPrimaryAccount(ConsentLevel.SYNC));

                    // Run test.
                    mSigninManager.revokeSyncConsent(SignoutReason.TEST, null, false);

                    Assert.assertFalse(mIdentityManager.hasPrimaryAccount(ConsentLevel.SYNC));
                    Assert.assertTrue(mIdentityManager.hasPrimaryAccount(ConsentLevel.SIGNIN));
                });

        // Wait for the operation to have completed - the revokeSyncConsent processing calls back
        // SigninManager, and if we don't wait for this to complete before test teardown then we
        // can hit a race condition where this async processing overlaps with the signout causing
        // teardown to fail.
        verify(mSignInStateObserverMock, timeout(CriteriaHelper.DEFAULT_MAX_TIME_TO_POLL).times(1))
                .onSignedOut();
    }

    @Test
    @MediumTest
    public void testSignInWithoutSync_waitForPrefCommit() {
        CoreAccountInfo coreAccountInfo =
                mSigninTestRule.addAccountAndWaitForSeeding(TEST_ACCOUNT1);
        SigninTestUtil.signinAndWaitForPrefsCommit(coreAccountInfo);

        Assert.assertTrue(mIdentityManager.hasPrimaryAccount(ConsentLevel.SIGNIN));
        verify(mSignInStateObserverMock, timeout(CriteriaHelper.DEFAULT_MAX_TIME_TO_POLL).times(1))
                .onSignedIn();
    }
}
