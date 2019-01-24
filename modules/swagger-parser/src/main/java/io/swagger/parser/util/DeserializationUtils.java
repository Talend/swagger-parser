package io.swagger.parser.util;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
import java.util.List;

public class DeserializationUtils {

    public static Integer maxDepth = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(DeserializationUtils.class);

    public static JsonNode deserializeIntoTree(String contents, String fileOrHost) {
        JsonNode result;

        try {
            if (isJson(contents)) {
                result = Json.mapper().readTree(contents);
            } else {
                result = readYamlTree(contents);
            }
        } catch (IOException e) {
            throw new RuntimeException("An exception was thrown while trying to deserialize the contents of " + fileOrHost + " into a JsonNode tree", e);
        }

        return result;
    }

    public static <T> T deserialize(Object contents, String fileOrHost, Class<T> expectedType) {
        T result;

        boolean isJson = false;

        if(contents instanceof String && isJson((String)contents)) {
            isJson = true;
        }

        try {
            if (contents instanceof String) {
                if (isJson) {
                    result = Json.mapper().readValue((String) contents, expectedType);
                } else {
                    result = Yaml.mapper().readValue((String) contents, expectedType);
                }
            } else {
                result = Json.mapper().convertValue(contents, expectedType);
            }
        } catch (Exception e) {
            throw new RuntimeException("An exception was thrown while trying to deserialize the contents of " + fileOrHost + " into type " + expectedType, e);
        }

        return result;
    }

    private static boolean isJson(String contents) {
        return contents.toString().trim().startsWith("{");
    }

    public static JsonNode readYamlTree(String contents) throws IOException {

        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(new CustomSnakeYamlConstructor());
            return Json.mapper().convertValue(yaml.load(contents), JsonNode.class);
        } catch (Throwable e) {
            LOGGER.warn("Error snake-parsing yaml content", e);
            return Yaml.mapper().readTree(contents);
        }
    }

    public static <T> T readYamlValue(String contents, Class<T> expectedType) {
        try {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(new CustomSnakeYamlConstructor());
            return Json.mapper().convertValue(yaml.load(contents), expectedType);
        } catch (Throwable e) {
            return Yaml.mapper().convertValue(contents, expectedType);
        }
    }

    static class SnakeException extends RuntimeException {
        public SnakeException() {
            super();
        }
        public SnakeException(String msg) {
            super(msg);
        }

        public SnakeException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    static class CustomSnakeYamlConstructor extends SafeConstructor {

        private boolean checkNode(MappingNode node, Integer depth) {
            if (node.getValue() == null) return true;
            if (depth > maxDepth) return false;
            int currentDepth = depth;
            List<NodeTuple> list = node.getValue();
            for (NodeTuple t : list) {
                Node n = t.getKeyNode();
                if (n instanceof MappingNode) {
                    boolean res = checkNode((MappingNode) n, currentDepth + 1);
                    if (!res) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public Object getSingleData(Class<?> type) {
            try {
                Node node = this.composer.getSingleNode();
                if (node != null) {
                    if (node instanceof MappingNode) {
                        if (!checkNode((MappingNode) node, new Integer(0))) {
                            throw new SnakeException("yaml tree depth exceeds max " + maxDepth);
                        }
                    }
                    if (Object.class != type) {
                        node.setTag(new Tag(type));
                    } else if (this.rootTag != null) {
                        node.setTag(this.rootTag);
                    }

                    return this.constructDocument(node);
                } else {
                    return null;
                }
            } catch (StackOverflowError e) {
                throw new SnakeException("StackOverflow safe-checking yaml content (maxDepth " + maxDepth + ")", e);
            } catch (Throwable e) {
                throw new SnakeException("Exception safe-checking yaml content  (maxDepth " + maxDepth + ")", e);
            }
        }
    }
}
