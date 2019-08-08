/**
 * Copyright (C) 2015-2019 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.servlet.spi;

import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.w3c.dom.Node;

import com.helger.commons.annotation.IsSPIInterface;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.http.HttpHeaderMap;
import com.helger.phase4.attachment.WSS4JAttachment;
import com.helger.phase4.ebms3header.Ebms3Error;
import com.helger.phase4.ebms3header.Ebms3SignalMessage;
import com.helger.phase4.ebms3header.Ebms3UserMessage;
import com.helger.phase4.model.pmode.IPMode;
import com.helger.phase4.servlet.IAS4MessageState;

/**
 * Implement this SPI interface to handle incoming messages appropriate
 *
 * @author Philip Helger
 */
@IsSPIInterface
public interface IAS4ServletMessageProcessorSPI extends Serializable
{
  /**
   * Process incoming AS4 user message
   *
   * @param aHttpHeaders
   *        The original HTTP headers. Never <code>null</code>.
   * @param aUserMessage
   *        The received user message. May not be <code>null</code>.
   * @param aPMode
   *        The source PMode used to parse the message.
   * @param aPayload
   *        Extracted, decrypted and verified payload node (e.g. SBDH). May be
   *        <code>null</code>. May also be <code>null</code> if a MIME message
   *        comes in - in that case the SOAP body MUST be empty and the main
   *        payload can be found in aIncomingAttachments[0].
   * @param aIncomingAttachments
   *        Extracted, decrypted and verified attachments. May be
   *        <code>null</code> or empty if no attachments are present.
   * @param aState
   *        The current message state. Can be used to determine all other things
   *        potentially necessary for processing the incoming message. Never
   *        <code>null</code>.
   * @param aProcessingErrorMessages
   *        List for error messages that occur during processing. Never
   *        <code>null</code>.
   * @return A non-<code>null</code> result object.
   */
  @Nonnull
  AS4MessageProcessorResult processAS4UserMessage (@Nonnull HttpHeaderMap aHttpHeaders,
                                                   @Nonnull Ebms3UserMessage aUserMessage,
                                                   @Nonnull IPMode aPMode,
                                                   @Nullable Node aPayload,
                                                   @Nullable ICommonsList <WSS4JAttachment> aIncomingAttachments,
                                                   @Nonnull IAS4MessageState aState,
                                                   @Nonnull ICommonsList <Ebms3Error> aProcessingErrorMessages);

  /**
   * Process incoming AS4 signal message - pull-request and receipt.<br>
   * Attachment and Payload are not needed since they are allowed, but should
   * not be added to a SignalMessage Because the will be ignored in the MSH -
   * Processing.
   *
   * @param aHttpHeaders
   *        The original HTTP headers. Never <code>null</code>.
   * @param aSignalMessage
   *        The received signal message. May not be <code>null</code>.
   * @param aPMode
   *        PMode - only needed for pull-request. May be <code>null</code>.
   * @param aState
   *        The current message state. Can be used to determine all other things
   *        potentially necessary for processing the incoming message. Never
   *        <code>null</code>.
   * @param aProcessingErrorMessages
   *        List for error messages that occur during processing. Never
   *        <code>null</code>.
   * @return A non-<code>null</code> result object.
   */
  @Nonnull
  AS4SignalMessageProcessorResult processAS4SignalMessage (@Nonnull HttpHeaderMap aHttpHeaders,
                                                           @Nonnull Ebms3SignalMessage aSignalMessage,
                                                           @Nullable IPMode aPMode,
                                                           @Nonnull IAS4MessageState aState,
                                                           @Nonnull ICommonsList <Ebms3Error> aProcessingErrorMessages);
}