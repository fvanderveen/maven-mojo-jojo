package maven.bug.moduleb;

import org.junit.Test;

public class ModuleB1Test {
	@Test
	public void testModuleB1() throws IllegalAccessException, InstantiationException {
		Class<ModuleB1> clazz = ModuleB1.class;
		clazz.newInstance();
	}
}
