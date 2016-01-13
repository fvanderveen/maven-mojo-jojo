package maven.bug.modulea;

import org.junit.Test;

public class ModuleA1Test {
	@Test
	public void testModuleA1() throws IllegalAccessException, InstantiationException {
		Class<ModuleA1> clazz = ModuleA1.class;
		clazz.newInstance();
	}
}
