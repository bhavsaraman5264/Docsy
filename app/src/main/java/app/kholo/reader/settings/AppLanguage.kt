package app.kholo.reader.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

private const val PREFS = "language"
private const val KEY = "tag"
private const val ANDROID_T = 33

enum class AppLanguage(val tag: String) { System(""), English("en"), Hindi("hi") }

/**
 * App ki apni bhasha. Android 13+ par system khud sambhalta hai. Purane phones par har Activity
 * apna context [wrap] karti hai. Yeh chhoti SharedPreferences hai, kyunki Activity banne se pehle chahiye.
 */
object LanguageStore {
    fun current(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.System
    }

    fun set(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
        if (Build.VERSION.SDK_INT >= ANDROID_T) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        } else {
            activity.recreate()
        }
    }

    fun wrap(base: Context): Context {
        val language = current(base)
        if (Build.VERSION.SDK_INT >= ANDROID_T || language == AppLanguage.System) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return base.createConfigurationContext(config)
    }
}
