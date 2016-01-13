package maven.bug.moduleb;

import org.junit.Test;

import maven.bug.modulea.ModuleA2;

public class ModuleB2Test {
	@Test
	public void testModuleB2() throws IllegalAccessException, InstantiationException {
		Class<ModuleB2> clazz = ModuleB2.class;
		clazz.newInstance();
	}
	
	@Test
	public void testModuleA2() throws IllegalAccessException, InstantiationException {
		Class<ModuleA2> clazz = ModuleA2.class;
		clazz.newInstance();
	}
}
