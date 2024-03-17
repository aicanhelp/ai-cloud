package org.mds.ray;

import io.fabric8.kubernetes.api.model.Service;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class SnakeYamlTest {
    @Test
    public void testLoad() throws Exception {
        Object o = new Yaml().load(new ClassPathResource("ray-service.yaml").getInputStream());
        log.info("" + o);
    }

    @Test
    public void testLoadAs() throws Exception {
        Service service = new Yaml().loadAs(new ClassPathResource("default-ray-service.yaml").getInputStream(), Service.class);
        log.info("" + service);
    }

}
