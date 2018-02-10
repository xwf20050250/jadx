package jadx.core.dex.visitors;

import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.nodes.JumpInfo;
import jadx.core.dex.instructions.GotoNode;
import jadx.core.dex.instructions.IfNode;
import jadx.core.dex.instructions.InsnDecoder;
import jadx.core.dex.instructions.SwitchNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.visitors.blocksmaker.BlockSplitter;
import jadx.core.utils.exceptions.JadxException;

/**
 * Insert jumps annotations
 */
@JadxVisitor(
		name = "InitJumps",
		desc = "Insert jumps annotations",
		runBefore = BlockSplitter.class
)
public class InitJumps extends AbstractVisitor {

	@Override
	public void visit(MethodNode mth) throws JadxException {
		InsnNode[] insnByOffset = mth.getInstructions();
		for (int offset = 0; offset < insnByOffset.length; offset++) {
			InsnNode insn = insnByOffset[offset];
			if (insn == null) {
				continue;
			}
			try {
				switch (insn.getType()) {
					case SWITCH:
						SwitchNode sw = (SwitchNode) insn;
						for (int target : sw.getTargets()) {
							addJump(insnByOffset, offset, target);
						}
						// default case
						int nextInsnOffset = InsnDecoder.getNextInsnOffset(insnByOffset, offset);
						if (nextInsnOffset != -1) {
							addJump(insnByOffset, offset, nextInsnOffset);
						}
						break;

					case IF:
						int next = InsnDecoder.getNextInsnOffset(insnByOffset, offset);
						if (next != -1) {
							addJump(insnByOffset, offset, next);
						}
						addJump(insnByOffset, offset, ((IfNode) insn).getTarget());
						break;

					case GOTO:
						addJump(insnByOffset, offset, ((GotoNode) insn).getTarget());
						break;

					default:
						break;
				}
			} catch (Exception e) {
				insnByOffset[offset] = null;
			}
		}
	}

	private static void addJump(InsnNode[] insnByOffset, int offset, int target) {
		insnByOffset[target].addAttr(AType.JUMP, new JumpInfo(offset, target));
	}
}
