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
package com.helger.as4.client;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;

import com.helger.as4.crypto.AS4CryptParams;
import com.helger.as4.crypto.AS4CryptoFactory;
import com.helger.as4.crypto.AS4CryptoProperties;
import com.helger.as4.crypto.AS4SigningParams;
import com.helger.as4.http.AS4HttpDebug;
import com.helger.as4.http.HttpMimeMessageEntity;
import com.helger.as4.http.HttpXMLEntity;
import com.helger.as4.messaging.domain.AbstractAS4Message;
import com.helger.as4.messaging.domain.MessageHelperMethods;
import com.helger.as4.model.pmode.leg.PModeLeg;
import com.helger.as4.soap.ESOAPVersion;
import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.OverrideOnDemand;
import com.helger.commons.annotation.ReturnsMutableObject;
import com.helger.commons.collection.impl.CommonsLinkedHashMap;
import com.helger.commons.collection.impl.ICommonsMap;
import com.helger.commons.functional.ISupplier;
import com.helger.commons.io.resource.IReadableResource;
import com.helger.commons.string.StringHelper;
import com.helger.commons.string.ToStringGenerator;
import com.helger.httpclient.response.ResponseHandlerMicroDom;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.security.keystore.IKeyStoreType;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.serialize.MicroWriter;

/**
 * Abstract AS4 client based in HTTP client
 *
 * @author Philip Helger
 */
public abstract class AbstractAS4Client extends BasicHttpPoster
{
  public static final class AS4BuiltMessage
  {
    private final String m_sMessageID;
    private final HttpEntity m_aHttpEntity;

    public AS4BuiltMessage (@Nonnull @Nonempty final String sMessageID, @Nonnull final HttpXMLEntity aHttpEntity)
    {
      m_sMessageID = ValueEnforcer.notEmpty (sMessageID, "MessageID");
      m_aHttpEntity = ValueEnforcer.notNull (aHttpEntity, "HttpEntity");
    }

    public AS4BuiltMessage (@Nonnull @Nonempty final String sMessageID,
                            @Nonnull final HttpMimeMessageEntity aHttpEntity)
    {
      m_sMessageID = ValueEnforcer.notEmpty (sMessageID, "MessageID");
      m_aHttpEntity = ValueEnforcer.notNull (aHttpEntity, "HttpEntity");
    }

    @Nonnull
    @Nonempty
    public String getMessageID ()
    {
      return m_sMessageID;
    }

    @Nonnull
    public HttpEntity getHttpEntity ()
    {
      return m_aHttpEntity;
    }

    @Override
    public String toString ()
    {
      return new ToStringGenerator (this).append ("MessageID", m_sMessageID)
                                         .append ("HttpEntity", m_aHttpEntity)
                                         .getToString ();
    }
  }

  public static final class AS4SentMessage <T>
  {
    private final AS4BuiltMessage m_aBuiltMsg;
    private final T m_aResponse;

    public AS4SentMessage (@Nonnull final AS4BuiltMessage aBuiltMsg, @Nullable final T aResponse)
    {
      m_aBuiltMsg = ValueEnforcer.notNull (aBuiltMsg, "BuiltMsg");
      m_aResponse = aResponse;
    }

    @Nonnull
    public AS4BuiltMessage getBuiltMessage ()
    {
      return m_aBuiltMsg;
    }

    @Nonnull
    @Nonempty
    public String getMessageID ()
    {
      return m_aBuiltMsg.getMessageID ();
    }

    @Nullable
    public T getResponse ()
    {
      return m_aResponse;
    }

    public boolean hasResponse ()
    {
      return m_aResponse != null;
    }

    @Override
    public String toString ()
    {
      return new ToStringGenerator (this).append ("MessageID", getMessageID ())
                                         .append ("Response", m_aResponse)
                                         .getToString ();
    }
  }

  public static final IKeyStoreType DEFAULT_KEYSTORE_TYPE = EKeyStoreType.JKS;

  // KeyStore attributes
  private IKeyStoreType m_aKeyStoreType = DEFAULT_KEYSTORE_TYPE;
  private IReadableResource m_aKeyStoreRes;
  private String m_sKeyStorePassword;
  private String m_sKeyStoreAlias;
  private String m_sKeyStoreKeyPassword;
  // Alternative
  private AS4CryptoFactory m_aCryptoFactory;
  private final AS4SigningParams m_aSigningParams = new AS4SigningParams ();
  private final AS4CryptParams m_aCryptParams = new AS4CryptParams ();

  // For Message Info
  private ISupplier <String> m_aMessageIDFactory = createDefaultMessageIDFactory ();
  private String m_sRefToMessageID;

  private ESOAPVersion m_eSOAPVersion = ESOAPVersion.AS4_DEFAULT;

  /**
   * @return The default message ID factory to be used.
   * @since 0.8.3
   */
  @Nonnull
  public static ISupplier <String> createDefaultMessageIDFactory ()
  {
    return MessageHelperMethods::createRandomMessageID;
  }

  protected AbstractAS4Client ()
  {}

  private void _checkKeyStoreAttributes ()
  {
    if (m_aCryptoFactory == null)
    {
      if (m_aKeyStoreType == null)
        throw new IllegalStateException ("KeyStore type is not configured.");
      if (m_aKeyStoreRes == null)
        throw new IllegalStateException ("KeyStore resources is not configured.");
      if (!m_aKeyStoreRes.exists ())
        throw new IllegalStateException ("KeyStore resources does not exist: " + m_aKeyStoreRes.getPath ());
      if (m_sKeyStorePassword == null)
        throw new IllegalStateException ("KeyStore password is not configured.");
      if (StringHelper.hasNoText (m_sKeyStoreAlias))
        throw new IllegalStateException ("KeyStore alias is not configured.");
      if (m_sKeyStoreKeyPassword == null)
        throw new IllegalStateException ("Key password is not configured.");
    }
  }

  @Nonnull
  protected AS4CryptoFactory internalCreateCryptoFactory ()
  {
    _checkKeyStoreAttributes ();

    // Shortcut?
    if (m_aCryptoFactory != null)
      return m_aCryptoFactory;

    final ICommonsMap <String, String> aCryptoProps = new CommonsLinkedHashMap <> ();
    aCryptoProps.put ("org.apache.wss4j.crypto.provider", org.apache.wss4j.common.crypto.Merlin.class.getName ());
    aCryptoProps.put (AS4CryptoProperties.KEYSTORE_TYPE, getKeyStoreType ().getID ());
    aCryptoProps.put (AS4CryptoProperties.KEYSTORE_FILE, getKeyStoreResource ().getPath ());
    aCryptoProps.put (AS4CryptoProperties.KEYSTORE_PASSWORD, getKeyStorePassword ());
    aCryptoProps.put (AS4CryptoProperties.KEY_ALIAS, getKeyStoreAlias ());
    aCryptoProps.put (AS4CryptoProperties.KEY_PASSWORD, getKeyStoreKeyPassword ());
    return new AS4CryptoFactory (aCryptoProps);
  }

  @OverrideOnDemand
  @Nonnull
  public abstract AS4BuiltMessage buildMessage (@Nullable final Consumer <? super AbstractAS4Message <?>> aMsgConsumer) throws Exception;

  @Nonnull
  public <T> AS4SentMessage <T> sendMessage (@Nonnull final String sURL,
                                             @Nonnull final ResponseHandler <? extends T> aResponseHandler,
                                             @Nullable final Consumer <? super AbstractAS4Message <?>> aMsgConsumer) throws Exception
  {
    final AS4BuiltMessage aBuiltMsg = buildMessage (aMsgConsumer);
    final T aResponse = sendGenericMessage (sURL, aBuiltMsg.getHttpEntity (), aResponseHandler);
    return new AS4SentMessage <> (aBuiltMsg, aResponse);
  }

  @Nullable
  public IMicroDocument sendMessageAndGetMicroDocument (@Nonnull final String sURL) throws Exception
  {
    final IMicroDocument ret = sendMessage (sURL, new ResponseHandlerMicroDom (), null).getResponse ();
    AS4HttpDebug.debug ( () -> "SEND-RESPONSE received: " +
                               MicroWriter.getNodeAsString (ret, AS4HttpDebug.getDebugXMLWriterSettings ()));
    return ret;
  }

  /**
   * @return The keystore type to use. Never <code>null</code>. Default is
   *         {@link #DEFAULT_KEYSTORE_TYPE}.
   */
  @Nonnull
  public final IKeyStoreType getKeyStoreType ()
  {
    return m_aKeyStoreType;
  }

  /**
   * The type of the keystore needs to be set if a keystore is used.<br>
   * MANDATORY if you want to use sign or encryption of an user message.
   * Defaults to "jks".
   *
   * @param aKeyStoreType
   *        keystore type that should be set, e.g. "jks". May not be
   *        <code>null</code>.
   */
  public final void setKeyStoreType (@Nonnull final IKeyStoreType aKeyStoreType)
  {
    ValueEnforcer.notNull (aKeyStoreType, "KeyStoreType");
    m_aKeyStoreType = aKeyStoreType;
    m_aCryptoFactory = null;
  }

  /**
   * @return The keystore resource to use. May be <code>null</code>.
   */
  @Nullable
  public final IReadableResource getKeyStoreResource ()
  {
    return m_aKeyStoreRes;
  }

  /**
   * The keystore that should be used can be set here.<br>
   * MANDATORY if you want to use sign or encryption of an usermessage.
   *
   * @param aKeyStoreRes
   *        the keystore file that should be used
   */
  public final void setKeyStoreResource (@Nullable final IReadableResource aKeyStoreRes)
  {
    m_aKeyStoreRes = aKeyStoreRes;
    m_aCryptoFactory = null;
  }

  /**
   * @return The keystore password to use. May be <code>null</code>.
   */
  @Nullable
  public final String getKeyStorePassword ()
  {
    return m_sKeyStorePassword;
  }

  /**
   * KeyStore password needs to be set if a keystore is used<br>
   * MANDATORY if you want to use sign or encryption of an usermessage.
   *
   * @param sKeyStorePassword
   *        password that should be set
   */
  public final void setKeyStorePassword (@Nullable final String sKeyStorePassword)
  {
    m_sKeyStorePassword = sKeyStorePassword;
    m_aCryptoFactory = null;
  }

  /**
   * @return The keystore key alias to use. May be <code>null</code>.
   */
  @Nullable
  public final String getKeyStoreAlias ()
  {
    return m_sKeyStoreAlias;
  }

  /**
   * KeyStorealias needs to be set if a keystore is used<br>
   * MANDATORY if you want to use sign or encryption of an usermessage.
   *
   * @param sKeyStoreAlias
   *        alias that should be set
   */
  public final void setKeyStoreAlias (@Nullable final String sKeyStoreAlias)
  {
    m_sKeyStoreAlias = sKeyStoreAlias;
    m_aCryptoFactory = null;
  }

  /**
   * @return The keystore key password to use. May be <code>null</code>.
   */

  @Nullable
  public final String getKeyStoreKeyPassword ()
  {
    return m_sKeyStoreKeyPassword;
  }

  /**
   * Key password needs to be set if a keystore is used<br>
   * MANDATORY if you want to use sign or encryption of an usermessage.
   *
   * @param sKeyStoreKeyPassword
   *        password that should be set
   */
  public final void setKeyStoreKeyPassword (@Nullable final String sKeyStoreKeyPassword)
  {
    m_sKeyStoreKeyPassword = sKeyStoreKeyPassword;
    m_aCryptoFactory = null;
  }

  @Nullable
  public final AS4CryptoFactory getAS4CryptoFactory ()
  {
    return m_aCryptoFactory;
  }

  public final void setAS4CryptoFactory (@Nullable final AS4CryptoFactory aCryptoFactory)
  {
    m_aCryptoFactory = aCryptoFactory;
  }

  /**
   * @return The signing algorithm to use. Never <code>null</code>.
   * @since 0.9.0
   */
  @Nonnull
  @ReturnsMutableObject
  public final AS4SigningParams signingParams ()
  {
    return m_aSigningParams;
  }

  /**
   * @return The encrypt and decrypt parameters to use. Never null
   *         <code>null</code>.
   * @since 0.9.0
   */
  @Nonnull
  @ReturnsMutableObject
  public final AS4CryptParams cryptParams ()
  {
    return m_aCryptParams;
  }

  /**
   * @return The Message ID factory to be used. May not be <code>null</code>.
   */
  @Nonnull
  public final ISupplier <String> getMessageIDFactory ()
  {
    return m_aMessageIDFactory;
  }

  /**
   * Set a constant message ID
   *
   * @param sMessageID
   *        Message to be used. May neither be <code>null</code> nor empty.
   */
  public final void setMessageID (@Nonnull @Nonempty final String sMessageID)
  {
    ValueEnforcer.notEmpty (sMessageID, "MessageID");
    setMessageIDFactory ( () -> sMessageID);
  }

  /**
   * Set the factory that creates message IDs. By default a random UUID is used.
   *
   * @param aMessageIDFactory
   *        Factory to be used. May not be <code>null</code>.
   */
  public final void setMessageIDFactory (@Nonnull final ISupplier <String> aMessageIDFactory)
  {
    ValueEnforcer.notNull (aMessageIDFactory, "MessageIDFactory");
    m_aMessageIDFactory = aMessageIDFactory;
  }

  @Nonnull
  @Nonempty
  protected final String createMessageID ()
  {
    final String ret = m_aMessageIDFactory.get ();
    if (StringHelper.hasNoText (ret))
      throw new IllegalStateException ("An empty MessageID was generated!");
    return ret;
  }

  @Nullable
  public final String getRefToMessageID ()
  {
    return m_sRefToMessageID;
  }

  public final boolean hasRefToMessageID ()
  {
    return StringHelper.hasText (m_sRefToMessageID);
  }

  public final void setRefToMessageID (@Nullable final String sRefToMessageID)
  {
    m_sRefToMessageID = sRefToMessageID;
  }

  /**
   * @return The SOAP version to be used. May not be <code>null</code>.
   */
  @Nonnull
  public final ESOAPVersion getSOAPVersion ()
  {
    return m_eSOAPVersion;
  }

  /**
   * This method sets the SOAP Version. AS4 - Profile default is SOAP 1.2
   *
   * @param eSOAPVersion
   *        SOAPVersion which should be set. MAy not be <code>null</code>.
   */
  public final void setSOAPVersion (@Nonnull final ESOAPVersion eSOAPVersion)
  {
    ValueEnforcer.notNull (eSOAPVersion, "SOAPVersion");
    m_eSOAPVersion = eSOAPVersion;
  }

  public final void setCryptoValuesFromPMode (@Nullable final PModeLeg aLeg)
  {
    if (aLeg != null)
    {
      signingParams ().setFromPMode (aLeg.getSecurity ());
      cryptParams ().setFromPMode (aLeg.getSecurity ());
    }
  }
}
