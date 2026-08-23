package io.github.nodyssey

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Every destination this app has, registered for the back stack's own serializer.
 *
 * `rememberNavBackStack` saves the stack across process death by serializing [NavKey]
 * polymorphically, and polymorphism needs to be told what the subtypes are. Android has an overload
 * that skips this — it reflects over the class name at runtime — but it is an `androidMain` overload
 * and does not exist on any other target, which is how step D1 found this: the desktop compilation
 * of `Navigation.kt` is what failed.
 *
 * So the list is written out, and the cost of that is that a new destination must be added here as
 * well as declared. The compiler will not say so; the symptom is a back stack that fails to restore
 * after the process is killed, and only for stacks containing the new key. It is the same list
 * `entryProvider` already has to carry, one file away.
 *
 * No `@SerialName` anywhere: the discriminator stays the fully-qualified class name, which is what
 * the reflective Android path was already writing. A saved stack from a previous build restores.
 */
internal val NavKeySavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(PostListKey::class)
                    subclass(SearchKey::class)
                    subclass(NotificationsKey::class)
                    subclass(ProfileKey::class)
                    subclass(SettingsKey::class)
                    subclass(ThemeSettingsKey::class)
                    subclass(DynamicColorKey::class)
                    subclass(NotificationSettingsKey::class)
                    subclass(ProxySettingsKey::class)
                    subclass(DohSettingsKey::class)
                    subclass(ImageHostKey::class)
                    subclass(AboutAppKey::class)
                    subclass(AboutCommunityKey::class)
                    subclass(PrivacyKey::class)
                    subclass(ChangelogKey::class)
                    subclass(OpenSourceLicensesKey::class)
                    subclass(AccountSettingsKey::class)
                    subclass(AccountProfileFieldsKey::class)
                    subclass(AccountSecurityKey::class)
                    subclass(AccountContactKey::class)
                    subclass(AccountBlockListKey::class)
                    subclass(AccountPreferencesKey::class)
                    subclass(PostComposerKey::class)
                    subclass(MessageThreadKey::class)
                    subclass(PostDetailKey::class)
                    subclass(UserSpaceKey::class)
                    subclass(FollowKey::class)
                    subclass(BookmarksKey::class)
                    subclass(ReadHistoryKey::class)
                    subclass(AssetsKey::class)
                    subclass(CreditKey::class)
                    subclass(StardustKey::class)
                    subclass(CommunityToolsKey::class)
                    subclass(AwardKey::class)
                    subclass(LuckyKey::class)
                    subclass(InviteKey::class)
                    subclass(RulingKey::class)
                    subclass(ImageViewerKey::class)
                    subclass(SignInKey::class)
                    subclass(WebKey::class)
                }
            }
    }
