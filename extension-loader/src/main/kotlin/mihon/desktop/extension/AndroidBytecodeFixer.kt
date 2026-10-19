package mihon.desktop.extension

import logcat.LogPriority
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue
import tachiyomi.core.common.util.system.logcat
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Rewrites dex2jar output that ART accepts but the JVM verifier rejects. R8 inlines trivial constructors, so a
 * constructor or an allocation calls an ancestor's `<init>` directly and the skipped constructors are removed;
 * and dex2jar's stack map frames are not always consistent.
 *
 * Fixes, in order: constructor calls redirected to the class the JVM expects, missing constructors added as
 * bridges to the direct superclass, then frames recomputed for every class. Allocations dex2jar would retype
 * are kept by DexConstructorFixer, before conversion.
 */
object AndroidBytecodeFixer {

    fun fix(jar: Path, appLoader: ClassLoader = AndroidBytecodeFixer::class.java.classLoader) {
        FileSystems.newFileSystem(jar).use { fs ->
            val files = Files.walk(fs.getPath("/")).use { paths ->
                paths.filter { it.name.endsWith(".class") }.toList()
            }
            val nodes = files.associateWith { path ->
                ClassNode().also { ClassReader(path.readBytes()).accept(it, ClassReader.SKIP_FRAMES) }
            }
            val classes = nodes.values.associateBy { it.name }
            val hierarchy = Hierarchy(classes, appLoader)

            classes.values.forEach { redirectConstructorCalls(it, classes, hierarchy) }

            nodes.forEach { (path, node) ->
                runCatching {
                    val writer = object : ClassWriter(COMPUTE_FRAMES) {
                        override fun getCommonSuperClass(type1: String, type2: String) =
                            hierarchy.commonSuperClass(type1, type2)
                    }
                    node.accept(writer)
                    writer.toByteArray()
                }
                    .onSuccess { path.writeBytes(it) }
                    .onFailure { logcat(LogPriority.WARN, it) { "Left ${node.name} as dex2jar wrote it" } }
            }
        }
    }

    private fun redirectConstructorCalls(owner: ClassNode, classes: Map<String, ClassNode>, hierarchy: Hierarchy) {
        // Bridges may be added to this class while its methods are walked
        for (method in owner.methods.toList()) {
            val calls = method.instructions.filterIsInstance<MethodInsnNode>()
                .filter { it.opcode == INVOKESPECIAL && it.name == "<init>" }
            if (calls.isEmpty()) continue
            val frames = runCatching { Analyzer(SourceInterpreter()).analyze(owner.name, method) }.getOrNull()

            for (call in calls) {
                if (frames != null) {
                    expectedOwner(call, owner, method, frames)
                        ?.takeIf { it != call.owner && call.owner in hierarchy.ancestors(it) }
                        ?.let { call.owner = it }
                }
                // Also covers calls DexConstructorFixer retargeted to a class whose trivial constructor R8 removed
                ensureConstructor(call.owner, call.desc, classes)
            }
        }
    }

    /** The class whose `<init>` the JVM requires for this call: the allocated type, or the direct superclass. */
    private fun expectedOwner(
        call: MethodInsnNode,
        owner: ClassNode,
        method: MethodNode,
        frames: Array<Frame<SourceValue>?>,
    ): String? {
        val frame = frames[method.instructions.indexOf(call)] ?: return null
        val receiver = frame.getStack(frame.stackSize - 1 - Type.getArgumentTypes(call.desc).size)
        return when (val origin = receiver.insns.singleOrNull()?.let { origin(it, method, frames) }) {
            is Origin.New -> origin.type
            // this(...) delegation is legal; anything else must go to the direct superclass
            Origin.This -> if (call.owner == owner.name) null else owner.superName
            null -> null
        }
    }

    private sealed interface Origin {
        data class New(val type: String) : Origin
        data object This : Origin
    }

    /** Follows a receiver back through dup/astore/aload to the `new` or the constructor's own `this`. */
    private fun origin(
        insn: AbstractInsnNode,
        method: MethodNode,
        frames: Array<Frame<SourceValue>?>,
        depth: Int = 0,
    ): Origin? {
        if (depth > MAX_TRACE_DEPTH) return null
        val frame = frames[method.instructions.indexOf(insn)] ?: return null
        fun top() = frame.getStack(frame.stackSize - 1).insns.singleOrNull()
        return when (insn.opcode) {
            NEW -> Origin.New((insn as TypeInsnNode).desc)
            DUP, ASTORE -> top()?.let { origin(it, method, frames, depth + 1) }
            ALOAD -> {
                val slot = (insn as VarInsnNode).`var`
                val local = frame.getLocal(slot)
                when {
                    // Never assigned in the method: it's a parameter, and slot 0 of a constructor is `this`
                    local.insns.isEmpty() ->
                        Origin.This.takeIf { slot == 0 && method.name == "<init>" && method.access and ACC_STATIC == 0 }
                    else -> local.insns.singleOrNull()?.let { origin(it, method, frames, depth + 1) }
                }
            }
            else -> null
        }
    }

    /**
     * Makes `[start].<init>(desc)` exist, adding a bridge to its direct superclass in every class of the jar up
     * the chain that lacks one (R8 removes trivial constructors). App classes keep their own constructors.
     */
    private fun ensureConstructor(start: String, desc: String, classes: Map<String, ClassNode>) {
        var current = start
        while (true) {
            val node = classes[current] ?: return
            if (node.methods.any { it.name == "<init>" && it.desc == desc }) return
            val parent = node.superName ?: return
            node.methods.add(bridgeConstructor(parent, desc))
            current = parent
        }
    }

    private fun bridgeConstructor(parent: String, desc: String) =
        MethodNode(ACC_PUBLIC or ACC_SYNTHETIC, "<init>", desc, null, null).apply {
            instructions.add(VarInsnNode(ALOAD, 0))
            var slot = 1
            for (type in Type.getArgumentTypes(desc)) {
                instructions.add(VarInsnNode(type.getOpcode(ILOAD), slot))
                slot += type.size
            }
            instructions.add(MethodInsnNode(INVOKESPECIAL, parent, "<init>", desc, false))
            instructions.add(InsnNode(RETURN))
        }

    /** Superclass lookups across the extension's own classes and the app's classpath (without initializing). */
    private class Hierarchy(private val classes: Map<String, ClassNode>, private val appLoader: ClassLoader) {
        private fun appClass(name: String) =
            runCatching { Class.forName(name.replace('/', '.'), false, appLoader) }.getOrNull()

        fun superOf(name: String): String? =
            classes[name]?.superName ?: appClass(name)?.superclass?.name?.replace('.', '/')

        fun ancestors(name: String): List<String> = generateSequence(superOf(name), ::superOf).toList()

        private fun isInterface(name: String): Boolean =
            classes[name]?.let { it.access and ACC_INTERFACE != 0 } ?: (appClass(name)?.isInterface == true)

        fun commonSuperClass(a: String, b: String): String {
            if (a == b) return a
            if (isInterface(a) || isInterface(b)) return OBJECT
            val ofB = (listOf(b) + ancestors(b)).toSet()
            return (listOf(a) + ancestors(a)).firstOrNull { it in ofB } ?: OBJECT
        }
    }

    private const val OBJECT = "java/lang/Object"
    private const val MAX_TRACE_DEPTH = 16
}
