package tech.tinkmaster.commons.kubernetes;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.custom.Quantity;

import java.math.BigDecimal;
import java.util.Map;

/**
 * This class extract cpu and memory resource from Kubernetes Quantity.
 * https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
 * <p>
 * All the suffix transformations are referred by SuffixFormatter.
 * https://github.com/kubernetes-client/java/blob/master/kubernetes/src/main/java/io/kubernetes/client/custom/SuffixFormatter.java
 */
public class ResourceUtil {
    public static final String RESOURCE_KEY_CPU = "cpu";
    public static final String RESOURCE_KEY_MEMORY = "memory";

    public static final String RESOURCE_SPEC_REQUEST_KEY = "request";


    /**
     * CPU will be calculated by 'm' base.
     *
     * @param map quantity map
     * @return cpu based on 'm'
     */
    public static double extractCpu(Map<String, Quantity> map) {
        Quantity cpu = map.get(RESOURCE_KEY_CPU);
        if (Quantity.Format.DECIMAL_SI.equals(cpu.getFormat())) {
            return cpu.getNumber().multiply(BigDecimal.valueOf(1000)).doubleValue();
        } else {
            throw new IllegalArgumentException("Not supported quantity format " + cpu.getFormat());
        }
    }

    /**
     * MEMORY will be calculated by 'Mi' base.
     *
     * @param map quantity map
     * @return memory based on 'Mi'
     */
    public static double extractMemory(Map<String, Quantity> map) {
        Quantity memory = map.get(RESOURCE_KEY_MEMORY);
        if (Quantity.Format.BINARY_SI.equals(memory.getFormat())) {
            return memory.getNumber().divide(BigDecimal.valueOf(2).pow(20)).doubleValue();
        } else if (Quantity.Format.DECIMAL_SI.equals(memory.getFormat())) {
            return memory.getNumber().divide(BigDecimal.valueOf(10).pow(6)).doubleValue();
        }
        else {
            throw new IllegalArgumentException("Not supported quantity format " + memory.getFormat());
        }
    }


    public static Map<String, Quantity> buildResource(double cpu, double memory) {
        return ImmutableMap.of(RESOURCE_KEY_CPU, Quantity.fromString(cpu + "m"), RESOURCE_KEY_MEMORY, Quantity.fromString(memory + "Mi"));
    }

}
