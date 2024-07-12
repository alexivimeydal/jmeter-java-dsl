package us.abstracta.jmeter.javadsl.codegeneration;

import us.abstracta.jmeter.javadsl.codegeneration.params.BoolParam;
import us.abstracta.jmeter.javadsl.codegeneration.params.ChildrenParam;
import us.abstracta.jmeter.javadsl.core.testelements.MultiLevelTestElement;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodCall extends MethodCallBase {

  private static final MethodCall EMPTY_METHOD_CALL = new EmptyMethodCall();
  private MethodCall childrenMethod;
  private ChildrenParam<?> childrenParam;

  public MethodCall(String methodName, Class<?> returnType, MethodParam... params) {
    super(methodName, returnType, params);
  }

  public static MethodCall fromBuilderMethod(Method method, MethodParam... params) {
    MethodCall ret = from(method, params);
    ret.requiredStaticImports.add(method.getDeclaringClass().getName());
    return ret;
  }

  private static MethodCall from(Method method, MethodParam... params) {
    return new MethodCall(method.getName(), method.getReturnType(), params);
  }

  public static MethodCall forStaticMethod(Class<?> methodClass, String methodName, MethodParam... params) {
    Class<?>[] paramsTypes = Arrays.stream(params)
            .map(MethodParam::getType)
            .toArray(Class[]::new);
    Method method = findRequiredStaticMethod(methodClass, methodName, paramsTypes);
    return new MethodCall(methodClass.getSimpleName() + "." + method.getName(), method.getReturnType(), params);
  }

  private static Method findRequiredStaticMethod(Class<?> methodClass, String methodName, Class<?>... paramsTypes) {
    try {
      Method ret = methodClass.getDeclaredMethod(methodName, paramsTypes);
      if (!Modifier.isPublic(ret.getModifiers()) || !Modifier.isStatic(ret.getModifiers())) {
        throw new RuntimeException("Can't access method " + ret + " which is no longer static or public. Check that no dependencies or APIs have been changed.");
      }
      return ret;
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Can't find method " + methodClass.getName() + "." + methodName + " for parameter types " + Arrays.toString(paramsTypes) + ". Check that no dependencies or APIs have been changed.", e);
    }
  }

  public static MethodCall buildUnsupported() {
    return new MethodCall("unsupported", MultiLevelTestElement.class);
  }

  public static MethodCall emptyCall() {
    return EMPTY_METHOD_CALL;
  }

  @Override
  public MethodCall child(MethodCall child) {
    solveChildrenParam().addChild(child);
    return this;
  }

  private ChildrenParam<?> solveChildrenParam() {
    if (childrenMethod == null) {
      MethodParam lastParam = params.isEmpty() ? null : params.get(params.size() - 1);
      if (lastParam instanceof ChildrenParam && chain.isEmpty()) {
        childrenMethod = this;
        childrenParam = (ChildrenParam<?>) lastParam;
      } else {
        childrenMethod = findChildrenMethod();
        chain.add(childrenMethod);
        childrenParam = (ChildrenParam<?>) childrenMethod.params.get(0);
      }
    }
    return childrenParam;
  }

  private MethodCall findChildrenMethod() {
    Method childrenMethod = null;
    Class<?> methodHolder = returnType;
    while (childrenMethod == null && methodHolder != Object.class) {
      childrenMethod = Arrays.stream(methodHolder.getDeclaredMethods())
              .filter(m -> Modifier.isPublic(m.getModifiers()) && "children".equals(m.getName()) && m.getParameterCount() == 1)
              .findAny()
              .orElse(null);
      methodHolder = methodHolder.getSuperclass();
    }
    if (childrenMethod == null) {
      throw new IllegalStateException("No children method found for " + returnType + ". This might be due to unexpected test plan structure or missing method in test element. Please create an issue in GitHub repository if you find any of these cases.");
    }
    return new ChildrenMethodCall(childrenMethod);
  }

  private static class ChildrenMethodCall extends MethodCall {

    protected ChildrenMethodCall(Method method) {
      super(method.getName(), method.getReturnType(), new ChildrenParam<>(method.getParameterTypes()[0]));
    }

    @Override
    public String buildCode(String indent) {
      String paramsCode = buildParamsCode(indent + Indentation.INDENT);
      return paramsCode.isEmpty() ? "" : methodName + "(" + paramsCode + indent + ")";
    }

  }

  private static class EmptyMethodCall extends MethodCall {

    protected EmptyMethodCall() {
      super(null, MultiLevelTestElement.class);
    }

    @Override
    public MethodCall child(MethodCall child) {
      // Just ignoring children
      return this;
    }

    @Override
    public String buildCode(String indent) {
      return "";
    }

  }

  public void replaceChild(MethodCall original, MethodCall replacement) {
    solveChildrenParam().replaceChild(original, replacement);
  }

  public void prependChild(MethodCall child) {
    solveChildrenParam().prependChild(child);
  }

  public MethodCall chain(String methodName, MethodParam... params) {
    if (params.length > 0 && Arrays.stream(params).allMatch(MethodParam::isDefault)) {
      return this;
    }
    Method method = null;
    if (params.length == 1 && params[0] instanceof BoolParam) {
      method = findMethodInClassHierarchyMatchingParams(methodName, returnType, new MethodParam[0]);
      if (method != null) {
        params = new MethodParam[0];
      }
    }
    if (method == null) {
      method = findMethodInClassHierarchyMatchingParams(methodName, returnType, params);
    }
    if (method == null) {
      throw buildNoMatchingMethodFoundException("public '" + methodName + "' method in " + returnType.getName(), params);
    }
    chain.add(MethodCall.from(method, params));
    return this;
  }

  public MethodCall chain(MethodCall methodCall) {
    chain.add(methodCall);
    return methodCall;
  }

  /**
   * Allows to add a comment as part of the chain of commands.
   * <p>
   * This is useful to add notes to drive user attention to some particular chained method. For
   * example, when parameters passed to a chained method need to be reviewed or changed.
   *
   * @param comment the comment to chain.
   * @return the method call for further usage.
   * @since 1.5
   */
  public MethodCall chainComment(String comment) {
    chain.add(new Comment(comment));
    return this;
  }

  private Method findMethodInClassHierarchyMatchingParams(String methodName, Class<?> methodClass, MethodParam[] params) {
    Method ret = null;
    while (ret == null && methodClass != Object.class) {
      ret = findMethodInClassMatchingParams(methodName, methodClass, params);
      methodClass = methodClass.getSuperclass();
    }
    return ret;
  }

  private Method findMethodInClassMatchingParams(String methodName, Class<?> methodClass, MethodParam[] params) {
    Stream<Method> chainableMethods = Arrays.stream(methodClass.getDeclaredMethods())
            .filter(m -> methodName.equals(m.getName()) && Modifier.isPublic(m.getModifiers()) && m.getReturnType().isAssignableFrom(methodClass));
    return findParamsMatchingMethod(chainableMethods, params);
  }

  protected static Method findParamsMatchingMethod(Stream<Method> methods, MethodParam[] params) {
    List<MethodParam> finalParams = Arrays.stream(params)
            .filter(p -> !p.isIgnored())
            .collect(Collectors.toList());
    return methods
            .filter(m -> methodMatchesParameters(m, finalParams))
            .findAny()
            .orElse(null);
  }

  private static boolean methodMatchesParameters(Method m, List<MethodParam> params) {
    if (m.getParameterCount() != params.size()) {
      return false;
    }
    Class<?>[] paramTypes = m.getParameterTypes();
    for (int i = 0; i < params.size(); i++) {
      if (!params.get(i).getType().isAssignableFrom(paramTypes[i])) {
        return false;
      }
    }
    return true;
  }

  protected static UnsupportedOperationException buildNoMatchingMethodFoundException(String methodCondition, MethodParam[] params) {
    return new UnsupportedOperationException("No " + methodCondition + " method was found for parameters " + Arrays.toString(params) + ". This is probably due to some change in DSL not reflected in associated code builder.");
  }

  public void reChain(MethodCall other) {
    this.chain.addAll(other.chain);
  }

  public void unchain(String methodName) {
    chain = chain.stream()
            .filter(m -> !(m instanceof MethodCall && methodName.equals(((MethodCall) m).methodName)))
            .collect(Collectors.toList());
  }

  public int chainSize() {
    return chain.size();
  }

  public String buildCode() {
    return buildCode("");
  }
}
