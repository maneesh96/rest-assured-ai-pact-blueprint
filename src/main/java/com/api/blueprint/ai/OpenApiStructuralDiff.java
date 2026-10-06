package com.api.blueprint.ai;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Offline, rule-based comparison of two OpenAPI 3 documents. It reports only changes that
 * break existing clients:
 * <ul>
 *   <li>a path or an operation that was removed,</li>
 *   <li>a request field or parameter that is newly required,</li>
 *   <li>a response field that was removed,</li>
 *   <li>a request or response field whose type changed.</li>
 * </ul>
 * Additive changes (new paths, new optional fields, a field that is no longer required) are
 * backward compatible and are not reported. Local {@code $ref}s are followed, so a change in
 * a shared component schema is reported at every operation that uses it.
 */
public final class OpenApiStructuralDiff {

    public static final String REMOVED_PATH = "REMOVED_PATH";
    public static final String REMOVED_OPERATION = "REMOVED_OPERATION";
    public static final String NEW_REQUIRED_REQUEST_FIELD = "NEW_REQUIRED_REQUEST_FIELD";
    public static final String NEW_REQUIRED_PARAMETER = "NEW_REQUIRED_PARAMETER";
    public static final String REMOVED_RESPONSE_FIELD = "REMOVED_RESPONSE_FIELD";
    public static final String CHANGED_TYPE = "CHANGED_TYPE";

    private static final List<String> HTTP_METHODS =
            List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final String LOCAL_REF_PREFIX = "#/";

    /** One backward-incompatible change. {@code endpoint} is "METHOD /path", or just the path. */
    public record Finding(String endpoint, String changeType, String description) {
    }

    private enum Direction { REQUEST, RESPONSE }

    private final Map<String, Object> baseline;
    private final Map<String, Object> current;
    private final Set<Finding> findings = new LinkedHashSet<>();

    private OpenApiStructuralDiff(Map<String, Object> baseline, Map<String, Object> current) {
        this.baseline = baseline;
        this.current = current;
    }

    /** Parses both documents and returns the breaking changes from baseline to current. */
    public static List<Finding> compare(String baselineYaml, String currentYaml) {
        return compare(parse(baselineYaml, "baseline"), parse(currentYaml, "current"));
    }

    static List<Finding> compare(Map<String, Object> baseline, Map<String, Object> current) {
        OpenApiStructuralDiff diff = new OpenApiStructuralDiff(baseline, current);
        diff.comparePaths();
        return List.copyOf(diff.findings);
    }

    private static Map<String, Object> parse(String yaml, String label) {
        Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(document instanceof Map)) {
            throw new IllegalArgumentException("The " + label + " spec is not a YAML mapping");
        }
        return asMap(document);
    }

    private void comparePaths() {
        Map<String, Object> currentPaths = asMap(current.get("paths"));
        asMap(baseline.get("paths")).forEach((path, baselineItem) -> {
            if (!currentPaths.containsKey(path)) {
                add(path, REMOVED_PATH, "Path '" + path + "' was removed.");
                return;
            }
            Map<String, Object> currentItem = asMap(currentPaths.get(path));
            for (String method : HTTP_METHODS) {
                Map<String, Object> baselineOperation = asMap(asMap(baselineItem).get(method));
                if (baselineOperation.isEmpty()) {
                    continue;
                }
                String endpoint = method.toUpperCase() + " " + path;
                if (!currentItem.containsKey(method)) {
                    add(endpoint, REMOVED_OPERATION, "Operation " + endpoint + " was removed.");
                    continue;
                }
                Map<String, Object> currentOperation = asMap(currentItem.get(method));
                compareParameters(endpoint, asMap(baselineItem), baselineOperation, currentItem, currentOperation);
                compareRequestBody(endpoint, baselineOperation, currentOperation);
                compareResponses(endpoint, baselineOperation, currentOperation);
            }
        });
    }

    private void compareParameters(String endpoint, Map<String, Object> baselineItem, Map<String, Object> baselineOperation,
                                   Map<String, Object> currentItem, Map<String, Object> currentOperation) {
        Map<String, Map<String, Object>> before = parameters(baseline, baselineItem, baselineOperation);
        Map<String, Map<String, Object>> after = parameters(current, currentItem, currentOperation);
        after.forEach((key, parameter) -> {
            Map<String, Object> previous = before.get(key);
            boolean requiredNow = Boolean.TRUE.equals(parameter.get("required"));
            boolean requiredBefore = previous != null && Boolean.TRUE.equals(previous.get("required"));
            if (requiredNow && !requiredBefore) {
                add(endpoint, NEW_REQUIRED_PARAMETER, "Parameter " + key + " is now required.");
            }
            if (previous != null) {
                compareSchemas(endpoint, "parameter " + key, "",
                        asMap(previous.get("schema")), asMap(parameter.get("schema")),
                        Direction.REQUEST, new HashSet<>());
            }
        });
    }

    /** Path-level and operation-level parameters keyed by "in:name"; the operation wins on clashes. */
    private static Map<String, Map<String, Object>> parameters(Map<String, Object> document,
                                                               Map<String, Object> pathItem,
                                                               Map<String, Object> operation) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (List<?> source : List.of(asList(pathItem.get("parameters")), asList(operation.get("parameters")))) {
            for (Object raw : source) {
                Map<String, Object> parameter = resolve(document, asMap(raw), new HashSet<>());
                byKey.put(parameter.get("in") + ":" + parameter.get("name"), parameter);
            }
        }
        return byKey;
    }

    private void compareRequestBody(String endpoint, Map<String, Object> baselineOperation,
                                    Map<String, Object> currentOperation) {
        Map<String, Object> before = resolve(baseline, asMap(baselineOperation.get("requestBody")), new HashSet<>());
        Map<String, Object> after = resolve(current, asMap(currentOperation.get("requestBody")), new HashSet<>());
        if (after.isEmpty()) {
            return;
        }
        if (before.isEmpty()) {
            if (Boolean.TRUE.equals(after.get("required"))) {
                add(endpoint, NEW_REQUIRED_REQUEST_FIELD, "A request body is now required.");
            }
            return;
        }
        compareContent(endpoint, "request body", before, after, Direction.REQUEST);
    }

    private void compareResponses(String endpoint, Map<String, Object> baselineOperation,
                                  Map<String, Object> currentOperation) {
        // Unquoted status codes load as integers, so match them by their string form.
        Map<String, Object> currentResponses = byStringKey(currentOperation.get("responses"));
        byStringKey(baselineOperation.get("responses")).forEach((status, baselineResponse) -> {
            if (!currentResponses.containsKey(status)) {
                return;
            }
            compareContent(endpoint, status + " response",
                    resolve(baseline, asMap(baselineResponse), new HashSet<>()),
                    resolve(current, asMap(currentResponses.get(status)), new HashSet<>()),
                    Direction.RESPONSE);
        });
    }

    private void compareContent(String endpoint, String location, Map<String, Object> before,
                                Map<String, Object> after, Direction direction) {
        Map<String, Object> afterContent = asMap(after.get("content"));
        asMap(before.get("content")).forEach((mediaType, baselineMedia) -> {
            if (!afterContent.containsKey(mediaType)) {
                return;
            }
            compareSchemas(endpoint, location, "",
                    asMap(asMap(baselineMedia).get("schema")),
                    asMap(asMap(afterContent.get(mediaType)).get("schema")),
                    direction, new HashSet<>());
        });
    }

    /**
     * Walks two schemas side by side. {@code location} names where the schema sits (for
     * example "request body" or "200 response"), {@code field} is the dotted path inside it
     * ("" for the root), and {@code visited} holds the $ref pairs already compared on this
     * branch so recursive schemas terminate.
     */
    private void compareSchemas(String endpoint, String location, String field, Map<String, Object> rawBefore,
                                Map<String, Object> rawAfter, Direction direction, Set<String> visited) {
        if (rawBefore.isEmpty() || rawAfter.isEmpty()) {
            return;
        }
        if (rawBefore.containsKey("$ref") && rawAfter.containsKey("$ref")
                && !visited.add(rawBefore.get("$ref") + "->" + rawAfter.get("$ref"))) {
            return;
        }
        Map<String, Object> before = resolve(baseline, rawBefore, new HashSet<>());
        Map<String, Object> after = resolve(current, rawAfter, new HashSet<>());

        Object typeBefore = before.get("type");
        Object typeAfter = after.get("type");
        if (typeBefore != null && typeAfter != null && !Objects.equals(typeBefore, typeAfter)) {
            add(endpoint, CHANGED_TYPE, describe(location, field) + " changed type from "
                    + typeBefore + " to " + typeAfter + ".");
            return;
        }

        Map<String, Object> propertiesBefore = asMap(before.get("properties"));
        Map<String, Object> propertiesAfter = asMap(after.get("properties"));

        if (direction == Direction.REQUEST) {
            Set<String> requiredBefore = stringSet(before.get("required"));
            for (String name : stringSet(after.get("required"))) {
                if (!requiredBefore.contains(name)) {
                    add(endpoint, NEW_REQUIRED_REQUEST_FIELD,
                            describe(location, child(field, name)) + " is now required.");
                }
            }
        } else {
            for (String name : propertiesBefore.keySet()) {
                if (!propertiesAfter.containsKey(name)) {
                    add(endpoint, REMOVED_RESPONSE_FIELD,
                            describe(location, child(field, name)) + " was removed.");
                }
            }
        }

        propertiesBefore.forEach((name, schema) -> {
            if (propertiesAfter.containsKey(name)) {
                compareSchemas(endpoint, location, child(field, name), asMap(schema),
                        asMap(propertiesAfter.get(name)), direction, new HashSet<>(visited));
            }
        });
        compareSchemas(endpoint, location, field + "[]", asMap(before.get("items")),
                asMap(after.get("items")), direction, new HashSet<>(visited));
    }

    /** Follows local $refs ("#/components/...") until a concrete object is reached. */
    private static Map<String, Object> resolve(Map<String, Object> document, Map<String, Object> node,
                                               Set<String> seen) {
        Object ref = node.get("$ref");
        if (!(ref instanceof String pointer) || !pointer.startsWith(LOCAL_REF_PREFIX)) {
            return node;
        }
        if (!seen.add(pointer)) {
            throw new IllegalArgumentException("Circular $ref: " + pointer);
        }
        Object target = document;
        for (String segment : pointer.substring(LOCAL_REF_PREFIX.length()).split("/")) {
            target = asMap(target).get(segment.replace("~1", "/").replace("~0", "~"));
            if (target == null) {
                throw new IllegalArgumentException("Unresolvable $ref: " + pointer);
            }
        }
        return resolve(document, asMap(target), seen);
    }

    private void add(String endpoint, String changeType, String description) {
        findings.add(new Finding(endpoint, changeType, description));
    }

    /** "Field 'category.name' in the request body", or "The 200 response" for the root schema. */
    private static String describe(String location, String field) {
        return field.isEmpty()
                ? "The " + location
                : "Field '" + field + "' in the " + location;
    }

    private static String child(String field, String name) {
        return field.isEmpty() ? name : field + "." + name;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private static Map<String, Object> byStringKey(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
        }
        return copy;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Set<String> stringSet(Object value) {
        Set<String> names = new LinkedHashSet<>();
        for (Object item : asList(value)) {
            names.add(String.valueOf(item));
        }
        return names;
    }
}
