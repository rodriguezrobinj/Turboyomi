package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import mihon.desktop.app.security.AppSecurity
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { context.appGraph.securityPreferences }
        // desktop: no telemetry, so no Firebase group
        return listOf(getSecurityGroup(securityPreferences))
    }

    @Composable
    private fun getSecurityGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        // desktop: Windows Hello, asked off the UI thread
        val authSupported by produceState(false) { value = withContext(Dispatchers.IO) { AppSecurity.helloAvailable } }
        val useAuthPref = securityPreferences.useAuthenticator
        val useAuth by useAuthPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_security),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    visible = authSupported,
                    onValueChanged = {
                        AppSecurity.verify(context.stringResource(MR.strings.lock_with_biometrics))
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter,
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        },
                    title = stringResource(MR.strings.lock_when_idle),
                    visible = authSupported && useAuth,
                    onValueChanged = {
                        AppSecurity.verify(context.stringResource(MR.strings.lock_when_idle))
                    },
                ),

                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent,
                    title = stringResource(MR.strings.hide_notification_content),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen,
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.secure_screen),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
            ),
        )
    }
}

private val LockAfterValues = listOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
