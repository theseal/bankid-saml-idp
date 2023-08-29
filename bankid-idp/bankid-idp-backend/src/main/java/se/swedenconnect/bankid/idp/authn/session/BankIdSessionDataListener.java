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
package se.swedenconnect.bankid.idp.authn.session;

import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import se.swedenconnect.bankid.idp.authn.context.PreviousDeviceSelection;
import se.swedenconnect.bankid.idp.authn.events.*;
import se.swedenconnect.bankid.rpapi.types.CollectResponse;
import se.swedenconnect.spring.saml.idp.events.Saml2PostUserAuthenticationEvent;

/**
 * A listener for BankID session events.
 *
 * @author Martin Lindström
 * @author Felix Hellman
 */
@Service
@Slf4j
public class BankIdSessionDataListener {

  private static final Map<Boolean, PreviousDeviceSelection> PREVIOUS_DEVICE_SELECTION_MAP =
      Map.of(true, PreviousDeviceSelection.OTHER, false, PreviousDeviceSelection.THIS_DEVICE);

  /** The session writer. */
  private final BankIdSessionWriter writer;

  /** The session reader. */
  private final BankIdSessionReader reader;

  /**
   * Constructor.
   *
   * @param writer session writer
   * @param reader session reader
   */
  public BankIdSessionDataListener(final BankIdSessionWriter writer, final BankIdSessionReader reader) {
    this.writer = writer;
    this.reader = reader;
  }

  /**
   * Removes BankID data from the current session
   *
   * @param event event to be processed
   * @see OrderResponseEvent
   */
  @EventListener
  public void handleAbortAuthEvent(final AbortAuthEvent event) {
    this.writer.delete(event.getRequest());
  }

  /**
   * Writes a published {@link OrderResponseEvent} to the user's session.
   *
   * @param event event to be processed
   * @see OrderResponseEvent
   */
  @EventListener
  public void handleOrderResponse(final OrderResponseEvent event) {
    log.info("Order response event was published {} for session {}",
        event.getResponse(), event.getRequest().getSession().getId());

    final BankIdSessionData bankIdSessionData = BankIdSessionData.of(event.getPollRequest(), event.getResponse());
    this.writer.save(event.getRequest(), bankIdSessionData);
  }

  /**
   * Writes a published {@link CollectResponseEvent} to the user's session.
   *
   * @param event event to be processed
   * @see CollectResponseEvent
   */
  @EventListener
  public void handleCollectResponse(final CollectResponseEvent event) {
    final HttpSession session = event.getRequest().getRequest().getSession();
    
    log.info("Collect response event was published {} for session {}", event.getCollectResponse(), session.getId());
    
    final BankIdSessionData previous =
        this.reader.loadSessionData(event.getRequest().getRequest()).getBankIdSessionData();
    this.writer.save(event.getRequest().getRequest(), BankIdSessionData.of(previous, event.getCollectResponse()));
    
    if (event.getCollectResponse().getStatus().equals(CollectResponse.Status.COMPLETE)) {
      this.writer.save(event.getRequest().getRequest(), event.getCollectResponse());
    }
  }

  /**
   * Writes a published {@link OrderCompletionEvent} to the user's session.
   *
   * @param event event to be processed
   * @see OrderCompletionEvent
   */
  @EventListener
  @Order(Integer.MAX_VALUE)
  public void handleCompletion(final OrderCompletionEvent event) {
    final BankIdSessionState sessionState = this.reader.loadSessionData(event.getRequest());
    final Boolean otherDevice = sessionState.getBankIdSessionData().getShowQr();
    final PreviousDeviceSelection previousDeviceSelection = PREVIOUS_DEVICE_SELECTION_MAP.get(otherDevice);
    this.writer.save(event.getRequest(), previousDeviceSelection);
    this.writer.delete(event.getRequest());
  }

  /**
   * Handles a published {@link OrderCancellationEvent} to delete relevant user session data.
   *
   * @param event event to be processed
   * @see OrderCancellationEvent
   */
  @EventListener
  public void handleOrderCancellationEvent(final OrderCancellationEvent event) {
    this.writer.delete(event.getRequest());
  }

  /**
   * Handles a published {@link UserVisibleDataEvent} to persist message for a user.
   *
   * @param event event to be processed
   */
  @EventListener
  public void handleUserVisibleDataEvent(final UserVisibleDataEvent event) {
    this.writer.save(event.getRequest(), event.getUserVisibleData());
  }
}
