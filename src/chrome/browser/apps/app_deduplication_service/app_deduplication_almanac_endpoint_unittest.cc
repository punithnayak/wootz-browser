// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/apps/app_deduplication_service/app_deduplication_almanac_endpoint.h"

#include <optional>

#include "base/functional/bind.h"
#include "base/functional/callback_forward.h"
#include "base/functional/callback_helpers.h"
#include "base/memory/scoped_refptr.h"
#include "base/test/bind.h"
#include "base/test/test_future.h"
#include "chrome/browser/apps/almanac_api_client/device_info_manager.h"
#include "chrome/browser/apps/app_deduplication_service/proto/app_deduplication.pb.h"
#include "chrome/browser/apps/app_deduplication_service/proto/deduplication_data.pb.h"
#include "components/version_info/channel.h"
#include "content/public/test/browser_task_environment.h"
#include "net/http/http_request_headers.h"
#include "services/network/public/cpp/resource_request.h"
#include "services/network/public/cpp/shared_url_loader_factory.h"
#include "services/network/public/cpp/weak_wrapper_shared_url_loader_factory.h"
#include "services/network/test/test_url_loader_factory.h"
#include "services/network/test/test_utils.h"
#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace apps {

class AppDeduplicationAlmanacEndpointTest : public testing::Test {
 public:
  AppDeduplicationAlmanacEndpointTest()
      : test_shared_loader_factory_(
            base::MakeRefCounted<network::WeakWrapperSharedURLLoaderFactory>(
                &url_loader_factory_)) {}

  void SetUp() override {
    device_info_.board = "brya";
    device_info_.user_type = "unmanaged";
  }

 protected:
  network::TestURLLoaderFactory url_loader_factory_;
  scoped_refptr<network::SharedURLLoaderFactory> test_shared_loader_factory_;

  DeviceInfo device_info_;

 private:
  content::BrowserTaskEnvironment task_environment_;
};

TEST_F(AppDeduplicationAlmanacEndpointTest,
       GetDeduplicateAppsFromServerRequest) {
  std::string method;
  std::string method_override_header;
  std::string content_type;

  url_loader_factory_.SetInterceptor(
      base::BindLambdaForTesting([&](const network::ResourceRequest& request) {
        request.headers.GetHeader(net::HttpRequestHeaders::kContentType,
                                  &content_type);
        request.headers.GetHeader("X-HTTP-Method-Override",
                                  &method_override_header);
        method = request.method;
      }));

  app_deduplication_almanac_endpoint::GetDeduplicateAppsFromServer(
      device_info_, *test_shared_loader_factory_, base::DoNothing());

  EXPECT_EQ(method, "POST");
  EXPECT_EQ(method_override_header, "GET");
  EXPECT_EQ(content_type, "application/x-protobuf");
}

TEST_F(AppDeduplicationAlmanacEndpointTest,
       GetDeduplicateAppsFromServerSuccess) {
  proto::DeduplicateResponse response;
  auto* group = response.add_app_group();
  group->set_app_group_uuid("15ca3ac3-c8cd-4a0c-a195-2ea210ea922c");
  group->add_package_id();
  group->set_package_id(0, "website:https://web.skype.com/");

  url_loader_factory_.AddResponse(
      app_deduplication_almanac_endpoint::GetServerUrl().spec(),
      response.SerializeAsString());

  base::test::TestFuture<std::optional<proto::DeduplicateData>> test_callback;
  app_deduplication_almanac_endpoint::GetDeduplicateAppsFromServer(
      device_info_, *test_shared_loader_factory_, test_callback.GetCallback());
  auto observed_response = test_callback.Get();
  EXPECT_TRUE(observed_response.has_value());
  EXPECT_EQ(observed_response->app_group_size(), 1);
}

TEST_F(AppDeduplicationAlmanacEndpointTest,
       GetDeduplicateAppsFromServerEmptyResponse) {
  url_loader_factory_.AddResponse(
      app_deduplication_almanac_endpoint::GetServerUrl().spec(), "");

  base::test::TestFuture<std::optional<proto::DeduplicateData>> response;
  app_deduplication_almanac_endpoint::GetDeduplicateAppsFromServer(
      device_info_, *test_shared_loader_factory_, response.GetCallback());
  EXPECT_FALSE(response.Get().has_value());
}

TEST_F(AppDeduplicationAlmanacEndpointTest, GetDeduplicateAppsFromServerError) {
  url_loader_factory_.AddResponse(
      app_deduplication_almanac_endpoint::GetServerUrl().spec(), /*content=*/"",
      net::HTTP_INTERNAL_SERVER_ERROR);

  base::test::TestFuture<std::optional<proto::DeduplicateData>> response;
  app_deduplication_almanac_endpoint::GetDeduplicateAppsFromServer(
      device_info_, *test_shared_loader_factory_, response.GetCallback());
  EXPECT_FALSE(response.Get().has_value());
}

TEST_F(AppDeduplicationAlmanacEndpointTest,
       GetDeduplicateAppsFromServerNetworkError) {
  url_loader_factory_.AddResponse(
      app_deduplication_almanac_endpoint::GetServerUrl(),
      network::mojom::URLResponseHead::New(), /*content=*/"",
      network::URLLoaderCompletionStatus(net::ERR_INSUFFICIENT_RESOURCES));

  base::test::TestFuture<std::optional<proto::DeduplicateData>> response;
  app_deduplication_almanac_endpoint::GetDeduplicateAppsFromServer(
      device_info_, *test_shared_loader_factory_, response.GetCallback());
  EXPECT_FALSE(response.Get().has_value());
}

}  // namespace apps
