/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.examples.kubectl.equivalents;

import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is Java equivalent of kubectl : `kubectl auth can-i create deployment.apps`
 */
public class CanIEquivalent {
  private static final Logger logger = LoggerFactory.getLogger(CanIEquivalent.class);

  public static void main(String[] args) {
    try (KubernetesClient client = new KubernetesClientBuilder().build()) {
      SelfSubjectAccessReview ssar = new SelfSubjectAccessReviewBuilder()
        .withNewSpec()
        .withNewResourceAttributes()
        .withGroup("apps")
        .withResource("deployments")
        .withVerb("create")
        .withNamespace("dev")
        .endResourceAttributes()
        .endSpec()
        .build();

      ssar = client.authorization().v1().selfSubjectAccessReview().create(ssar);

      logger.info("Allowed: {}", ssar.getStatus().getAllowed());
    }
  }
}
