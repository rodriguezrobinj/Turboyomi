package mihon.desktop.extension.vendor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import mihon.desktop.extension.DexConstructorFixer
import java.nio.file.Path
import kotlin.io.path.readBytes

class Dex2JarException(message: String) : Exception(message)

object Dex2Jar {
    /** Converts the dex code inside [apk] into [jar] and rewrites java.* classes that differ from Android's. */
    fun convert(apk: Path, jar: Path) {
        // DexConstructorFixer is this repo's addition: keeps allocation types R8's constructor inlining would lose
        val reader = DexConstructorFixer(MultiDexFileReader.open(apk.readBytes()))
        val handler = BaksmaliBaseDexExceptionHandler()
        Dex2jar
            .from(reader)
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .dontSanitizeNames(true)
            .computeFrames(true)
            .to(jar)
        if (handler.hasException()) {
            val errorFile = jar.resolveSibling("${jar.fileName}.dex2jar-error.txt")
            handler.dump(errorFile, emptyArray<String>())
            throw Dex2JarException("dex2jar failed, details in $errorFile")
        }
        BytecodeEditor.fixAndroidClasses(jar)
    }
}
