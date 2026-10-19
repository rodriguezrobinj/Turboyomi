package mihon.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.F_FULL
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.V1_8
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream

/**
 * Each test builds the shape R8 emits for Android (legal in ART) and checks the JVM rejects it before the fix and
 * runs it after.
 */
class AndroidBytecodeFixerTest {

    private class ClassSpec(val name: String, val bytes: ByteArray)

    private fun cls(
        name: String,
        superName: String = "java/lang/Object",
        access: Int = ACC_PUBLIC,
        body: ClassWriter.() -> Unit = {},
    ) = ClassSpec(
        name,
        ClassWriter(ClassWriter.COMPUTE_MAXS).run {
            visit(V1_8, access, name, null, superName, null)
            body()
            visitEnd()
            toByteArray()
        },
    )

    /** `<init>(desc)` that calls [target]`.<init>(desc)` on `this`, loading every argument. */
    private fun ClassWriter.ctor(target: String, desc: String = "()V") {
        visitMethod(ACC_PUBLIC, "<init>", desc, null, null).apply {
            visitCode()
            visitVarInsn(ALOAD, 0)
            if (desc == "(I)V") visitVarInsn(ILOAD, 1)
            visitMethodInsn(INVOKESPECIAL, target, "<init>", desc, false)
            visitInsn(RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    /** `static Object make()` doing `new [type]; dup; invokespecial [target].<init>(desc)`. */
    private fun ClassWriter.factory(type: String, target: String, desc: String = "()V") {
        visitMethod(ACC_PUBLIC or ACC_STATIC, "make", "()Ljava/lang/Object;", null, null).apply {
            visitCode()
            visitTypeInsn(NEW, type)
            visitInsn(DUP)
            if (desc == "(I)V") visitInsn(ICONST_1)
            visitMethodInsn(INVOKESPECIAL, target, "<init>", desc, false)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun jarOf(vararg classes: ClassSpec): Path {
        val jar = createTempDirectory("fixer José").resolve("ext.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            classes.forEach {
                out.putNextEntry(JarEntry("${it.name}.class"))
                out.write(it.bytes)
                out.closeEntry()
            }
        }
        return jar
    }

    private fun <T> withLoader(jar: Path, block: (ClassLoader) -> T): T =
        URLClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader).use(block)

    private fun newInstance(jar: Path, name: String): Any =
        withLoader(jar) { it.loadClass(name).getDeclaredConstructor().newInstance() }

    private fun callMake(jar: Path, name: String): Any = withLoader(jar) {
        try {
            it.loadClass(name).getMethod("make").invoke(null)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }

    @Test
    fun `constructor calling a grandparent constructor is redirected to the direct superclass`() {
        // R8 dropped Mid's trivial constructor and made Leaf call Object.<init> directly
        val jar = jarOf(
            cls("t1/Mid", access = ACC_PUBLIC or ACC_ABSTRACT),
            cls("t1/Leaf", superName = "t1/Mid") { ctor("java/lang/Object") },
        )
        shouldThrow<VerifyError> { newInstance(jar, "t1.Leaf") }

        AndroidBytecodeFixer.fix(jar)

        newInstance(jar, "t1.Leaf").javaClass.superclass.name shouldBe "t1.Mid"
    }

    @Test
    fun `allocation calling an ancestor constructor gets bridge constructors with the same arguments`() {
        val jar = jarOf(
            cls("t2/Base") { ctor("java/lang/Object") },
            cls("t2/Base2", superName = "t2/Base") {
                // Base2(int) exists only so the call has arguments to carry through the bridges
                visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, "t2/Base", "<init>", "()V", false)
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
            },
            cls("t2/Mid", superName = "t2/Base2"),
            cls("t2/Leaf", superName = "t2/Mid"),
            cls("t2/Factory") { factory(type = "t2/Leaf", target = "t2/Base2", desc = "(I)V") },
        )
        shouldThrow<VerifyError> { callMake(jar, "t2.Factory") }

        AndroidBytecodeFixer.fix(jar)

        callMake(jar, "t2.Factory").javaClass.name shouldBe "t2.Leaf"
    }

    @Test
    fun `wrong stack map frames are recomputed`() {
        // static int f(int x) { return x != 0 ? 1 : 0 } with a frame that claims x is an Object at the branch target
        val jar = jarOf(
            ClassSpec(
                "t5/Frames",
                ClassWriter(ClassWriter.COMPUTE_MAXS).run {
                    visit(V1_8, ACC_PUBLIC, "t5/Frames", null, "java/lang/Object", null)
                    visitMethod(ACC_PUBLIC or ACC_STATIC, "f", "(I)I", null, null).apply {
                        visitCode()
                        val zero = Label()
                        visitVarInsn(ILOAD, 0)
                        visitJumpInsn(IFEQ, zero)
                        visitInsn(ICONST_1)
                        visitInsn(IRETURN)
                        visitLabel(zero)
                        visitFrame(F_FULL, 1, arrayOf("java/lang/Object"), 0, null)
                        visitInsn(ICONST_0)
                        visitInsn(IRETURN)
                        visitMaxs(0, 0)
                        visitEnd()
                    }
                    visitEnd()
                    toByteArray()
                },
            ),
        )
        fun call() = withLoader(jar) { it.loadClass("t5.Frames").getMethod("f", Int::class.java).invoke(null, 0) }
        shouldThrow<VerifyError> { call() }

        AndroidBytecodeFixer.fix(jar)

        call() shouldBe 0
    }
}
