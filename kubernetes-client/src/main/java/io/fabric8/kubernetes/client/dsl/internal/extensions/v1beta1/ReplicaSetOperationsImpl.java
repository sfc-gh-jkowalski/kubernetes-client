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
package io.fabric8.kubernetes.client.dsl.internal.extensions.v1beta1;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.extensions.DeploymentRollback;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetList;
import io.fabric8.kubernetes.client.Client;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.*;
import io.fabric8.kubernetes.client.dsl.internal.HasMetadataOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.OperationContext;
import io.fabric8.kubernetes.client.dsl.internal.RollingOperationContext;
import io.fabric8.kubernetes.client.dsl.internal.apps.v1.RollableScalableResourceOperation;
import io.fabric8.kubernetes.client.dsl.internal.apps.v1.RollingUpdater;
import io.fabric8.kubernetes.client.utils.internal.PodOperationUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ReplicaSetOperationsImpl
    extends RollableScalableResourceOperation<ReplicaSet, ReplicaSetList, RollableScalableResource<ReplicaSet>>
    implements TimeoutImageEditReplacePatchable<ReplicaSet> {

  public ReplicaSetOperationsImpl(Client client) {
    this(new RollingOperationContext(), HasMetadataOperationsImpl.defaultContext(client));
  }

  ReplicaSetOperationsImpl(RollingOperationContext context, OperationContext superContext) {
    super(context, superContext.withApiGroupName("extensions")
        .withApiGroupVersion("v1beta1")
        .withPlural("replicasets"), ReplicaSet.class, ReplicaSetList.class);
  }

  @Override
  public ReplicaSetOperationsImpl newInstance(OperationContext context) {
    return new ReplicaSetOperationsImpl(rollingOperationContext, context);
  }

  @Override
  public ReplicaSetOperationsImpl newInstance(RollingOperationContext context) {
    return new ReplicaSetOperationsImpl(context, this.context);
  }

  @Override
  public ReplicaSet pause() {
    throw new UnsupportedOperationException(context.getPlural() + " \"" + name + "\" pausing is not supported");
  }

  @Override
  public ReplicaSet resume() {
    throw new UnsupportedOperationException(context.getPlural() + " \"" + name + "\" resuming is not supported");
  }

  @Override
  public ReplicaSet restart() {
    throw new UnsupportedOperationException(context.getPlural() + " \"" + name + "\" restarting is not supported");
  }

  @Override
  public ReplicaSet undo() {
    throw new UnsupportedOperationException("no rollbacker has been implemented for \"" + get().getKind() + "\"");
  }

  @Override
  public ReplicaSet withReplicas(int count) {
    return accept(r -> r.getSpec().setReplicas(count));
  }

  @Override
  public RollingUpdater<ReplicaSet, ReplicaSetList> getRollingUpdater(long rollingTimeout, TimeUnit rollingTimeUnit) {
    return new ReplicaSetRollingUpdater(context.getClient(), getNamespace(), rollingTimeUnit.toMillis(rollingTimeout),
        config.getLoggingInterval());
  }

  @Override
  public int getCurrentReplicas(ReplicaSet current) {
    return current.getStatus().getReplicas();
  }

  @Override
  public int getDesiredReplicas(ReplicaSet item) {
    return item.getSpec().getReplicas();
  }

  @Override
  public long getObservedGeneration(ReplicaSet current) {
    return (current != null && current.getStatus() != null
        && current.getStatus().getObservedGeneration() != null) ? current.getStatus().getObservedGeneration() : -1;
  }

  @Override
  public Status rollback(DeploymentRollback deploymentRollback) {
    throw new KubernetesClientException("rollback not supported in case of ReplicaSets");
  }

  @Override
  public String getLog(boolean isPretty) {
    StringBuilder stringBuilder = new StringBuilder();
    List<PodResource> podOperationList = new ReplicaSetOperationsImpl(rollingOperationContext.withPrettyOutput(isPretty),
        context).doGetLog();
    for (PodResource podOperation : podOperationList) {
      stringBuilder.append(podOperation.getLog(isPretty));
    }
    return stringBuilder.toString();
  }

  private List<PodResource> doGetLog() {
    ReplicaSet replicaSet = requireFromServer();
    return PodOperationUtil.getPodOperationsForController(context,
        rollingOperationContext.getPodOperationContext(), replicaSet.getMetadata().getUid(),
        getReplicaSetSelectorLabels(replicaSet));
  }

  /**
   * Returns an unclosed Reader. It's the caller responsibility to close it.
   *
   * @return Reader
   */
  @Override
  public Reader getLogReader() {
    return PodOperationUtil.getLogReader(doGetLog());
  }

  /**
   * Returns an unclosed InputStream. It's the caller responsibility to close it.
   *
   * @return InputStream
   */
  @Override
  public InputStream getLogInputStream() {
    return PodOperationUtil.getLogInputStream(doGetLog());
  }

  @Override
  public LogWatch watchLog(OutputStream out) {
    return PodOperationUtil.watchLog(doGetLog(), out);
  }

  static Map<String, String> getReplicaSetSelectorLabels(ReplicaSet replicaSet) {
    Map<String, String> labels = new HashMap<>();

    if (replicaSet != null && replicaSet.getSpec() != null && replicaSet.getSpec().getSelector() != null) {
      labels.putAll(replicaSet.getSpec().getSelector().getMatchLabels());
    }
    return labels;
  }

  @Override
  protected List<Container> getContainers(ReplicaSet value) {
    return value.getSpec().getTemplate().getSpec().getContainers();
  }

  @Override
  public TimeTailPrettyLoggable limitBytes(int limitBytes) {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withLimitBytes(limitBytes), context);
  }

  @Override
  public TimeTailPrettyLoggable terminated() {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withTerminatedStatus(true), context);
  }

  @Override
  public Loggable withPrettyOutput() {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withPrettyOutput(true), context);
  }

  @Override
  public PrettyLoggable tailingLines(int lines) {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withTailingLines(lines), context);
  }

  @Override
  public TailPrettyLoggable sinceTime(String timestamp) {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withSinceTimestamp(timestamp), context);
  }

  @Override
  public TailPrettyLoggable sinceSeconds(int seconds) {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withSinceSeconds(seconds), context);
  }

  @Override
  public BytesLimitTerminateTimeTailPrettyLoggable usingTimestamps() {
    return new ReplicaSetOperationsImpl(rollingOperationContext.withTimestamps(true), context);
  }
}
