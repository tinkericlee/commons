package tech.tinkmaster.commons.kubernetes;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1Node;
import io.kubernetes.client.models.V1NodeList;
import io.kubernetes.client.models.V1PodList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class KubernetesUtil {
  private static final String POD_SPEC_FIELD_NODE_NAME = "spec.nodeName";
  private static final String POD_STATUS_FIELD_PHASE = "status.phase";

  private static final String POD_STATUS_CONDITION_TYPE_READY = "Ready";

  public static Map<String, Map<String, Quantity>> calculateClusterRemainResource(
      ApiClient apiClient) throws ApiException {
    V1NodeList nodeList = listAllNodes(apiClient);
    Map<String, Map<String, Quantity>> quantities =
        nodeList
            .getItems()
            .stream()
            .filter(KubernetesUtil::isNodeReady)
            .collect(
                Collectors.toMap(
                    node -> node.getMetadata().getName(),
                    node -> node.getStatus().getAllocatable()));
    Map<String, Map<String, Quantity>> remainResources = new HashMap<>();

    quantities.forEach(
        (s, stringQuantityMap) -> {
          try {
            AtomicReference<Double> containerCpu =
                new AtomicReference<>(ResourceUtil.extractCpu(stringQuantityMap));
            AtomicReference<Double> containerMemory =
                new AtomicReference<>(ResourceUtil.extractMemory(stringQuantityMap));

            V1PodList podList = listPodOnThisNode(apiClient, s, "Running");
            podList
                .getItems()
                .forEach(
                    pod -> {
                      pod.getSpec()
                          .getContainers()
                          .forEach(
                              container -> {
                                Map<String, Quantity> resource =
                                    container.getResources().getRequests();
                                if (resource != null) {
                                  if (resource.containsKey(ResourceUtil.RESOURCE_KEY_CPU)) {
                                    containerCpu.updateAndGet(
                                        v -> v - ResourceUtil.extractCpu(resource));
                                  }
                                  if (resource.containsKey(ResourceUtil.RESOURCE_KEY_MEMORY)) {
                                    containerMemory.updateAndGet(
                                        v -> v - ResourceUtil.extractMemory(resource));
                                  }
                                }
                              });
                    });

            remainResources.put(
                s, ResourceUtil.buildResource(containerCpu.get(), containerMemory.get()));
          } catch (ApiException e) {
            throw new IllegalStateException(e);
          }
        });
    return remainResources;
  }

  public static Map<String, Quantity> getClusterAllocatableResource(ApiClient apiClient)
      throws ApiException {
    V1NodeList nodeList = listAllNodes(apiClient);
    List<Map<String, Quantity>> quantities =
        nodeList
            .getItems()
            .stream()
            .filter(KubernetesUtil::isNodeReady)
            .map(node -> node.getStatus().getAllocatable())
            .collect(Collectors.toList());

    double cpu = 0;
    double memory = 0;
    for (Map<String, Quantity> quantity : quantities) {
      cpu += ResourceUtil.extractCpu(quantity);
      memory += ResourceUtil.extractMemory(quantity);
    }

    return ImmutableMap.of(
        ResourceUtil.RESOURCE_KEY_CPU,
        Quantity.fromString(cpu + "m"),
        ResourceUtil.RESOURCE_KEY_MEMORY,
        Quantity.fromString(memory + "Mi"));
  }

  public static V1PodList listPodOnThisNode(
      ApiClient apiClient, String nodeName, String statusPhase) throws ApiException {
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    ImmutableMap.Builder<String, String> fieldSelector = ImmutableMap.builder();
    fieldSelector.put(POD_SPEC_FIELD_NODE_NAME, nodeName);
    if (StringUtils.isNotBlank(statusPhase)) {
      fieldSelector.put(POD_STATUS_FIELD_PHASE, statusPhase);
    }
    return coreV1Api.listPodForAllNamespaces(
        null, buildSelector(fieldSelector.build()), null, null, null, null, null, null);
  }

  public static V1NodeList listAllNodes(ApiClient apiClient) throws ApiException {
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    return coreV1Api.listNode(null, null, null, null, null, null, null, null);
  }

  private static boolean isNodeReady(V1Node node) {
    return node.getStatus()
        .getConditions()
        .stream()
        .anyMatch(
            condition ->
                POD_STATUS_CONDITION_TYPE_READY.equals(condition.getType())
                    && Boolean.parseBoolean(condition.getStatus()));
  }

  private static String buildSelector(Map<String, String> fieldsMap) {
    if (fieldsMap.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("");
    fieldsMap.forEach(
        (s1, s2) -> {
          sb.append(s1);
          sb.append("=");
          sb.append(s2);
          sb.append(",");
        });
    return sb.deleteCharAt(sb.lastIndexOf(",")).toString();
  }
}
