package org.mds.ray.domain.kubernetes.template;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang.StringUtils;

@Getter
@Setter
@Accessors(chain = true)
public class K8sRayClusterTemplate {
    private String name;

    private SubTemplate service;
    private SubTemplate head;
    private SubTemplate worker;


    @Getter
    @Setter
    @Accessors(chain = true)
    public static class SubTemplate {
        private String content;
        private String url;

        public String getContent() {
            if (!StringUtils.isBlank(this.content)) {
                return this.content;
            }
            this.content = this.load();
            return this.content;
        }

        private String load() {
            return "";
        }
    }
}
