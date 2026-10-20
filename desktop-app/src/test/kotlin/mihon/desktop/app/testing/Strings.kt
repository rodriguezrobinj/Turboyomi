package mihon.desktop.app.testing

import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

/** The text the UI shows for [res] in this JVM's locale, so tests don't depend on the machine's language. */
fun str(res: StringResource, vararg args: Any): String =
    if (args.isEmpty()) StringDesc.Resource(res).localized() else StringDesc.ResourceFormatted(res, *args).localized()
