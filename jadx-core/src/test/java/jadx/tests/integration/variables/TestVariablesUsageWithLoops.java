package jadx.tests.integration.variables;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jadx.core.dex.nodes.ClassNode;
import jadx.tests.api.IntegrationTest;

import static jadx.tests.api.utils.JadxMatchers.containsOne;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

public class TestVariablesUsageWithLoops extends IntegrationTest {

	public static class TestEnhancedFor {

		public void test() {
			List list;
			synchronized (this) {
				list = new ArrayList();
			}
			for (Object o : list) {
				System.out.println(o);
			}
		}
	}

	@Test
	public void testEnhancedFor() {
		ClassNode cls = getClassNode(TestEnhancedFor.class);
		String code = cls.getCode().toString();

		assertThat(code, containsOne(indent() + "list = new ArrayList"));
		assertThat(code, containsOne("for (Object o : list) {"));
		assertThat(code, not(containsString("Iterator")));
	}

	public static class TestForLoop {
		public void test() {
			List list;
			synchronized (this) {
				list = new ArrayList();
			}
			for (int i = 0; i < list.size(); i++) {
				System.out.println(i);
			}
		}
	}

	@Test
	public void testForLoop() {
		ClassNode cls = getClassNode(TestForLoop.class);
		String code = cls.getCode().toString();

		assertThat(code, containsOne(indent() + "list = new ArrayList"));
	}

	public static class TestForEachArray {
		public void test() {
			int[] arr;
			synchronized (this) {
				arr = new int[]{1, 2, 3};
			}
			for (int i : arr) {
				System.out.println(i);
			}
		}
	}

	@Test
	public void testForEachArray() {
		ClassNode cls = getClassNode(TestForEachArray.class);
		String code = cls.getCode().toString();

		assertThat(code, containsOne(indent() + "arr = new int[]{1, 2, 3};"));
		assertThat(code, containsOne("for (int i : arr) {"));
		assertThat(code, not(containsString("int i;")));
		assertThat(code, not(containsString("int length;")));
	}
}
