package mihon.desktop.extension

import com.googlecode.d2j.DexLabel
import com.googlecode.d2j.Field
import com.googlecode.d2j.Method
import com.googlecode.d2j.reader.BaseDexFileReader
import com.googlecode.d2j.reader.Op
import com.googlecode.d2j.visitors.DexClassVisitor
import com.googlecode.d2j.visitors.DexCodeVisitor
import com.googlecode.d2j.visitors.DexFileVisitor
import com.googlecode.d2j.visitors.DexMethodVisitor

/**
 * Reads a dex file for dex2jar, retargeting constructor calls R8 inlined before dex2jar sees them.
 *
 * R8 allocates `new-instance T` and initializes it with an ancestor's `<init>` when T's own constructor was
 * trivial. ART accepts that; dex2jar turns it into `new Ancestor()`, so the object loses its real type (an
 * interceptor becomes a plain Object, a concrete source becomes its abstract parent). Calling `T.<init>` instead
 * keeps the type; AndroidBytecodeFixer then adds the constructors R8 removed.
 */
class DexConstructorFixer(private val delegate: BaseDexFileReader) : BaseDexFileReader by delegate {

    override fun accept(visitor: DexFileVisitor) = delegate.accept(FileVisitor(visitor))

    override fun accept(visitor: DexFileVisitor, config: Int) = delegate.accept(FileVisitor(visitor), config)

    override fun accept(visitor: DexFileVisitor, classIdx: Int, config: Int) =
        delegate.accept(FileVisitor(visitor), classIdx, config)

    private class FileVisitor(visitor: DexFileVisitor) : DexFileVisitor(visitor) {
        override fun visit(access: Int, className: String?, superClass: String?, interfaceNames: Array<String>?) =
            super.visit(access, className, superClass, interfaceNames)?.let(::ClassVisitor)
    }

    private class ClassVisitor(visitor: DexClassVisitor) : DexClassVisitor(visitor) {
        override fun visitMethod(accessFlags: Int, method: Method?) =
            super.visitMethod(accessFlags, method)?.let(::MethodVisitor)
    }

    private class MethodVisitor(visitor: DexMethodVisitor) : DexMethodVisitor(visitor) {
        override fun visitCode() = super.visitCode()?.let(::CodeVisitor)
    }

    /**
     * Tracks which registers hold a fresh `new-instance`. Straight-line only: any label (a jump target) and any
     * write to a register drops what is known, so an uncertain case is left exactly as R8 wrote it.
     */
    private class CodeVisitor(visitor: DexCodeVisitor) : DexCodeVisitor(visitor) {
        private val allocated = HashMap<Int, String>()

        override fun visitTypeStmt(op: Op, a: Int, b: Int, type: String) {
            if (op == Op.NEW_INSTANCE) allocated[a] = type else allocated.remove(a)
            super.visitTypeStmt(op, a, b, type)
        }

        override fun visitStmt2R(op: Op, a: Int, b: Int) {
            val moved = allocated[b]
            if (moved != null && op in MOVE_OBJECT) allocated[a] = moved else allocated.remove(a)
            super.visitStmt2R(op, a, b)
        }

        override fun visitMethodStmt(op: Op, args: IntArray, method: Method) {
            val type = args.firstOrNull()?.let(allocated::get)
            val target = if (op in INVOKE_DIRECT && method.name == "<init>" && type != null && type != method.owner) {
                Method(type, method.name, method.parameterTypes, method.returnType)
            } else {
                method
            }
            if (method.name == "<init>") args.firstOrNull()?.let(allocated::remove)
            super.visitMethodStmt(op, args, target)
        }

        override fun visitLabel(label: DexLabel?) {
            allocated.clear()
            super.visitLabel(label)
        }

        override fun visitConstStmt(op: Op?, a: Int, value: Any?) {
            allocated.remove(a)
            super.visitConstStmt(op, a, value)
        }

        override fun visitStmt1R(op: Op?, reg: Int) {
            allocated.remove(reg)
            super.visitStmt1R(op, reg)
        }

        override fun visitStmt3R(op: Op?, a: Int, b: Int, c: Int) {
            allocated.remove(a)
            super.visitStmt3R(op, a, b, c)
        }

        override fun visitStmt2R1N(op: Op?, distReg: Int, srcReg: Int, content: Int) {
            allocated.remove(distReg)
            super.visitStmt2R1N(op, distReg, srcReg, content)
        }

        override fun visitFieldStmt(op: Op?, a: Int, b: Int, field: Field?) {
            allocated.remove(a)
            super.visitFieldStmt(op, a, b, field)
        }
    }

    private companion object {
        val INVOKE_DIRECT = setOf(Op.INVOKE_DIRECT, Op.INVOKE_DIRECT_RANGE)
        val MOVE_OBJECT = setOf(Op.MOVE_OBJECT, Op.MOVE_OBJECT_FROM16, Op.MOVE_OBJECT_16)
    }
}
