/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.privacy;

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorPrivacyManager;
import android.icu.text.RelativeDateTimeFormatter;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.applications.RecentAppOpsAccess;
import com.android.settingslib.utils.StringUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This controller displays a list of apps that recently access the microphone. Only non-system apps
 * are displayed.
 */
public class MicrophoneRecentAccessesPreferenceController extends
        PreferenceController<PreferenceCategory> {

    private final SensorPrivacyManager mSensorPrivacyManager;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mListener =
            (sensor, enabled) -> refreshUi();
    private final Set<Preference> mAddedPreferences = new HashSet<>();

    private final RecentAppOpsAccess mRecentMicrophoneAccesses;
    private final int mRecentAppsMaxCount;

    public MicrophoneRecentAccessesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                RecentAppOpsAccess.createForMicrophone(context),
                context.getResources()
                        .getInteger(R.integer.recent_microphone_access_apps_list_count),
                SensorPrivacyManager.getInstance(context));
    }

    @VisibleForTesting
    MicrophoneRecentAccessesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            RecentAppOpsAccess recentMicrophoneAccesses, int recentAppsMaxCount,
            SensorPrivacyManager sensorPrivacyManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentMicrophoneAccesses = recentMicrophoneAccesses;
        mRecentAppsMaxCount = recentAppsMaxCount;
        mSensorPrivacyManager = sensorPrivacyManager;
    }

    @Override
    protected Class<PreferenceCategory> getPreferenceType() {
        return PreferenceCategory.class;
    }

    @Override
    protected void onStartInternal() {
        mSensorPrivacyManager.addSensorPrivacyListener(
                SensorPrivacyManager.Sensors.MICROPHONE, mListener);
    }

    @Override
    protected void onStopInternal() {
        mSensorPrivacyManager.removeSensorPrivacyListener(SensorPrivacyManager.Sensors.MICROPHONE,
                mListener);
    }

    @Override
    public void updateState(PreferenceCategory preference) {
        super.updateState(preference);
        if (mSensorPrivacyManager.isSensorPrivacyEnabled(
                SensorPrivacyManager.Sensors.MICROPHONE)) {
            getPreference().setVisible(false);
            return;
        }
        getPreference().setVisible(true);
        List<RecentAppOpsAccess.Access> sortedRecentMicrophoneAccesses = loadData();
        updateUi(sortedRecentMicrophoneAccesses);
    }

    private List<RecentAppOpsAccess.Access> loadData() {
        return mRecentMicrophoneAccesses.getAppListSorted(/* showSystem= */ false);
    }

    private void updateUi(List<RecentAppOpsAccess.Access> sortedRecentMicrophoneAccesses) {
        // remove any already added preferences
        for (Preference addedPreference : mAddedPreferences) {
            getPreference().removePreference(addedPreference);
        }
        mAddedPreferences.clear();

        if (sortedRecentMicrophoneAccesses.isEmpty()) {
            Preference emptyPreference = createNoRecentAccessPreference();
            getPreference().addPreference(emptyPreference);
            mAddedPreferences.add(emptyPreference);
        } else {
            int count = Math.min(sortedRecentMicrophoneAccesses.size(), mRecentAppsMaxCount);
            for (int i = 0; i < count; i++) {
                RecentAppOpsAccess.Access request = sortedRecentMicrophoneAccesses.get(i);
                Preference appPreference = createAppPreference(
                        getContext(),
                        request);
                getPreference().addPreference(appPreference);
                mAddedPreferences.add(appPreference);
            }
        }
    }

    private Preference createNoRecentAccessPreference() {
        Preference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.microphone_no_recent_access);
        preference.setSelectable(false);
        return preference;
    }

    /**
     * Create a {@link CarUiPreference} for an app with it's last access time and a link to its
     * microphone permission settings.
     */
    private static CarUiPreference createAppPreference(Context prefContext,
            RecentAppOpsAccess.Access access) {
        CarUiPreference pref = new CarUiPreference(prefContext);
        pref.setIcon(access.icon);
        pref.setTitle(access.label);
        pref.setSummary(StringUtil.formatRelativeTime(prefContext,
                System.currentTimeMillis() - access.accessFinishTime, false,
                RelativeDateTimeFormatter.Style.SHORT));
        pref.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
            intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME,
                    Manifest.permission_group.MICROPHONE);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, access.packageName);
            intent.putExtra(Intent.EXTRA_USER, access.userHandle);
            prefContext.startActivity(intent);
            return true;
        });
        return pref;
    }
}
