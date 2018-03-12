/*
 * Copyright 2015-2018 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.sciareto.preferences;

import com.igormaznitsa.meta.common.utils.IOUtils;
import com.igormaznitsa.mindmap.model.logger.Logger;
import com.igormaznitsa.mindmap.model.logger.LoggerFactory;
import org.apache.commons.codec.binary.Base64;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencesManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(PreferencesManager.class);
  private static final PreferencesManager INSTANCE = new PreferencesManager();

  private final Preferences prefs;
  private final UUID installationUUID;

  private static final String PROPERTY_UUID = "installation.uuid"; //NOI18N

  private final Map<String, Object> localCache = new HashMap<>();

  private PreferencesManager() {
    this.prefs = Preferences.userNodeForPackage(PreferencesManager.class);
    String packedUuid = this.prefs.get(PROPERTY_UUID, null);
    if (packedUuid == null) {
      try {
        final UUID newUUID = UUID.randomUUID();
        packedUuid = Base64.encodeBase64String(IOUtils.packData(newUUID.toString().getBytes("UTF-8"))); //NOI18N
        this.prefs.put(PROPERTY_UUID, packedUuid);
        this.prefs.flush();
        LOGGER.info("Generated new installation UUID : " + newUUID.toString()); //NOI18N

        final Thread thread = new Thread(new Runnable() {
          @Override
          public void run() {
            LOGGER.info("Send first start metrics"); //NOI18N
            com.igormaznitsa.sciareto.metrics.MetricsService.getInstance().onFirstStart();
          }
        }, "SCIARETO_FIRST_START_METRICS"); //NOI18N
        thread.setDaemon(true);
        thread.start();

      } catch (Exception ex) {
        LOGGER.error("Can't generate UUID", ex); //NOI18N
      }
    }
    try {
      this.installationUUID = UUID.fromString(new String(IOUtils.unpackData(Base64.decodeBase64(packedUuid)), "UTF-8")); //NOI18N
      LOGGER.info("Installation UUID : " + this.installationUUID.toString()); //NOI18N
    } catch (UnsupportedEncodingException ex) {
      LOGGER.error("Can't decode UUID", ex); //NOI18N
      throw new Error("Unexpected error", ex); //NOI18N
    }
  }

  @Nullable
  public Font getFont(@Nonnull final Preferences pref, @Nonnull final String key, @Nullable final Font dflt) {
    synchronized (this.localCache) {
      Font result = (Font) this.localCache.get(key);
      if (result == null) {
        result = PrefUtils.str2font(pref.get(key, null), dflt);
        if (result != null) {
          this.localCache.put(key, result);
        }
      }
      return result;
    }
  }

  public void setFont(@Nonnull final Preferences pref, @Nonnull final String key, @Nullable final Font font) {
    synchronized (this.localCache) {
      if (font == null) {
        this.localCache.remove(key);
        pref.remove(key);
      } else {
        final String packed = PrefUtils.font2str(font);
        this.localCache.put(key, font);
        pref.put(key, packed);
      }
    }
  }

  @Nonnull
  public UUID getInstallationUUID() {
    return this.installationUUID;
  }

  @Nonnull
  public static PreferencesManager getInstance() {
    return INSTANCE;
  }

  @Nonnull
  public synchronized Preferences getPreferences() {
    return this.prefs;
  }

  public synchronized void flush() {
    try {
      this.prefs.flush();
    } catch (BackingStoreException ex) {
      LOGGER.error("Can't flush preferences", ex); //NOI18N
    }
  }

}
