package org.mds.ray;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.util.*;


@Slf4j
public class KubernetesTest {
    @Test
    public void testService() {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {
            client.services()
                    .inNamespace("default")
                    .list()
                    .getItems()
                    .forEach(hasMetadataResource -> {
                        log.info("Service: " + hasMetadataResource);
                    });
        }
    }

    @Test
    public void createService() throws Exception {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {
            Service service = new Yaml().loadAs(new ClassPathResource("default-ray-service.yaml").getInputStream(), Service.class);
            service.getSpec().getPorts().forEach(servicePort -> {
                servicePort.getTargetPort().setIntVal(Integer.parseInt(servicePort.getTargetPort().getStrVal()));
                servicePort.getTargetPort().setStrVal(null);
            });
//            Service service1 = new Service();
//            service1.setApiVersion("v1");
//            service1.setKind("Service");
//            ObjectMeta meta = new ObjectMeta();
//            meta.setName("service-ray-cluster");
//            service1.setMetadata(meta);
//            Map<String, String> labels = new HashMap<>();
//            labels.put("app", "ray-cluster-head");
//            meta.setLabels(labels);
//            ServiceSpec spec = new ServiceSpec();
//            spec.setClusterIP("None");
//            List<ServicePort> ports = new ArrayList<>();
//            ServicePort port1 = new ServicePort();
//            port1.setName("client");
//            port1.setPort(10001);
//            port1.setTargetPort(new IntOrString(10001));
//            port1.setProtocol("TCP");
//            ports.add(port1);
//            spec.setPorts(ports);
//            service1.setSpec(spec);
            client.services()
                    .inNamespace("default")
                    .create(service);
        }
    }

    @Test
    public void createHead() throws Exception {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {
            Deployment service = new Yaml().loadAs(new ClassPathResource("default-ray-head.yaml").getInputStream(), Deployment.class);
            client.apps().deployments()
                    .inNamespace("default")
                    .create(service);
        }
    }

    @Test
    public void createWorker() throws Exception {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {
            Deployment service = new Yaml().loadAs(new ClassPathResource("default-ray-worker.yaml").getInputStream(), Deployment.class);
            client.apps().deployments()
                    .inNamespace("default")
                    .create(service);
        }
    }
}
