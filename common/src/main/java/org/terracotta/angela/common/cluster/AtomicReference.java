/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicReference;

public class AtomicReference<T> {

  private final IgniteAtomicReference<T> igniteReference;

  AtomicReference(Ignite ignite, String name, T initialValue) {
    igniteReference = ignite.atomicReference("Atomic-Reference-" + name, initialValue, true);
  }

  public void set(T value) {
    igniteReference.set(value);
  }

  public boolean compareAndSet(T expect, T update) {
    return igniteReference.compareAndSet(expect, update);
  }

  public T get() {
    return igniteReference.get();
  }
}
