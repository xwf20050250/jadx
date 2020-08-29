package jadx.core.codegen;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.plugins.input.data.AccessFlags;
import jadx.api.plugins.input.data.annotations.EncodedValue;
import jadx.core.Consts;
import jadx.core.Jadx;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.annotations.MethodParameters;
import jadx.core.dex.attributes.nodes.JumpInfo;
import jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.instructions.ConstStringNode;
import jadx.core.dex.instructions.IfNode;
import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.CodeVar;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.instructions.args.SSAVar;
import jadx.core.dex.nodes.IMethodDetails;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.trycatch.CatchAttr;
import jadx.core.dex.visitors.DepthTraversal;
import jadx.core.dex.visitors.IDexTreeVisitor;
import jadx.core.utils.CodeGenUtils;
import jadx.core.utils.InsnUtils;
import jadx.core.utils.Utils;
import jadx.core.utils.exceptions.CodegenException;
import jadx.core.utils.exceptions.DecodeException;
import jadx.core.utils.exceptions.JadxOverflowException;

import static jadx.core.codegen.MethodGen.FallbackOption.BLOCK_DUMP;
import static jadx.core.codegen.MethodGen.FallbackOption.COMMENTED_DUMP;
import static jadx.core.codegen.MethodGen.FallbackOption.FALLBACK_MODE;

public class MethodGen {
	private static final Logger LOG = LoggerFactory.getLogger(MethodGen.class);

	private final MethodNode mth;
	private final ClassGen classGen;
	private final AnnotationGen annotationGen;
	private final NameGen nameGen;

	public MethodGen(ClassGen classGen, MethodNode mth) {
		this.mth = mth;
		this.classGen = classGen;
		this.annotationGen = classGen.getAnnotationGen();
		this.nameGen = new NameGen(mth, classGen.isFallbackMode());
	}

	public ClassGen getClassGen() {
		return classGen;
	}

	public NameGen getNameGen() {
		return nameGen;
	}

	public MethodNode getMethodNode() {
		return mth;
	}

	public boolean addDefinition(CodeWriter code) {
		if (mth.getMethodInfo().isClassInit()) {
			code.attachDefinition(mth);
			code.startLine("static");
			return true;
		}
		if (mth.contains(AFlag.ANONYMOUS_CONSTRUCTOR)) {
			// don't add method name and arguments
			code.startLine();
			code.attachDefinition(mth);
			return false;
		}
		if (Consts.DEBUG_USAGE) {
			ClassGen.addMthUsageInfo(code, mth);
		}
		addOverrideAnnotation(code, mth);
		annotationGen.addForMethod(code, mth);

		AccessInfo clsAccFlags = mth.getParentClass().getAccessFlags();
		AccessInfo ai = mth.getAccessFlags();
		// don't add 'abstract' and 'public' to methods in interface
		if (clsAccFlags.isInterface()) {
			ai = ai.remove(AccessFlags.ABSTRACT);
			ai = ai.remove(AccessFlags.PUBLIC);
		}
		// don't add 'public' for annotations
		if (clsAccFlags.isAnnotation()) {
			ai = ai.remove(AccessFlags.PUBLIC);
		}

		if (mth.getMethodInfo().hasAlias() && !ai.isConstructor()) {
			CodeGenUtils.addRenamedComment(code, mth, mth.getName());
		}
		if (mth.contains(AFlag.INCONSISTENT_CODE)) {
			code.startLine("/* Code decompiled incorrectly, please refer to instructions dump. */");
		}

		code.startLineWithNum(mth.getSourceLine());
		code.add(ai.makeString());
		if (Consts.DEBUG) {
			code.add(mth.isVirtual() ? "/* virtual */ " : "/* direct */ ");
		}
		if (clsAccFlags.isInterface() && !mth.isNoCode()) {
			// add 'default' for method with code in interface
			code.add("default ");
		}

		if (classGen.addGenericTypeParameters(code, mth.getTypeParameters(), false)) {
			code.add(' ');
		}
		if (ai.isConstructor()) {
			code.attachDefinition(mth);
			code.add(classGen.getClassNode().getShortName()); // constructor
		} else {
			classGen.useType(code, mth.getReturnType());
			code.add(' ');
			code.attachDefinition(mth);
			code.add(mth.getAlias());
		}
		code.add('(');

		List<RegisterArg> args = mth.getArgRegs();
		if (mth.getMethodInfo().isConstructor()
				&& mth.getParentClass().contains(AType.ENUM_CLASS)) {
			if (args.size() == 2) {
				args = Collections.emptyList();
			} else if (args.size() > 2) {
				args = args.subList(2, args.size());
			} else {
				mth.addComment("JADX WARN: Incorrect number of args for enum constructor: " + args.size() + " (expected >= 2)");
			}
		} else if (mth.contains(AFlag.SKIP_FIRST_ARG)) {
			args = args.subList(1, args.size());
		}
		addMethodArguments(code, args);
		code.add(')');

		annotationGen.addThrows(mth, code);

		// add default value if in annotation class
		if (mth.getParentClass().getAccessFlags().isAnnotation()) {
			EncodedValue def = annotationGen.getAnnotationDefaultValue(mth.getName());
			if (def != null) {
				code.add(" default ");
				annotationGen.encodeValue(mth.root(), code, def);
			}
		}
		return true;
	}

	private void addOverrideAnnotation(CodeWriter code, MethodNode mth) {
		MethodOverrideAttr overrideAttr = mth.get(AType.METHOD_OVERRIDE);
		if (overrideAttr == null) {
			return;
		}
		code.startLine("@Override");
		code.add(" // ");
		Iterator<IMethodDetails> it = overrideAttr.getOverrideList().iterator();
		while (it.hasNext()) {
			IMethodDetails methodDetails = it.next();
			code.add(methodDetails.getMethodInfo().getDeclClass().getAliasFullName());
			if (it.hasNext()) {
				code.add(", ");
			}
		}
	}

	private void addMethodArguments(CodeWriter code, List<RegisterArg> args) {
		MethodParameters paramsAnnotation = mth.get(AType.ANNOTATION_MTH_PARAMETERS);
		int i = 0;
		Iterator<RegisterArg> it = args.iterator();
		while (it.hasNext()) {
			RegisterArg mthArg = it.next();
			SSAVar ssaVar = mthArg.getSVar();
			CodeVar var;
			if (ssaVar == null) {
				// null for abstract or interface methods
				var = CodeVar.fromMthArg(mthArg, classGen.isFallbackMode());
			} else {
				var = ssaVar.getCodeVar();
			}

			// add argument annotation
			if (paramsAnnotation != null) {
				annotationGen.addForParameter(code, paramsAnnotation, i);
			}
			if (var.isFinal()) {
				code.add("final ");
			}
			ArgType argType;
			ArgType varType = var.getType();
			if (varType == null || varType == ArgType.UNKNOWN) {
				// occur on decompilation errors
				argType = mthArg.getInitType();
			} else {
				argType = varType;
			}
			if (!it.hasNext() && mth.getAccessFlags().isVarArgs()) {
				// change last array argument to varargs
				if (argType.isArray()) {
					ArgType elType = argType.getArrayElement();
					classGen.useType(code, elType);
					code.add("...");
				} else {
					mth.addComment("JADX INFO: Last argument in varargs method is not array: " + var);
					classGen.useType(code, argType);
				}
			} else {
				classGen.useType(code, argType);
			}
			code.add(' ');
			code.add(nameGen.assignArg(var));

			i++;
			if (it.hasNext()) {
				code.add(", ");
			}
		}
	}

	public void addInstructions(CodeWriter code) throws CodegenException {
		if (mth.root().getArgs().isFallbackMode()) {
			addFallbackMethodCode(code, FALLBACK_MODE);
		} else if (classGen.isFallbackMode()) {
			dumpInstructions(code);
		} else {
			addRegionInsns(code);
		}
	}

	public void addRegionInsns(CodeWriter code) throws CodegenException {
		try {
			RegionGen regionGen = new RegionGen(this);
			regionGen.makeRegion(code, mth.getRegion());
		} catch (StackOverflowError | BootstrapMethodError e) {
			mth.addError("Method code generation error", new JadxOverflowException("StackOverflow"));
			classGen.insertDecompilationProblems(code, mth);
			dumpInstructions(code);
		} catch (Exception e) {
			if (mth.getParentClass().getTopParentClass().contains(AFlag.RESTART_CODEGEN)) {
				throw e;
			}
			mth.addError("Method code generation error", e);
			classGen.insertDecompilationProblems(code, mth);
			dumpInstructions(code);
		}
	}

	public void dumpInstructions(CodeWriter code) {
		code.startLine("/*");
		addFallbackMethodCode(code, COMMENTED_DUMP);
		code.startLine("*/");

		code.startLine("throw new UnsupportedOperationException(\"Method not decompiled: ")
				.add(mth.getParentClass().getClassInfo().getAliasFullName())
				.add('.')
				.add(mth.getAlias())
				.add('(')
				.add(Utils.listToString(mth.getMethodInfo().getArgumentsTypes()))
				.add("):")
				.add(mth.getMethodInfo().getReturnType().toString())
				.add("\");");
	}

	public void addFallbackMethodCode(CodeWriter code, FallbackOption fallbackOption) {
		// load original instructions
		try {
			mth.unload();
			mth.load();
			for (IDexTreeVisitor visitor : Jadx.getFallbackPassesList()) {
				DepthTraversal.visit(visitor, mth);
			}
		} catch (DecodeException e) {
			LOG.error("Error reload instructions in fallback mode:", e);
			code.startLine("// Can't load method instructions: " + e.getMessage());
			return;
		}
		InsnNode[] insnArr = mth.getInstructions();
		if (insnArr == null) {
			code.startLine("// Can't load method instructions.");
			return;
		}
		code.incIndent();
		if (mth.getThisArg() != null) {
			code.startLine(nameGen.useArg(mth.getThisArg())).add(" = this;");
		}
		addFallbackInsns(code, mth, insnArr, fallbackOption);
		code.decIndent();
	}

	public enum FallbackOption {
		FALLBACK_MODE,
		BLOCK_DUMP,
		COMMENTED_DUMP
	}

	public static void addFallbackInsns(CodeWriter code, MethodNode mth, InsnNode[] insnArr, FallbackOption option) {
		InsnGen insnGen = new InsnGen(getFallbackMethodGen(mth), true);
		boolean attachInsns = mth.root().getArgs().isJsonOutput();
		InsnNode prevInsn = null;
		for (InsnNode insn : insnArr) {
			if (insn == null) {
				continue;
			}
			if (option != BLOCK_DUMP && needLabel(insn, prevInsn)) {
				code.decIndent();
				code.startLine(getLabelName(insn.getOffset()) + ':');
				code.incIndent();
			}
			if (insn.getType() == InsnType.NOP) {
				continue;
			}
			try {
				boolean escapeComment = isCommentEscapeNeeded(insn, option);
				if (escapeComment) {
					code.decIndent();
					code.startLine("*/");
					code.startLine("//  ");
				} else {
					code.startLine();
				}
				if (attachInsns) {
					code.attachLineAnnotation(insn);
				}
				RegisterArg resArg = insn.getResult();
				if (resArg != null) {
					ArgType varType = resArg.getInitType();
					if (varType.isTypeKnown()) {
						code.add(varType.toString()).add(' ');
					}
				}
				insnGen.makeInsn(insn, code, InsnGen.Flags.INLINE);
				if (escapeComment) {
					code.startLine("/*");
					code.incIndent();
				}

				CatchAttr catchAttr = insn.get(AType.CATCH_BLOCK);
				if (catchAttr != null) {
					code.add("     // " + catchAttr);
				}
			} catch (CodegenException e) {
				LOG.debug("Error generate fallback instruction: ", e.getCause());
				code.startLine("// error: " + insn);
			}
			prevInsn = insn;
		}
	}

	private static boolean isCommentEscapeNeeded(InsnNode insn, FallbackOption option) {
		if (option == COMMENTED_DUMP) {
			if (insn.getType() == InsnType.CONST_STR) {
				String str = ((ConstStringNode) insn).getString();
				return str.contains("*/");
			}
		}
		return false;
	}

	private static boolean needLabel(InsnNode insn, InsnNode prevInsn) {
		if (insn.contains(AType.EXC_HANDLER)) {
			return true;
		}
		if (insn.contains(AType.JUMP)) {
			// don't add label for ifs else branch
			if (prevInsn != null && prevInsn.getType() == InsnType.IF) {
				List<JumpInfo> jumps = insn.getAll(AType.JUMP);
				if (jumps.size() == 1) {
					JumpInfo jump = jumps.get(0);
					if (jump.getSrc() == prevInsn.getOffset() && jump.getDest() == insn.getOffset()) {
						int target = ((IfNode) prevInsn).getTarget();
						return insn.getOffset() == target;
					}
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Return fallback variant of method codegen
	 */
	public static MethodGen getFallbackMethodGen(MethodNode mth) {
		ClassGen clsGen = new ClassGen(mth.getParentClass(), null, false, true, true);
		return new MethodGen(clsGen, mth);
	}

	public static String getLabelName(int offset) {
		return "L_" + InsnUtils.formatOffset(offset);
	}
}
