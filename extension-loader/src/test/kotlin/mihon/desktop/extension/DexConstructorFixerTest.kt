package mihon.desktop.extension

import com.googlecode.d2j.DexConstants.ACC_CONSTRUCTOR
import com.googlecode.d2j.DexConstants.ACC_PUBLIC
import com.googlecode.d2j.DexConstants.ACC_STATIC
import com.googlecode.d2j.Method
import com.googlecode.d2j.dex.writer.DexFileWriter
import com.googlecode.d2j.reader.Op
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.vendor.Dex2Jar
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes

class DexConstructorFixerTest {

    private val noArgs = emptyArray<String>()

    /**
     * What R8 emits when it inlines Leaf's trivial constructor: `new-instance Leaf` initialized with Base's
     * constructor. ART accepts it; dex2jar used to turn it into `new Base()`, losing the real type.
     */
    private fun dexWithInlinedConstructor(): Path {
        val writer = DexFileWriter()
        writer.visit(ACC_PUBLIC, "Lt6/Base;", "Ljava/lang/Object;", null)!!.apply {
            visitMethod(ACC_PUBLIC or ACC_CONSTRUCTOR, Method("Lt6/Base;", "<init>", noArgs, "V"))!!.apply {
                visitCode()!!.apply {
                    visitRegister(1)
                    visitMethodStmt(
                        Op.INVOKE_DIRECT,
                        intArrayOf(0),
                        Method("Ljava/lang/Object;", "<init>", noArgs, "V"),
                    )
                    visitStmt0R(Op.RETURN_VOID)
                    visitEnd()
                }
                visitEnd()
            }
            visitEnd()
        }
        writer.visit(ACC_PUBLIC, "Lt6/Leaf;", "Lt6/Base;", null)!!.visitEnd()
        writer.visit(ACC_PUBLIC, "Lt6/Factory;", "Ljava/lang/Object;", null)!!.apply {
            visitMethod(
                ACC_PUBLIC or ACC_STATIC,
                Method("Lt6/Factory;", "make", noArgs, "Ljava/lang/Object;"),
            )!!.apply {
                visitCode()!!.apply {
                    visitRegister(1)
                    visitTypeStmt(Op.NEW_INSTANCE, 0, 0, "Lt6/Leaf;")
                    visitMethodStmt(Op.INVOKE_DIRECT, intArrayOf(0), Method("Lt6/Base;", "<init>", noArgs, "V"))
                    visitStmt1R(Op.RETURN_OBJECT, 0)
                    visitEnd()
                }
                visitEnd()
            }
            visitEnd()
        }
        writer.visitEnd()
        return createTempDirectory("dex José").resolve("classes.dex").also { it.writeBytes(writer.toByteArray()) }
    }

    @Test
    fun `allocations keep their type when R8 inlined the constructor`() {
        val dex = dexWithInlinedConstructor()
        val jar = dex.resolveSibling("ext.jar")

        Dex2Jar.convert(dex, jar)
        AndroidBytecodeFixer.fix(jar)

        URLClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader).use {
            it.loadClass("t6.Factory").getMethod("make").invoke(null).javaClass.name
        } shouldBe "t6.Leaf"
    }
}
