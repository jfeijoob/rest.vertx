package com.zandero.rest.data;

import com.zandero.rest.test.TestPostRest;
import com.zandero.rest.test.TestRest;
import com.zandero.rest.test.json.Dummy;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 *
 */
public class RouteDefinitionTest {

	@Test
	public void getDefinitionTest() throws NoSuchMethodException {

		RouteDefinition base = new RouteDefinition(TestRest.class);

		assertEquals("/test", base.getPath());

		assertNotNull(base.getProduces());
		assertEquals(1, base.getProduces().length);

		assertNull(base.getMethod());
		assertNull(base.getConsumes());

		// 2.
		Method method = TestRest.class.getMethod("echo");
		RouteDefinition def = new RouteDefinition(base, method.getAnnotations());

		assertEquals("/test/echo", def.getPath());

		assertNotNull(def.getProduces());
		assertEquals(1, def.getProduces().length);

		assertEquals(HttpMethod.GET, def.getMethod());

		assertNull(def.getConsumes());
	}

	@Test
	public void getBodyParamTest() throws NoSuchMethodException {

		RouteDefinition base = new RouteDefinition(TestPostRest.class);

		// 1.
		Method method = TestPostRest.class.getMethods()[0];
		RouteDefinition def = new RouteDefinition(base, method.getAnnotations());

		def.setParameters(method.getParameterTypes(), method.getParameterAnnotations());

		assertEquals("/post/json", def.getPath());
		assertEquals(HttpMethod.POST, def.getMethod());

		assertEquals(2, def.getParameters().size());

		MethodParameter param = def.getParameters().get(0);
		assertEquals(Dummy.class.getName(), param.getName());
		assertEquals(ParameterType.body, param.getType());
		assertEquals(Dummy.class, param.getDataType());
		assertNull(param.getDefaultValue());

		param = def.getParameters().get(1);
		assertEquals("X-Test", param.getName());
		assertEquals(ParameterType.header, param.getType());
		assertEquals(String.class, param.getDataType());
		assertNull(param.getDefaultValue());
	}
}