package jadx.core.dex.regions.loops;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.nodes.InsnNode;

public final class ForLoop extends LoopType {

	private final InsnNode initInsn;
	private final InsnNode incrInsn;

	public ForLoop(InsnNode initInsn, InsnNode incrInsn) {
		this.initInsn = initInsn;
		this.incrInsn = incrInsn;

		initInsn.add(AFlag.COUNT_IN_VAR_USAGE);
		incrInsn.add(AFlag.COUNT_IN_VAR_USAGE);
	}

	public InsnNode getInitInsn() {
		return initInsn;
	}

	public InsnNode getIncrInsn() {
		return incrInsn;
	}
}
