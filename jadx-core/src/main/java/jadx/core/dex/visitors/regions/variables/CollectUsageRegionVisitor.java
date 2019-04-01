package jadx.core.dex.visitors.regions.variables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AttrNode;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.instructions.args.SSAVar;
import jadx.core.dex.nodes.IBlock;
import jadx.core.dex.nodes.IRegion;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.visitors.regions.TracedRegionVisitor;

class CollectUsageRegionVisitor extends TracedRegionVisitor {
	private final List<RegisterArg> args;
	private final Map<SSAVar, VarUsage> usageMap;

	public CollectUsageRegionVisitor() {
		this.usageMap = new LinkedHashMap<>();
		this.args = new ArrayList<>();
	}

	public Map<SSAVar, VarUsage> getUsageMap() {
		return usageMap;
	}

	@Override
	public void processBlockTraced(MethodNode mth, IBlock block, IRegion curRegion) {
		UsePlace usePlace = new UsePlace(curRegion, block);
		int len = block.getInstructions().size();
		for (int i = 0; i < len; i++) {
			InsnNode insn = block.getInstructions().get(i);
			processInsn(insn, usePlace);
		}
	}

	protected void processInsn(InsnNode insn, UsePlace usePlace) {
		if (insn == null) {
			return;
		}
		boolean countInsn = countInUsage(insn);
		// result
		RegisterArg result = insn.getResult();
		if (result != null && result.isRegister()) {
			if (countInsn || countInUsage(result)) {
				VarUsage usage = getUsage(result.getSVar());
				usage.getAssigns().add(usePlace);
			}
		}
		// args
		args.clear();
		insn.getRegisterArgs(args);
		for (RegisterArg arg : args) {
			if (countInsn || countInUsage(arg)) {
				VarUsage usage = getUsage(arg.getSVar());
				usage.getUses().add(usePlace);
			}
		}
	}

	protected boolean countInUsage(AttrNode node) {
		if (node.contains(AFlag.COUNT_IN_VAR_USAGE)) {
			return true;
		}
		return !node.contains(AFlag.DONT_GENERATE);
	}

	private VarUsage getUsage(SSAVar ssaVar) {
		return usageMap.computeIfAbsent(ssaVar, VarUsage::new);
	}
}
