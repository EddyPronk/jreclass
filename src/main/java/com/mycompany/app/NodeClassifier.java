package com.mycompany.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.commons.text.StringSubstitutor;

import javax.xml.crypto.Data;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;


public class NodeClassifier
{

    private Map<String, NodeClass> nodeClassesMap = new HashMap<>();
    private Map<String, Node> nodesMap = new HashMap<>();

    public void load(String baseDir, DataSource source) {
        String[] directories = {"classes", "nodes"};

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

        for (String dir : directories) {
            try (Stream<Path> paths = Files.walk(Path.of(baseDir, dir))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(source.fileExtension()))
                        .forEach(path -> {
                            try {
                                if (dir.equals("nodes")) {
                                    var stream = new FileInputStream(path.toString());
                                    loadClass(path.toString(), stream, source);
                                } else {
                                    var stream = new FileInputStream(path.toString());
                                    NodeClass nodeClass = source.readValue(stream, NodeClass.class);
                                    String className = path.toString()
                                            .replace(baseDir + "classes/", "")
                                            .replace(".yml", "")
                                            .replace("/", ".");

                                    if (className.endsWith(".init")) {
                                        className = className.substring(0, className.length() - ".init".length());
                                    }

                                    nodeClassesMap.put(className, nodeClass);
                                }
                            } catch (IOException e) {
                                System.out.println("error in" + path);
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadClass(String name, InputStream file, DataSource source) throws IOException
    {
        Node node = source.readValue(file, Node.class);
        nodesMap.put(name, node);
    }

    public void writeClassesToFile(Map<String, NodeClass> nodeClassesMap, String outputFilename) {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        List<String> classNames = new ArrayList<>(nodeClassesMap.keySet());

        try {
            yamlMapper.writeValue(new File(outputFilename), classNames);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    void process(String nodeName, String filename)
    {
        try {
            File outputFile = new File(filename);
            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                process(nodeName, outputStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void process(String nodeName, OutputStream out) {

        Node node = getNode(nodeName);


        try {
            writeClassesToFile(nodeClassesMap, "classes.yml");
            List<String> classList = resolveClasses(node, nodeClassesMap);
            var params = mergeParameters(node, classList, nodeClassesMap);
            node.setParameters((params));

            Node resolvedNode = resolvePlaceholders(node);
            resolvedNode.setClasses(classList);

            setNodeApplications(resolvedNode, classList, nodeClassesMap);

            File outputFile = new File("output.yaml");
            try {
                ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
                yamlMapper.writeValue(out, resolvedNode);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        } catch (Exception e) {
            return;
        }
    }


    public static void setNodeApplications(Node node, List<String> classNames, Map<String, NodeClass> nodeClassesMap) {
        Set<String> applicationsSet = collectApplications(classNames, nodeClassesMap);
        List<String> applicationsList = new ArrayList<>(applicationsSet);
        node.setApplications(applicationsList);
    }

    public static Set<String> collectApplications(List<String> classNames, Map<String, NodeClass> nodeClassesMap) {
        Set<String> applications = new LinkedHashSet<>();

        for (String className : classNames) {
            NodeClass nodeClass = nodeClassesMap.get(className);
            if (nodeClass != null && nodeClass.getApplications() != null) {
                applications.addAll(nodeClass.getApplications());
            }
        }

        return applications;
    }


    public Node getNode(String path) {
        return nodesMap.get(path);
    }

    public static List<String> resolveClasses(Node node, Map<String, NodeClass> nodeClassesMap) {
        List<String> resolvedClassNames = new ArrayList<>();
        List<String> inputClasses = node.getClasses();

        for (String className : inputClasses) {
            resolveClassWithHierarchy(className, nodeClassesMap, resolvedClassNames);
        }

        return resolvedClassNames;
    }

    private static void resolveClassWithHierarchy(String className, Map<String, NodeClass> nodeClassesMap, List<String> resolvedClassNames) {
        if (!nodeClassesMap.containsKey(className)) {
            return;
        }

        NodeClass nodeClass = nodeClassesMap.get(className);

        // Resolve base classes first
        if (nodeClass.getClasses() != null) {
            for (String baseClass : nodeClass.getClasses()) {
                resolveClassWithHierarchy(baseClass, nodeClassesMap, resolvedClassNames);
            }
        }

        // Add the current class name if it's not already in the list
        if (!resolvedClassNames.contains(className)) {
            resolvedClassNames.add(className);
        }
    }

    public Map<String, Object> mergeParameters(Node node, List<String> resolvedClassNames, Map<String, NodeClass> nodeClassesMap) {
        Map<String, Object> mergedParameters = new HashMap<>();

        // Merge the parameters from the resolved classes
        for (String className : resolvedClassNames) {
            //System.out.println("class " + className);
            NodeClass cls = nodeClassesMap.get(className);
            Map<String, Object> classParameters = cls.getParameters();
            if (classParameters != null) {
                mergeMaps(mergedParameters, classParameters, className);
            }
        }

        // Merge the parameters from the node itself
        Map<String, Object> nodeParameters = node.getParameters();
        if (nodeParameters != null) {
            mergeMaps(mergedParameters, nodeParameters, "node");
        }

        return mergedParameters;
    }

    private void mergeMaps(Map<String, Object> target, Map<String, Object> source, String context) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.containsKey(key) && target.get(key) instanceof Map && value instanceof Map) {
                //noinspection unchecked
                mergeMaps((Map<String, Object>) target.get(key), (Map<String, Object>) value, context);
            } else {
                System.out.println("merge " + key + " from " + context);

                target.put(key, value);
            }
        }
    }


    private Object resolvePlaceholdersRecursively(Object value, StringSubstitutor substitutor) {
        if (value instanceof Map) {
            Map<String, Object> mapValue = (Map<String, Object>) value;
            Map<String, Object> resultMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
                resultMap.put(entry.getKey(), resolvePlaceholdersRecursively(entry.getValue(), substitutor));
            }
            return resultMap;
        } else if (value instanceof List) {
            List<?> listValue = (List<?>) value;
            List<Object> resultList = new ArrayList<>();
            for (Object listItem : listValue) {
                resultList.add(resolvePlaceholdersRecursively(listItem, substitutor));
            }
            return resultList;
        } else if (value instanceof String) {
            String replacedValue = substitutor.replace((String) value);

            // Attempt to convert the replaced string value back to the original data type
            if (replacedValue.equals("true") || replacedValue.equals("false")) {
                return Boolean.parseBoolean(replacedValue);
            } else {
                try {
                    return Integer.parseInt(replacedValue);
                } catch (NumberFormatException e) {
                    // Not an integer, return as string
                }
            }
            return replacedValue;
        } else {
            // Preserve type information for non-string values
            return value;
        }
    }

    public Node resolvePlaceholders(Node node) {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        try {
            Node newNode = yamlMapper.readValue(yamlMapper.writeValueAsString(node), Node.class);
            Map<String, Object> parameters = newNode.getParameters();

            Map<String, Object> valuesMap = new HashMap<>();
            flattenParameters(parameters, "", valuesMap);
            StringSubstitutor substitutor = new StringSubstitutor(valuesMap);

            Map<String, Object> resolvedParameters = (Map<String, Object>) resolvePlaceholdersRecursively(parameters, substitutor);
            newNode.setParameters(resolvedParameters);

            return newNode;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void flattenParameters(Map<String, Object> parameters, String prefix, Map<String, Object> valuesMap) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String newKey = prefix.isEmpty() ? entry.getKey() : prefix + ":" + entry.getKey();

            if (entry.getValue() instanceof Map) {
                //noinspection unchecked
                flattenParameters((Map<String, Object>) entry.getValue(), newKey, valuesMap);
            } else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    Object listItem = list.get(i);
                    if (listItem instanceof Map) {
                        // items in a list will not be interpolated
                    }
                }
            } else {
                valuesMap.put(newKey, entry.getValue());
            }
        }
    }
}
