package com.zandero.rest;

import com.zandero.rest.data.MethodParameter;
import com.zandero.rest.data.ParameterType;
import com.zandero.rest.data.RouteDefinition;
import com.zandero.utils.Assert;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects all JAX-RS annotations to be transformed into routes
 */
public final class AnnotationProcessor {

	private AnnotationProcessor() {
		// hide constructor
	}

	/**
	 * Extracts all JAX-RS annotated methods from class and all its subclasses and interfaces
	 * @param clazz to extract from
	 * @return map of routeDefinition and class method to execute
	 */
	public static Map<RouteDefinition, Method> get(Class clazz) {

		Map<RouteDefinition, Method> out = new HashMap<>();

		Map<RouteDefinition, Method> candidates = collect(clazz);
		// Final check if definitions are OK
		for (RouteDefinition definition : candidates.keySet()) {

			if (definition.getMethod() == null) { // skip non REST methods
				continue;
			}

			Method method = candidates.get(definition);
			Assert.notNull(definition.getRoutePath(), getClassMethod(clazz, method) + " - Missing route @Path!");

			int bodyParamCount = 0;
			for (MethodParameter param : definition.getParameters()) {
				if (bodyParamCount > 0 && (ParameterType.body.equals(param.getType()) || ParameterType.unknown.equals(param.getType()))) {
					// OK we have to body params ...
					throw new IllegalArgumentException(getClassMethod(clazz, method) + " - to many body arguments given. " +
					                                   "Missing argument annotation (@PathParam, @QueryParam, @FormParam, @HeaderParam, @CookieParam or @Context) for: " +
					                                   param.getType() + " " + param.getName() + "!");
				}

				if (ParameterType.unknown.equals(param.getType())) { // proclaim as body param
					// check if method allows for a body param
					Assert.isTrue(definition.requestHasBody(), getClassMethod(clazz, method) + " - " +
					                                           "Missing argument annotation (@PathParam, @QueryParam, @FormParam, @HeaderParam, @CookieParam or @Context) for: " +
					                                           param.getName() + "!");

					param.setType(ParameterType.body);
				}

				if (ParameterType.body.equals(param.getType())) {
					bodyParamCount++;
				}
			}

			out.put(definition, method);
		}

		return out;
	}

	/**
	 * Gets all route definitions for base class / interfaces and inherited / abstract classes
	 *
	 * @param clazz to inspect
	 * @return collection of all definitions
	 */
	private static Map<RouteDefinition, Method> collect(Class clazz) {

		Map<RouteDefinition, Method> out = getDefinitions(clazz);
		for (Class inter : clazz.getInterfaces()) {

			Map<RouteDefinition, Method> found = collect(inter);
			out = join(out, found);
		}

		Class superClass = clazz.getSuperclass();
		if (superClass != Object.class && superClass != null) {
			Map<RouteDefinition, Method> found = collect(superClass);
			out = join(out, found);
		}

		return out;
	}

	/**
	 * Joins additional data provided in subclass/ interfaces with base definition
	 *
	 * @param base base definition
	 * @param add  additional definition
	 * @return joined definition
	 */
	private static Map<RouteDefinition, Method> join(Map<RouteDefinition, Method> base, Map<RouteDefinition, Method> add) {

		for (RouteDefinition definition : base.keySet()) {
			Method method = base.get(definition);

			RouteDefinition additional = find(add, method);
			definition.join(additional);
		}

		return base;
	}

	/**
	 * Find mathing definition for same method ...
	 *
	 * @param add        to search
	 * @param method     base
	 * @return found definition or null if no match found
	 */
	private static RouteDefinition find(Map<RouteDefinition, Method> add, Method method) {

		if (add == null || add.size() == 0) {
			return null;
		}

		for (RouteDefinition additional : add.keySet()) {
			Method match = add.get(additional);

			if (isMatching(method, match)) {
				return additional;
			}
		}

		return null;
	}

	/**
	 * Checks if methods in base and inherited/abstract class are the same method
	 * @param base method
	 * @param compare to compare
	 * @return true if the same, false otherwise
	 */
	private static boolean isMatching(Method base, Method compare) {

		// if names and argument types match ... then this are the same method
		if (base.getName().equals(compare.getName()) &&
		    base.getParameterCount() == compare.getParameterCount()) {

			Class<?>[] typeBase = base.getParameterTypes();
			Class<?>[] typeCompare = compare.getParameterTypes();

			for (int index = 0; index < typeBase.length; index++) {
				Class clazzBase = typeBase[index];
				Class clazzCompare = typeCompare[index];
				if (!clazzBase.equals(clazzCompare)) {
					return false;
				}
			}

			return true;
		}

		return false;
	}


	/**
	 * Checks class for JAX-RS annotations and returns a list of route definitions to build routes upon
	 *
	 * @param clazz to be checked
	 * @return list of definitions or empty list if none present
	 */
	private static Map<RouteDefinition, Method> getDefinitions(Class clazz) {

		Assert.notNull(clazz, "Missing class with JAX-RS annotations!");

		// base
		RouteDefinition root = new RouteDefinition(clazz);

		// go over methods ...
		Map<RouteDefinition, Method> output = new LinkedHashMap<>();
		for (Method method : clazz.getMethods()) {

			if (isRestCompatible(method)) { // Path must be present

				try {
					RouteDefinition definition = new RouteDefinition(root, method);
					output.put(definition, method);
				}
				catch (IllegalArgumentException e) {

					throw new IllegalArgumentException(getClassMethod(clazz, method) + " - " + e.getMessage());
				}
			}
		}

		return output;
	}

	/**
	 * @param method to check if REST compatible
	 * @return true if REST method, false otherwise
	 */
	private static boolean isRestCompatible(Method method) {

		return (!method.getDeclaringClass().isInstance(Object.class) &&
		        !isNative(method) && !isFinal(method) &&
		        (isPublic(method) || isInterface(method) || isAbstract(method)));
	}

	private static boolean isNative(Method method) {
		return ((method.getModifiers() & Modifier.NATIVE) != 0);
	}

	private static boolean isFinal(Method method) {
		return ((method.getModifiers() & Modifier.FINAL) != 0);
	}

	private static boolean isPublic(Method method) {
		return ((method.getModifiers() & Modifier.PUBLIC) != 0);
	}

	private static boolean isInterface(Method method) {
		return ((method.getModifiers() & Modifier.INTERFACE) != 0);
	}

	private static boolean isAbstract(Method method) {
		return ((method.getModifiers() & Modifier.ABSTRACT) != 0);
	}

	/**
	 * Helper to convert class/method to String for reporting purposes
	 * @param clazz holding method
	 * @param method method in class
	 * @return class.method(type arg0, type arg1 .. type argN)
	 */
	private static String getClassMethod(Class clazz, Method method) {
		StringBuilder builder = new StringBuilder();
		builder.append(clazz.getName()).append(".").append(method.getName());
		builder.append("(");
		if (method.getParameterCount() > 0) {
			for (int i = 0; i < method.getParameterCount(); i++) {
				Parameter param = method.getParameters()[i];
				builder.append(param.getType().getSimpleName()).append(" ").append(param.getName());

				if (i + 1 < method.getParameterCount()) {
					builder.append(", ");
				}
			}
		}
		builder.append(")");
		return builder.toString();
	}
}
