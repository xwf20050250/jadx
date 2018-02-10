package jadx.core.dex.visitors;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;

/**
 * Check instruction correctness
 */
@JadxVisitor(
		name = "VerifyInstructions",
		desc = "Remove bad or useless instructions",
		runBefore = InitJumps.class
)
public class CheckInstructions extends AbstractVisitor {
	private static final Logger LOG = LoggerFactory.getLogger(CheckInstructions.class);

	@Override
	public void visit(MethodNode mth) {
		if (mth.isNoCode()) {
			return;
		}
		int mthRegsCount = mth.getRegsCount();
		InsnNode[] insns = mth.getInstructions();
		int count = insns.length;
		for (int i = 0; i < count; i++) {
			InsnNode insn = insns[i];
			if (insn == null) {
				continue;
			}
			boolean remove = checkRegisters(insn, mthRegsCount);
			if (!remove) {
				remove = checkInsns(insn);
			}
			if (remove) {
				InsnNode nop = new InsnNode(InsnType.NOP, 0);
				nop.setOffset(insn.getOffset());
				nop.copyAttributesFrom(insn);
				insns[i] = nop;
			}
		}
	}

	private boolean checkInsns(InsnNode insn) {
		switch (insn.getType()) {
			case IF:
//				IfNode ifNode = (IfNode) insn;
//				if (ifNode.getArg(0).equals(ifNode.getArg(1))) {
//					IfOp op = ifNode.getOp();
//					if (op == IfOp.GT || op == IfOp.LT || op == IfOp.NE) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Remove useless if instruction: {}", ifNode);
//						}
//						return true;
//					}
//				}
				break;
		}
		return false;
	}

	public boolean checkRegisters(InsnNode insnNode, int regsCount) {
		List<RegisterArg> list = new LinkedList<>();
		RegisterArg resultArg = insnNode.getResult();
		if (resultArg != null) {
			list.add(resultArg);
		}
		insnNode.getRegisterArgs(list);
		for (RegisterArg arg : list) {
			if (arg.getRegNum() >= regsCount) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Incorrect register number in instruction: " + insnNode + ", expected to be less than " + regsCount);
				}
				return true;
			}
		}
		return false;
	}
}
