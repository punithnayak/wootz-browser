// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/safety_hub/unused_site_permissions_service.h"

#include <cstdint>
#include <ctime>
#include <list>
#include <memory>

#include "base/functional/bind.h"
#include "base/functional/callback_forward.h"
#include "base/json/values_util.h"
#include "base/memory/scoped_refptr.h"
#include "base/run_loop.h"
#include "base/strings/string_number_conversions.h"
#include "base/test/metrics/histogram_tester.h"
#include "base/test/scoped_feature_list.h"
#include "base/test/simple_test_clock.h"
#include "base/time/clock.h"
#include "base/time/default_clock.h"
#include "base/time/time.h"
#include "base/values.h"
#include "chrome/browser/history/history_service_factory.h"
#include "chrome/browser/ui/safety_hub/safety_hub_service.h"
#include "chrome/browser/ui/safety_hub/safety_hub_test_util.h"
#include "chrome/browser/ui/safety_hub/unused_site_permissions_service_factory.h"
#include "chrome/common/chrome_features.h"
#include "chrome/test/base/chrome_render_view_host_test_harness.h"
#include "components/content_settings/core/browser/content_settings_utils.h"
#include "components/content_settings/core/browser/host_content_settings_map.h"
#include "components/content_settings/core/browser/website_settings_registry.h"
#include "components/content_settings/core/common/content_settings.h"
#include "components/content_settings/core/common/content_settings_constraints.h"
#include "components/content_settings/core/common/content_settings_pattern.h"
#include "components/content_settings/core/common/content_settings_types.h"
#include "components/content_settings/core/common/features.h"
#include "components/history/core/browser/history_database_params.h"
#include "components/history/core/browser/history_service.h"
#include "components/history/core/test/test_history_database.h"
#include "components/keyed_service/core/refcounted_keyed_service.h"
#include "components/permissions/constants.h"
#include "components/permissions/permission_uma_util.h"
#include "components/permissions/permission_util.h"
#include "components/permissions/pref_names.h"
#include "components/permissions/test/test_permissions_client.h"
#include "components/sync_preferences/testing_pref_service_syncable.h"
#include "components/ukm/content/source_url_recorder.h"
#include "components/ukm/test_ukm_recorder.h"
#include "services/metrics/public/cpp/ukm_builders.h"
#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace {

std::unique_ptr<KeyedService> BuildUnusedSitePermissionsService(
    content::BrowserContext* context) {
  return std::make_unique<UnusedSitePermissionsService>(
      context, Profile::FromBrowserContext(context)->GetPrefs());
}

scoped_refptr<RefcountedKeyedService> BuildTestHostContentSettingsMap(
    content::BrowserContext* context) {
  return base::MakeRefCounted<HostContentSettingsMap>(
      Profile::FromBrowserContext(context)->GetPrefs(), false, true, false,
      false);
}

std::unique_ptr<KeyedService> BuildTestHistoryService(
    content::BrowserContext* context) {
  auto service = std::make_unique<history::HistoryService>();
  service->Init(history::TestHistoryDatabaseParamsForPath(context->GetPath()));
  return service;
}

PermissionsData CreatePermissionsData(
    ContentSettingsPattern& origin,
    std::set<ContentSettingsType>& permission_types) {
  PermissionsData permissions_data;
  permissions_data.origin = origin;
  permissions_data.permission_types = permission_types;
  return permissions_data;
}

}  // namespace

class UnusedSitePermissionsServiceTest
    : public ChromeRenderViewHostTestHarness {
 public:
  UnusedSitePermissionsServiceTest() {
    feature_list_.InitWithFeatures(
        /*enabled_features=*/
        {content_settings::features::kSafetyCheckUnusedSitePermissions,
         content_settings::features::
             kSafetyCheckUnusedSitePermissionsForSupportedChooserPermissions,
         features::kSafetyHub},
        /*disabled_features=*/{});
  }
  void SetUp() override {
    ChromeRenderViewHostTestHarness::SetUp();
    base::Time time;
    ASSERT_TRUE(base::Time::FromString("2022-09-07 13:00", &time));
    clock_.SetNow(time);
    prefs()->SetBoolean(
        permissions::prefs::kUnusedSitePermissionsRevocationEnabled, true);
    callback_count_ = 0;

    // The following lines also serve to first access and thus create the two
    // services.
    hcsm()->SetClockForTesting(&clock_);
    service()->SetClockForTesting(&clock_);
  }

  void TearDown() override {
    service()->SetClockForTesting(base::DefaultClock::GetInstance());
    hcsm()->SetClockForTesting(base::DefaultClock::GetInstance());

    // ~BrowserTaskEnvironment() will properly call Shutdown on the services.
    ChromeRenderViewHostTestHarness::TearDown();
  }

  TestingProfile::TestingFactories GetTestingFactories() const override {
    return {{HostContentSettingsMapFactory::GetInstance(),
             base::BindRepeating(&BuildTestHostContentSettingsMap)},
            // Needed for background UKM reporting.
            {HistoryServiceFactory::GetInstance(),
             base::BindRepeating(&BuildTestHistoryService)},
            {UnusedSitePermissionsServiceFactory::GetInstance(),
             base::BindRepeating(&BuildUnusedSitePermissionsService)}};
  }

  void ResetService() {
    // Setting the factory has the side effect of resetting the service
    // instance.
    UnusedSitePermissionsServiceFactory::GetInstance()->SetTestingFactory(
        profile(), base::BindRepeating(&BuildUnusedSitePermissionsService));
  }

  base::SimpleTestClock* clock() { return &clock_; }

  UnusedSitePermissionsService* service() {
    return UnusedSitePermissionsServiceFactory::GetForProfile(profile());
  }

  HostContentSettingsMap* hcsm() {
    return HostContentSettingsMapFactory::GetForProfile(profile());
  }

  sync_preferences::TestingPrefServiceSyncable* prefs() {
    return profile()->GetTestingPrefService();
  }

  uint8_t callback_count() { return callback_count_; }

  base::Time GetLastVisitedDate(GURL url, ContentSettingsType type) {
    content_settings::SettingInfo info;
    hcsm()->GetWebsiteSetting(url, url, type, &info);
    return info.metadata.last_visited();
  }

  ContentSettingsForOneType GetRevokedUnusedPermissions(
      HostContentSettingsMap* hcsm) {
    return hcsm->GetSettingsForOneType(
        ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  }

  base::Value::List GetRevokedPermissionsForOneOrigin(
      HostContentSettingsMap* hcsm,
      const GURL& url) {
    base::Value setting_value(hcsm->GetWebsiteSetting(
        url, url, ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
        nullptr));

    base::Value::List permissions_list;
    if (!setting_value.is_dict() ||
        !setting_value.GetDict().FindList(permissions::kRevokedKey)) {
      return permissions_list;
    }

    permissions_list =
        std::move(*setting_value.GetDict().FindList(permissions::kRevokedKey));

    return permissions_list;
  }

 private:
  base::SimpleTestClock clock_;
  uint8_t callback_count_;
  base::test::ScopedFeatureList feature_list_;
};

TEST_F(UnusedSitePermissionsServiceTest, UnusedSitePermissionsServiceTest) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);
  ukm::InitializeSourceUrlRecorderForWebContents(web_contents());

  const GURL url1("https://example1.com");
  const GURL url2("https://example2.com");
  const ContentSettingsType type1 = ContentSettingsType::GEOLOCATION;
  const ContentSettingsType type2 = ContentSettingsType::MEDIASTREAM_CAMERA;
  const ContentSettingsType type3 =
      ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA;
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  const base::Time now = clock()->Now();
  const base::TimeDelta precision =
      content_settings::GetCoarseVisitedTimePrecision();

  auto* history_service = HistoryServiceFactory::GetForProfile(
      profile(), ServiceAccessType::EXPLICIT_ACCESS);
  history_service->AddPage(url1, clock()->Now(),
                           history::VisitSource::SOURCE_BROWSED);
  // Add one content setting for `url1` and two content settings +
  // one website setting for `url2`.
  hcsm()->SetContentSettingDefaultScope(
      url1, url1, type1, ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetContentSettingDefaultScope(
      url2, url2, type1, ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetContentSettingDefaultScope(
      url2, url2, type2, ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetWebsiteSettingDefaultScope(
      url2, url2, type3, base::Value(base::Value::Dict().Set("foo", "bar")),
      constraint);

  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 0u);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel through time for 20 days.
  clock()->Advance(base::Days(20));
  base::Time future = clock()->Now();

  // The old settings should now be tracked as unused.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 4u);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);
  // Visit `url2` and check that the corresponding content setting got updated.
  UnusedSitePermissionsService::TabHelper::CreateForWebContents(web_contents(),
                                                                service());
  NavigateAndCommit(url2);
  EXPECT_LE(GetLastVisitedDate(url1, type1), now);
  EXPECT_GE(GetLastVisitedDate(url1, type1), now - precision);
  EXPECT_LE(GetLastVisitedDate(url2, type1), future);
  EXPECT_GE(GetLastVisitedDate(url2, type1), future - precision);
  EXPECT_LE(GetLastVisitedDate(url2, type2), future);
  EXPECT_GE(GetLastVisitedDate(url2, type2), future - precision);
  EXPECT_LE(GetLastVisitedDate(url2, type3), future);
  EXPECT_GE(GetLastVisitedDate(url2, type3), future - precision);

  // Check that the service is only tracking one entry now.
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 1u);

  // Travel through time for 50 days to make permissions be revoked.
  clock()->Advance(base::Days(50));

  // Unused permissions should be auto revoked.
  base::HistogramTester histogram_tester;
  ukm::TestAutoSetUkmRecorder ukm_recorder;
  base::RunLoop wait_for_ukm_loop;
  ukm_recorder.SetOnAddEntryCallback(ukm::builders::Permission::kEntryName,
                                     wait_for_ukm_loop.QuitClosure());

  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());

  // url2 should be on tracked permissions list.
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 3u);
  std::string url2_str =
      ContentSettingsPattern::FromURLNoWildcard(url2).ToString();
  EXPECT_EQ(url2_str, service()
                          ->GetTrackedUnusedPermissionsForTesting()[0]
                          .source.primary_pattern.ToString());
  EXPECT_EQ(url2_str, service()
                          ->GetTrackedUnusedPermissionsForTesting()[1]
                          .source.primary_pattern.ToString());
  EXPECT_EQ(url2_str, service()
                          ->GetTrackedUnusedPermissionsForTesting()[2]
                          .source.primary_pattern.ToString());
  // `url1` should be on revoked permissions list.
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 1u);
  std::string url1_str =
      ContentSettingsPattern::FromURLNoWildcard(url1).ToString();
  EXPECT_EQ(url1_str,
            GetRevokedUnusedPermissions(hcsm())[0].primary_pattern.ToString());

  // Revocation related histograms should be recorded for the revoked
  // geolocation grant, but nothing for other permission types.
  histogram_tester.ExpectUniqueSample(
      "Permissions.Action.Geolocation",
      static_cast<int>(permissions::PermissionAction::REVOKED), 1);
  histogram_tester.ExpectTotalCount(
      "Permissions.Revocation.ElapsedTimeSinceGrant.Geolocation", 1);
  EXPECT_THAT(histogram_tester.GetTotalCountsForPrefix("Permissions.Action."),
              testing::SizeIs(1));
  EXPECT_THAT(histogram_tester.GetTotalCountsForPrefix(
                  "Permissions.Revocation.ElapsedTimeSinceGrant."),
              testing::SizeIs(1));

  // Revocation UKM events should be emitted as well, and it takes a round
  // trip to the HistoryService, so wait for it.
  wait_for_ukm_loop.Run();

  auto entries = ukm_recorder.GetEntriesByName("Permission");
  ASSERT_EQ(1u, entries.size());
  ukm_recorder.ExpectEntrySourceHasUrl(entries[0], url1);
  ukm_recorder.ExpectEntryMetric(
      entries[0], "Source",
      static_cast<int64_t>(
          permissions::PermissionSourceUI::SAFETY_HUB_AUTO_REVOCATION));
}

TEST_F(UnusedSitePermissionsServiceTest, TrackOnlySingleOriginTest) {
  const GURL url1("https://example1.com");
  const GURL url2("https://[*.]example2.com");
  const GURL url3("file:///foo/bar.txt");
  const ContentSettingsType type = ContentSettingsType::GEOLOCATION;
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  // Add one setting for all urls.
  hcsm()->SetContentSettingDefaultScope(
      url1, url1, type, ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetContentSettingDefaultScope(
      url2, url2, type, ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetContentSettingDefaultScope(
      url2, url3, type, ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 0u);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel through time for 20 days.
  clock()->Advance(base::Days(20));

  // Only `url1` should be tracked because it is the only single origin url.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 1u);
  auto tracked_origin = service()->GetTrackedUnusedPermissionsForTesting()[0];
  EXPECT_EQ(GURL(tracked_origin.source.primary_pattern.ToString()), url1);
}

TEST_F(UnusedSitePermissionsServiceTest, TrackUnusedButDontRevoke) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const GURL url("https://example1.com");
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  // Grant GEOLOCATION permission for the url.
  hcsm()->SetContentSettingDefaultScope(
      url, url, ContentSettingsType::GEOLOCATION,
      ContentSetting::CONTENT_SETTING_BLOCK, constraint);

  // Travel through time for 20 days.
  clock()->Advance(base::Days(20));

  // GEOLOCATION permission should be on the tracked unused site permissions
  // list as it is denied 20 days before. The permission is not suitable for
  // revocation and this test verifies that RevokeUnusedPermissions() does not
  // enter infinite loop in such case.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  auto unused_permissions = service()->GetTrackedUnusedPermissionsForTesting();
  ASSERT_EQ(unused_permissions.size(), 1u);
  EXPECT_EQ(unused_permissions[0].type, ContentSettingsType::GEOLOCATION);
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url).size(), 0u);
}

TEST_F(UnusedSitePermissionsServiceTest, SecondaryPatternAlwaysWildcard) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const ContentSettingsType types[] = {
      ContentSettingsType::GEOLOCATION,
      ContentSettingsType::AUTOMATIC_DOWNLOADS};
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  // Test combinations of a single origin |primary_pattern| and different
  // |secondary_pattern|s: equal to primary pattern, different single origin
  // pattern, with domain with wildcard, wildcard.
  for (const auto type : types) {
    hcsm()->SetContentSettingDefaultScope(
        GURL("https://example1.com"), GURL("https://example1.com"), type,
        ContentSetting::CONTENT_SETTING_ALLOW, constraint);
    hcsm()->SetContentSettingDefaultScope(
        GURL("https://example2.com"), GURL("https://example3.com"), type,
        ContentSetting::CONTENT_SETTING_ALLOW, constraint);
    hcsm()->SetContentSettingDefaultScope(
        GURL("https://example3.com"), GURL("https://[*.]example1.com"), type,
        ContentSetting::CONTENT_SETTING_ALLOW, constraint);
    hcsm()->SetContentSettingDefaultScope(
        GURL("https://example4.com"), GURL("*"), type,
        ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  }

  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel through time for 70 days so that permissions are revoked.
  clock()->Advance(base::Days(70));
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());

  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 4u);
  for (auto unused_permission : GetRevokedUnusedPermissions(hcsm())) {
    EXPECT_EQ(unused_permission.secondary_pattern,
              ContentSettingsPattern::Wildcard());
  }
}

TEST_F(UnusedSitePermissionsServiceTest, MultipleRevocationsForSameOrigin) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const GURL url("https://example1.com");
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  // Grant GEOLOCATION permission for the url.
  hcsm()->SetContentSettingDefaultScope(
      url, url, ContentSettingsType::GEOLOCATION,
      ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 0u);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel through time for 20 days.
  clock()->Advance(base::Days(20));

  // Grant MEDIASTREAM_CAMERA permission for the url.
  hcsm()->SetContentSettingDefaultScope(
      url, url, ContentSettingsType::MEDIASTREAM_CAMERA,
      ContentSetting::CONTENT_SETTING_ALLOW, constraint);

  // GEOLOCATION permission should be on the tracked unused site permissions
  // list as it is granted 20 days before. MEDIASTREAM_CAMERA permission should
  // not be tracked as it is just granted.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 1u);
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting()[0].type,
            ContentSettingsType::GEOLOCATION);

  // Travel through time for 50 days.
  clock()->Advance(base::Days(50));

  // GEOLOCATION permission should be on the revoked permissions list as it is
  // granted 70 days before. MEDIASTREAM_CAMERA permission should be on the
  // recently unused permissions list as it is granted 50 days before.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url).size(), 1u);
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url)[0].GetInt(),
            static_cast<int32_t>(ContentSettingsType::GEOLOCATION));
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 1u);
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting()[0].type,
            ContentSettingsType::MEDIASTREAM_CAMERA);
}

// TODO(crbug.com/40928115): Flaky on all platforms.
TEST_F(UnusedSitePermissionsServiceTest,
       DISABLED_ClearRevokedPermissionsListAfter30d) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const GURL url("https://example1.com");
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  hcsm()->SetContentSettingDefaultScope(
      url, url, ContentSettingsType::MEDIASTREAM_CAMERA,
      ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetContentSettingDefaultScope(
      url, url, ContentSettingsType::GEOLOCATION,
      ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetWebsiteSettingDefaultScope(
      url, url, ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA,
      base::Value(base::Value::Dict().Set("foo", "bar")), constraint);

  // Travel through time for 70 days.
  clock()->Advance(base::Days(70));

  // Both GEOLOCATION and MEDIASTREAM_CAMERA permissions should be on the
  // revoked permissions list as they are granted more than 60 days before.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url).size(), 3u);
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url)[0].GetInt(),
            static_cast<int32_t>(ContentSettingsType::GEOLOCATION));
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url)[1].GetInt(),
            static_cast<int32_t>(ContentSettingsType::MEDIASTREAM_CAMERA));
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url)[2].GetInt(),
            static_cast<int32_t>(
                ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA));

  // Travel through time for 30 days.
  clock()->Advance(base::Days(30));

  // No permission should be on the revoked permissions list as they are revoked
  // more than 30 days before.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url).size(), 0u);
}

TEST_F(UnusedSitePermissionsServiceTest, RegrantPermissionsForOrigin) {
  const std::string url1 = "https://example1.com:443";
  const std::string url2 = "https://example2.com:443";
  const ContentSettingsType regular_type = ContentSettingsType::GEOLOCATION;
  const ContentSettingsType chooser_type =
      ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA;

  // `REVOKED_UNUSED_SITE_PERMISSIONS` stores base::Value::Dict with two keys:
  // (1) key for an int list of revoked permission types
  // (2) key for a dictionary, which key is a string permission type, mapped to
  //     its revoked permission data in base::Value (i.e. {"foo": "bar"})
  // {
  //  "revoked": [3, 8, ... ],
  //  "revoked-chooser-permissions": {"8": {"foo": "bar"}}
  // }
  auto dict =
      base::Value::Dict()
          .Set(permissions::kRevokedKey,
               base::Value::List()
                   .Append(static_cast<int32_t>(regular_type))
                   .Append(static_cast<int32_t>(chooser_type)))
          .Set(permissions::kRevokedChooserPermissionsKey,
               base::Value::Dict().Set(
                   base::NumberToString(static_cast<int32_t>(chooser_type)),
                   base::Value(base::Value::Dict().Set("foo", "bar"))));

  // Add `url1` and `url2` to revoked permissions list.
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url1), GURL(url1),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url2), GURL(url2),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));

  // Check there are 2 origin in revoked permissions list.
  ContentSettingsForOneType revoked_permissions_list =
      hcsm()->GetSettingsForOneType(
          ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(2U, revoked_permissions_list.size());

  // Allow the permission for `url1` again
  service()->RegrantPermissionsForOrigin(url::Origin::Create(GURL(url1)));

  // Check there is only `url2` in revoked permissions list.
  revoked_permissions_list = hcsm()->GetSettingsForOneType(
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(1U, revoked_permissions_list.size());

  // Check if the permissions of `url1` is regranted.
  EXPECT_EQ(ContentSetting::CONTENT_SETTING_ALLOW,
            hcsm()->GetContentSetting(GURL(url1), GURL(url1), regular_type));
  EXPECT_EQ(base::Value::Dict().Set("foo", "bar"),
            hcsm()->GetWebsiteSetting(GURL(url1), GURL(url1), chooser_type));

  // Undoing the changes should add `url1` back to the list of revoked
  // permissions and reset its permissions.
  PermissionsData permissions_data;
  permissions_data.origin =
      ContentSettingsPattern::FromURLNoWildcard(GURL(url1));
  permissions_data.permission_types = {regular_type, chooser_type};
  permissions_data.chooser_permissions_data = base::Value::Dict().Set(
      base::NumberToString(static_cast<int32_t>(chooser_type)),
      base::Value::Dict().Set("foo", "bar"));
  service()->UndoRegrantPermissionsForOrigin(permissions_data);

  revoked_permissions_list = hcsm()->GetSettingsForOneType(
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(2U, revoked_permissions_list.size());
  EXPECT_EQ(ContentSetting::CONTENT_SETTING_ASK,
            hcsm()->GetContentSetting(GURL(url1), GURL(url1), regular_type));
  EXPECT_EQ(base::Value(),
            hcsm()->GetWebsiteSetting(GURL(url1), GURL(url1), chooser_type));
}

TEST_F(UnusedSitePermissionsServiceTest, RegrantPreventsAutorevoke) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const GURL url1 = GURL("https://example1.com:443");
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  hcsm()->SetContentSettingDefaultScope(
      url1, url1, ContentSettingsType::GEOLOCATION,
      ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel 70 days through time so that the granted permission is revoked.
  clock()->Advance(base::Days(70));
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 1u);

  // After regranting permissions they are not revoked again even after >60 days
  // pass.
  service()->RegrantPermissionsForOrigin(url::Origin::Create(url1));
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  clock()->Advance(base::Days(70));
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);
}

TEST_F(UnusedSitePermissionsServiceTest, UndoRegrantPermissionsForOrigin) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const GURL url1 = GURL("https://example1.com:443");
  const ContentSettingsType regular_type = ContentSettingsType::GEOLOCATION;
  const ContentSettingsType chooser_type =
      ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA;
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  hcsm()->SetContentSettingDefaultScope(url1, url1, regular_type,
                                        ContentSetting::CONTENT_SETTING_ALLOW,
                                        constraint);
  hcsm()->SetWebsiteSettingDefaultScope(
      url1, url1, chooser_type,
      base::Value(base::Value::Dict().Set("foo", "bar")), constraint);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel 70 days through time so that the granted permission is revoked.
  clock()->Advance(base::Days(70));
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 1u);
  const ContentSettingPatternSource revoked_permission =
      GetRevokedUnusedPermissions(hcsm())[0];

  // Permission remains revoked after regrant and undo.
  service()->RegrantPermissionsForOrigin(url::Origin::Create(url1));
  PermissionsData permissions_data;
  permissions_data.origin = ContentSettingsPattern::FromURLNoWildcard(url1);
  permissions_data.permission_types = {regular_type, chooser_type};
  permissions_data.chooser_permissions_data = base::Value::Dict().Set(
      base::NumberToString(static_cast<int32_t>(chooser_type)),
      base::Value::Dict().Set("foo", "bar"));
  permissions_data.constraints = content_settings::ContentSettingConstraints(
      revoked_permission.metadata.expiration() -
      revoked_permission.metadata.lifetime());
  permissions_data.constraints.set_lifetime(
      revoked_permission.metadata.lifetime());
  service()->UndoRegrantPermissionsForOrigin(permissions_data);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 1u);

  // Revoked permission is cleaned up after >30 days.
  clock()->Advance(base::Days(40));
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // If that permission is granted again, it will still be autorevoked.
  hcsm()->SetContentSettingDefaultScope(url1, url1, regular_type,
                                        ContentSetting::CONTENT_SETTING_ALLOW,
                                        constraint);
  hcsm()->SetWebsiteSettingDefaultScope(
      url1, url1, chooser_type,
      base::Value(base::Value::Dict().Set("foo", "bar")), constraint);
  clock()->Advance(base::Days(70));
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 1u);
}

TEST_F(UnusedSitePermissionsServiceTest, NotRevokeNotificationPermission) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitAndEnableFeature(
      content_settings::features::kSafetyCheckUnusedSitePermissions);

  const GURL url("https://example1.com");
  content_settings::ContentSettingConstraints constraint;
  constraint.set_track_last_visit_for_autoexpiration(true);

  // Grant GEOLOCATION and NOTIFICATION permission for the url.
  hcsm()->SetContentSettingDefaultScope(
      url, url, ContentSettingsType::GEOLOCATION,
      ContentSetting::CONTENT_SETTING_ALLOW, constraint);
  hcsm()->SetContentSettingDefaultScope(url, url,
                                        ContentSettingsType::NOTIFICATIONS,
                                        ContentSetting::CONTENT_SETTING_ALLOW);
  EXPECT_EQ(service()->GetTrackedUnusedPermissionsForTesting().size(), 0u);
  EXPECT_EQ(GetRevokedUnusedPermissions(hcsm()).size(), 0u);

  // Travel through time for 70 days.
  clock()->Advance(base::Days(70));

  // GEOLOCATION permission should be on the revoked permissions list, but
  // NOTIFICATION permissions should not be as notification permissions are out
  // of scope.
  safety_hub_test_util::UpdateSafetyHubServiceAsync(service());
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url).size(), 1u);
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url)[0].GetInt(),
            static_cast<int32_t>(ContentSettingsType::GEOLOCATION));

  // Clearing revoked permissions list should delete unused GEOLOCATION from it
  // but leave used NOTIFICATION permissions intact.
  service()->ClearRevokedPermissionsList();
  EXPECT_EQ(GetRevokedPermissionsForOneOrigin(hcsm(), url).size(), 0u);
  EXPECT_EQ(hcsm()->GetContentSetting(GURL(url), GURL(url),
                                      ContentSettingsType::GEOLOCATION),
            ContentSetting::CONTENT_SETTING_ASK);
  EXPECT_EQ(hcsm()->GetContentSetting(GURL(url), GURL(url),
                                      ContentSettingsType::NOTIFICATIONS),
            ContentSetting::CONTENT_SETTING_ALLOW);
}

TEST_F(UnusedSitePermissionsServiceTest, ClearRevokedPermissionsList) {
  const std::string url1 = "https://example1.com:443";
  const std::string url2 = "https://example2.com:443";
  const ContentSettingsType regular_type = ContentSettingsType::GEOLOCATION;
  const ContentSettingsType chooser_type =
      ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA;

  // `REVOKED_UNUSED_SITE_PERMISSIONS` stores base::Value::Dict with two keys:
  // (1) key for an int list of revoked permission types
  // (2) key for a dictionary, which key is a string permission type, mapped to
  //     its revoked permission data in base::Value (i.e. {"foo": "bar"})
  // {
  //  "revoked": [3, 8, ... ],
  //  "revoked-chooser-permissions": {"8": {"foo": "bar"}}
  // }
  auto dict =
      base::Value::Dict()
          .Set(permissions::kRevokedKey,
               base::Value::List()
                   .Append(static_cast<int32_t>(regular_type))
                   .Append(static_cast<int32_t>(chooser_type)))
          .Set(permissions::kRevokedChooserPermissionsKey,
               base::Value::Dict().Set(
                   base::NumberToString(static_cast<int32_t>(chooser_type)),
                   base::Value(base::Value::Dict().Set("foo", "bar"))));

  // Add `url1` and `url2` to revoked permissions list.
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url1), GURL(url1),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url2), GURL(url2),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));

  // Check there are 2 origins in the revoked permissions list.
  ContentSettingsForOneType revoked_permissions_list =
      hcsm()->GetSettingsForOneType(
          ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(2U, revoked_permissions_list.size());

  service()->ClearRevokedPermissionsList();

  // Revoked permissions list should be empty.
  revoked_permissions_list = hcsm()->GetSettingsForOneType(
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(revoked_permissions_list.size(), 0U);
}

TEST_F(UnusedSitePermissionsServiceTest, RecordRegrantMetricForAllowAgain) {
  const std::string url = "https://example.com:443";
  auto dict = base::Value::Dict().Set(
      permissions::kRevokedKey, base::Value::List().Append(static_cast<int32_t>(
                                    ContentSettingsType::GEOLOCATION)));

  auto cleanUpThreshold =
      content_settings::features::
          kSafetyCheckUnusedSitePermissionsRevocationCleanUpThreshold.Get();
  content_settings::ContentSettingConstraints constraint(clock()->Now());
  constraint.set_lifetime(cleanUpThreshold);

  // Add `url` to revoked permissions list.
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url), GURL(url),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()), constraint);

  // Assert there is 1 origin in revoked permissions list.
  ContentSettingsForOneType revoked_permissions_list =
      hcsm()->GetSettingsForOneType(
          ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  ASSERT_EQ(1U, revoked_permissions_list.size());

  // Advance 14 days; this will be the expected histogram sample.
  clock()->Advance(base::Days(14));
  base::HistogramTester histogram_tester;

  // Allow the permission for `url` again
  service()->RegrantPermissionsForOrigin(url::Origin::Create(GURL(url)));

  // Only a single entry should be recorded in the histogram.
  const std::vector<base::Bucket> buckets = histogram_tester.GetAllSamples(
      "Settings.SafetyCheck.UnusedSitePermissionsAllowAgainDays");
  EXPECT_EQ(1U, buckets.size());
  // The recorded metric should be the elapsed days since the revocation.
  histogram_tester.ExpectUniqueSample(
      "Settings.SafetyCheck.UnusedSitePermissionsAllowAgainDays", 14, 1);
}

TEST_F(UnusedSitePermissionsServiceTest,
       RemoveSiteFromRevokedPermissionsListOnPermissionChange) {
  const GURL url1 = GURL("https://example1.com:443");
  const GURL url2 = GURL("https://example2.com:443");
  const ContentSettingsType regular_type = ContentSettingsType::GEOLOCATION;
  const ContentSettingsType chooser_type =
      ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA;

  // `REVOKED_UNUSED_SITE_PERMISSIONS` stores base::Value::Dict with two keys:
  // (1) key for an int list of revoked permission types
  // (2) key for a dictionary, which key is a string permission type, mapped to
  //     its revoked permission data in base::Value (i.e. {"foo": "bar"})
  // {
  //  "revoked": [3, 8, ... ],
  //  "revoked-chooser-permissions": {"8": {"foo": "bar"}}
  // }
  auto dict =
      base::Value::Dict()
          .Set(permissions::kRevokedKey,
               base::Value::List()
                   .Append(static_cast<int32_t>(regular_type))
                   .Append(static_cast<int32_t>(chooser_type)))
          .Set(permissions::kRevokedChooserPermissionsKey,
               base::Value::Dict().Set(
                   base::NumberToString(static_cast<int32_t>(chooser_type)),
                   base::Value(base::Value::Dict().Set("foo", "bar"))));

  // Add url1 and url2 to revoked permissions list.
  hcsm()->SetWebsiteSettingDefaultScope(
      url1, url1, ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));
  hcsm()->SetWebsiteSettingDefaultScope(
      url2, url2, ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));

  ContentSettingsForOneType revoked_permissions_list =
      hcsm()->GetSettingsForOneType(
          ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);

  EXPECT_EQ(2U, revoked_permissions_list.size());

  // For a site where permissions have been revoked, granting a revoked
  // permission again will remove the site from the list.
  hcsm()->SetContentSettingDefaultScope(
      url1, GURL(), ContentSettingsType::GEOLOCATION, CONTENT_SETTING_ALLOW);
  // Check there is only url2 in revoked permissions list.
  revoked_permissions_list = hcsm()->GetSettingsForOneType(
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(1U, revoked_permissions_list.size());

  // Grant the revoked chooser permissions again from url2, and check that
  // the revoked permission list is empty.
  hcsm()->SetWebsiteSettingDefaultScope(
      url2, GURL(), ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA,
      base::Value(base::Value::Dict().Set("foo", "baz")));
  revoked_permissions_list = hcsm()->GetSettingsForOneType(
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS);
  EXPECT_EQ(0U, revoked_permissions_list.size());
}

TEST_F(UnusedSitePermissionsServiceTest, InitializeLatestResult) {
  const std::string url1 = "https://example1.com:443";
  const std::string url2 = "https://example2.com:443";
  const ContentSettingsType regular_type = ContentSettingsType::GEOLOCATION;
  const ContentSettingsType chooser_type =
      ContentSettingsType::FILE_SYSTEM_ACCESS_CHOOSER_DATA;

  // `REVOKED_UNUSED_SITE_PERMISSIONS` stores base::Value::Dict with two keys:
  // (1) key for an int list of revoked permission types
  // (2) key for a dictionary, which key is a string permission type, mapped to
  //     its revoked permission data in base::Value (i.e. {"foo": "bar"})
  // {
  //  "revoked": [3, 8, ... ],
  //  "revoked-chooser-permissions": {"8": {"foo": "bar"}}
  // }
  auto dict =
      base::Value::Dict()
          .Set(permissions::kRevokedKey,
               base::Value::List()
                   .Append(static_cast<int32_t>(regular_type))
                   .Append(static_cast<int32_t>(chooser_type)))
          .Set(permissions::kRevokedChooserPermissionsKey,
               base::Value::Dict().Set(
                   base::NumberToString(static_cast<int32_t>(chooser_type)),
                   base::Value(base::Value::Dict().Set("foo", "bar"))));

  // Add `url1` and `url2` to revoked permissions list.
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url1), GURL(url1),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));
  hcsm()->SetWebsiteSettingDefaultScope(
      GURL(url2), GURL(url2),
      ContentSettingsType::REVOKED_UNUSED_SITE_PERMISSIONS,
      base::Value(dict.Clone()));

  // When we start up a new service instance, the latest result (i.e. the list
  // of revoked permissions) should be immediately available.
  auto new_service = std::make_unique<UnusedSitePermissionsService>(
      profile(), profile()->GetPrefs());
  std::optional<std::unique_ptr<SafetyHubService::Result>> opt_result =
      new_service->GetCachedResult();
  EXPECT_TRUE(opt_result.has_value());
  auto* result =
      static_cast<UnusedSitePermissionsService::UnusedSitePermissionsResult*>(
          opt_result.value().get());
  EXPECT_EQ(2U, result->GetRevokedPermissions().size());
}

TEST_F(UnusedSitePermissionsServiceTest, ResultToFromDict) {
  const std::string url1 = "https://example1.com:443";
  auto origin = ContentSettingsPattern::FromString(url1);
  std::set<ContentSettingsType> permission_types(
      {ContentSettingsType::GEOLOCATION});
  auto result = std::make_unique<
      UnusedSitePermissionsService::UnusedSitePermissionsResult>();
  result->AddRevokedPermission(CreatePermissionsData(origin, permission_types));
  EXPECT_EQ(1U, result->GetRevokedPermissions().size());
  EXPECT_EQ(origin, result->GetRevokedPermissions().front().origin);

  // When converting to dict, the values of the revoked permissions should be
  // correctly converted to base::Value.
  base::Value::Dict dict = result->ToDictValue();
  auto* revoked_origins_list = dict.FindList(kUnusedSitePermissionsResultKey);
  EXPECT_EQ(1U, revoked_origins_list->size());
  EXPECT_EQ(url1, revoked_origins_list->front().GetString());
}

TEST_F(UnusedSitePermissionsServiceTest, ResultGetRevokedOrigins) {
  auto origin1 = ContentSettingsPattern::FromString("https://example1.com:443");
  auto origin2 = ContentSettingsPattern::FromString("https://example2.com:443");
  std::set<ContentSettingsType> permission_types(
      {ContentSettingsType::GEOLOCATION});
  auto result = std::make_unique<
      UnusedSitePermissionsService::UnusedSitePermissionsResult>();
  EXPECT_EQ(0U, result->GetRevokedOrigins().size());
  result->AddRevokedPermission(
      CreatePermissionsData(origin1, permission_types));
  EXPECT_EQ(1U, result->GetRevokedOrigins().size());
  EXPECT_EQ(origin1, *result->GetRevokedOrigins().begin());
  result->AddRevokedPermission(
      CreatePermissionsData(origin2, permission_types));
  EXPECT_EQ(2U, result->GetRevokedOrigins().size());
  EXPECT_TRUE(result->GetRevokedOrigins().contains(origin1));
  EXPECT_TRUE(result->GetRevokedOrigins().contains(origin2));
  permission_types = {ContentSettingsType::MEDIASTREAM_MIC};
  result->AddRevokedPermission(
      CreatePermissionsData(origin2, permission_types));
  EXPECT_EQ(2U, result->GetRevokedOrigins().size());
}

TEST_F(UnusedSitePermissionsServiceTest, ResultIsTriggerForMenuNotification) {
  auto origin = ContentSettingsPattern::FromString("https://example1.com:443");
  std::set<ContentSettingsType> permission_types(
      {ContentSettingsType::GEOLOCATION});
  auto result = std::make_unique<
      UnusedSitePermissionsService::UnusedSitePermissionsResult>();
  EXPECT_FALSE(result->IsTriggerForMenuNotification());
  result->AddRevokedPermission(CreatePermissionsData(origin, permission_types));
  EXPECT_TRUE(result->IsTriggerForMenuNotification());
}

TEST_F(UnusedSitePermissionsServiceTest, ResultWarrantsNewMenuNotification) {
  auto origin1 = ContentSettingsPattern::FromString("https://example1.com:443");
  auto origin2 = ContentSettingsPattern::FromString("https://example2.com:443");
  std::set<ContentSettingsType> permission_types(
      {ContentSettingsType::GEOLOCATION});
  auto old_result = std::make_unique<
      UnusedSitePermissionsService::UnusedSitePermissionsResult>();
  auto new_result = std::make_unique<
      UnusedSitePermissionsService::UnusedSitePermissionsResult>();
  EXPECT_FALSE(
      new_result->WarrantsNewMenuNotification(old_result->ToDictValue()));
  // origin1 revoked in new, but not in old -> warrants notification
  new_result->AddRevokedPermission(
      CreatePermissionsData(origin1, permission_types));
  EXPECT_TRUE(
      new_result->WarrantsNewMenuNotification(old_result->ToDictValue()));
  // origin1 in both new and old -> no notification
  old_result->AddRevokedPermission(
      CreatePermissionsData(origin1, permission_types));
  EXPECT_FALSE(
      new_result->WarrantsNewMenuNotification(old_result->ToDictValue()));
  // origin1 in both, origin2 in new -> warrants notification
  new_result->AddRevokedPermission(
      CreatePermissionsData(origin2, permission_types));
  EXPECT_TRUE(
      new_result->WarrantsNewMenuNotification(old_result->ToDictValue()));
  // origin1 and origin2 in both new and old -> no notification
  old_result->AddRevokedPermission(
      CreatePermissionsData(origin2, permission_types));
  EXPECT_FALSE(
      new_result->WarrantsNewMenuNotification(old_result->ToDictValue()));
}

TEST_F(UnusedSitePermissionsServiceTest, AutoRevocationSetting) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitWithFeatureStates(
      {{content_settings::features::kSafetyCheckUnusedSitePermissions, false}});

  // When auto-revocation is on, the timer is started by
  // StartRepeatedUpdates() on start-up.
  ResetService();
  EXPECT_TRUE(service()->IsTimerRunningForTesting());

  // Disable auto-revocation by setting kUnusedSitePermissionsRevocationEnabled
  // pref to false. This should stop the repeated timer.
  prefs()->SetBoolean(
      permissions::prefs::kUnusedSitePermissionsRevocationEnabled, false);
  EXPECT_FALSE(service()->IsTimerRunningForTesting());

  // Reset the service so auto-revocation is off on the service creation. The
  // repeated timer is not started on service creation in this case.
  ResetService();
  EXPECT_FALSE(service()->IsTimerRunningForTesting());

  // Enable auto-revocation by setting kUnusedSitePermissionsRevocationEnabled
  // pref to true. This should restart the repeated timer.
  prefs()->SetBoolean(
      permissions::prefs::kUnusedSitePermissionsRevocationEnabled, true);
  EXPECT_TRUE(service()->IsTimerRunningForTesting());
}

class UnusedSitePermissionsServiceSafetyHubDisabledTest
    : public ChromeRenderViewHostTestHarness {
 public:
  UnusedSitePermissionsServiceSafetyHubDisabledTest() {
    feature_list_.InitWithFeatures(
        /*enabled_features=*/{},
        /*disabled_features=*/
        {features::kSafetyHub});
  }
  void SetUp() override {
    ChromeRenderViewHostTestHarness::SetUp();
    HostContentSettingsMap::RegisterProfilePrefs(prefs_.registry());
    permissions::RegisterProfilePrefs(prefs_.registry());
    hcsm_ = base::MakeRefCounted<HostContentSettingsMap>(&prefs_, false, true,
                                                         false, false);
    service_ = std::make_unique<UnusedSitePermissionsService>(
        profile(), profile()->GetPrefs());
    callback_count_ = 0;
  }

  void TearDown() override {
    service_->Shutdown();
    service_.reset();
    hcsm_->ShutdownOnUIThread();
    base::RunLoop().RunUntilIdle();
    ChromeRenderViewHostTestHarness::TearDown();
  }

  void ResetService() {
    service_ = std::make_unique<UnusedSitePermissionsService>(
        profile(), profile()->GetPrefs());
  }

  UnusedSitePermissionsService* service() { return service_.get(); }

  sync_preferences::TestingPrefServiceSyncable* prefs() { return &prefs_; }

  uint8_t callback_count() { return callback_count_; }

 private:
  sync_preferences::TestingPrefServiceSyncable prefs_;
  std::unique_ptr<UnusedSitePermissionsService> service_;
  scoped_refptr<HostContentSettingsMap> hcsm_;
  uint8_t callback_count_;
  base::test::ScopedFeatureList feature_list_;
};

TEST_F(UnusedSitePermissionsServiceSafetyHubDisabledTest,
       UnusedSitePermissionsRevocationEnabled) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitWithFeatureStates(
      {{content_settings::features::kSafetyCheckUnusedSitePermissions, true}});
  // If Safety Hub is disabled but kSafetyCheckUnusedSitePermissions is on,
  // auto-revocation still happens (i.e. the timer is started on start-up).
  ResetService();
  EXPECT_TRUE(service()->IsTimerRunningForTesting());
}

TEST_F(UnusedSitePermissionsServiceSafetyHubDisabledTest,
       UnusedSitePermissionsRevocationDisabled) {
  base::test::ScopedFeatureList scoped_feature;
  scoped_feature.InitWithFeatureStates(
      {{content_settings::features::kSafetyCheckUnusedSitePermissions, false}});

  // If both kSafetyHub and kSafetyCheckUnusedSitePermissions are disabled, then
  // no auto-revocation should happen (i.e. no repeated timers should start).
  ResetService();
  EXPECT_FALSE(service()->IsTimerRunningForTesting());
}
