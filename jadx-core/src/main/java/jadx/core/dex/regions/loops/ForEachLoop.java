package jadx.core.dex.regions.loops;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.instructions.args.InsnArg;
import jadx.core.dex.instructions.args.RegisterArg;

public final class ForEachLoop extends LoopType {
	private final RegisterArg varArg;
	private final InsnArg iterableArg;

	public ForEachLoop(RegisterArg varArg, InsnArg iterableArg) {
		this.varArg = varArg;
		this.iterableArg = iterableArg;

		// will be declared at codegen
		varArg.getSVar().getCodeVar().setDeclared(true);

		varArg.add(AFlag.COUNT_IN_VAR_USAGE);
		iterableArg.add(AFlag.COUNT_IN_VAR_USAGE);
	}

	public RegisterArg getVarArg() {
		return varArg;
	}

	public InsnArg getIterableArg() {
		return iterableArg;
	}
}
