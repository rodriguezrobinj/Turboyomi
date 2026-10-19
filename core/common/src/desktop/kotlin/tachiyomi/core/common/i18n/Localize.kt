package tachiyomi.core.common.i18n

import android.content.Context
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Plural
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

// Desktop copy of Mihon's Localize.kt: same signatures, resolved with moko's JVM `localized()`.

@Suppress("UnusedReceiverParameter")
fun Context.stringResource(resource: StringResource): String {
    return StringDesc.Resource(resource).localized().fixed()
}

@Suppress("UnusedReceiverParameter")
fun Context.stringResource(resource: StringResource, vararg args: Any): String {
    return StringDesc.ResourceFormatted(resource, *args).localized().fixed()
}

@Suppress("UnusedReceiverParameter")
fun Context.pluralStringResource(resource: PluralsResource, count: Int): String {
    return StringDesc.Plural(resource, count).localized().fixed()
}

@Suppress("UnusedReceiverParameter")
fun Context.pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any): String {
    return StringDesc.PluralFormatted(resource, count, *args).localized().fixed()
}

// TODO: janky workaround for https://github.com/icerockdev/moko-resources/issues/337
private fun String.fixed() =
    this.replace("""\""", """"""")
