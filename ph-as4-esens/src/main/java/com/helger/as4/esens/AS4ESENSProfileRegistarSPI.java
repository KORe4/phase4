/**
 * Copyright (C) 2015-2017 Philip Helger (www.helger.com)
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
package com.helger.as4.esens;

import javax.annotation.Nonnull;

import com.helger.as4.model.profile.AS4Profile;
import com.helger.as4.model.profile.IAS4ProfileRegistrar;
import com.helger.as4.model.profile.IAS4ProfileRegistrarSPI;
import com.helger.commons.annotation.IsSPIImplementation;

@IsSPIImplementation
public class AS4ESENSProfileRegistarSPI implements IAS4ProfileRegistrarSPI
{
  public void registerAS4Profile (@Nonnull final IAS4ProfileRegistrar aRegistrar)
  {
    aRegistrar.registerProfile (new AS4Profile ("esens",
                                                "e-SENS",
                                                () -> new ESENSCompatibilityValidator (),
                                                () -> ESENSPMode.createESENSPModeConfig ()));
  }
}
