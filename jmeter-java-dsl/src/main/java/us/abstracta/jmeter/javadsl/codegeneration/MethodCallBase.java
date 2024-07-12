package us.abstracta.jmeter.javadsl.codegeneration;


import java.util.*;
import java.util.stream.Collectors;


public abstract class MethodCallBase implements CodeSegment {

    public final String methodName;
    public final Class<?> returnType;
    public final List<MethodParam> params;
    protected List<CodeSegment> chain = new ArrayList<>();
    public final Set<String> requiredStaticImports = new HashSet<>();
    private boolean commented;
    private String headingComment;

    public MethodCallBase(String methodName, Class<?> returnType, MethodParam... params) {
        this.methodName = methodName;
        this.returnType = returnType;
        this.params = Arrays.asList(params);
    }

    public void setCommented(boolean commented) {
        this.commented = commented;
    }

    public boolean isCommented() {
        return commented;
    }

    public void headingComment(String comment) {
        headingComment = comment;
    }

    @Override
    public Set<String> getStaticImports() {
        Set<String> ret = new HashSet<>(requiredStaticImports);
        params.stream()
                .filter(p -> !p.isIgnored())
                .forEach(p -> ret.addAll(p.getStaticImports()));
        chain.forEach(c -> ret.addAll(c.getStaticImports()));
        getMethodDefinitions().values()
                .forEach(m -> ret.addAll(m.getStaticImports()));
        return ret;
    }

    @Override
    public Set<String> getImports() {
        Set<String> ret = new HashSet<>();
        params.stream()
                .filter(p -> !p.isIgnored())
                .forEach(p -> ret.addAll(p.getImports()));
        chain.forEach(c -> ret.addAll(c.getImports()));
        getMethodDefinitions().values()
                .forEach(m -> {
                    ret.add(m.getReturnType().getName());
                    ret.addAll(m.getImports());
                });
        return ret;
    }

    @Override
    public Map<String, MethodCall> getMethodDefinitions() {
        Map<String, MethodCall> ret = new LinkedHashMap<>();
        params.stream()
                .filter(p -> !p.isIgnored())
                .forEach(p -> ret.putAll(p.getMethodDefinitions()));
        chain.forEach(c -> ret.putAll(c.getMethodDefinitions()));
        return ret;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public abstract MethodCall child(MethodCall child);

    protected String buildParamsCode(String indent) {
        String ret = params.stream()
                .filter(p -> !p.isIgnored())
                .map(p -> p.buildCode(indent))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return ret.replace(", \n", ",\n").replaceAll("\n\\s*\n", "\n");
    }

    protected String buildChainedCode(String indent) {
        StringBuilder ret = new StringBuilder();
        for (CodeSegment seg : chain) {
            String segCode = seg.buildCode(indent);
            if (!segCode.isEmpty()) {
                ret.append("\n")
                        .append(indent)
                        .append(seg instanceof MethodCall ? "." : "")
                        .append(segCode);
            }
        }
        return ret.toString();
    }

    @Override
    public String buildCode(String indent) {
        StringBuilder ret = new StringBuilder();
        if (headingComment != null) {
            ret.append("// ")
                    .append(headingComment)
                    .append("\n")
                    .append(indent);
        }
        ret.append(methodName)
                .append("(");
        String childIndent = indent + Indentation.INDENT;
        String paramsCode = buildParamsCode(childIndent);
        ret.append(paramsCode);
        boolean hasChildren = paramsCode.endsWith("\n");
        if (hasChildren) {
            ret.append(indent);
        }
        ret.append(")");
        String chainedCode = buildChainedCode(childIndent);
        if (!chainedCode.isEmpty() && hasChildren) {
            chainedCode = chainedCode.substring(1 + childIndent.length());
        }
        ret.append(chainedCode);
        return commented ? commented(ret.toString(), indent) : ret.toString();
    }

    private String commented(String str, String indent) {
        return "//" + str.replace("\n" + indent, "\n" + indent + "//");
    }

    public String buildAssignmentCode(String indent) {
        String ret = buildCode(indent);
        String indentedParenthesis = Indentation.INDENT + ")";
        return chain.isEmpty() && ret.endsWith(indentedParenthesis)
                ? ret.substring(0, ret.length() - indentedParenthesis.length()) + ")"
                : ret;
    }
}
