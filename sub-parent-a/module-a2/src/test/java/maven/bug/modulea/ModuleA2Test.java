package maven.bug.modulea;

import org.junit.Test;

public class ModuleA2Test {
	@Test
	public void testModuleA2() throws IllegalAccessException, InstantiationException {
		Class<ModuleA2> clazz = ModuleA2.class;
		clazz.newInstance();
	}
}
