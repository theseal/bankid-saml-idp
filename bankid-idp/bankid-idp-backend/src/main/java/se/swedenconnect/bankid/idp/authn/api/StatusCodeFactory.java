/*
 * Copyright 2023 Sweden Connect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.swedenconnect.bankid.idp.authn.api;

import static se.swedenconnect.bankid.rpapi.types.CollectResponse.Status.PENDING;
import static se.swedenconnect.bankid.rpapi.types.CollectResponse.Status.FAILED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import se.swedenconnect.bankid.idp.ApplicationVersion;
import se.swedenconnect.bankid.idp.authn.context.BankIdOperation;
import se.swedenconnect.bankid.rpapi.types.CollectResponse;
import se.swedenconnect.bankid.rpapi.types.ErrorCode;
import se.swedenconnect.bankid.rpapi.types.ProgressStatus;

public class StatusCodeFactory {

  private static final Map<Predicate<StatusData>, String> MESSAGE_CONDITIONS = new HashMap<>() {

    private static final long serialVersionUID = ApplicationVersion.SERIAL_VERSION_UID;

  {
    put(c -> PENDING.equals(c.getCollectResponse().getStatus()) && List.of(ProgressStatus.OUTSTANDING_TRANSACTION, ProgressStatus.NO_CLIENT).contains(c.getCollectResponse().getProgressStatus()), "rfa1");
    put(c -> ErrorCode.CANCELLED.equals(c.getCollectResponse().getErrorCode()), "rfa3");
    put(c -> ErrorCode.ALREADY_IN_PROGRESS.equals(c.getCollectResponse().getErrorCode()), "rfa4");
    put(c -> Objects.nonNull(c.getCollectResponse().getErrorCode()) && List.of(ErrorCode.REQUEST_TIMEOUT, ErrorCode.MAINTENANCE, ErrorCode.INTERNAL_ERROR).contains(c.getCollectResponse().getErrorCode()), "rfa5");
    put(c -> FAILED.equals(c.getCollectResponse().getStatus()) && ProgressStatus.NO_CLIENT.equals(c.getCollectResponse().getProgressStatus()), "rfa6");
    put(c -> FAILED.equals(c.getCollectResponse().getStatus()) && ProgressStatus.EXPIRED_TRANSACTION.equals(c.getCollectResponse().getProgressStatus()), "rfa8");
    put(c -> PENDING.equals(c.getCollectResponse().getStatus()) && ProgressStatus.USER_SIGN.equals(c.getCollectResponse().getProgressStatus()), "rfa9");
    put(c -> PENDING.equals(c.getCollectResponse().getStatus()) && ProgressStatus.OUTSTANDING_TRANSACTION.equals(c.getCollectResponse().getProgressStatus()), "rfa13");
    put(c -> PENDING.equals(c.getCollectResponse().getStatus()) && Objects.equals(c.getOperation(), BankIdOperation.AUTH), "rfa21-auth");
    put(c -> PENDING.equals(c.getCollectResponse().getStatus()) && Objects.equals(c.getOperation(), BankIdOperation.SIGN), "rfa21-sign");
    put(c -> FAILED.equals(c.getCollectResponse().getStatus()), "rfa22");
  }};

  private static final Map<Predicate<StatusData>, String> QR_MESSAGE_CONDITIONS = Map.of(
      c ->  c.getShowQr() && PENDING.equals(c.getCollectResponse().getStatus()) && ProgressStatus.USER_SIGN.equals(c.getCollectResponse().getProgressStatus()), "rfa9",
      c -> c.getShowQr() && PENDING.equals(c.getCollectResponse().getStatus()), "ext2"
  );

  public static String statusCode(final CollectResponse json, final Boolean showQr, final BankIdOperation operation) {
    Stream<Map.Entry<Predicate<StatusData>, String>> qrMessageStream = QR_MESSAGE_CONDITIONS.entrySet().stream();
    Stream<Map.Entry<Predicate<StatusData>, String>> messageStream =  MESSAGE_CONDITIONS.entrySet().stream();
    Optional<String> message = Stream.concat(qrMessageStream, messageStream)
        .filter(kv -> kv.getKey().test(new StatusData(json, showQr, operation)))
        .map(Map.Entry::getValue)
        .findFirst();
    return "bankid.msg." + message.orElseGet(() -> "blank");
  }

  @AllArgsConstructor
  @Data
  private static class StatusData {
    CollectResponse collectResponse;
    Boolean showQr;
    BankIdOperation operation;
  }
}