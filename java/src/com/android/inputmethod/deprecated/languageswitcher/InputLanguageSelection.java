/*
 * Copyright (C) 2008-2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.deprecated.languageswitcher;

import com.android.inputmethod.keyboard.KeyboardParser;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SharedPreferencesCompat;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import org.xmlpull.v1.XmlPullParserException;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class InputLanguageSelection extends PreferenceActivity {

    private SharedPreferences mPrefs;
    private String mSelectedLanguages;
    private HashMap<CheckBoxPreference, Locale> mLocaleMap =
            new HashMap<CheckBoxPreference, Locale>();

    private static class Loc implements Comparable<Object> {
        private static Collator sCollator = Collator.getInstance();

        private String mLabel;
        public final Locale mLocale;

        public Loc(String label, Locale locale) {
            this.mLabel = label;
            this.mLocale = locale;
        }

        public void setLabel(String label) {
            this.mLabel = label;
        }

        @Override
        public String toString() {
            return this.mLabel;
        }

        @Override
        public int compareTo(Object o) {
            return sCollator.compare(this.mLabel, ((Loc) o).mLabel);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.language_prefs);
        // Get the settings preferences
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSelectedLanguages = mPrefs.getString(Settings.PREF_SELECTED_LANGUAGES, "");
        String[] languageList = mSelectedLanguages.split(",");
        ArrayList<Loc> availableLanguages = getUniqueLocales();
        PreferenceGroup parent = getPreferenceScreen();
        for (int i = 0; i < availableLanguages.size(); i++) {
            Locale locale = availableLanguages.get(i).mLocale;
            final Pair<Boolean, Boolean> hasDictionaryOrLayout = hasDictionaryOrLayout(locale);
            final boolean hasDictionary = hasDictionaryOrLayout.first;
            final boolean hasLayout = hasDictionaryOrLayout.second;
            // Add this locale to the supported list if:
            // 1) this locale has a layout/ 2) this locale has a dictionary and the length
            // of the locale is equal to or larger than 5.
            if (!hasLayout && !(hasDictionary && locale.toString().length() >= 5)) {
                continue;
            }
            CheckBoxPreference pref = new CheckBoxPreference(this);
            pref.setTitle(SubtypeSwitcher.getFullDisplayName(locale, true));
            boolean checked = isLocaleIn(locale, languageList);
            pref.setChecked(checked);
            if (hasDictionary) {
                pref.setSummary(R.string.has_dictionary);
            }
            mLocaleMap.put(pref, locale);
            parent.addPreference(pref);
        }
    }

    private boolean isLocaleIn(Locale locale, String[] list) {
        String lang = get5Code(locale);
        for (int i = 0; i < list.length; i++) {
            if (lang.equalsIgnoreCase(list[i])) return true;
        }
        return false;
    }

    private Pair<Boolean, Boolean> hasDictionaryOrLayout(Locale locale) {
        if (locale == null) return new Pair<Boolean, Boolean>(false, false);
        final Resources res = getResources();
        final Locale saveLocale = Utils.setSystemLocale(res, locale);
        final boolean hasDictionary = DictionaryFactory.isDictionaryAvailable(this, locale);
        boolean hasLayout = false;

        try {
            final String localeStr = locale.toString();
            final String[] layoutCountryCodes = KeyboardParser.parseKeyboardLocale(
                    this, R.xml.kbd_qwerty).split(",", -1);
            if (!TextUtils.isEmpty(localeStr) && layoutCountryCodes.length > 0) {
                for (String s : layoutCountryCodes) {
                    if (s.equals(localeStr)) {
                        hasLayout = true;
                        break;
                    }
                }
            }
        } catch (XmlPullParserException e) {
        } catch (IOException e) {
        }
        Utils.setSystemLocale(res, saveLocale);
        return new Pair<Boolean, Boolean>(hasDictionary, hasLayout);
    }

    private String get5Code(Locale locale) {
        String country = locale.getCountry();
        return locale.getLanguage()
                + (TextUtils.isEmpty(country) ? "" : "_" + country);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save the selected languages
        String checkedLanguages = "";
        PreferenceGroup parent = getPreferenceScreen();
        int count = parent.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            CheckBoxPreference pref = (CheckBoxPreference) parent.getPreference(i);
            if (pref.isChecked()) {
                checkedLanguages += get5Code(mLocaleMap.get(pref)) + ",";
            }
        }
        if (checkedLanguages.length() < 1) checkedLanguages = null; // Save null
        Editor editor = mPrefs.edit();
        editor.putString(Settings.PREF_SELECTED_LANGUAGES, checkedLanguages);
        SharedPreferencesCompat.apply(editor);
    }

    public ArrayList<Loc> getUniqueLocales() {
        String[] locales = getAssets().getLocales();
        Arrays.sort(locales);
        ArrayList<Loc> uniqueLocales = new ArrayList<Loc>();

        final int origSize = locales.length;
        Loc[] preprocess = new Loc[origSize];
        int finalSize = 0;
        for (int i = 0 ; i < origSize; i++ ) {
            String s = locales[i];
            int len = s.length();
            String language = "";
            String country = "";
            if (len == 5) {
                language = s.substring(0, 2);
                country = s.substring(3, 5);
            } else if (len < 5) {
                language = s;
            }
            Locale l = new Locale(language, country);

            // Exclude languages that are not relevant to LatinIME
            if (TextUtils.isEmpty(language)) {
                continue;
            }

            if (finalSize == 0) {
                preprocess[finalSize++] =
                        new Loc(SubtypeSwitcher.getFullDisplayName(l, true), l);
            } else {
                // check previous entry:
                //  same lang and a country -> upgrade to full name and
                //    insert ours with full name
                //  diff lang -> insert ours with lang-only name
                if (preprocess[finalSize-1].mLocale.getLanguage().equals(
                        language)) {
                    preprocess[finalSize-1].setLabel(SubtypeSwitcher.getFullDisplayName(
                            preprocess[finalSize-1].mLocale, false));
                    preprocess[finalSize++] =
                            new Loc(SubtypeSwitcher.getFullDisplayName(l, false), l);
                } else {
                    String displayName;
                    if (s.equals("zz_ZZ")) {
                        // ignore this locale
                    } else {
                        displayName = SubtypeSwitcher.getFullDisplayName(l, true);
                        preprocess[finalSize++] = new Loc(displayName, l);
                    }
                }
            }
        }
        for (int i = 0; i < finalSize ; i++) {
            uniqueLocales.add(preprocess[i]);
        }
        return uniqueLocales;
    }
}