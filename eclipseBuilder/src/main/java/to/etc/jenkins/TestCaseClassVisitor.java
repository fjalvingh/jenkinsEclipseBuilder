package to.etc.jenkins;

import org.objectweb.asm.*;

public class TestCaseClassVisitor implements ClassVisitor {
	private boolean m_found;

	private String m_name;

	private MethodVisitor m_methodVisitor;

	public TestCaseClassVisitor() {
		m_methodVisitor = new MethodVisitor() {

			public void visitVarInsn(int arg0, int arg1) {}

			public void visitTypeInsn(int arg0, String arg1) {}

			public void visitTryCatchBlock(Label arg0, Label arg1, Label arg2, String arg3) {}

			public void visitTableSwitchInsn(int arg0, int arg1, Label arg2, Label[] arg3) {}

			public AnnotationVisitor visitParameterAnnotation(int arg0, String arg1, boolean arg2) {
				return null;
			}

			public void visitMultiANewArrayInsn(String arg0, int arg1) {}

			public void visitMethodInsn(int arg0, String arg1, String arg2, String arg3) {}

			public void visitMaxs(int arg0, int arg1) {}

			public void visitLookupSwitchInsn(Label arg0, int[] arg1, Label[] arg2) {}

			public void visitLocalVariable(String arg0, String arg1, String arg2, Label arg3, Label arg4, int arg5) {}

			public void visitLineNumber(int arg0, Label arg1) {}

			public void visitLdcInsn(Object arg0) {}

			public void visitLabel(Label arg0) {}

			public void visitJumpInsn(int arg0, Label arg1) {}

			public void visitIntInsn(int arg0, int arg1) {}

			public void visitInsn(int arg0) {}

			public void visitIincInsn(int arg0, int arg1) {}

			public void visitFieldInsn(int arg0, String arg1, String arg2, String arg3) {}

			public void visitEnd() {}

			public void visitCode() {}

			public void visitAttribute(Attribute arg0) {}

			public AnnotationVisitor visitAnnotationDefault() {
				return null;
			}

			public AnnotationVisitor visitAnnotation(String desc, boolean arg1) {
//				System.out.println("Annotation: " + desc + " in " + m_name);
				if("Lorg/junit/Test;".equals(desc)) {
					m_found = true;
					throw new RuntimeException("Done");
				}
				return null;
			}
		};
	}

	public String getName() {
		return m_name;
	}

	public boolean isFound() {
		return m_found;
	}

	public void reset() {
		m_found = false;
	}

	public void visitSource(String source, String debug) {}

	public void visitOuterClass(String owner, String name, String desc) {}

	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		return m_methodVisitor;
	}

	public void visitInnerClass(String name, String outerName, String innerName, int access) {}

	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		return null;
	}

	public void visitEnd() {}

	public void visitAttribute(Attribute attr) {}

	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
//		System.out.println("Annotation: " + desc + " in " + m_name);
		if("Lorg/junit/Test;".equals(desc)) {
			m_found = true;
			throw new RuntimeException("Done");
		}
		return null;
	}

	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		m_name = name.replace('/', '.');
//		System.out.println("classname: "+name);
	}
}
