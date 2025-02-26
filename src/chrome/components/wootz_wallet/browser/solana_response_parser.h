/* Copyright (c) 2022 The Wootz Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

#ifndef WOOTZ_COMPONENTS_WOOTZ_WALLET_BROWSER_SOLANA_RESPONSE_PARSER_H_
#define WOOTZ_COMPONENTS_WOOTZ_WALLET_BROWSER_SOLANA_RESPONSE_PARSER_H_

#include <optional>
#include <string>
#include <vector>

#include "base/functional/callback.h"
#include "base/values.h"
#include "chrome/components/wootz_wallet/common/wootz_wallet.mojom.h"

// TODO(apaymyshev): refactor utility methods to return std::optional instead
// of bool + out-parameter.

namespace wootz_wallet {

struct SolanaSignatureStatus;
struct SolanaAccountInfo;

namespace solana {

bool ParseGetBalance(const base::Value& json_value, uint64_t* balance);
bool ParseGetTokenAccountBalance(const base::Value& json_value,
                                 std::string* amount,
                                 uint8_t* decimals,
                                 std::string* ui_amount_string);
bool ParseSendTransaction(const base::Value& json_value, std::string* tx_id);
bool ParseGetLatestBlockhash(const base::Value& json_value,
                             std::string* hash,
                             uint64_t* last_valid_block_height);
bool ParseGetSignatureStatuses(
    const base::Value& json_value,
    std::vector<std::optional<SolanaSignatureStatus>>* statuses);
bool ParseGetAccountInfo(const base::Value& json_value,
                         std::optional<SolanaAccountInfo>* account_info_out);
bool ParseGetAccountInfoPayload(
    const base::Value::Dict& value_dict,
    std::optional<SolanaAccountInfo>* account_info_out);
bool ParseGetFeeForMessage(const base::Value& json_value, uint64_t* fee);
bool ParseGetBlockHeight(const base::Value& json_value, uint64_t* block_height);
bool ParseGetTokenAccountsByOwner(const base::Value& json_value,
                                  std::vector<SolanaAccountInfo>* accounts);

std::optional<bool> ParseIsBlockhashValid(const base::Value& json_value);

std::optional<std::vector<mojom::SPLTokenAmountPtr>> ParseGetSPLTokenBalances(
    const base::Value& json_value);

std::optional<uint64_t> ParseSimulateTransaction(const base::Value& json_value);
std::optional<std::vector<std::pair<uint64_t, uint64_t>>>
ParseGetSolanaPrioritizationFees(const base::Value& json_value);

base::OnceCallback<std::optional<std::string>(const std::string& raw_response)>
ConverterForGetAccountInfo();
base::OnceCallback<std::optional<std::string>(const std::string& raw_response)>
ConverterForGetProgramAccounts();

}  // namespace solana

}  // namespace wootz_wallet

#endif  // WOOTZ_COMPONENTS_WOOTZ_WALLET_BROWSER_SOLANA_RESPONSE_PARSER_H_
